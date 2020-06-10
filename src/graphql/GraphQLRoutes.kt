package com.authorization.graphql

import com.apurebase.kgraphql.GraphQLError
import com.apurebase.kgraphql.schema.Schema
import com.google.gson.Gson
import io.ktor.application.call
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location
import io.ktor.locations.post
import io.ktor.request.receive
import io.ktor.response.respondText
import io.ktor.routing.Route
import org.slf4j.Logger

@KtorExperimentalLocationsAPI
@Location("/graphql")
data class GraphQLRequest(
    val query: String = "",
    val variables: Map<String, Any> = emptyMap()
)

fun GraphQLError.asMap(e: Exception): Map<String, Map<String, String>> {
    return mapOf(
        "errors" to mapOf("message" to e.message!!.replace("\"", ""))
    )
}

@KtorExperimentalLocationsAPI
fun Route.graphql(log: Logger, gson: Gson, schema: Schema) {

    post<GraphQLRequest> {
        val request = call.receive<GraphQLRequest>()

        val query = request.query
        log.info("graphql query: $query")

        val variables = gson.toJson(request.variables)
        log.info("graphql variables: $variables")

        try {
            val result = schema.execute(query, variables)
            call.respondText(result)
        } catch (e: Exception) {
            call.respondText(gson.toJson(GraphQLError(e.localizedMessage).asMap(e)))
        }

    }
}