package com.campuslife.data

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class Filters(
    val query: String = "",
    val category: String? = null,
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val dateLabel: String = "Все даты",
)

data class EventUi(
    val event: EventEntity,
    val registered: Int,
    val isRegistered: Boolean,
    val isFav: Boolean,
    val reminderMin: Int?,
) {
    val full get() = registered >= event.capacity
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    private val prefs = app.getSharedPreferences("campuslife", 0)

    val userName = MutableStateFlow(prefs.getString("name", "Ученик") ?: "Ученик")
    val isOrganizer = MutableStateFlow(prefs.getBoolean("organizer", false))
    val filters = MutableStateFlow(Filters())
    val message = MutableStateFlow<String?>(null)

    val registrations: StateFlow<List<RegistrationEntity>> = dao.observeRegistrations()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val all: StateFlow<List<EventUi>> = combine(
        dao.observeEvents(), dao.observeRegistrations(), dao.observeFavorites(), dao.observeReminders(),
    ) { events, regs, favs, rems ->
        val favIds = favs.map { it.eventId }.toSet()
        val remMap = rems.associate { it.eventId to it.minutesBefore }
        val byEvent = regs.groupBy { it.eventId }
        events.map { e ->
            val r = byEvent[e.id].orEmpty()
            EventUi(e, r.size, r.any { it.attendeeId == ME }, e.id in favIds, remMap[e.id])
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Лента с учётом поиска и фильтров. Прошедшие события скрываются. */
    val feed: StateFlow<List<EventUi>> = combine(all, filters) { list, f ->
        val q = f.query.trim().lowercase()
        val minStart = System.currentTimeMillis() - 2 * 3_600_000L
        list.filter { u ->
            val e = u.event
            e.startMillis >= minStart &&
                (q.isEmpty() || e.title.lowercase().contains(q)) &&
                (f.category == null || e.category == f.category) &&
                (f.dateFrom == null || e.startMillis >= f.dateFrom) &&
                (f.dateTo == null || e.startMillis < f.dateTo)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val myRegistrations = all.map { l -> l.filter { it.isRegistered }.sortedBy { it.event.startMillis } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val favorites = all.map { l -> l.filter { it.isFav }.sortedBy { it.event.startMillis } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val myEvents = all.map { l -> l.filter { it.event.createdByMe }.sortedBy { it.event.startMillis } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun find(id: Long): Flow<EventUi?> = all.map { l -> l.firstOrNull { it.event.id == id } }

    init {
        viewModelScope.launch { dao.seedIfEmpty() }
    }

    // ----- профиль -----
    fun setName(n: String) { userName.value = n; prefs.edit().putString("name", n).apply() }
    fun setOrganizer(v: Boolean) { isOrganizer.value = v; prefs.edit().putBoolean("organizer", v).apply() }
    fun consumeMessage() { message.value = null }

    // ----- фильтры -----
    fun setQuery(q: String) = filters.update { it.copy(query = q) }
    fun setCategory(c: String?) = filters.update { it.copy(category = c) }
    fun setDateRange(from: Long?, to: Long?, label: String) =
        filters.update { it.copy(dateFrom = from, dateTo = to, dateLabel = label) }

    // ----- запись -----
    fun register(eventId: Long) = viewModelScope.launch {
        val u = all.value.firstOrNull { it.event.id == eventId } ?: return@launch
        when {
            u.isRegistered -> message.value = "Вы уже записаны"
            u.full -> message.value = "Свободных мест нет"
            else -> {
                dao.upsertReg(RegistrationEntity(eventId, ME, userName.value, false, System.currentTimeMillis()))
                message.value = "Вы записаны на «${u.event.title}»"
            }
        }
    }

    fun unregister(eventId: Long) = viewModelScope.launch {
        dao.deleteReg(eventId, ME)
        message.value = "Запись отменена"
    }

    // ----- избранное и напоминания -----
    fun toggleFav(eventId: Long) = viewModelScope.launch {
        val isFav = all.value.firstOrNull { it.event.id == eventId }?.isFav == true
        if (isFav) dao.removeFav(eventId) else dao.addFav(FavoriteEntity(eventId))
    }

    fun setReminder(eventId: Long, minutes: Int?) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        val e = all.value.firstOrNull { it.event.id == eventId }?.event ?: return@launch
        if (minutes == null) {
            dao.removeReminder(eventId)
            cancelReminder(ctx, eventId)
            message.value = "Напоминание выключено"
        } else {
            dao.upsertReminder(ReminderEntity(eventId, minutes))
            scheduleReminder(ctx, e, minutes)
            message.value = "Напомним ${reminderLabel(minutes)} до начала"
        }
    }

    // ----- организатор -----
    fun createEvent(title: String, desc: String, category: String, start: Long, location: String, capacity: Int) =
        viewModelScope.launch {
            val key = title.hashCode().toString().replace("-", "n")
            dao.insertEvent(
                EventEntity(
                    title = title.trim(), description = desc.trim(), category = category, startMillis = start,
                    location = location.trim(), imageUrl = "https://picsum.photos/seed/$key/800/450",
                    capacity = capacity, organizer = userName.value, createdByMe = true,
                )
            )
            message.value = "Событие опубликовано"
        }

    fun deleteEvent(eventId: Long) = viewModelScope.launch {
        dao.deleteRegsOf(eventId); dao.removeFav(eventId); dao.removeReminder(eventId)
        cancelReminder(getApplication(), eventId)
        dao.deleteEvent(eventId)
        message.value = "Событие удалено"
    }

    /** Обработка отсканированного QR-кода (два сценария: запись на событие и отметка билета). */
    fun handleQr(raw: String) = viewModelScope.launch {
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
        if (uri == null || uri.scheme != "campuslife") { message.value = "Неизвестный QR-код"; return@launch }
        when (uri.host) {
            "event" -> {
                val id = uri.getQueryParameter("id")?.toLongOrNull()
                if (id == null) message.value = "Некорректный QR-код" else register(id)
            }
            "ticket" -> {
                if (!isOrganizer.value) { message.value = "Отмечать участников могут только организаторы"; return@launch }
                val e = uri.getQueryParameter("e")?.toLongOrNull()
                val aid = uri.getQueryParameter("id")
                val name = uri.getQueryParameter("n") ?: "Участник"
                if (e == null || aid == null) { message.value = "Некорректный билет"; return@launch }
                val ev = all.value.firstOrNull { it.event.id == e }
                if (ev == null) { message.value = "Мероприятие не найдено"; return@launch }
                val existing = dao.getReg(e, aid)
                if (existing == null && ev.full) { message.value = "Свободных мест нет"; return@launch }
                dao.upsertReg(
                    existing?.copy(checkedIn = true)
                        ?: RegistrationEntity(e, aid, name, true, System.currentTimeMillis())
                )
                message.value = "✓ $name отмечен(а) — «${ev.event.title}»"
            }
            else -> message.value = "Неизвестный QR-код"
        }
    }
}
