package com.guitarclass.exception

import java.time.LocalDateTime

/**
 * 표준 에러 응답 형식
 */
data class ErrorResponse(
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val status: Int,
    val error: String,
    val message: String,
    val path: String? = null
)

/**
 * Validation 에러 응답 형식
 */
data class ValidationErrorResponse(
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val status: Int,
    val error: String,
    val message: String,
    val errors: List<FieldError>,
    val path: String? = null
)

data class FieldError(
    val field: String,
    val message: String,
    val rejectedValue: Any?
)
