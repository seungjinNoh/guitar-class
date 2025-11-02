package com.guitarclass.service

import org.springframework.web.multipart.MultipartFile

interface FileStorageService {
    /**
     * 파일을 저장하고 접근 가능한 URL을 반환합니다.
     * @param file 저장할 파일
     * @param directory 저장할 디렉토리 (예: "posts", "avatars")
     * @return 파일의 저장된 이름과 접근 URL
     */
    fun uploadFile(file: MultipartFile, directory: String): FileUploadResult

    /**
     * 파일을 삭제합니다.
     * @param fileUrl 삭제할 파일의 URL
     */
    fun deleteFile(fileUrl: String)
}

data class FileUploadResult(
    val storedFileName: String,
    val fileUrl: String
)
