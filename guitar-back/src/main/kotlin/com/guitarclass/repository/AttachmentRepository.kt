package com.guitarclass.repository

import com.guitarclass.domain.Attachment
import com.guitarclass.domain.Post
import org.springframework.data.jpa.repository.JpaRepository

interface AttachmentRepository : JpaRepository<Attachment, Long> {
    fun findByPost(post: Post): List<Attachment>
    fun findByPostId(postId: Long): List<Attachment>
    fun findByPostIsNull(): List<Attachment>
    fun findByIdIn(ids: List<Long>): List<Attachment>
}
