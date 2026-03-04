package ru.campus.parsers.donstu

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import ru.campus.parser.sdk.model.Credentials
import ru.campus.parser.sdk.model.ParserResult
import ru.campus.parsers.tests.sdk.dump.createDumpMockHttpClient
import ru.campus.parsers.tests.sdk.dump.createDumpMockParserApi
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DumpParserTest {

    @Test
    fun success() = runTest {
        val collector = DonStuParser(
            credentials = Credentials("", ""),
            httpClient = createDumpMockHttpClient(),
            parserApi = createDumpMockParserApi(),
            dateProvider = { LocalDateTime.parse("2026-04-18T00:00:00") }
        )
        val data: ParserResult = collector.parse()

        assertEquals(
            expected = data.entitiesCount,
            actual = data.savedEntities.size
        )
    }
}
