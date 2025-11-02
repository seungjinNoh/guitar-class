package com.guitarclass.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "access_logs")
class AccessLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User? = null,

    @Column(nullable = false, length = 500)
    var requestUrl: String,

    @Column(nullable = false, length = 10)
    var httpMethod: String,

    @Column(nullable = false, length = 45)
    var ipAddress: String,

    @Column(nullable = false)
    var accessTime: LocalDateTime = LocalDateTime.now()
)
