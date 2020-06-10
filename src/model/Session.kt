package com.authorization.model

import org.bson.codecs.pojo.annotations.BsonId

class Session(@BsonId val id: String? = null, val userName: String, val token: String)