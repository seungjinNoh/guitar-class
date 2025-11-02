package com.guitarclass

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@SpringBootApplication
@EnableJpaAuditing
class GuitarClassApplication

fun main(args: Array<String>) {
    runApplication<GuitarClassApplication>(*args)
}
