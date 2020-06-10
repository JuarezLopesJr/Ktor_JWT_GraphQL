package com.authorization.utilities

import com.authorization.graphql.AppSchema
import com.google.gson.Gson
import org.koin.dsl.module

val appModule = module {
    single { Gson() }
    single { AppSchema() }
}