package com.guitarclass.service

import com.guitarclass.config.JwtTokenProvider
import com.guitarclass.domain.RefreshToken
import com.guitarclass.domain.User
import com.guitarclass.dto.AuthResponse
import com.guitarclass.dto.LoginRequest
import com.guitarclass.dto.SignupRequest
import com.guitarclass.dto.UserDto
import com.guitarclass.repository.RefreshTokenRepository
import com.guitarclass.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider
) {

    /**
     * 회원가입
     */
    fun signup(request: SignupRequest): AuthResponse {
        // 이메일 중복 체크
        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("이미 존재하는 이메일입니다")
        }

        // 사용자 생성
        val user = User(
            email = request.email,
            password = passwordEncoder.encode(request.password),
            nickname = request.nickname,
            role = "USER"
        )

        val savedUser = userRepository.save(user)

        // 토큰 생성 (회원가입 시 자동 로그인 없음)
        val accessToken = jwtTokenProvider.generateAccessToken(savedUser.email, savedUser.role)
        val refreshToken = jwtTokenProvider.generateRefreshToken(savedUser.email, false)

        // Refresh Token 저장
        saveRefreshToken(savedUser, refreshToken, false)

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = savedUser.toDto()
        )
    }

    /**
     * 로그인
     */
    fun login(request: LoginRequest): AuthResponse {
        // 사용자 조회
        val user = userRepository.findByEmail(request.email)
            .orElseThrow { IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다") }

        // 비밀번호 확인
        if (!passwordEncoder.matches(request.password, user.password)) {
            throw IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다")
        }

        // 토큰 생성
        val accessToken = jwtTokenProvider.generateAccessToken(user.email, user.role)
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.email, request.autoLogin)

        // 기존 Refresh Token 삭제 후 새로 저장
        refreshTokenRepository.deleteByUser(user)
        saveRefreshToken(user, refreshToken, request.autoLogin)

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = user.toDto()
        )
    }

    /**
     * Access Token 갱신
     */
    fun refreshAccessToken(refreshToken: String): AuthResponse {
        // Refresh Token 검증
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw IllegalArgumentException("유효하지 않은 리프레시 토큰입니다")
        }

        // DB에서 Refresh Token 조회
        val storedToken = refreshTokenRepository.findByToken(refreshToken)
            .orElseThrow { IllegalArgumentException("리프레시 토큰을 찾을 수 없습니다") }

        // 만료 확인
        if (storedToken.isExpired()) {
            refreshTokenRepository.delete(storedToken)
            throw IllegalArgumentException("만료된 리프레시 토큰입니다")
        }

        val user = storedToken.user

        // 새로운 Access Token 생성
        val newAccessToken = jwtTokenProvider.generateAccessToken(user.email, user.role)

        return AuthResponse(
            accessToken = newAccessToken,
            refreshToken = null, // Refresh Token은 재발급하지 않음
            user = user.toDto()
        )
    }

    /**
     * 로그아웃
     */
    fun logout(refreshToken: String) {
        refreshTokenRepository.deleteByToken(refreshToken)
    }

    /**
     * Refresh Token 저장
     */
    private fun saveRefreshToken(user: User, token: String, isAutoLogin: Boolean) {
        val expiryDate = jwtTokenProvider.getRefreshTokenExpiryDate(isAutoLogin)

        val refreshToken = RefreshToken(
            token = token,
            user = user,
            expiryDate = expiryDate.toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime(),
            isAutoLogin = isAutoLogin
        )

        refreshTokenRepository.save(refreshToken)
    }

    /**
     * User to UserDto
     */
    private fun User.toDto() = UserDto(
        id = id,
        email = email,
        nickname = nickname,
        role = role
    )
}
