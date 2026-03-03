/*
 * Copyright 2022 LLC Campus.
 */

package ru.campus.parsers.donstu.group

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.apache.logging.log4j.Logger
import ru.campus.parser.sdk.DateProvider
import ru.campus.parser.sdk.base.ScheduleCollector
import ru.campus.parser.sdk.model.Entity
import ru.campus.parser.sdk.model.ExplicitDatePredicate
import ru.campus.parser.sdk.model.Schedule
import ru.campus.parser.sdk.model.TimeTableInterval
import ru.campus.parser.sdk.model.WeekScheduleItem
import ru.campus.parsers.donstu.model.DonStuRaspDatesResponse
import ru.campus.parsers.donstu.model.DonStuScheduleResponse

class DonStuGroupScheduleCollector(
    private val httpClient: HttpClient,
    private val logger: Logger,
    private val dateProvider: DateProvider,
) : ScheduleCollector {
    private val baseUrl = "https://edu.donstu.ru/api"
    private val json = Json { ignoreUnknownKeys = true }
    override suspend fun collectSchedule(
        entity: Entity,
        intervals: List<TimeTableInterval>,
    ): ScheduleCollector.Result {
        // пауза перед каждой группой
        //delay(300)
        val datesResponse = getWithRetry("$baseUrl/GetRaspDates?idGroup=${entity.code}")

        val datesData = json.decodeFromString<DonStuRaspDatesResponse>(datesResponse)

        val today = dateProvider.getCurrentDateTime().date
        val targetDates = datesData.data.dates
            .map { LocalDate.parse(it) }
            .filter { it >= today }
            .take(28)

        if (targetDates.isEmpty()) {
            logger.info("Нет дат расписания для группы {} (id={}), даты с сервера: {}",
                entity.name, entity.code, datesData.data.dates)
            return ScheduleCollector.Result(
                processedEntity = entity,
                weekScheduleItems = emptyList()
            )
        }

        // Шаг 2: расписание на каждую дату
        val weekScheduleItems = mutableListOf<WeekScheduleItem>()

        for (date in targetDates) {
            // Задержка пред каждой группой так как слищком много одновременных запросов (Наверно можно в BaseParser изменить количество паралльленых )
            //delay(200)
            val scheduleResponse = getWithRetry("$baseUrl/Rasp?idGroup=${entity.code}&sdate=$date")

            val schedule = json.decodeFromString<DonStuScheduleResponse>(scheduleResponse)

            if (schedule.data.rasp.isEmpty()) continue

            // Строим интервалы по номеру занятия из JSON
            schedule.data.rasp.forEach { lesson ->
                val parsed = DonStuDisciplineParser.parse(lesson.disciplina)

                val interval = TimeTableInterval(
                    lessonNumber = lesson.nomerZanyatiya,
                    startTime = lesson.nachalo,
                    endTime = lesson.konec,
                )

                // Ссылка на вебинар если есть
                val links = listOfNotNull(
                    lesson.ssylka
                        ?.takeIf { it.isNotBlank() }
                        ?.let { Schedule.Link(title = "Ссылка", url = it) }
                )

                weekScheduleItems.add(
                    WeekScheduleItem(
                        dayOfWeek = date.dayOfWeek,
                        timeTableInterval = interval,
                        dayCondition = ExplicitDatePredicate(date),
                        lesson = Schedule.Lesson(
                            subject = parsed.subject,
                            type = parsed.type,
                            classroom = lesson.auditoriya
                                ?.takeIf { if (it.length <= 1) { // it.isNotBlank()
                                    logger.warn("Некорректная аудитория '{}' у группы {} на дату {}", it, entity.name, date)
                                    false
                                } else {
                                    true
                                }},
                            building = null, // корпуса нет в API
                            teachers = listOfNotNull(
                                lesson.prepodavatel
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let {
                                        Schedule.Entity(
                                            name = it,
                                            code = lesson.kodPrepodavatelya?.toString()
                                        )
                                    }
                            ),
                            links = links,
                        )
                    )
                )
            }
        }

        return ScheduleCollector.Result(

            processedEntity = entity,
            weekScheduleItems = weekScheduleItems,
        )
    }
    private suspend fun getWithRetry(url: String): String {
        while (true) {
            try {
                val response: String = httpClient.get(url) {
                    headers {
                        append(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        append(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    }
                }.body()
                if (!response.contains("Too many requests")) return response
                logger.warn("Too many requests для {}. Ждём 8 секунд...", url)
                delay(8_000)
            } catch (e: java.net.SocketException) {
                logger.warn("Сетевая ошибка для {} : {}. Ждём 5 секунд...", url, e.message)
                delay(5_000)
            }
        }
    }
}
object DonStuDisciplineParser {

    private val PREFIXES = mapOf(
        "лек" to "Лекция",
        "пр" to "Практика",
        "лаб" to "Лабораторная",
        "сем" to "Семинар",
        "конс" to "Консультация",
        "зач" to "Зачет",
        "экз" to "Экзамен",
        "кп" to "Курсовой проект",
        "кр" to "Курсовая работа",
    )

    data class ParsedDiscipline(
        val subject: String,
        val type: String?,
    )

    fun parse(raw: String): ParsedDiscipline {
        val trimmed = raw.trim()
        for ((prefix, typeName) in PREFIXES) {
            if (trimmed.startsWith("$prefix ", ignoreCase = true)) {
                val subject = trimmed.removePrefix("$prefix ")
                    .trim()
                return ParsedDiscipline(subject = subject, type = typeName)
            }
        }
        // Префикс не найден — возвращаем как есть
        return ParsedDiscipline(subject = trimmed, type = null)
    }

}
