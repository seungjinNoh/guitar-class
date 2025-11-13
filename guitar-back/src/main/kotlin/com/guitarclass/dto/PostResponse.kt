package com.guitarclass.dto

import com.guitarclass.domain.Post
import java.time.LocalDateTime

data class PostResponse(
    val id: Long,
    val title: String,
    val content: String,
    val authorId: Long,
    val authorNickname: String,
    val viewCount: Long,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val commentCount: Int,
    val attachmentCount: Int,
    val attachments: List<AttachmentResponse>
) {
    companion object {
        fun from(post: Post): PostResponse {
            return PostResponse(
                id = post.id!!,
                title = post.title,
                content = post.content,
                authorId = post.author.id!!,
                authorNickname = post.author.nickname,
                viewCount = post.viewCount,
                createdAt = post.createdAt,
                updatedAt = post.updatedAt,
                commentCount = post.comments.size,
                attachmentCount = post.attachments.size,
                attachments = post.attachments.map { AttachmentResponse.from(it) }
            )
        }
    }
}

data class PostListResponse(
    val id: Long,
    val title: String,
    val authorNickname: String,
    val viewCount: Long,
    val createdAt: LocalDateTime,
    val commentCount: Int
) {
    companion object {
        fun from(post: Post): PostListResponse {
            return PostListResponse(
                id = post.id!!,
                title = post.title,
                authorNickname = post.author.nickname,
                viewCount = post.viewCount,
                createdAt = post.createdAt,
                commentCount = post.comments.size
            )
        }
    }
}
