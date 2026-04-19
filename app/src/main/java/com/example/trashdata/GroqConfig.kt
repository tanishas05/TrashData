package com.example.trashdata

object GroqConfig {
    // API key is read from BuildConfig, which pulls it from local.properties at build time.
    // Add this line to your local.properties (never commit that file):
    //   GROQ_API_KEY=gsk_yourKeyHere
    val API_KEY: String get() = BuildConfig.GROQ_API_KEY
}