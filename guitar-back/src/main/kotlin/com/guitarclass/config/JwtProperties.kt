package com.guitarclass.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    var secret: String = "",
    var accessTokenExpiration: Long = 3600000, // 1 hour
    var refreshTokenExpiration: Long = 2592000000, // 30 days
    var refreshTokenExpirationShort: Long = 86400000 // 1 day
)
