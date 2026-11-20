package com.campuslife.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

const val ME = "me"

val CATEGORIES = listOf("Спорт", "Творчество", "Наука", "Конкурсы")

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val category: String,
    val startMillis: Long,
    val location: String,
    val imageUrl: String,
    val capacity: Int,
    val organizer: String,
    val createdByMe: Boolean = false,
)

@Entity(tableName = "registrations", primaryKeys = ["eventId", "attendeeId"])
data class RegistrationEntity(
    val eventId: Long,
    val attendeeId: String,
    val attendeeName: String,
    val checkedIn: Boolean = false,
    val createdAt: Long,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val eventId: Long)

@Entity(tableName = "reminders")
data class ReminderEntity(@PrimaryKey val eventId: Long, val minutesBefore: Int)

@Dao
interface AppDao {
    @Query("SELECT * FROM events ORDER BY startMillis") fun observeEvents(): Flow<List<EventEntity>>
    @Query("SELECT COUNT(*) FROM events") suspend fun eventCount(): Int
    @Insert suspend fun insertEvent(e: EventEntity): Long
    @Query("DELETE FROM events WHERE id = :id") suspend fun deleteEvent(id: Long)

    @Query("SELECT * FROM registrations") fun observeRegistrations(): Flow<List<RegistrationEntity>>
    @Query("SELECT * FROM registrations WHERE eventId = :e AND attendeeId = :a")
    suspend fun getReg(e: Long, a: String): RegistrationEntity?
    @Query("SELECT COUNT(*) FROM registrations WHERE eventId = :e") suspend fun regCount(e: Long): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertReg(r: RegistrationEntity)
    @Query("DELETE FROM registrations WHERE eventId = :e AND attendeeId = :a")
    suspend fun deleteReg(e: Long, a: String)
    @Query("DELETE FROM registrations WHERE eventId = :e") suspend fun deleteRegsOf(e: Long)

    @Query("SELECT * FROM favorites") fun observeFavorites(): Flow<List<FavoriteEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addFav(f: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE eventId = :id") suspend fun removeFav(id: Long)

    @Query("SELECT * FROM reminders") fun observeReminders(): Flow<List<ReminderEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertReminder(r: ReminderEntity)
    @Query("DELETE FROM reminders WHERE eventId = :id") suspend fun removeReminder(id: Long)
}

@Database(
    entities = [EventEntity::class, RegistrationEntity::class, FavoriteEntity::class, ReminderEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "campuslife.db")
                .build().also { inst = it }
        }
    }
}

/** Демо-данные: даты считаются от текущего момента, чтобы афиша всегда была актуальной. */
suspend fun AppDao.seedIfEmpty() {
    if (eventCount() > 0) return
    val h = 3_600_000L
    val d = 24 * h
    val now = System.currentTimeMillis()
    fun img(k: String) = "https://picsum.photos/seed/$k/800/450"
    data class S(val t: String, val desc: String, val c: String, val at: Long, val loc: String, val k: String, val cap: Int, val org: String, val fake: Int)
    val list = listOf(
        S("Школьный турнир по баскетболу", "Командный турнир 8–11 классов. Играем 3×3, судьи — старшеклассники. Форма спортивная, сменная обувь обязательна.", "Спорт", now + 5 * h, "Спортзал, 2 этаж", "basket", 40, "Кафедра физкультуры", 18),
        S("Мастер-класс по скетчингу", "Учимся быстро рисовать городские зарисовки маркерами и линерами. Материалы выдаём, приходите с настроением.", "Творчество", now + 1 * d + 3 * h, "Кабинет ИЗО, 204", "sketch", 20, "Арт-клуб", 12),
        S("Олимпиада по робототехнике", "Соберите и запрограммируйте робота для прохождения лабиринта за ограниченное время. Команды по 2–3 человека.", "Наука", now + 2 * d + 4 * h, "Лаборатория, 305", "robot", 30, "Клуб робототехники", 22),
        S("Конкурс чтецов «Живое слово»", "Читаем стихи и прозу русских классиков и современных авторов. Выступление до 3 минут, жюри из учителей литературы.", "Конкурсы", now + 3 * d + 2 * h, "Актовый зал", "poetry", 50, "Литературный клуб", 9),
        S("Открытая лекция «Как устроен космос»", "Приглашённый астрофизик расскажет о чёрных дырах, экзопланетах и последних открытиях телескопа JWST. Будет время для вопросов.", "Наука", now + 4 * d + 1 * h, "Актовый зал", "space", 120, "Научное общество", 64),
        S("Футбольный матч 9А — 10Б", "Дружеский матч между параллелями. Болельщики приветствуются, вход свободный.", "Спорт", now + 5 * d + 6 * h, "Школьный стадион", "football", 80, "Спортивный совет", 31),
        S("Театральная студия: открытый показ", "Постановка по мотивам «Маленького принца». После показа — обсуждение и набор в студию.", "Творчество", now + 6 * d + 5 * h, "Актовый зал", "theatre", 100, "Театральная студия", 45),
        S("Хакатон «Школа будущего»", "6 часов, чтобы придумать и показать прототип приложения для школы. Команды 3–4 человека, менторы из IT-компаний.", "Конкурсы", now + 8 * d + 2 * h, "Библиотека, медиазона", "hack", 60, "IT-клуб", 38),
        S("Шахматный турнир", "Блиц-турнир по швейцарской системе, 7 туров. Победители получат кубки и призы от родительского комитета.", "Спорт", now + 9 * d + 4 * h, "Кабинет 112", "chess", 32, "Шахматный клуб", 14),
        S("Химическое шоу", "Безопасные и эффектные опыты: слоновья зубная паста, цветной огонь, жидкий азот. Для 5–11 классов.", "Наука", now + 11 * d + 3 * h, "Кабинет химии, 118", "chem", 45, "Кафедра естественных наук", 27),
    )
    val names = listOf("Анна", "Иван", "Мария", "Дмитрий", "София", "Артём", "Полина", "Егор", "Алиса", "Максим", "Ева", "Кирилл")
    list.forEach { s ->
        val id = insertEvent(EventEntity(0, s.t, s.desc, s.c, s.at, s.loc, img(s.k), s.cap, s.org))
        repeat(s.fake) { i ->
            upsertReg(RegistrationEntity(id, "seed$i", names[i % names.size] + " №${i + 1}", false, now - i * h))
        }
    }
}
