/*
 * Copyright 2026 LLC Campus.
 */

package ru.campus.parsers.donstu.model

import kotlinx.serialization.Serializable

@Serializable
data class DonStuGroupListResponse(
    val data: List<DonStuGroup>,
    val state: Int? = null,
)

@Serializable
data class DonStuGroup(
    val name: String,
    val id: Int,
    val kurs: Int? = null,
    val facul: String? = null,
    val yearName: String? = null,
    val facultyID: Int? = null,
)
