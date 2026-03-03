/*
 * Copyright 2026 LLC Campus.
 */

package ru.campus.parsers.donstu.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DonStuScheduleResponse(
    val data: DonStuScheduleData,
    val state: Int? = null,
)

@Serializable
data class DonStuScheduleData(
    val isCyclicalSchedule: Boolean,
    val rasp: List<DonStuLesson>,
)

@Serializable
data class DonStuLesson(
    @SerialName("код") val kod: Int,
    @SerialName("дата") val data: String,
    @SerialName("начало") val nachalo: String,
    @SerialName("конец") val konec: String,
    @SerialName("деньНедели") val denNedeli: Int,
    @SerialName("день_недели") val denNedeliStr: String,
    @SerialName("номерЗанятия") val nomerZanyatiya: Int,
    @SerialName("дисциплина") val disciplina: String,
    @SerialName("преподаватель") val prepodavatel: String? = null,
    @SerialName("кодПреподавателя") val kodPrepodavatelya: Int? = null,
    @SerialName("аудитория") val auditoriya: String? = null,
    @SerialName("номерПодгруппы") val nomerPodgruppy: Int? = null,
    @SerialName("ссылка") val ssylka: String? = null,
    @SerialName("замена") val zamena: Boolean = false,
    @SerialName("типНедели") val tipNedeli: Int? = null,
)