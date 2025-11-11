package com.guitarclass.service

import com.guitarclass.domain.Comment
import com.guitarclass.dto.CommentRequest
import com.guitarclass.dto.CommentResponse
import com.guitarclass.repository.CommentRepository
import com.guitarclass.repository.PostRepository
import com.guitarclass.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CommentService(
    private val commentRepository: CommentRepository,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository
) {

    fun getCommentsByPostId(postId: Long): List<CommentResponse> {
        // 게시글 존재 여부 확인
        postRepository.findById(postId)
            .orElseThrow { IllegalArgumentException("게시글을 찾을 수 없습니다") }

        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId)
            .map { CommentResponse.from(it) }
    }

    @Transactional
    fun createComment(postId: Long, request: CommentRequest, userId: Long): CommentResponse {
        val post = postRepository.findById(postId)
            .orElseThrow { IllegalArgumentException("게시글을 찾을 수 없습니다") }

        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다") }

        val comment = Comment(
            content = request.content,
            post = post,
            author = user
        )

        val savedComment = commentRepository.save(comment)
        return CommentResponse.from(savedComment)
    }

    @Transactional
    fun updateComment(commentId: Long, request: CommentRequest, userId: Long): CommentResponse {
        val comment = findCommentById(commentId)

        if (comment.author.id != userId) {
            throw IllegalArgumentException("댓글 수정 권한이 없습니다")
        }

        comment.content = request.content

        return CommentResponse.from(comment)
    }

    @Transactional
    fun deleteComment(commentId: Long, userId: Long) {
        val comment = findCommentById(commentId)

        if (comment.author.id != userId) {
            throw IllegalArgumentException("댓글 삭제 권한이 없습니다")
        }

        commentRepository.delete(comment)
    }

    private fun findCommentById(commentId: Long): Comment {
        return commentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("댓글을 찾을 수 없습니다") }
    }
}
