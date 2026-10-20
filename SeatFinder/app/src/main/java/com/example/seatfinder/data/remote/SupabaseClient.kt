package com.example.seatfinder.data.remote

import com.example.seatfinder.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

//Client Supabase unico dell'app: punto d'accesso al DB. Le credenziali (SUPABASE_URL, SUPABASE_KEY) stanno in BuildConfig

object SupabaseClient {

    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY
    ) {
        install(Postgrest)
    }
}