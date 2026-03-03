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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.apache.logging.log4j.Logger
import ru.campus.parser.sdk.base.EntitiesCollector
import ru.campus.parser.sdk.model.Entity
import ru.campus.parsers.donstu.model.DonStuGroupListResponse

class DonStuGroupEntitiesCollector(
    private val httpClient: HttpClient,
    private val logger: Logger,
) : EntitiesCollector {

    private val baseUrl = "https://edu.donstu.ru/api"
    private val json = Json { ignoreUnknownKeys = true }
    override suspend fun collectEntities(): List<Entity> {
        logger.info("Загружаем список групп!")
        val response: String = getWithRetry("$baseUrl/raspGrouplist")

        val parsed = json.decodeFromString<DonStuGroupListResponse>(response)

        logger.info("Получено групп: {}", parsed.data.size)
        return parsed.data.map { group ->
            // Поиск где падает парсер, а именно факультет где название < 1 символ
//            require(group.facul != null && group.facul.length > 1) {
//                "Некорректный факультет у группы ${group.name} (id=${group.id}): '${group.facul}'"
//            }
            Entity(
                type = Entity.Type.Group,
                name = group.name,
                code = group.id.toString(),
                scheduleUrl = "https://edu.donstu.ru/webapp/#/Rasp?idGroup=${group.id}",
                extra = Entity.Extra(
                    faculty = if (group.facul != null && group.facul.length <= 1) {
                        "${group.facul} (Доп образование)"
                    } else {
                        group.facul
                    },
                    course = group.kurs,
                )
            )
        }

    }
    // Метод для повтора попытки сбора
    private suspend fun getWithRetry(url: String): String {
        while(true)  {
            try {
                val response: String = httpClient.get(url) {
                    headers {
                        append(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        append(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    }
                }.body()
                if (!response.contains("Too many requests")) return response
                logger.warn("Too many requests для {}. Ждём 5 секунд...", url)
                delay(5_000)
            } catch (e: java.net.SocketException) {
                logger.warn("Сетевая ошибка для {} : {}. Ждём 4 секунд...", url, e.message)
                delay(4_000)
            }
        }
    }
}




