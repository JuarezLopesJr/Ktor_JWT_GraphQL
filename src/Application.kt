package com.authorization

import com.authorization.graphql.AppSchema
import com.authorization.graphql.graphql
import com.authorization.model.LoginRegister
import com.authorization.model.MyJwt
import com.authorization.utilities.appModule
import com.google.gson.Gson
import io.ktor.application.*
import io.ktor.auth.Authentication
import io.ktor.auth.UserIdPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.default
import io.ktor.http.content.static
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Locations
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.routing
import org.koin.core.KoinApplication.Companion.logger
import org.koin.core.context.startKoin
import org.koin.core.logger.PrintLogger
import org.koin.ktor.ext.inject
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.eq
import org.litote.kmongo.reactivestreams.KMongo

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@KtorExperimentalLocationsAPI
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    logger = PrintLogger()
    startKoin { modules(appModule) }

    val myJwt = MyJwt("supersecretkey")

    install(Authentication) {
        jwt {
            verifier(myJwt.verifier)
            validate {
                UserIdPrincipal(it.payload.getClaim("key").asString())
            }
        }
    }

    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
        }
    }

    install(Locations)

    routing {
        val appSchema: AppSchema by inject()
        val gson: Gson by inject()
        val client = KMongo.createClient(
            "mongodb+srv://john:1234@cluster0-vhryk.gcp.mongodb.net/test?retryWrites=true&w=majority"
        ).coroutine

        fun clientLoginRegister() = client.getDatabase("Account")
            .getCollection<LoginRegister>("LoginRegister")

        suspend fun getLoginRegister(call: ApplicationCall): LoginRegister {
            val post = call.receive<LoginRegister>()
            return LoginRegister(
                gson.fromJson(post.userName, String::class.java),
                gson.fromJson(post.password, String::class.java)
            )
        }

        suspend fun setNewLoginRegister(call: ApplicationCall, loginRegister: LoginRegister) {
            clientLoginRegister().findOne(LoginRegister::userName eq loginRegister.userName)?.let {
                call.respond(HttpStatusCode.BadRequest, "Username already exists")
                return@setNewLoginRegister
            }
            clientLoginRegister().insertOne(loginRegister)
        }

        suspend fun respondLoginRegister(loginRegister: LoginRegister): Triple<String, String, String> {
            val newToken = myJwt.sign(loginRegister.userName)
            val loginRegisterObject = clientLoginRegister()
                .findOne(gson.toJson(mapOf("userName" to loginRegister.userName)))
            val id = loginRegisterObject!!.id!! //?: "5ee116a1a3115763b88b6dcc"
            val userName = loginRegister.userName

            return Triple(newToken, id, userName)
        }

        post("/sign-up") {
            val loginRegister = getLoginRegister(call)
            setNewLoginRegister(call, loginRegister)
            val (newToken, id, userName) = respondLoginRegister(loginRegister)
            call.respond(
                mapOf(
                    "token" to newToken,
                    "userName" to userName,
                    "expiresIn" to myJwt.expiration,
                    "id" to id
                )
            )
        }

        post("sign-in") {
            val loginRegister = getLoginRegister(call)
            val hasMatch = clientLoginRegister().findOne(
                LoginRegister::userName eq loginRegister.userName,
                LoginRegister::password eq loginRegister.password
            )

            if (hasMatch != null) {
                val (newToken, id, userName) = respondLoginRegister(loginRegister)
                call.respond(
                    mapOf(
                        "token" to newToken,
                        "userName" to userName,
                        "expiresIn" to myJwt.expiration,
                        "id" to id
                    )
                )
            } else {
                call.respond(HttpStatusCode.BadRequest, "Account doesn't exists")
            }
        }

        graphql(log, gson, appSchema.schema)

        static("/") {
            default("index.html")
        }
    }
}

