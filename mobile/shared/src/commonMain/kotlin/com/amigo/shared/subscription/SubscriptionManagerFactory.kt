package com.amigo.shared.subscription

import com.amigo.shared.data.SupabaseClientProvider

object SubscriptionManagerFactory {
    fun create(): SubscriptionManager {
        val supabase = SupabaseClientProvider.getClient()
        return SubscriptionManager(supabase)
    }
}
