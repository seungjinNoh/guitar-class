package com.guitarclass.service

import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path

@Service
class ZipService {

    /**
     * ZIP 파일의 내용을 나타내는 데이터 클래스
     */
    data class ZipContent(
        val tempDirectory: Path,
        val markdownFile: File?,
        val assetFiles: List<File>
    )

    /**
     * MultipartFile로 받은 ZIP 파일을 압축 해제하고 내용을 분석
     *
     * @param zipFile 업로드된 ZIP 파일
     * @return ZipContent (임시 디렉토리, 마크다운 파일, 에셋 파일 목록)
     * @throws IllegalArgumentException ZIP 파일이 비어있거나 유효하지 않은 경우
     */
    fun extractZipFile(zipFile: MultipartFile): ZipContent {
        if (zipFile.isEmpty) {
            throw IllegalArgumentException("ZIP 파일이 비어있습니다.")
        }

        val tempDir = Files.createTempDirectory("notion-import-")
        var markdownFile: File? = null
        val assetFiles = mutableListOf<File>()

        try {
            ZipArchiveInputStream(zipFile.inputStream).use { zipInputStream ->
                var entry = zipInputStream.nextEntry

                while (entry != null) {
                    // 디렉토리는 스킵
                    if (!entry.isDirectory) {
                        val fileName = entry.name.substringAfterLast('/')
                        val outputFile = tempDir.resolve(fileName).toFile()

                        // 파일 저장
                        FileOutputStream(outputFile).use { outputStream ->
                            zipInputStream.copyTo(outputStream)
                        }

                        // 파일 타입 분류
                        when {
                            fileName.endsWith(".md", ignoreCase = true) -> {
                                markdownFile = outputFile
                            }
                            isAssetFile(fileName) -> {
                                assetFiles.add(outputFile)
                            }
                        }
                    }
                    entry = zipInputStream.nextEntry
                }
            }

            return ZipContent(
                tempDirectory = tempDir,
                markdownFile = markdownFile,
                assetFiles = assetFiles
            )
        } catch (e: Exception) {
            // 에러 발생 시 임시 디렉토리 정리
            cleanupTempDirectory(tempDir)
            throw IllegalStateException("ZIP 파일 압축 해제 중 오류가 발생했습니다: ${e.message}", e)
        }
    }

    /**
     * 에셋 파일인지 확인 (이미지, 동영상, PDF)
     */
    private fun isAssetFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in listOf(
            // 이미지
            "jpg", "jpeg", "png", "gif", "webp", "svg",
            // 동영상
            "mp4", "webm", "mov", "avi",
            // 문서
            "pdf"
        )
    }

    /**
     * 임시 디렉토리 삭제
     */
    fun cleanupTempDirectory(tempDirectory: Path) {
        try {
            tempDirectory.toFile().deleteRecursively()
        } catch (e: Exception) {
            // 로깅만 하고 예외는 던지지 않음
            println("임시 디렉토리 삭제 실패: ${tempDirectory}, 에러: ${e.message}")
        }
    }

    /**
     * 파일명에서 노션이 추가한 해시 제거
     * 예: "image_abc123.png" -> "image.png"
     */
    fun removeNotionHash(fileName: String): String {
        val namePart = fileName.substringBeforeLast('.')
        val extension = fileName.substringAfterLast('.', "")

        // 노션 해시 패턴: 파일명 뒤에 공백 + 32자 해시
        // 예: "image abc123def456.png" 또는 "image_abc123.png"
        val cleaned = namePart.replace(Regex("\\s+[a-f0-9]{32}$"), "")
                              .replace(Regex("_[a-f0-9]{6,}$"), "")

        return if (extension.isNotEmpty()) "$cleaned.$extension" else cleaned
    }

    /**
     * 원본 파일명을 기준으로 에셋 파일 찾기
     * 노션 해시가 붙어있는 파일을 매칭
     */
    fun findAssetFile(assetFiles: List<File>, originalFileName: String): File? {
        // 1. 정확히 일치하는 파일명 찾기
        assetFiles.find { it.name == originalFileName }?.let { return it }

        // 2. 해시 제거 후 매칭
        val cleanedOriginal = removeNotionHash(originalFileName)
        return assetFiles.find { removeNotionHash(it.name) == cleanedOriginal }
    }
}
