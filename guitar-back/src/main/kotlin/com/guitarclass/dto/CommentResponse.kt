package com.guitarclass.dto

import com.guitarclass.domain.Comment
import java.time.LocalDateTime

data class CommentResponse(
    val id: Long,
    val content: String,
    val postId: Long,
    val authorId: Long,
    val authorNickname: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(comment: Comment): CommentResponse {
            return CommentResponse(
                id = comment.id!!,
                content = comment.content,
                postId = comment.post.id!!,
                authorId = comment.author.id!!,
                authorNickname = comment.author.nickname,
                createdAt = comment.createdAt,
                updatedAt = comment.updatedAt
            )
        }
    }
}
