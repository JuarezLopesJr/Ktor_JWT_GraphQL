package com.authorization.model

import org.bson.codecs.pojo.annotations.BsonId

data class LoginRegister(
    val userName: String,
    val password: String,

    @BsonId
    val id: String? = null
)