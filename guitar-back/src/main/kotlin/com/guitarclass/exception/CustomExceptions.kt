package com.guitarclass.exception

/**
 * 리소스를 찾을 수 없을 때
 */
class ResourceNotFoundException(message: String) : RuntimeException(message)

/**
 * 인증되지 않은 접근
 */
class UnauthorizedException(message: String) : RuntimeException(message)

/**
 * 권한이 없는 접근
 */
class ForbiddenException(message: String) : RuntimeException(message)

/**
 * 잘못된 요청
 */
class BadRequestException(message: String) : RuntimeException(message)

/**
 * 중복된 리소스
 */
class DuplicateResourceException(message: String) : RuntimeException(message)

/**
 * 유효하지 않은 토큰
 */
class InvalidTokenException(message: String) : RuntimeException(message)
