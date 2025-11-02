package com.guitarclass.domain

import jakarta.persistence.*

@Entity
@Table(name = "attachments")
class Attachment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 255)
    var originalFileName: String,

    @Column(nullable = false, length = 500)
    var storedFileName: String,

    @Column(nullable = false, length = 500)
    var s3Url: String,

    @Column(nullable = false)
    var fileSize: Long,

    @Column(nullable = false, length = 100)
    var contentType: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    var post: Post
) : BaseEntity()
