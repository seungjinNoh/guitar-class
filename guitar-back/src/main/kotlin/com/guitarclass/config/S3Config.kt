package com.guitarclass.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

@Configuration
class S3Config(
    @Value("\${aws.s3.access-key}") private val accessKey: String,
    @Value("\${aws.s3.secret-key}") private val secretKey: String,
    @Value("\${aws.s3.region}") private val region: String
) {

    @Bean
    fun s3Client(): S3Client {
        val credentials = AwsBasicCredentials.create(accessKey, secretKey)

        return S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .build()
    }
}
