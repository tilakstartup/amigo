package com.amigo.shared.profile

import com.amigo.shared.data.SupabaseClientProvider

object ProfileManagerFactory {
    fun create(): ProfileManager {
        val supabase = SupabaseClientProvider.getClient()
        return ProfileManager(supabase)
    }
}
