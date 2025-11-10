package com.guitarclass.controller

import com.guitarclass.dto.PostRequest
import com.guitarclass.dto.PostResponse
import com.guitarclass.dto.PostListResponse
import com.guitarclass.repository.UserRepository
import com.guitarclass.service.PostService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/posts")
class PostController(
    private val postService: PostService,
    private val userRepository: UserRepository
) {

    /**
     * 게시글 목록 조회 (페이징)
     */
    @GetMapping
    fun getPosts(
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable
    ): ResponseEntity<Page<PostListResponse>> {
        val posts = postService.getPosts(pageable)
        return ResponseEntity.ok(posts)
    }

    /**
     * 게시글 상세 조회 (조회수 증가)
     */
    @GetMapping("/{id}")
    fun getPost(@PathVariable id: Long): ResponseEntity<PostResponse> {
        val post = postService.getPost(id)
        return ResponseEntity.ok(post)
    }

    /**
     * 게시글 작성 (인증 필요)
     */
    @PostMapping
    fun createPost(
        @Valid @RequestBody request: PostRequest,
        authentication: Authentication
    ): ResponseEntity<PostResponse> {
        val userId = getUserIdFromAuthentication(authentication)
        val post = postService.createPost(request, userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(post)
    }

    /**
     * 게시글 수정 (인증 필요, 작성자만 가능)
     */
    @PutMapping("/{id}")
    fun updatePost(
        @PathVariable id: Long,
        @Valid @RequestBody request: PostRequest,
        authentication: Authentication
    ): ResponseEntity<PostResponse> {
        val userId = getUserIdFromAuthentication(authentication)
        val post = postService.updatePost(id, request, userId)
        return ResponseEntity.ok(post)
    }

    /**
     * 게시글 삭제 (인증 필요, 작성자만 가능)
     */
    @DeleteMapping("/{id}")
    fun deletePost(
        @PathVariable id: Long,
        authentication: Authentication
    ): ResponseEntity<Map<String, String>> {
        val userId = getUserIdFromAuthentication(authentication)
        postService.deletePost(id, userId)
        return ResponseEntity.ok(mapOf("message" to "게시글이 삭제되었습니다"))
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
