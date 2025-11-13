package com.guitarclass.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class PostRequest(
    @field:NotBlank(message = "제목은 필수입니다")
    @field:Size(max = 200, message = "제목은 200자를 초과할 수 없습니다")
    val title: String,

    @field:NotBlank(message = "내용은 필수입니다")
    val content: String,

    val attachmentIds: List<Long>? = null
)
