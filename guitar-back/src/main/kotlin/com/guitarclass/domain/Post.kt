package com.guitarclass.domain

import jakarta.persistence.*

@Entity
@Table(name = "posts")
class Post(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 200)
    var title: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    var author: User,

    @Column(nullable = false)
    var viewCount: Long = 0,

    @OneToMany(mappedBy = "post", cascade = [CascadeType.ALL], orphanRemoval = true)
    var comments: MutableList<Comment> = mutableListOf(),

    @OneToMany(mappedBy = "post", cascade = [CascadeType.ALL], orphanRemoval = true)
    var attachments: MutableList<Attachment> = mutableListOf()
) : BaseEntity() {

    fun incrementViewCount() {
        this.viewCount++
    }
}
