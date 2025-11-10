package com.guitarclass.config

import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    private val jwtProperties: JwtProperties
) {
    private val secretKey: SecretKey = Keys.hmacShaKeyFor(
        jwtProperties.secret.toByteArray(StandardCharsets.UTF_8)
    )

    /**
     * Access Token 생성
     */
    fun generateAccessToken(email: String, role: String): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtProperties.accessTokenExpiration)

        return Jwts.builder()
            .setSubject(email)
            .claim("role", role)
            .claim("type", "ACCESS")
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(secretKey, SignatureAlgorithm.HS512)
            .compact()
    }

    /**
     * Refresh Token 생성
     */
    fun generateRefreshToken(email: String, isAutoLogin: Boolean): String {
        val now = Date()
        val expiration = if (isAutoLogin) {
            jwtProperties.refreshTokenExpiration // 30 days
        } else {
            jwtProperties.refreshTokenExpirationShort // 1 day
        }
        val expiryDate = Date(now.time + expiration)

        return Jwts.builder()
            .setSubject(email)
            .claim("type", "REFRESH")
            .claim("autoLogin", isAutoLogin)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(secretKey, SignatureAlgorithm.HS512)
            .compact()
    }

    /**
     * 토큰에서 이메일 추출
     */
    fun getEmailFromToken(token: String): String {
        return getClaims(token).subject
    }

    /**
     * 토큰에서 권한 추출
     */
    fun getRoleFromToken(token: String): String? {
        return getClaims(token).get("role", String::class.java)
    }

    /**
     * 토큰 유효성 검증
     */
    fun validateToken(token: String): Boolean {
        return try {
            getClaims(token)
            true
        } catch (ex: SecurityException) {
            // Invalid JWT signature
            false
        } catch (ex: MalformedJwtException) {
            // Invalid JWT token
            false
        } catch (ex: ExpiredJwtException) {
            // Expired JWT token
            false
        } catch (ex: UnsupportedJwtException) {
            // Unsupported JWT token
            false
        } catch (ex: IllegalArgumentException) {
            // JWT claims string is empty
            false
        }
    }

    /**
     * 토큰이 Access Token인지 확인
     */
    fun isAccessToken(token: String): Boolean {
        return try {
            val type = getClaims(token).get("type", String::class.java)
            type == "ACCESS"
        } catch (ex: Exception) {
            false
        }
    }

    /**
     * 토큰이 Refresh Token인지 확인
     */
    fun isRefreshToken(token: String): Boolean {
        return try {
            val type = getClaims(token).get("type", String::class.java)
            type == "REFRESH"
        } catch (ex: Exception) {
            false
        }
    }

    /**
     * Authentication 객체 생성
     */
    fun getAuthentication(token: String): Authentication {
        val email = getEmailFromToken(token)
        val role = getRoleFromToken(token) ?: "USER"

        val authorities: Collection<GrantedAuthority> = listOf(SimpleGrantedAuthority("ROLE_$role"))
        val principal = User(email, "", authorities)

        return UsernamePasswordAuthenticationToken(principal, token, authorities)
    }

    /**
     * 토큰에서 Claims 추출
     */
    private fun getClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    /**
     * Refresh Token 만료 기간 계산
     */
    fun getRefreshTokenExpiryDate(isAutoLogin: Boolean): Date {
        val now = Date()
        val expiration = if (isAutoLogin) {
            jwtProperties.refreshTokenExpiration
        } else {
            jwtProperties.refreshTokenExpirationShort
        }
        return Date(now.time + expiration)
    }
}
