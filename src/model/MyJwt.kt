package com.authorization.model

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.authorization.utilities.extensions.generateUniqueId
import java.util.*

class MyJwt(secret: String) {
    private val algorithm = Algorithm.HMAC256(secret)
    val expiration = 300
    lateinit var date: Date
    val verifier: JWTVerifier = JWT.require(algorithm).build()

    fun sign(userName: String): String {
        date = Calendar.getInstance().apply {
            time = Date()
            roll(Calendar.MINUTE, 5)
        }.time

        return JWT.create()
            .withIssuer(userName)
            .withExpiresAt(date)
            .withClaim("key", userName)
            .withClaim("uniqueId", generateUniqueId())
            .sign(algorithm)

    }
}