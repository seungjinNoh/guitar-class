package com.guitarclass.dto

import com.guitarclass.domain.Attachment

data class AttachmentResponse(
    val id: Long,
    val originalFileName: String,
    val fileUrl: String,
    val fileSize: Long,
    val contentType: String
) {
    companion object {
        fun from(attachment: Attachment): AttachmentResponse {
            return AttachmentResponse(
                id = attachment.id!!,
                originalFileName = attachment.originalFileName,
                fileUrl = attachment.s3Url,
                fileSize = attachment.fileSize,
                contentType = attachment.contentType
            )
        }
    }
}
