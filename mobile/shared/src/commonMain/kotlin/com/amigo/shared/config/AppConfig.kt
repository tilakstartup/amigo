package com.amigo.shared.config

/**
 * Application configuration
 * Values are set at build time from environment variables
 */
object AppConfig {
    // Bedrock Agent Configuration
    // Agent IDs are resolved server-side by the Lambda — no client-side IDs needed
    const val BEDROCK_API_ENDPOINT = "https://vhz4of3ijg.execute-api.us-east-1.amazonaws.com/dev/invoke"
    
    // Supabase Configuration
    const val SUPABASE_URL = "https://hibbnohfwvbglyxgyaav.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhpYmJub2hmd3ZiZ2x5eGd5YWF2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzI4NjQwNDMsImV4cCI6MjA4ODQ0MDA0M30.8acSzRLPqFFOf1WF-k5BECV8Vfdx1bVlaKTxM_s26Rc"
}
