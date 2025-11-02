package com.guitarclass.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api")
class HealthController {

    @GetMapping("/health")
    fun health(): Map<String, Any> {
        return mapOf(
            "status" to "UP",
            "timestamp" to LocalDateTime.now(),
            "service" to "guitar-class-backend"
        )
    }
}
