package com.example.mwpctracker

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime

object SupabaseInstance {
    val client = createSupabaseClient(
        supabaseUrl = "https://vyfzvtnhshzdjtxxaidg.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZ5Znp2dG5oc2h6ZGp0eHhhaWRnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODMzMTgwNTQsImV4cCI6MjA5ODg5NDA1NH0.0QavmgnoKVnNlg33eBfspztwcSQyQn9sk8iriTODgmY"

    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
        install(Realtime)
    }
}