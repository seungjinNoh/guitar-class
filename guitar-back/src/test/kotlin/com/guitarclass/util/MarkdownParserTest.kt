package com.guitarclass.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MarkdownParserTest {

    private val parser = MarkdownParser()

    @Test
    fun `이미지 참조 추출 테스트 - 기본 패턴`() {
        // Given: 노션 스타일 마크다운 (이미지 참조)
        val markdown = """
            # 제목

            ![image.png](image.png)
            ![photo](photo.jpg)
            ![](./Untitled.png)
        """.trimIndent()

        // When
        val result = parser.extractImageReferences(markdown)

        // Then
        assertEquals(3, result.size, "3개의 이미지 참조가 있어야 함")

        assertEquals("image.png", result[0].altText)
        assertEquals("image.png", result[0].filePath)
        assertEquals("![image.png](image.png)", result[0].originalText)

        assertEquals("photo", result[1].altText)
        assertEquals("photo.jpg", result[1].filePath)

        assertEquals("", result[2].altText) // alt text 없음
        assertEquals("./Untitled.png", result[2].filePath)
    }

    @Test
    fun `이미지 참조 추출 테스트 - 공백과 특수문자`() {
        // Given: 공백이나 특수문자가 포함된 파일명
        val markdown = """
            ![My Photo](./My%20Photo.jpg)
            ![](스크린샷.png)
        """.trimIndent()

        // When
        val result = parser.extractImageReferences(markdown)

        // Then
        assertEquals(2, result.size)
        assertEquals("./My%20Photo.jpg", result[0].filePath)
        assertEquals("스크린샷.png", result[1].filePath)
    }

    @Test
    fun `링크 참조 추출 테스트 - 파일 링크만`() {
        // Given: 다양한 링크 (URL과 파일 혼합)
        val markdown = """
            [다운로드](document.pdf)
            [비디오 보기](video.mp4)
            [구글](https://google.com)
            [문서](https://docs.google.com/doc.pdf)
        """.trimIndent()

        // When
        val result = parser.extractLinkReferences(markdown)

        // Then
        assertEquals(2, result.size, "파일 링크만 추출되어야 함 (URL 제외)")

        assertEquals("다운로드", result[0].linkText)
        assertEquals("document.pdf", result[0].filePath)

        assertEquals("비디오 보기", result[1].linkText)
        assertEquals("video.mp4", result[1].filePath)
    }

    @Test
    fun `parse 메서드 - 종합 테스트`() {
        // Given: 실제 노션 export 마크다운 예시
        val markdown = """
            # 기타 강의 자료

            ## 1강: 기본 코드

            아래 이미지를 참고하세요.

            ![chord-diagram.png](chord-diagram.png)
            ![](./finger-position_abc123.jpg)

            ## 참고 자료

            - [강의 PDF](lecture-note.pdf)
            - [공식 사이트](https://example.com)
            - [동영상 자료](tutorial.mp4)
        """.trimIndent()

        // When
        val result = parser.parse(markdown)

        // Then
        assertEquals(4, result.referencedFiles.size, "4개의 파일 참조 (이미지 2 + 링크 2)")
        assertTrue(result.referencedFiles.contains("chord-diagram.png"))
        assertTrue(result.referencedFiles.contains("finger-position_abc123.jpg"))
        assertTrue(result.referencedFiles.contains("lecture-note.pdf"))
        assertTrue(result.referencedFiles.contains("tutorial.mp4"))

        assertEquals(2, result.imageReferences.size)
        assertEquals(2, result.linkReferences.size)
    }

    @Test
    fun `파일명 추출 테스트 - URL 디코딩`() {
        // Given: URL 인코딩된 경로
        val markdown = """
            ![](./images/My%20Photo%202024.png)
            ![](스크린샷%202024-01-15.png)
        """.trimIndent()

        // When
        val result = parser.parse(markdown)

        // Then
        assertTrue(result.referencedFiles.contains("My Photo 2024.png"), "공백이 디코딩되어야 함")
        assertTrue(result.referencedFiles.contains("스크린샷 2024-01-15.png"))
    }

    @Test
    fun `replaceFilePaths 테스트 - 이미지 경로 치환`() {
        // Given
        val markdown = """
            ![image](image.png)
            ![](photo.jpg)
        """.trimIndent()

        val replacements = mapOf(
            "image.png" to "https://s3.amazonaws.com/bucket/image.png",
            "photo.jpg" to "https://s3.amazonaws.com/bucket/photo.jpg"
        )

        // When
        val result = parser.replaceFilePaths(markdown, replacements)

        // Then
        assertTrue(result.contains("![image](https://s3.amazonaws.com/bucket/image.png)"))
        assertTrue(result.contains("![](https://s3.amazonaws.com/bucket/photo.jpg)"))
        assertFalse(result.contains("](image.png)"), "원본 경로가 남아있으면 안 됨")
        assertFalse(result.contains("](photo.jpg)"), "원본 경로가 남아있으면 안 됨")
    }

    @Test
    fun `replaceFilePaths 테스트 - 링크 경로 치환`() {
        // Given
        val markdown = """
            [문서 다운로드](document.pdf)
            [영상 보기](video.mp4)
        """.trimIndent()

        val replacements = mapOf(
            "document.pdf" to "https://s3.amazonaws.com/bucket/document.pdf",
            "video.mp4" to "https://s3.amazonaws.com/bucket/video.mp4"
        )

        // When
        val result = parser.replaceFilePaths(markdown, replacements)

        // Then
        assertTrue(result.contains("[문서 다운로드](https://s3.amazonaws.com/bucket/document.pdf)"))
        assertTrue(result.contains("[영상 보기](https://s3.amazonaws.com/bucket/video.mp4)"))
    }

    @Test
    fun `빈 마크다운 처리`() {
        // Given
        val markdown = ""

        // When
        val result = parser.parse(markdown)

        // Then
        assertTrue(result.referencedFiles.isEmpty())
        assertTrue(result.imageReferences.isEmpty())
        assertTrue(result.linkReferences.isEmpty())
    }

    @Test
    fun `파일 참조가 없는 마크다운`() {
        // Given
        val markdown = """
            # 제목

            단순 텍스트 내용입니다.

            ## 소제목

            - 리스트 1
            - 리스트 2
        """.trimIndent()

        // When
        val result = parser.parse(markdown)

        // Then
        assertTrue(result.referencedFiles.isEmpty())
        assertTrue(result.imageReferences.isEmpty())
        assertTrue(result.linkReferences.isEmpty())
    }

    @Test
    fun `지원하지 않는 파일 형식은 제외`() {
        // Given: 텍스트 파일 링크는 제외되어야 함
        val markdown = """
            [CSV 파일](data.csv)
            [텍스트](notes.txt)
            [이미지](photo.jpg)
        """.trimIndent()

        // When
        val result = parser.extractLinkReferences(markdown)

        // Then
        assertEquals(1, result.size, "jpg만 에셋 파일로 인정")
        assertEquals("photo.jpg", result[0].filePath)
    }

    @Test
    fun `복잡한 노션 마크다운 예시`() {
        // Given: 실제 노션에서 export한 마크다운 시뮬레이션
        val markdown = """
            # Guitar Class - Lesson 1

            Created: January 15, 2025
            Tags: Beginner, Chords

            ## Introduction

            This lesson covers basic guitar chords.

            ![C Chord Diagram](C-Major-Chord_a1b2c3d4e5f6.png)

            Watch the tutorial video:

            [Tutorial Video](./lesson-1-tutorial_xyz789.mp4)

            ## Practice Sheet

            Download the practice sheet here:

            [Practice Sheet PDF](practice-sheet.pdf)

            ![](Untitled.png)

            Check out our [website](https://guitarclass.com) for more lessons!
        """.trimIndent()

        // When
        val result = parser.parse(markdown)

        // Then
        assertEquals(4, result.referencedFiles.size)
        assertTrue(result.referencedFiles.contains("C-Major-Chord_a1b2c3d4e5f6.png"))
        assertTrue(result.referencedFiles.contains("lesson-1-tutorial_xyz789.mp4"))
        assertTrue(result.referencedFiles.contains("practice-sheet.pdf"))
        assertTrue(result.referencedFiles.contains("Untitled.png"))

        assertEquals(2, result.imageReferences.size)
        assertEquals(2, result.linkReferences.size)
    }
}
