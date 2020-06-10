package com.authorization.graphql

import com.apurebase.kgraphql.KGraphQL
import com.auth0.jwt.JWT
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.interfaces.DecodedJWT
import com.authorization.model.LoginRegister
import com.authorization.model.Session
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.eq
import org.litote.kmongo.reactivestreams.KMongo
import java.util.*

class AppSchema {
    private val client = KMongo.createClient(
        "mongodb+srv://john:1234@cluster0-vhryk.gcp.mongodb.net/test?retryWrites=true&w=majority"
    ).coroutine
    val sessionExpiredError = "This session is expired. Sign in again"

    val schema = KGraphQL.schema {
        configure {
            useDefaultPrettyPrinter = true
        }

        fun newUser(token: String): String? {
            return try {
                val decodeJWT = JWT.decode(token)
                if (Date() > decodeJWT.expiresAt) {
                    "Please sign in"
                } else {
                    null
                }
            } catch (e: JWTDecodeException) {
                "Token failure"
            }
        }

        suspend fun isTokenActive(decodedJWT: DecodedJWT): Boolean {
            val tokenFound = client.getDatabase("Account")
                .getCollection<Session>("Session")
                .findOne(Session::token eq decodedJWT.token)
            return tokenFound == null
        }

        query("search") {
            description = "return some database"

            resolver { token: String, query: String ->
                if (newUser(token) == null) {
                    if (isTokenActive(JWT.decode(token))) {
                        "You've searched $query on the database"
                    } else {
                        sessionExpiredError
                    }
                } else {
                    newUser(token)
                }
            }
        }

        mutation("add") {
            description = "add data to database"

            resolver { token: String, name: String ->
                if (newUser(token) == null) {
                    if (isTokenActive(JWT.decode(token))) {
                        "You've added $name on the database"
                    } else {
                        sessionExpiredError
                    }
                } else {
                    newUser(token)
                }
            }
        }

        suspend fun addSession(decodedJWT: DecodedJWT) {
            client.getDatabase("Account")
                .getCollection<Session>("Session")
                .insertOne(Session(userName = decodedJWT.issuer, token = decodedJWT.token))
        }

        query("logout") {
            description = "logout user"

            resolver { token: String ->
                if (newUser(token) == null) {
                    val decodedJWT = JWT.decode(token)
                    if (isTokenActive(decodedJWT)) {
                        addSession(decodedJWT)
                        "Log out"
                    } else {
                        sessionExpiredError
                    }
                } else {
                    newUser(token)
                }
            }
        }

        suspend fun deleteUser(decodedJWT: DecodedJWT, user: String): String {
            return if (decodedJWT.issuer == "admin") {
                val deleteResult = client.getDatabase("Account")
                    .getCollection<LoginRegister>("LoginRegister")
                    .deleteOne(LoginRegister::userName eq user)
                if (deleteResult.wasAcknowledged()) {
                    "You've deleted $user from the database"
                } else {
                    "$user failed to delete on the database"
                }
            } else {
                "Unauthorized request"
            }
        }

        query("deleteUser") {
            description = "hard delete user"

            resolver { token: String, user: String ->
                if (newUser(token) == null) {
                    val decodedJWT = JWT.decode(token)
                    if (isTokenActive(decodedJWT)) {
                        deleteUser(decodedJWT, user)
                    } else {
                        sessionExpiredError
                    }
                } else {
                    newUser(token)
                }
            }
        }
    }
}