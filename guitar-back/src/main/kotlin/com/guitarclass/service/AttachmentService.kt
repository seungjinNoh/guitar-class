package com.guitarclass.service

import com.guitarclass.domain.Attachment
import com.guitarclass.domain.Post
import com.guitarclass.repository.AttachmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
@Transactional
class AttachmentService(
    private val attachmentRepository: AttachmentRepository,
    private val fileStorageService: FileStorageService
) {

    /**
     * 파일을 업로드하고 Attachment 엔티티를 생성합니다 (Post 연관 없이)
     * 게시글 작성 전에 먼저 파일을 업로드할 수 있습니다.
     */
    fun uploadFile(file: MultipartFile): AttachmentUploadResponse {
        validateFile(file)

        val uploadResult = fileStorageService.uploadFile(file, "posts")

        return AttachmentUploadResponse(
            originalFileName = file.originalFilename ?: "unknown",
            storedFileName = uploadResult.storedFileName,
            fileUrl = uploadResult.fileUrl,
            fileSize = file.size,
            contentType = file.contentType ?: "application/octet-stream"
        )
    }

    /**
     * 업로드된 파일 정보를 Post와 연결하여 DB에 저장합니다
     */
    fun saveAttachment(post: Post, uploadResponse: AttachmentUploadResponse): Attachment {
        val attachment = Attachment(
            originalFileName = uploadResponse.originalFileName,
            storedFileName = uploadResponse.storedFileName,
            s3Url = uploadResponse.fileUrl,
            fileSize = uploadResponse.fileSize,
            contentType = uploadResponse.contentType,
            post = post
        )
        return attachmentRepository.save(attachment)
    }

    /**
     * Post에 속한 모든 첨부파일 조회
     */
    @Transactional(readOnly = true)
    fun getAttachmentsByPostId(postId: Long): List<Attachment> {
        return attachmentRepository.findByPostId(postId)
    }

    /**
     * 첨부파일 삭제 (S3와 DB 모두)
     */
    fun deleteAttachment(attachmentId: Long) {
        val attachment = attachmentRepository.findById(attachmentId)
            .orElseThrow { IllegalArgumentException("첨부파일을 찾을 수 없습니다: $attachmentId") }

        fileStorageService.deleteFile(attachment.s3Url)
        attachmentRepository.delete(attachment)
    }

    private fun validateFile(file: MultipartFile) {
        if (file.isEmpty) {
            throw IllegalArgumentException("파일이 비어있습니다")
        }

        val maxFileSize = 50 * 1024 * 1024 // 50MB
        if (file.size > maxFileSize) {
            throw IllegalArgumentException("파일 크기는 50MB를 초과할 수 없습니다")
        }

        val allowedContentTypes = setOf(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "application/pdf",
            "video/mp4",
            "video/webm"
        )

        if (file.contentType !in allowedContentTypes) {
            throw IllegalArgumentException("지원하지 않는 파일 형식입니다: ${file.contentType}")
        }
    }
}

data class AttachmentUploadResponse(
    val originalFileName: String,
    val storedFileName: String,
    val fileUrl: String,
    val fileSize: Long,
    val contentType: String
)
