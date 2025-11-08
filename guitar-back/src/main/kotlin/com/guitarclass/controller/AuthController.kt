package com.guitarclass.controller

import com.guitarclass.dto.AuthResponse
import com.guitarclass.dto.LoginRequest
import com.guitarclass.dto.RefreshTokenRequest
import com.guitarclass.dto.SignupRequest
import com.guitarclass.service.AuthService
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    /**
     * 회원가입
     */
    @PostMapping("/signup")
    fun signup(
        @Valid @RequestBody request: SignupRequest,
        response: HttpServletResponse
    ): ResponseEntity<AuthResponse> {
        val authResponse = authService.signup(request)

        // Refresh Token을 HttpOnly Cookie에 저장
        setRefreshTokenCookie(response, authResponse.refreshToken!!)

        return ResponseEntity.status(HttpStatus.CREATED).body(authResponse)
    }

    /**
     * 로그인
     */
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        response: HttpServletResponse
    ): ResponseEntity<AuthResponse> {
        val authResponse = authService.login(request)

        // Refresh Token을 HttpOnly Cookie에 저장
        setRefreshTokenCookie(response, authResponse.refreshToken!!)

        return ResponseEntity.ok(authResponse)
    }

    /**
     * Access Token 갱신
     */
    @PostMapping("/refresh")
    fun refresh(
        @CookieValue(name = "refreshToken", required = false) cookieRefreshToken: String?,
        @RequestBody(required = false) bodyRequest: RefreshTokenRequest?
    ): ResponseEntity<AuthResponse> {
        // Cookie 또는 Body에서 Refresh Token 가져오기
        val refreshToken = cookieRefreshToken ?: bodyRequest?.refreshToken
            ?: throw IllegalArgumentException("리프레시 토큰이 필요합니다")

        val authResponse = authService.refreshAccessToken(refreshToken)

        return ResponseEntity.ok(authResponse)
    }

    /**
     * 로그아웃
     */
    @PostMapping("/logout")
    fun logout(
        @CookieValue(name = "refreshToken", required = false) cookieRefreshToken: String?,
        @RequestBody(required = false) bodyRequest: RefreshTokenRequest?,
        response: HttpServletResponse
    ): ResponseEntity<Map<String, String>> {
        // Cookie 또는 Body에서 Refresh Token 가져오기
        val refreshToken = cookieRefreshToken ?: bodyRequest?.refreshToken

        if (refreshToken != null) {
            authService.logout(refreshToken)
        }

        // Refresh Token Cookie 삭제
        clearRefreshTokenCookie(response)

        return ResponseEntity.ok(mapOf("message" to "로그아웃 되었습니다"))
    }

    /**
     * Refresh Token을 HttpOnly Cookie에 설정
     */
    private fun setRefreshTokenCookie(response: HttpServletResponse, refreshToken: String) {
        val cookie = Cookie("refreshToken", refreshToken).apply {
            isHttpOnly = true // JavaScript에서 접근 불가 (XSS 방지)
            secure = false // HTTPS에서만 전송 (개발 환경에서는 false)
            path = "/"
            maxAge = 30 * 24 * 60 * 60 // 30일 (초 단위)
        }
        response.addCookie(cookie)
    }

    /**
     * Refresh Token Cookie 삭제
     */
    private fun clearRefreshTokenCookie(response: HttpServletResponse) {
        val cookie = Cookie("refreshToken", null).apply {
            isHttpOnly = true
            secure = false
            path = "/"
            maxAge = 0 // 즉시 만료
        }
        response.addCookie(cookie)
    }
}
