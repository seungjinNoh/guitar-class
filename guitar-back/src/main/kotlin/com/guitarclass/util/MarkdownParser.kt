package com.guitarclass.util

import org.springframework.stereotype.Component

@Component
class MarkdownParser {

    /**
     * 마크다운 파싱 결과를 담는 데이터 클래스
     */
    data class ParseResult(
        val referencedFiles: List<String>,
        val imageReferences: List<ImageReference>,
        val linkReferences: List<LinkReference>
    )

    /**
     * 이미지 참조 정보
     */
    data class ImageReference(
        val altText: String,
        val filePath: String,
        val originalText: String  // 원본 마크다운 텍스트 (치환용)
    )

    /**
     * 링크 참조 정보 (파일 다운로드 링크)
     */
    data class LinkReference(
        val linkText: String,
        val filePath: String,
        val originalText: String  // 원본 마크다운 텍스트 (치환용)
    )

    /**
     * 마크다운 텍스트에서 이미지와 파일 참조를 파싱
     *
     * @param markdownContent 마크다운 텍스트 내용
     * @return ParseResult (참조된 파일 목록, 이미지 참조, 링크 참조)
     */
    fun parse(markdownContent: String): ParseResult {
        val imageReferences = extractImageReferences(markdownContent)
        val linkReferences = extractLinkReferences(markdownContent)

        val allReferencedFiles = mutableSetOf<String>()
        allReferencedFiles.addAll(imageReferences.map { extractFileName(it.filePath) })
        allReferencedFiles.addAll(linkReferences.map { extractFileName(it.filePath) })

        return ParseResult(
            referencedFiles = allReferencedFiles.toList(),
            imageReferences = imageReferences,
            linkReferences = linkReferences
        )
    }

    /**
     * 이미지 참조 패턴 추출
     * 패턴: ![alt text](경로)
     * 노션 예시: ![image.png](image.png) 또는 ![](./Untitled.png)
     */
    fun extractImageReferences(markdownContent: String): List<ImageReference> {
        // 정규식: !\[([^\]]*)\]\(([^)]+)\)
        // - !\[ : 이미지 시작
        // - ([^\]]*) : alt text (대괄호가 아닌 문자들)
        // - \]\( : ]( 구분자
        // - ([^)]+) : 파일 경로 (닫는 괄호가 아닌 문자들)
        // - \) : 닫는 괄호
        val imagePattern = Regex("""!\[([^\]]*)\]\(([^)]+)\)""")

        return imagePattern.findAll(markdownContent).map { matchResult ->
            ImageReference(
                altText = matchResult.groupValues[1],
                filePath = matchResult.groupValues[2],
                originalText = matchResult.value
            )
        }.toList()
    }

    /**
     * 파일 링크 참조 패턴 추출
     * 패턴: [link text](파일경로)
     * 노션에서는 PDF, 동영상 등의 파일을 링크로 표시
     * 이미지가 아닌 파일 링크만 추출 (확장자로 구분)
     */
    fun extractLinkReferences(markdownContent: String): List<LinkReference> {
        // 1. 먼저 이미지 패턴 제거 (이미지는 별도로 처리)
        val imagePattern = Regex("""!\[([^\]]*)\]\(([^)]+)\)""")
        val contentWithoutImages = markdownContent.replace(imagePattern, "")

        // 2. 링크 패턴 추출
        // 정규식: \[([^\]]+)\]\(([^)]+)\)
        val linkPattern = Regex("""\[([^\]]+)\]\(([^)]+)\)""")

        return linkPattern.findAll(contentWithoutImages).mapNotNull { matchResult ->
            val linkText = matchResult.groupValues[1]
            val filePath = matchResult.groupValues[2]

            // URL이 아니고, 파일 확장자가 있는 경우만 추출
            if (!isUrl(filePath) && isAssetFile(filePath)) {
                LinkReference(
                    linkText = linkText,
                    filePath = filePath,
                    originalText = matchResult.value
                )
            } else {
                null
            }
        }.toList()
    }

    /**
     * 파일명 추출 (경로에서 파일명만 추출)
     * 예: "./images/photo.jpg" -> "photo.jpg"
     */
    private fun extractFileName(filePath: String): String {
        // URL 디코딩 (노션은 공백을 %20으로 인코딩)
        val decoded = java.net.URLDecoder.decode(filePath, "UTF-8")
        // 경로 구분자로 분리해서 마지막 요소 반환
        return decoded.substringAfterLast('/')
    }

    /**
     * URL인지 확인 (http://, https://, // 등으로 시작)
     */
    private fun isUrl(path: String): Boolean {
        return path.startsWith("http://") ||
               path.startsWith("https://") ||
               path.startsWith("//")
    }

    /**
     * 에셋 파일인지 확인 (이미지, 동영상, PDF)
     */
    private fun isAssetFile(filePath: String): Boolean {
        val extension = filePath.substringAfterLast('.', "").lowercase()
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
     * 마크다운 내용에서 특정 경로를 치환
     *
     * @param markdownContent 원본 마크다운 내용
     * @param replacements Map<원본경로, 새로운경로> (예: "./image.png" -> "https://s3.../image.png")
     * @return 치환된 마크다운 내용
     */
    fun replaceFilePaths(markdownContent: String, replacements: Map<String, String>): String {
        var result = markdownContent

        // 각 치환 항목에 대해 원본 텍스트를 찾아서 교체
        replacements.forEach { (oldPath, newPath) ->
            // oldPath를 정규식에서 안전하게 사용하기 위해 이스케이프
            val escapedOldPath = Regex.escape(oldPath)

            // 이미지: ![...](oldPath) -> ![...](newPath)
            // alt text는 상관없이 경로만 매칭
            result = result.replace(Regex("""!\[([^\]]*)\]\($escapedOldPath\)""")) { matchResult ->
                "![${matchResult.groupValues[1]}]($newPath)"
            }

            // 일반 링크: [...](oldPath) -> [...](newPath)
            result = result.replace(Regex("""\[([^\]]+)\]\($escapedOldPath\)""")) { matchResult ->
                "[${matchResult.groupValues[1]}]($newPath)"
            }
        }

        return result
    }
}
