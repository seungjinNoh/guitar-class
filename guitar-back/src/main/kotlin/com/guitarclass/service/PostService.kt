package com.guitarclass.service

import com.guitarclass.domain.Post
import com.guitarclass.dto.PostRequest
import com.guitarclass.dto.PostResponse
import com.guitarclass.dto.PostListResponse
import com.guitarclass.repository.PostRepository
import com.guitarclass.repository.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PostService(
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val attachmentRepository: com.guitarclass.repository.AttachmentRepository
) {

    fun getPosts(pageable: Pageable): Page<PostListResponse> {
        return postRepository.findAllByOrderByCreatedAtDesc(pageable)
            .map { PostListResponse.from(it) }
    }

    @Transactional
    fun getPost(postId: Long): PostResponse {
        val post = findPostById(postId)
        post.incrementViewCount()
        return PostResponse.from(post)
    }

    @Transactional
    fun createPost(request: PostRequest, userId: Long): PostResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다") }

        val post = Post(
            title = request.title,
            content = request.content,
            author = user
        )

        val savedPost = postRepository.save(post)

        // 첨부파일 연결
        request.attachmentIds?.let { ids ->
            if (ids.isNotEmpty()) {
                val attachments = attachmentRepository.findByIdIn(ids)

                // 첨부파일이 존재하고 아직 게시글에 연결되지 않은 경우만 연결
                attachments.filter { it.post == null }.forEach { attachment ->
                    attachment.post = savedPost
                    savedPost.attachments.add(attachment)
                }
            }
        }

        return PostResponse.from(savedPost)
    }

    @Transactional
    fun updatePost(postId: Long, request: PostRequest, userId: Long): PostResponse {
        val post = findPostById(postId)

        if (post.author.id != userId) {
            throw IllegalArgumentException("게시글 수정 권한이 없습니다")
        }

        post.title = request.title
        post.content = request.content

        // 첨부파일 업데이트
        request.attachmentIds?.let { newIds ->
            // 기존 첨부파일 연결 해제 (새로운 ID 목록에 없는 것들)
            val existingAttachments = post.attachments.toList()
            existingAttachments.forEach { attachment ->
                if (attachment.id !in newIds) {
                    attachment.post = null
                    post.attachments.remove(attachment)
                }
            }

            // 새로운 첨부파일 연결
            val currentAttachmentIds = post.attachments.mapNotNull { it.id }
            val idsToAdd = newIds.filter { it !in currentAttachmentIds }

            if (idsToAdd.isNotEmpty()) {
                val attachmentsToAdd = attachmentRepository.findByIdIn(idsToAdd)
                attachmentsToAdd.filter { it.post == null }.forEach { attachment ->
                    attachment.post = post
                    post.attachments.add(attachment)
                }
            }
        }

        return PostResponse.from(post)
    }

    @Transactional
    fun deletePost(postId: Long, userId: Long) {
        val post = findPostById(postId)

        if (post.author.id != userId) {
            throw IllegalArgumentException("게시글 삭제 권한이 없습니다")
        }

        postRepository.delete(post)
    }

    private fun findPostById(postId: Long): Post {
        return postRepository.findById(postId)
            .orElseThrow { IllegalArgumentException("게시글을 찾을 수 없습니다") }
    }
}
