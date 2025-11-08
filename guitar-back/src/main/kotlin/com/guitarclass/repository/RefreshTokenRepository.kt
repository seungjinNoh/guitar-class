package com.guitarclass.repository

import com.guitarclass.domain.RefreshToken
import com.guitarclass.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByToken(token: String): Optional<RefreshToken>
    fun findByUser(user: User): List<RefreshToken>
    fun deleteByUser(user: User)
    fun deleteByToken(token: String)
}
