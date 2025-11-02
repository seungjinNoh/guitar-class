package com.guitarclass.controller

import com.guitarclass.dto.AttachmentResponse
import com.guitarclass.service.AttachmentService
import com.guitarclass.service.AttachmentUploadResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/attachments")
class AttachmentController(
    private val attachmentService: AttachmentService
) {

    /**
     * 파일 업로드 API
     * 게시글 작성 전에 먼저 파일을 업로드합니다.
     * 에디터에서 이미지/파일을 드래그앤드롭하면 즉시 S3에 업로드되고 URL을 반환받습니다.
     *
     * @param file 업로드할 파일
     * @return 업로드된 파일의 URL 정보
     */
    @PostMapping("/upload")
    fun uploadFile(
        @RequestParam("file") file: MultipartFile
    ): ResponseEntity<AttachmentUploadResponse> {
        val response = attachmentService.uploadFile(file)
        return ResponseEntity.ok(response)
    }

    /**
     * 특정 게시글의 첨부파일 목록 조회
     *
     * @param postId 게시글 ID
     * @return 첨부파일 목록
     */
    @GetMapping("/post/{postId}")
    fun getAttachmentsByPostId(
        @PathVariable postId: Long
    ): ResponseEntity<List<AttachmentResponse>> {
        val attachments = attachmentService.getAttachmentsByPostId(postId)
        val responses = attachments.map { AttachmentResponse.from(it) }
        return ResponseEntity.ok(responses)
    }

    /**
     * 첨부파일 삭제
     *
     * @param attachmentId 첨부파일 ID
     */
    @DeleteMapping("/{attachmentId}")
    fun deleteAttachment(
        @PathVariable attachmentId: Long
    ): ResponseEntity<Void> {
        attachmentService.deleteAttachment(attachmentId)
        return ResponseEntity.noContent().build()
    }
}
