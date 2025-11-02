package com.guitarclass.domain

import jakarta.persistence.*

@Entity
@Table(name = "comments")
class Comment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    var post: Post,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    var author: User
) : BaseEntity()
