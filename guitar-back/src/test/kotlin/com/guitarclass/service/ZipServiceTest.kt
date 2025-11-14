package com.guitarclass.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.web.MockMultipartFile
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipServiceTest {

    private val zipService = ZipService()

    @Test
    fun `ZIP 파일 압축 해제 성공`() {
        // Given: 마크다운과 이미지가 포함된 ZIP 파일 생성
        val zipBytes = createTestZipFile(
            "test.md" to "# Test Markdown\n![image](image.png)".toByteArray(),
            "image.png" to ByteArray(100) { it.toByte() }
        )
        val zipFile = MockMultipartFile(
            "file",
            "test.zip",
            "application/zip",
            zipBytes
        )

        // When: ZIP 파일 압축 해제
        val result = zipService.extractZipFile(zipFile)

        // Then: 마크다운 파일과 이미지 파일이 추출됨
        assertNotNull(result.markdownFile, "마크다운 파일이 있어야 함")
        assertEquals("test.md", result.markdownFile?.name)
        assertEquals(1, result.assetFiles.size, "이미지 파일 1개가 있어야 함")
        assertEquals("image.png", result.assetFiles[0].name)

        // Cleanup
        zipService.cleanupTempDirectory(result.tempDirectory)
    }

    @Test
    fun `여러 에셋 파일이 포함된 ZIP 처리`() {
        // Given: 다양한 에셋 파일이 포함된 ZIP
        val zipBytes = createTestZipFile(
            "article.md" to "# Article".toByteArray(),
            "photo.jpg" to ByteArray(50),
            "video.mp4" to ByteArray(200),
            "document.pdf" to ByteArray(100)
        )
        val zipFile = MockMultipartFile("file", "test.zip", "application/zip", zipBytes)

        // When
        val result = zipService.extractZipFile(zipFile)

        // Then
        assertEquals(3, result.assetFiles.size, "에셋 파일 3개(jpg, mp4, pdf)")
        val fileNames = result.assetFiles.map { it.name }
        assertTrue(fileNames.contains("photo.jpg"))
        assertTrue(fileNames.contains("video.mp4"))
        assertTrue(fileNames.contains("document.pdf"))

        // Cleanup
        zipService.cleanupTempDirectory(result.tempDirectory)
    }

    @Test
    fun `빈 ZIP 파일 처리 시 예외 발생`() {
        // Given: 빈 파일
        val emptyFile = MockMultipartFile("file", "empty.zip", "application/zip", ByteArray(0))

        // When & Then
        assertThrows(IllegalArgumentException::class.java) {
            zipService.extractZipFile(emptyFile)
        }
    }

    @Test
    fun `노션 해시 제거 테스트`() {
        // Given & When & Then
        assertEquals("image.png", zipService.removeNotionHash("image_a1b2c3.png"))
        assertEquals("photo.jpg", zipService.removeNotionHash("photo_abc123def.jpg"))
        assertEquals("document.pdf", zipService.removeNotionHash("document.pdf")) // 해시 없음
        assertEquals("file-name.png", zipService.removeNotionHash("file-name_123456.png"))
    }

    @Test
    fun `원본 파일명으로 에셋 찾기`() {
        // Given
        val zipBytes = createTestZipFile(
            "test.md" to "content".toByteArray(),
            "image_abc123.png" to ByteArray(50),
            "photo_def456.jpg" to ByteArray(50)
        )
        val zipFile = MockMultipartFile("file", "test.zip", "application/zip", zipBytes)
        val result = zipService.extractZipFile(zipFile)

        // When: 원본 파일명(해시 없음)으로 찾기
        val foundImage = zipService.findAssetFile(result.assetFiles, "image.png")
        val foundPhoto = zipService.findAssetFile(result.assetFiles, "photo.jpg")
        val notFound = zipService.findAssetFile(result.assetFiles, "notexist.png")

        // Then
        assertNotNull(foundImage)
        assertEquals("image_abc123.png", foundImage?.name)
        assertNotNull(foundPhoto)
        assertEquals("photo_def456.jpg", foundPhoto?.name)
        assertNull(notFound)

        // Cleanup
        zipService.cleanupTempDirectory(result.tempDirectory)
    }

    @Test
    fun `마크다운 없는 ZIP 파일 처리`() {
        // Given: 이미지만 있는 ZIP
        val zipBytes = createTestZipFile(
            "image.png" to ByteArray(100)
        )
        val zipFile = MockMultipartFile("file", "test.zip", "application/zip", zipBytes)

        // When
        val result = zipService.extractZipFile(zipFile)

        // Then
        assertNull(result.markdownFile, "마크다운 파일이 없어야 함")
        assertEquals(1, result.assetFiles.size)

        // Cleanup
        zipService.cleanupTempDirectory(result.tempDirectory)
    }

    /**
     * 테스트용 ZIP 파일 생성 헬퍼 함수
     */
    private fun createTestZipFile(vararg files: Pair<String, ByteArray>): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        ZipOutputStream(byteArrayOutputStream).use { zipOut ->
            files.forEach { (fileName, content) ->
                val entry = ZipEntry(fileName)
                zipOut.putNextEntry(entry)
                zipOut.write(content)
                zipOut.closeEntry()
            }
        }
        return byteArrayOutputStream.toByteArray()
    }
}
