package com.guitarclass.controller

import com.guitarclass.dto.CommentRequest
import com.guitarclass.dto.CommentResponse
import com.guitarclass.repository.UserRepository
import com.guitarclass.service.CommentService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class CommentController(
    private val commentService: CommentService,
    private val userRepository: UserRepository
) {

    /**
     * 특정 게시글의 댓글 목록 조회
     */
    @GetMapping("/posts/{postId}/comments")
    fun getComments(@PathVariable postId: Long): ResponseEntity<List<CommentResponse>> {
        val comments = commentService.getCommentsByPostId(postId)
        return ResponseEntity.ok(comments)
    }

    /**
     * 댓글 작성 (인증 필요)
     */
    @PostMapping("/posts/{postId}/comments")
    fun createComment(
        @PathVariable postId: Long,
        @Valid @RequestBody request: CommentRequest,
        authentication: Authentication
    ): ResponseEntity<CommentResponse> {
        val userId = getUserIdFromAuthentication(authentication)
        val comment = commentService.createComment(postId, request, userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(comment)
    }

    /**
     * 댓글 수정 (인증 필요, 작성자만 가능)
     */
    @PutMapping("/comments/{id}")
    fun updateComment(
        @PathVariable id: Long,
        @Valid @RequestBody request: CommentRequest,
        authentication: Authentication
    ): ResponseEntity<CommentResponse> {
        val userId = getUserIdFromAuthentication(authentication)
        val comment = commentService.updateComment(id, request, userId)
        return ResponseEntity.ok(comment)
    }

    /**
     * 댓글 삭제 (인증 필요, 작성자만 가능)
     */
    @DeleteMapping("/comments/{id}")
    fun deleteComment(
        @PathVariable id: Long,
        authentication: Authentication
    ): ResponseEntity<Map<String, String>> {
        val userId = getUserIdFromAuthentication(authentication)
        commentService.deleteComment(id, userId)
        return ResponseEntity.ok(mapOf("message" to "댓글이 삭제되었습니다"))
    }

    /**
     * Authentication에서 User ID 추출
     */
    private fun getUserIdFromAuthentication(authentication: Authentication): Long {
        val email = authentication.name
        val user = userRepository.findByEmail(email)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다") }
        return user.id!!
    }
}
