package com.guitarclass.domain

import jakarta.persistence.*

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true, length = 50)
    var email: String,

    @Column(nullable = false)
    var password: String,

    @Column(nullable = false, length = 50)
    var nickname: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: UserRole = UserRole.USER
) : BaseEntity()

enum class UserRole {
    USER,
    ADMIN
}
