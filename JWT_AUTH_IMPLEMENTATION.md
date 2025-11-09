# JWT 인증 시스템 구현 상세 문서

> 면접 대비용 - Guitar Class 프로젝트 인증 시스템 설명

## 목차
1. [개요](#개요)
2. [기술 스택 및 선택 이유](#기술-스택-및-선택-이유)
3. [인증 아키텍처](#인증-아키텍처)
4. [구현 상세](#구현-상세)
5. [보안 고려사항](#보안-고려사항)
6. [API 명세](#api-명세)
7. [예상 질문 & 답변](#예상-질문--답변)

---

## 개요

### 프로젝트 요구사항
- 사용자 회원가입/로그인 기능
- **자동 로그인 기능**: 브라우저를 껐다 켜도 로그인 유지 (최대 30일)
- 자동 로그인 미선택 시: 1일 후 재로그인 필요
- 보안을 고려한 토큰 관리

### 구현 방식
**JWT (JSON Web Token) 기반 Access Token + Refresh Token 패턴**

---

## 기술 스택 및 선택 이유

### 1. JWT (JSON Web Token)
**선택 이유:**
- Stateless 인증: 서버에 세션을 저장하지 않아 확장성이 좋음
- RESTful API와 궁합이 좋음 (상태를 저장하지 않는 원칙)
- 토큰 자체에 사용자 정보 포함 가능 (Payload)
- 모바일 앱, SPA 등 다양한 클라이언트와 호환

**단점 및 대응:**
- 토큰 탈취 위험 → Access Token 짧은 만료 시간(1시간) + Refresh Token 분리

### 2. Access Token + Refresh Token 패턴
**선택 이유:**
- **Access Token**: 짧은 유효기간(1시간)으로 탈취 시 피해 최소화
- **Refresh Token**: 긴 유효기간(30일)으로 자동 로그인 구현
- 보안과 사용자 편의성의 균형

**구조:**
```
Access Token (1시간)  → API 요청 시 사용
Refresh Token (30일)  → Access Token 재발급 전용
```

### 3. HttpOnly Cookie
**선택 이유:**
- JavaScript로 접근 불가 → XSS(Cross-Site Scripting) 공격 방지
- 브라우저가 자동으로 쿠키 전송 → 프론트엔드 구현 간소화
- LocalStorage 대비 보안성 우수

**대안과 비교:**
- LocalStorage: JavaScript로 접근 가능 → XSS 취약
- SessionStorage: 브라우저 종료 시 삭제 → 자동 로그인 불가

### 4. PostgreSQL (Refresh Token 저장)
**선택 이유:**
- 프로젝트 기본 DB로 선택 완료
- Refresh Token 무효화 가능 (로그아웃 시 즉시 삭제)
- 사용자별 토큰 관리 용이

**대안과 비교:**
- Redis: 더 빠르지만 추가 인프라 필요
- PostgreSQL: 이미 사용 중이므로 추가 비용 없음

### 5. Spring Security + BCrypt
**선택 이유:**
- Spring Boot 표준 보안 프레임워크
- BCrypt: 단방향 암호화, Salt 자동 생성, 느린 해싱(Brute-force 공격 방어)
- 검증된 보안 기술

---

## 인증 아키텍처

### 전체 플로우

```
┌─────────────┐          ┌─────────────┐          ┌─────────────┐
│  Frontend   │          │   Backend   │          │  PostgreSQL │
│  (Browser)  │          │ (Spring)    │          │             │
└──────┬──────┘          └──────┬──────┘          └──────┬──────┘
       │                        │                        │
       │  1. POST /login        │                        │
       │  (email, password,     │                        │
       │   autoLogin: true)     │                        │
       ├───────────────────────>│                        │
       │                        │                        │
       │                        │  2. 사용자 조회 &      │
       │                        │     비밀번호 검증      │
       │                        ├───────────────────────>│
       │                        │<───────────────────────┤
       │                        │                        │
       │                        │  3. Access Token(1h) + │
       │                        │     Refresh Token(30d) │
       │                        │     생성               │
       │                        │                        │
       │                        │  4. Refresh Token 저장 │
       │                        ├───────────────────────>│
       │                        │                        │
       │  5. Response:          │                        │
       │  - Body: Access Token  │                        │
       │  - Cookie: Refresh     │                        │
       │    Token (HttpOnly)    │                        │
       │<───────────────────────┤                        │
       │                        │                        │
       │  6. API 요청           │                        │
       │  (Authorization:       │                        │
       │   Bearer <Access>)     │                        │
       ├───────────────────────>│                        │
       │                        │  7. JWT 검증           │
       │                        │  (JwtAuthenticationFilter)
       │                        │                        │
       │  8. Response           │                        │
       │<───────────────────────┤                        │
       │                        │                        │
       │  [Access Token 만료]   │                        │
       │                        │                        │
       │  9. POST /refresh      │                        │
       │  (Cookie: Refresh)     │                        │
       ├───────────────────────>│                        │
       │                        │  10. Refresh Token     │
       │                        │      DB 조회 & 검증    │
       │                        ├───────────────────────>│
       │                        │<───────────────────────┤
       │                        │                        │
       │  11. 새 Access Token   │                        │
       │<───────────────────────┤                        │
       │                        │                        │
```

### 자동 로그인 메커니즘

**시나리오 1: 자동 로그인 체크 O**
```
로그인 → Refresh Token (30일) 발급 → HttpOnly Cookie 저장
브라우저 종료 → Cookie 유지
브라우저 재실행 → Cookie의 Refresh Token으로 Access Token 재발급
30일 후 → Refresh Token 만료 → 재로그인 필요
```

**시나리오 2: 자동 로그인 체크 X**
```
로그인 → Refresh Token (1일) 발급 → HttpOnly Cookie 저장
1일 후 → Refresh Token 만료 → 재로그인 필요
```

---

## 예상 질문 & 답변

### Q1. JWT를 선택한 이유는?
**A:**
- **Stateless 특성**: 서버가 세션을 저장하지 않아 수평 확장이 쉽습니다. 로드밸런서 뒤에 여러 서버가 있어도 세션 동기화 불필요합니다.
- **RESTful 원칙**: REST API는 stateless여야 하는데 JWT가 이에 적합합니다.
- **범용성**: 웹, 모바일, IoT 등 다양한 클라이언트와 호환됩니다.
- **Self-contained**: 토큰 자체에 사용자 정보와 권한이 포함되어 매번 DB 조회가 불필요합니다.

**단점 인지:**
- 토큰 탈취 시 만료 전까지 무효화가 어렵다는 단점이 있지만, Access Token을 짧게(1시간) 설정하고 Refresh Token을 DB에 저장하여 즉시 무효화 가능하게 구현했습니다.

---

### Q2. Access Token과 Refresh Token을 분리한 이유는?
**A:**
- **보안과 편의성의 균형**:
  - Access Token만 사용하면: 짧은 만료(보안 좋음) → 자주 재로그인(불편함)
  - Refresh Token만 사용하면: 긴 만료(편리함) → 탈취 시 위험(보안 약함)

- **구체적 시나리오**:
  1. Access Token(1시간)이 탈취되어도 1시간 후 자동 만료
  2. Refresh Token(30일)은 HttpOnly Cookie에 저장 → XSS 공격 불가
  3. Refresh Token은 `/refresh` 엔드포인트에서만 사용 → 공격자가 API 직접 호출 불가
  4. Refresh Token은 DB 저장 → 로그아웃 시 즉시 무효화 가능

---

### Q3. Refresh Token을 왜 DB에 저장했나요? Redis가 더 빠르지 않나요?
**A:**
- **프로젝트 규모 고려**:
  - 현재는 교회 기타 강습용 소규모 서비스로, PostgreSQL로 충분합니다.
  - Redis를 추가하면 인프라 복잡도와 운영 비용이 증가합니다.

- **PostgreSQL의 장점**:
  - 이미 사용 중인 DB이므로 추가 설치 불필요
  - 트랜잭션으로 User와 RefreshToken 일관성 보장
  - 백업 및 복구가 간단

- **성능**:
  - Refresh Token 조회는 로그인/토큰 갱신 시에만 발생 (빈도가 낮음)
  - 인덱스(token 컬럼)로 충분히 빠른 조회 가능

- **확장성**:
  - 향후 트래픽이 증가하면 Redis로 마이그레이션 가능 (Repository 인터페이스만 교체)

---

### Q4. HttpOnly Cookie를 사용한 이유는?
**A:**
- **XSS 공격 방어**:
  - LocalStorage/SessionStorage는 JavaScript로 접근 가능 → XSS 취약
  - HttpOnly Cookie는 JavaScript로 접근 불가 → XSS 공격으로도 탈취 불가

- **자동 로그인 구현**:
  - Cookie는 브라우저 종료 후에도 유지 가능 (maxAge 설정)
  - SessionStorage는 브라우저 종료 시 삭제됨

- **CSRF는?**:
  - 일반적으로 Cookie는 CSRF 취약하지만, 우리 프로젝트는 Refresh Token만 Cookie에 저장
  - Refresh Token은 `/refresh` 엔드포인트에서만 사용하고, 이 엔드포인트는 GET이 아닌 POST
  - 실제 API 호출은 Access Token(헤더)으로 하므로 CSRF 영향 없음

---

### Q5. Access Token 만료 시 프론트엔드에서 어떻게 처리하나요?
**A:**
프론트엔드에서 Axios Interceptor를 활용한 자동 갱신 로직:

```javascript
// Axios 응답 인터셉터
axios.interceptors.response.use(
  response => response,
  async error => {
    const originalRequest = error.config;

    // Access Token 만료 (401 에러)
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        // Refresh Token으로 새 Access Token 발급
        const { data } = await axios.post('/api/auth/refresh', {}, {
          withCredentials: true // Cookie 전송
        });

        // 새 Access Token 저장
        setAccessToken(data.accessToken);

        // 실패했던 원래 요청 재시도
        originalRequest.headers['Authorization'] = `Bearer ${data.accessToken}`;
        return axios(originalRequest);

      } catch (refreshError) {
        // Refresh Token도 만료 → 로그인 페이지로 이동
        redirectToLogin();
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);
```

**사용자 경험**:
1. API 호출 → 401 에러 발생
2. 자동으로 `/refresh` 호출 → 새 Access Token 받음
3. 원래 요청 재시도 → 사용자는 에러를 느끼지 못함
4. Refresh Token도 만료 시 로그인 페이지로 리다이렉트

---

### Q6. 로그아웃은 어떻게 구현했나요?
**A:**
```kotlin
fun logout(refreshToken: String) {
    // DB에서 Refresh Token 삭제
    refreshTokenRepository.deleteByToken(refreshToken)
    // → 이후 /refresh 요청 시 실패 (토큰 찾을 수 없음)
}
```

**클라이언트 동작**:
1. `/api/auth/logout` 호출 (Cookie의 Refresh Token 전송)
2. 서버가 DB에서 Refresh Token 삭제
3. 서버가 `Set-Cookie: refreshToken=; Max-Age=0` 응답 (Cookie 삭제)
4. 클라이언트가 메모리의 Access Token 삭제

**Access Token은?**
- 서버에서 즉시 무효화할 수 없음 (Stateless의 한계)
- 하지만 최대 1시간 후 자동 만료
- Refresh Token이 없으면 재발급 불가능 → 1시간 후 완전 로그아웃

---

### Q7. 비밀번호는 어떻게 암호화했나요?
**A:**
- **BCrypt 알고리즘 사용**:
  ```kotlin
  val encoded = passwordEncoder.encode("plainPassword")
  // 결과: $2a$10$N9qo8uL...  (Salt 포함)
  ```

- **BCrypt의 장점**:
  1. **단방향 해싱**: 암호화된 비밀번호에서 원본 복구 불가능
  2. **Salt 자동 생성**: 같은 비밀번호도 다른 해시값 생성 (레인보우 테이블 공격 방어)
  3. **느린 알고리즘**: 의도적으로 느림 (Brute-force 공격 어렵게 함)
  4. **Work Factor 조정 가능**: 하드웨어 성능 향상에 대응 가능

- **검증**:
  ```kotlin
  passwordEncoder.matches(rawPassword, encodedPassword) // true/false
  ```

---

### Q8. CORS는 어떻게 설정했나요?
**A:**
```kotlin
@Bean
fun corsConfigurationSource(): CorsConfigurationSource {
    val configuration = CorsConfiguration()

    configuration.allowedOrigins = listOf(
        "http://localhost:3000",  // Next.js 개발 서버
        "http://localhost:3001"
    )
    configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
    configuration.allowedHeaders = listOf("*")
    configuration.allowCredentials = true  // 쿠키 전송 허용 ★
    configuration.maxAge = 3600L

    return source
}
```

**중요 포인트**:
- `allowCredentials = true`: Cookie(Refresh Token) 전송 허용
- `allowedOrigins`를 `*`로 하면 안 됨 (allowCredentials와 충돌)
- 프로덕션에서는 실제 프론트엔드 도메인으로 변경 필요

---

### Q9. JWT Secret Key 관리는 어떻게 하나요?
**A:**
```yaml
# application.yml
jwt:
  secret: ${JWT_SECRET:default-secret-key}
```

**환경별 관리**:
- **개발 환경**: `.env` 파일 또는 IDE 환경변수
  ```bash
  export JWT_SECRET="your-dev-secret-key-min-512-bits"
  ```

- **프로덕션**:
  - AWS Secrets Manager / Parameter Store
  - Kubernetes Secrets
  - Docker Secrets

- **보안 규칙**:
  - HS512 알고리즘은 최소 512비트(64바이트) 필요
  - Git에 절대 커밋하지 않음 (`.gitignore`에 추가)
  - 주기적으로 교체 (Key Rotation)

---

### Q10. 다중 기기 로그인은 어떻게 처리하나요?
**A:**
현재 구현:
```kotlin
fun login(request: LoginRequest): AuthResponse {
    // ...
    refreshTokenRepository.deleteByUser(user) // 기존 토큰 모두 삭제
    saveRefreshToken(user, refreshToken, request.autoLogin)
    // ...
}
```

**현재 동작**:
- 로그인 시 기존 Refresh Token 모두 삭제 → 다른 기기에서 자동 로그아웃
- 하나의 기기에서만 로그인 유지

**개선 방안** (면접에서 추가 설명 가능):
```kotlin
// RefreshToken 엔티티에 필드 추가
data class RefreshToken(
    // ...
    val deviceInfo: String,  // User-Agent 또는 기기 식별자
    val lastUsedAt: LocalDateTime
)

// 여러 Refresh Token 허용
fun login(request: LoginRequest): AuthResponse {
    // 기존 토큰 삭제하지 않음
    // 단, 최대 5개까지만 허용 (오래된 것부터 삭제)
    val tokens = refreshTokenRepository.findByUser(user)
    if (tokens.size >= 5) {
        val oldest = tokens.minByOrNull { it.lastUsedAt }
        refreshTokenRepository.delete(oldest)
    }
}
```

---

### Q11. JWT의 단점과 대안은?
**A:**

**JWT의 단점**:
1. **토큰 무효화 어려움**: 발급된 토큰은 만료 전까지 유효
2. **Payload 크기**: 토큰에 정보가 많으면 매 요청마다 네트워크 부하
3. **Secret Key 관리 부담**: 키 유출 시 모든 토큰 재발급 필요

**우리 프로젝트의 대응**:
1. Refresh Token은 DB 저장 → 즉시 무효화 가능, Access Token은 짧은 만료(1시간)
2. 최소한의 Payload만 포함 (email, role만)
3. 환경 변수로 관리 + 프로덕션은 Secret Manager 사용 예정

**대안 기술**:
- **Session 기반 인증**:
  - 장점: 서버에서 즉시 무효화 가능
  - 단점: Stateful, 확장성 떨어짐, Redis 등 추가 인프라 필요

- **OAuth 2.0**:
  - 소셜 로그인, 제3자 인증에 적합
  - 우리 프로젝트는 자체 회원 시스템이므로 과함

---

### Q12. 성능 최적화는 어떻게 했나요?
**A:**

**1. 인덱스 설계**:
```sql
CREATE INDEX idx_refresh_token ON refresh_tokens(token);
CREATE INDEX idx_user_email ON users(email);
```
- Refresh Token 조회: O(1) → O(log n)
- 이메일로 사용자 조회: 빈번하므로 인덱스 필수

**2. N+1 문제 방지**:
```kotlin
@ManyToOne(fetch = FetchType.LAZY)
val user: User
```
- RefreshToken 조회 시 User는 필요할 때만 로딩

**3. 토큰 검증 최적화**:
```kotlin
fun validateToken(token: String): Boolean {
    return try {
        getClaims(token)  // 서명 검증 + 만료 확인
        true
    } catch (ex: JwtException) {
        false
    }
}
```
- 매 API 요청마다 실행되므로 빠른 검증 필요
- JJWT 라이브러리가 내부적으로 최적화됨

**4. DB 조회 최소화**:
- Access Token은 DB 조회 없이 자체 검증 (Self-contained)
- Refresh Token만 DB 조회 (빈도가 낮음)

---

### Q13. 테스트는 어떻게 작성할 예정인가요?
**A:**

**1. Unit Test (Service 레이어)**:
```kotlin
@Test
fun `로그인 성공 시 토큰 발급`() {
    // given
    val user = User(email = "test@example.com", ...)
    every { userRepository.findByEmail(any()) } returns Optional.of(user)
    every { passwordEncoder.matches(any(), any()) } returns true

    // when
    val result = authService.login(LoginRequest(...))

    // then
    assertThat(result.accessToken).isNotEmpty()
    assertThat(result.refreshToken).isNotEmpty()
}

@Test
fun `잘못된 비밀번호로 로그인 실패`() {
    // given
    every { passwordEncoder.matches(any(), any()) } returns false

    // when & then
    assertThrows<IllegalArgumentException> {
        authService.login(LoginRequest(...))
    }
}
```

**2. Integration Test (Controller)**:
```kotlin
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {
    @Test
    fun `회원가입 API 테스트`() {
        mockMvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@example.com",...}""")
        )
        .andExpect(status().isCreated)
        .andExpect(jsonPath("$.user.email").value("test@example.com"))
        .andExpect(cookie().exists("refreshToken"))
    }
}
```

**3. Security Test**:
```kotlin
@Test
fun `Access Token 없이 보호된 API 호출 시 401`() {
    mockMvc.perform(get("/api/posts"))
        .andExpect(status().isUnauthorized)
}

@Test
fun `유효한 Access Token으로 API 호출 성공`() {
    val token = jwtTokenProvider.generateAccessToken("test@example.com", "USER")

    mockMvc.perform(
        get("/api/posts")
            .header("Authorization", "Bearer $token")
    )
    .andExpect(status().isOk)
}
```

### 다음 단계
1. 통합 테스트 작성
2. API Rate Limiting 적용
3. 로그인 실패 횟수 제한 (계정 잠금)
4. 이메일 인증 추가 (선택 사항)

---

## 참고 자료

- [JWT 공식 문서](https://jwt.io/)
- [Spring Security 공식 문서](https://spring.io/projects/spring-security)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [RFC 7519 - JWT Specification](https://datatracker.ietf.org/doc/html/rfc7519)
