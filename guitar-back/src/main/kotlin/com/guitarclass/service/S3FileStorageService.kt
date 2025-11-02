package com.guitarclass.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.*

@Service
class S3FileStorageService(
    private val s3Client: S3Client,
    @Value("\${aws.s3.bucket}") private val bucketName: String
) : FileStorageService {

    override fun uploadFile(file: MultipartFile, directory: String): FileUploadResult {
        val originalFilename = file.originalFilename ?: throw IllegalArgumentException("파일명이 없습니다")
        val extension = originalFilename.substringAfterLast(".", "")
        val storedFileName = "${directory}/${UUID.randomUUID()}.${extension}"

        val putObjectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(storedFileName)
            .contentType(file.contentType)
            .contentLength(file.size)
            .build()

        s3Client.putObject(
            putObjectRequest,
            RequestBody.fromInputStream(file.inputStream, file.size)
        )

        val fileUrl = "https://${bucketName}.s3.amazonaws.com/${storedFileName}"

        return FileUploadResult(
            storedFileName = storedFileName,
            fileUrl = fileUrl
        )
    }

    override fun deleteFile(fileUrl: String) {
        // URL에서 키 추출: https://bucket.s3.amazonaws.com/key -> key
        val key = fileUrl.substringAfter(".s3.amazonaws.com/")

        val deleteObjectRequest = DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build()

        s3Client.deleteObject(deleteObjectRequest)
    }
}
