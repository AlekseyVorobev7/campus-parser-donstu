/*
 * Copyright 2026 LLC Campus.
 */

package ru.campus.parsers.donstu.model

import kotlinx.serialization.Serializable

@Serializable
data class DonStuRaspDatesResponse(
    val data: DonStuRaspDatesData,
    val state: Int? = null,
)

@Serializable
data class DonStuRaspDatesData(
    // Сервак ДГТУ присылает для некоторых групп json в котором данные о семестре пустые поэтому возможен null
    // В коллекторе есть вывод в логи информации о том что дата нет или они пустые
    val minDate: String?,
    val maxDate: String?,
    val selDate: String?,
    val dates: List<String>,
)
