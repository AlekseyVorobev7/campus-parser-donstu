/*
 * Copyright 2022 LLC Campus.
 */

package ru.campus.parsers.donstu.group

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        val datesResponse = getWithRetry("$baseUrl/GetRaspDates?idGroup=${entity.code}")
        val datesData = json.decodeFromString<DonStuRaspDatesResponse>(datesResponse)
        val today = dateProvider.getCurrentDateTime().date
        val targetDates = datesData.data.dates
            .map { LocalDate.parse(it) }
            .filter { it >= today }
            .take(28)

        if (targetDates.isEmpty()) {
            logger.info(
                "Нет дат расписания для группы {} (id={}), даты с сервера: {}",
                entity.name, entity.code, datesData.data.dates
            )
            return ScheduleCollector.Result(
                processedEntity = entity,
                weekScheduleItems = emptyList()
            )
        }

        val weekScheduleItems = coroutineScope {
            targetDates
                .map { date ->
                    async {
                        val scheduleResponse = getWithRetry("$baseUrl/Rasp?idGroup=${entity.code}&sdate=$date")
                        Pair(date, json.decodeFromString<DonStuScheduleResponse>(scheduleResponse))
                    }
                }
                .awaitAll()
                .flatMap { (date, schedule) ->
                    if (schedule.data.rasp.isEmpty()) return@flatMap emptyList()
                    schedule.data.rasp.mapNotNull { lesson ->
                        if (lesson.nachalo == null || lesson.konec == null || lesson.nomerZanyatiya == null) {
                            logger.warn(
                                "Нет временного интервала для пары '{}' группы {} на дату {}",
                                lesson.disciplina, entity.name, date
                            )
                            return@mapNotNull null
                        }
                        val parsed = DonStuDisciplineParser.parse(lesson.disciplina)
                        WeekScheduleItem(
                            dayOfWeek = date.dayOfWeek,
                            timeTableInterval = TimeTableInterval(
                                lessonNumber = lesson.nomerZanyatiya,
                                startTime = lesson.nachalo,
                                endTime = lesson.konec,
                            ),
                            dayCondition = ExplicitDatePredicate(date),
                            lesson = Schedule.Lesson(
                                subject = parsed.subject,
                                type = parsed.type,
                                classroom = lesson.auditoriya?.takeIf { it.length > 1 },
                                building = null,
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
                                links = listOfNotNull(
                                    lesson.ssylka
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let { Schedule.Link(title = "Ссылка", url = it) }
                                ),
                            )
                        )
                    }
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
                        append(
                            HttpHeaders.UserAgent,
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        )
                        append(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    }
                }.body()
                if (!response.contains("Too many requests")) return response
                logger.warn("Too many requests для {}. Ждём 8 секунд...", url)
                delay(8_000)
            } catch (e: java.io.IOException) {
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
                return ParsedDiscipline(
                    subject = trimmed.removePrefix("$prefix ").trim(),
                    type = typeName
                )
            }
        }
        return ParsedDiscipline(subject = trimmed, type = null)
    }
}