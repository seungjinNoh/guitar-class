# JWT 인증 시스템 구현 가이드

## 목차
1. [JWT란 무엇인가?](#jwt란-무엇인가)
2. [JWT 기본 원리](#jwt-기본-원리)
3. [Access Token과 Refresh Token](#access-token과-refresh-token)
4. [구현 아키텍처](#구현-아키텍처)
5. [주요 컴포넌트 상세](#주요-컴포넌트-상세)
6. [인증 흐름](#인증-흐름)
7. [보안 고려사항](#보안-고려사항)

---

## JWT란 무엇인가?

**JWT (JSON Web Token)** 는 웹 표준(RFC 7519)으로, 두 개체 간에 정보를 안전하게 전송하기 위한 컴팩트하고 자가수용적인(self-contained) 방식입니다.

### JWT의 특징
- **자가수용적(Self-contained)**: 토큰 자체에 사용자 정보와 권한이 포함되어 있어 별도 조회 불필요
- **무상태성(Stateless)**: 서버가 세션을 저장하지 않아도 됨
- **확장성(Scalable)**: 서버를 수평 확장할 때 세션 동기화 문제 없음
- **다중 플랫폼 지원**: 모바일, 웹 등 다양한 클라이언트에서 동일하게 사용 가능

---

## JWT 기본 원리

### JWT 구조
JWT는 `.`으로 구분된 세 부분으로 구성됩니다:

```
xxxxx.yyyyy.zzzzz
Header.Payload.Signature
```

#### 1. Header (헤더)
토큰의 타입과 해싱 알고리즘 정보를 포함:
```json
{
  "alg": "HS512",
  "typ": "JWT"
}
```

#### 2. Payload (페이로드)
실제 전달할 데이터(Claims)를 포함:
```json
{
  "sub": "user@example.com",
  "role": "USER",
  "type": "ACCESS",
  "iat": 1704672000,
  "exp": 1704675600
}
```

주요 Claim:
- `sub` (subject): 토큰 주체 (사용자 이메일)
- `iat` (issued at): 토큰 발급 시각
- `exp` (expiration): 토큰 만료 시각
- 커스텀 Claim: `role`, `type`, `autoLogin` 등

#### 3. Signature (서명)
헤더와 페이로드를 비밀키로 서명하여 위변조 방지:
```
HMACSHA512(
  base64UrlEncode(header) + "." + base64UrlEncode(payload),
  secret
)
```

### JWT 검증 프로세스
1. 토큰을 Header, Payload, Signature로 분리
2. Header와 Payload를 비밀키로 다시 서명
3. 계산된 서명과 토큰의 Signature 비교
4. 일치하면 토큰이 유효하고 위변조되지 않았음을 확인
5. Payload의 만료시간(`exp`) 확인

---

## Access Token과 Refresh Token

### 왜 두 개의 토큰이 필요한가?

#### Access Token
- **역할**: API 요청 시 인증에 사용
- **특징**: 짧은 만료 시간 (1시간)
- **저장 위치**: 클라이언트 메모리 또는 로컬 스토리지
- **보안**: 탈취되어도 1시간 후 자동 만료

#### Refresh Token
- **역할**: Access Token 재발급에 사용
- **특징**: 긴 만료 시간 (1일 ~ 30일)
- **저장 위치**: HttpOnly Cookie (XSS 공격 방어)
- **보안**: DB에 저장되어 서버에서 관리 및 무효화 가능

### 이중 토큰 전략의 장점
1. **보안 강화**: Access Token 탈취 시 피해 최소화 (1시간 만료)
2. **사용자 편의성**: Refresh Token으로 로그인 유지 (자동 로그인)
3. **강제 로그아웃 가능**: Refresh Token을 DB에서 삭제하면 즉시 세션 종료
4. **XSS 방어**: Refresh Token을 HttpOnly Cookie에 저장하여 JavaScript 접근 차단

---

## 구현 아키텍처

### 시스템 구성도

```
┌─────────────┐
│   Client    │
│ (Browser)   │
└──────┬──────┘
       │ 1. POST /api/auth/login
       │    (email, password, autoLogin)
       ▼
┌─────────────────────────────────────────┐
│         AuthController                  │
│  ┌───────────────────────────────────┐  │
│  │ setRefreshTokenCookie()           │  │
│  │  - HttpOnly: true                 │  │
│  │  - Secure: false (dev)            │  │
│  │  - MaxAge: 30 days                │  │
│  └───────────────────────────────────┘  │
└──────┬──────────────────────────────────┘
       │ 2. authService.login()
       ▼
┌─────────────────────────────────────────┐
│          AuthService                    │
│  ┌───────────────────────────────────┐  │
│  │ 1. 사용자 인증 (email/password)   │  │
│  │ 2. Access Token 생성 (1시간)     │  │
│  │ 3. Refresh Token 생성 (1~30일)   │  │
│  │ 4. Refresh Token DB 저장         │  │
│  └───────────────────────────────────┘  │
└──────┬──────────────────────────────────┘
       │ 3. jwtTokenProvider.generateAccessToken()
       ▼
┌─────────────────────────────────────────┐
│       JwtTokenProvider                  │
│  ┌───────────────────────────────────┐  │
│  │ - HMAC-SHA512 서명               │  │
│  │ - Claims 설정 (email, role)      │  │
│  │ - 만료 시간 설정                 │  │
│  └───────────────────────────────────┘  │
└──────┬──────────────────────────────────┘
       │ 4. Return tokens
       ▼
┌─────────────┐
│   Client    │
│  - Access Token (Response Body)        │
│  - Refresh Token (HttpOnly Cookie)     │
└─────────────┘
```

### API 요청 흐름

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ GET /api/posts
       │ Authorization: Bearer <Access Token>
       ▼
┌──────────────────────────────────────────┐
│   JwtAuthenticationFilter                │
│  ┌────────────────────────────────────┐  │
│  │ 1. Extract JWT from Header         │  │
│  │ 2. Validate Token                  │  │
│  │ 3. Check if Access Token           │  │
│  │ 4. Create Authentication object    │  │
│  │ 5. Set SecurityContext             │  │
│  └────────────────────────────────────┘  │
└──────┬───────────────────────────────────┘
       │ Request proceeds with authentication
       ▼
┌─────────────────────────────────────────┐
│      Spring Security Chain              │
│  - Authorization checks                 │
│  - Role-based access control            │
└──────┬──────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────┐
│         Controller                      │
│  - Handle business logic                │
└─────────────────────────────────────────┘
```

---

## 주요 컴포넌트 상세

### 1. JwtTokenProvider
**위치**: `config/JwtTokenProvider.kt`

#### 주요 역할
- JWT 토큰 생성 및 검증
- 토큰에서 사용자 정보 추출
- Spring Security Authentication 객체 생성

#### 핵심 메서드

##### generateAccessToken()
```kotlin
fun generateAccessToken(email: String, role: String): String {
    val now = Date()
    val expiryDate = Date(now.time + 3600000) // 1시간

    return Jwts.builder()
        .setSubject(email)                     // 주체: 사용자 이메일
        .claim("role", role)                   // 권한 정보
        .claim("type", "ACCESS")               // 토큰 타입
        .setIssuedAt(now)                      // 발급 시각
        .setExpiration(expiryDate)             // 만료 시각
        .signWith(secretKey, HS512)            // HMAC-SHA512 서명
        .compact()
}
```

##### generateRefreshToken()
```kotlin
fun generateRefreshToken(email: String, isAutoLogin: Boolean): String {
    val now = Date()
    val expiration = if (isAutoLogin) {
        2592000000L  // 30일
    } else {
        86400000L    // 1일
    }
    val expiryDate = Date(now.time + expiration)

    return Jwts.builder()
        .setSubject(email)
        .claim("type", "REFRESH")              // 토큰 타입 구분
        .claim("autoLogin", isAutoLogin)
        .setIssuedAt(now)
        .setExpiration(expiryDate)
        .signWith(secretKey, HS512)
        .compact()
}
```

##### validateToken()
```kotlin
fun validateToken(token: String): Boolean {
    return try {
        getClaims(token)  // 서명 검증 + 만료 시간 확인
        true
    } catch (ex: ExpiredJwtException) {
        false  // 만료된 토큰
    } catch (ex: SecurityException) {
        false  // 잘못된 서명
    } catch (ex: MalformedJwtException) {
        false  // 형식 오류
    }
}
```

##### getAuthentication()
```kotlin
fun getAuthentication(token: String): Authentication {
    val email = getEmailFromToken(token)
    val role = getRoleFromToken(token) ?: "USER"

    val authorities = listOf(SimpleGrantedAuthority("ROLE_$role"))
    val principal = User(email, "", authorities)

    return UsernamePasswordAuthenticationToken(principal, token, authorities)
}
```

**핵심 원리**: JWT 토큰의 정보만으로 Spring Security의 Authentication 객체를 생성하여, DB 조회 없이 인증 처리

---

### 2. JwtAuthenticationFilter
**위치**: `config/JwtAuthenticationFilter.kt`

#### 주요 역할
- 모든 HTTP 요청에 대해 JWT 토큰 검증
- 유효한 토큰이면 SecurityContext에 인증 정보 설정
- Spring Security 필터 체인의 일부로 동작

#### 필터 동작 원리

```kotlin
override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain
) {
    try {
        // 1. Request Header에서 JWT 추출
        val jwt = getJwtFromRequest(request)

        // 2. 토큰 검증 (서명, 만료, 타입 확인)
        if (jwt != null &&
            jwtTokenProvider.validateToken(jwt) &&
            jwtTokenProvider.isAccessToken(jwt)) {

            // 3. Authentication 객체 생성
            val authentication = jwtTokenProvider.getAuthentication(jwt)

            // 4. SecurityContext에 인증 정보 저장
            SecurityContextHolder.getContext().authentication = authentication
        }
    } catch (ex: Exception) {
        logger.error("Could not set user authentication", ex)
    }

    // 5. 다음 필터로 요청 전달
    filterChain.doFilter(request, response)
}

private fun getJwtFromRequest(request: HttpServletRequest): String? {
    val bearerToken = request.getHeader("Authorization")
    return if (bearerToken?.startsWith("Bearer ") == true) {
        bearerToken.substring(7)  // "Bearer " 제거
    } else null
}
```

**핵심 원리**:
- `OncePerRequestFilter`를 상속하여 요청당 한 번만 실행
- SecurityContext에 인증 정보를 설정하면, 이후 Spring Security가 자동으로 권한 검사 수행

---

### 3. SecurityConfig
**위치**: `config/SecurityConfig.kt`

#### 주요 역할
- Spring Security 전체 설정
- JWT 필터 등록
- CORS 설정
- 인증/인가 규칙 정의

#### 핵심 설정

```kotlin
@Bean
fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
    http
        .cors { it.configurationSource(corsConfigurationSource()) }
        .csrf { it.disable() }  // JWT 사용 시 CSRF 불필요
        .sessionManagement {
            it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)  // 세션 사용 안 함
        }
        .authorizeHttpRequests { authorize ->
            authorize
                // 인증 없이 접근 가능
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/posts", "/api/posts/{id}").permitAll()

                // 나머지는 인증 필요
                .anyRequest().authenticated()
        }
        // JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 추가
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

    return http.build()
}
```

**핵심 원리**:
- **STATELESS**: 서버가 세션을 저장하지 않음 (JWT가 모든 인증 정보 포함)
- **CSRF Disable**: JWT는 쿠키 기반 인증이 아니므로 CSRF 공격에 안전
- **Filter 순서**: JWT 필터를 먼저 실행하여 인증 정보를 SecurityContext에 설정

---

### 4. AuthService
**위치**: `service/AuthService.kt`

#### 주요 역할
- 회원가입, 로그인, 토큰 갱신, 로그아웃 비즈니스 로직
- Refresh Token DB 관리
- 비밀번호 암호화 및 검증

#### 핵심 메서드

##### login()
```kotlin
fun login(request: LoginRequest): AuthResponse {
    // 1. 사용자 조회
    val user = userRepository.findByEmail(request.email)
        .orElseThrow { IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다") }

    // 2. 비밀번호 검증 (BCrypt)
    if (!passwordEncoder.matches(request.password, user.password)) {
        throw IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다")
    }

    // 3. 토큰 생성
    val accessToken = jwtTokenProvider.generateAccessToken(user.email, user.role)
    val refreshToken = jwtTokenProvider.generateRefreshToken(user.email, request.autoLogin)

    // 4. Refresh Token DB 저장 (기존 토큰 삭제 후)
    refreshTokenRepository.deleteByUser(user)
    saveRefreshToken(user, refreshToken, request.autoLogin)

    return AuthResponse(accessToken, refreshToken, user.toDto())
}
```

##### refreshAccessToken()
```kotlin
fun refreshAccessToken(refreshToken: String): AuthResponse {
    // 1. Refresh Token 검증 (서명, 만료, 타입)
    if (!jwtTokenProvider.validateToken(refreshToken) ||
        !jwtTokenProvider.isRefreshToken(refreshToken)) {
        throw IllegalArgumentException("유효하지 않은 리프레시 토큰입니다")
    }

    // 2. DB에서 Refresh Token 조회
    val storedToken = refreshTokenRepository.findByToken(refreshToken)
        .orElseThrow { IllegalArgumentException("리프레시 토큰을 찾을 수 없습니다") }

    // 3. 만료 확인 (이중 검증)
    if (storedToken.isExpired()) {
        refreshTokenRepository.delete(storedToken)
        throw IllegalArgumentException("만료된 리프레시 토큰입니다")
    }

    // 4. 새로운 Access Token 생성
    val user = storedToken.user
    val newAccessToken = jwtTokenProvider.generateAccessToken(user.email, user.role)

    return AuthResponse(newAccessToken, null, user.toDto())
}
```

**핵심 원리**:
- Refresh Token은 **DB에 저장**하여 서버에서 관리
- 로그아웃 시 DB에서 삭제하면 해당 Refresh Token으로 재발급 불가 (강제 로그아웃)
- Access Token 갱신 시 Refresh Token은 재발급하지 않음 (보안 강화)

---

### 5. AuthController
**위치**: `controller/AuthController.kt`

#### 주요 역할
- 인증 관련 REST API 제공
- Refresh Token을 HttpOnly Cookie로 관리

#### HttpOnly Cookie 설정

```kotlin
private fun setRefreshTokenCookie(response: HttpServletResponse, refreshToken: String) {
    val cookie = Cookie("refreshToken", refreshToken).apply {
        isHttpOnly = true  // JavaScript 접근 차단 (XSS 방어)
        secure = false     // HTTPS에서만 전송 (프로덕션에서는 true)
        path = "/"         // 모든 경로에서 전송
        maxAge = 30 * 24 * 60 * 60  // 30일 (초 단위)
    }
    response.addCookie(cookie)
}
```

**핵심 원리**:
- **HttpOnly**: JavaScript에서 `document.cookie`로 접근 불가 → XSS 공격 시 토큰 탈취 방지
- **Secure**: HTTPS에서만 전송 → 중간자 공격(MITM) 방어
- **SameSite**: CSRF 공격 방어 (프로덕션에서 설정)

---

## 인증 흐름

### 1. 로그인 흐름

```
사용자                    클라이언트                  서버
  │                          │                         │
  │  이메일/비밀번호 입력      │                         │
  ├─────────────────────────>│                         │
  │                          │  POST /api/auth/login   │
  │                          │  {email, password,      │
  │                          │   autoLogin: true}      │
  │                          ├────────────────────────>│
  │                          │                         │ 1. 사용자 조회
  │                          │                         │ 2. 비밀번호 검증
  │                          │                         │ 3. Access Token 생성 (1h)
  │                          │                         │ 4. Refresh Token 생성 (30d)
  │                          │                         │ 5. Refresh Token DB 저장
  │                          │                         │
  │                          │  Response:              │
  │                          │  - Body: {              │
  │                          │      accessToken,       │
  │                          │      user               │
  │                          │    }                    │
  │                          │  - Cookie: refreshToken │
  │                          │    (HttpOnly)           │
  │                          │<────────────────────────┤
  │                          │                         │
  │                          │ Access Token 저장        │
  │                          │ (메모리/로컬스토리지)     │
  │                          │                         │
  │  로그인 성공              │                         │
  │<─────────────────────────┤                         │
```

### 2. API 요청 흐름

```
클라이언트                  JWT Filter                Spring Security              Controller
  │                          │                         │                             │
  │  GET /api/posts          │                         │                             │
  │  Authorization:          │                         │                             │
  │  Bearer <Access Token>   │                         │                             │
  ├─────────────────────────>│                         │                             │
  │                          │ 1. Extract JWT          │                             │
  │                          │ 2. Validate signature   │                             │
  │                          │ 3. Check expiration     │                             │
  │                          │ 4. Verify type=ACCESS   │                             │
  │                          │ 5. Create Authentication│                             │
  │                          │ 6. Set SecurityContext  │                             │
  │                          ├────────────────────────>│                             │
  │                          │                         │ 7. Check authorization      │
  │                          │                         │    (@PreAuthorize, roles)   │
  │                          │                         ├────────────────────────────>│
  │                          │                         │                             │ 8. Process request
  │                          │                         │                             │
  │                          │                         │          Response           │
  │<─────────────────────────┴─────────────────────────┴─────────────────────────────┤
```

### 3. Token 갱신 흐름

```
클라이언트                  서버
  │                          │
  │  API 요청                │
  ├─────────────────────────>│
  │                          │ Access Token 만료 감지
  │  401 Unauthorized        │
  │<─────────────────────────┤
  │                          │
  │  POST /api/auth/refresh  │
  │  Cookie: refreshToken    │
  ├─────────────────────────>│
  │                          │ 1. Refresh Token 검증
  │                          │ 2. DB에서 조회
  │                          │ 3. 만료 확인
  │                          │ 4. 새 Access Token 생성
  │                          │
  │  {accessToken, user}     │
  │<─────────────────────────┤
  │                          │
  │ 새 Access Token 저장     │
  │                          │
  │  API 재요청              │
  ├─────────────────────────>│
  │                          │
  │  200 OK + Data           │
  │<─────────────────────────┤
```

### 4. 로그아웃 흐름

```
클라이언트                  서버                    DB
  │                          │                      │
  │  POST /api/auth/logout   │                      │
  │  Cookie: refreshToken    │                      │
  ├─────────────────────────>│                      │
  │                          │  DELETE FROM         │
  │                          │  refresh_token       │
  │                          │  WHERE token = ?     │
  │                          ├─────────────────────>│
  │                          │                      │
  │                          │      Success         │
  │                          │<─────────────────────┤
  │                          │                      │
  │  Response:               │                      │
  │  - Set-Cookie:           │                      │
  │    refreshToken=;        │                      │
  │    MaxAge=0              │                      │
  │<─────────────────────────┤                      │
  │                          │                      │
  │ Access Token 삭제        │                      │
  │ (메모리/로컬스토리지)     │                      │
```

---

## 보안 고려사항

### 1. XSS (Cross-Site Scripting) 방어

#### 문제
- Access Token을 로컬 스토리지에 저장하면 JavaScript로 접근 가능
- 악성 스크립트가 토큰을 탈취할 수 있음

#### 해결책
```kotlin
// Refresh Token을 HttpOnly Cookie에 저장
val cookie = Cookie("refreshToken", refreshToken).apply {
    isHttpOnly = true  // JavaScript 접근 차단
}
```

**원리**:
- HttpOnly 쿠키는 `document.cookie`로 접근 불가
- 브라우저가 자동으로 쿠키를 HTTP 요청에 포함
- XSS 공격이 성공해도 Refresh Token은 탈취 불가

### 2. CSRF (Cross-Site Request Forgery) 방어

#### 문제
- HttpOnly Cookie는 자동으로 전송되므로 CSRF 공격에 취약할 수 있음

#### 해결책
```kotlin
http.csrf { it.disable() }  // JWT 사용 시 CSRF 불필요
```

**원리**:
- JWT는 쿠키가 아닌 **Authorization 헤더**로 전송
- Authorization 헤더는 JavaScript에서만 설정 가능
- 악성 사이트는 다른 도메인의 JavaScript를 실행할 수 없음 (Same-Origin Policy)
- Refresh Token만 쿠키로 전송되지만, `/api/auth/refresh` 엔드포인트만 사용하므로 영향 제한적

### 3. Token 탈취 시 피해 최소화

#### 전략
1. **Access Token 짧은 만료 시간** (1시간)
   - 탈취되어도 1시간 후 자동 만료

2. **Refresh Token DB 관리**
   - 서버에서 무효화 가능
   - 로그아웃 시 즉시 삭제

3. **자동 로그인 옵션**
   ```kotlin
   val expiration = if (isAutoLogin) {
       30일  // 편의성 우선
   } else {
       1일   // 보안 우선
   }
   ```

### 4. 비밀번호 보안

```kotlin
@Bean
fun passwordEncoder(): PasswordEncoder {
    return BCryptPasswordEncoder()
}
```

**원리**:
- BCrypt는 **단방향 해시 함수** + **솔트(Salt)**
- 같은 비밀번호도 매번 다른 해시값 생성
- DB가 유출되어도 원본 비밀번호 복원 불가
- 무차별 대입 공격에 강함 (느린 해싱 속도)

### 5. JWT Secret Key 관리

```yaml
# application.yml
jwt:
  secret: ${JWT_SECRET:your-256-bit-secret-key-change-in-production}
```

**주의사항**:
- Secret Key는 최소 256비트 (32바이트) 이상
- 환경 변수로 관리 (코드에 하드코딩 금지)
- 프로덕션에서 반드시 변경
- Secret Key가 유출되면 모든 토큰을 위조 가능

### 6. HTTPS 사용 (프로덕션)

```kotlin
cookie.secure = true  // HTTPS에서만 전송
```

**원리**:
- HTTP는 평문 전송 → 중간자 공격(MITM) 가능
- HTTPS는 TLS 암호화 → 토큰 전송 안전
- 개발 환경(localhost)에서는 `false`, 프로덕션에서는 `true`

---

## 실제 사용 예시

### 1. 회원가입
```bash
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123",
    "nickname": "기타왕"
  }'
```

**응답**:
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": null,
  "user": {
    "id": 1,
    "email": "user@example.com",
    "nickname": "기타왕",
    "role": "USER"
  }
}
```

### 2. 로그인 (자동 로그인 활성화)
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{
    "email": "user@example.com",
    "password": "password123",
    "autoLogin": true
  }'
```

**응답 헤더**:
```
Set-Cookie: refreshToken=eyJhbGciOiJIUzUxMiJ9...; Path=/; HttpOnly; Max-Age=2592000
```

### 3. 인증이 필요한 API 호출
```bash
curl -X POST http://localhost:8080/api/posts \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..." \
  -d '{
    "title": "첫 번째 레슨",
    "content": "# C 코드 배우기\n..."
  }'
```

### 4. Access Token 갱신
```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -b cookies.txt
```

### 5. 로그아웃
```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -b cookies.txt \
  -c cookies.txt
```

---

## 정리

### JWT 인증의 핵심 원리
1. **자가수용적 토큰**: 토큰 자체에 사용자 정보 포함 → DB 조회 불필요
2. **서명 검증**: HMAC-SHA512로 위변조 방지
3. **무상태성**: 서버가 세션 저장 안 함 → 확장성 우수
4. **이중 토큰**: Access Token (단기) + Refresh Token (장기) → 보안과 편의성 균형

### 보안 전략
1. **XSS 방어**: Refresh Token → HttpOnly Cookie
2. **토큰 탈취 대응**: Access Token 짧은 만료 (1시간)
3. **강제 로그아웃**: Refresh Token DB 관리
4. **HTTPS**: 프로덕션에서 필수
5. **BCrypt**: 비밀번호 안전한 저장

### 구현 체크리스트
- [x] JwtTokenProvider: 토큰 생성/검증
- [x] JwtAuthenticationFilter: 요청마다 토큰 검증
- [x] SecurityConfig: Spring Security 설정
- [x] AuthService: 인증 비즈니스 로직
- [x] AuthController: 인증 API 엔드포인트
- [x] RefreshToken 엔티티: DB 관리
- [x] HttpOnly Cookie: Refresh Token 안전한 저장
- [x] 자동 로그인: 1일 vs 30일 옵션
- [x] 전역 예외 처리: 인증 실패 처리

---

**작성일**: 2025-01-09
**마지막 커밋**: cd07272 - feat: JWT 인증 시스템 구현
