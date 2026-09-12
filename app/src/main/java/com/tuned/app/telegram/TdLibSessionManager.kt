package com.tuned.app.telegram

import android.content.Context

/**
 * TDLib explicitly does not support two Client instances pointed at the same database directory
 * at once — so the whole app must share a single TelegramUserAuth, not create a new one per
 * screen. This holds that one instance.
 */
object TdLibSessionManager {
    @Volatile
    var auth: TelegramUserAuth? = null
        private set

    fun getOrCreate(context: Context, apiId: Int, apiHash: String): TelegramUserAuth {
        val existing = auth
        if (existing != null) return existing
        synchronized(this) {
            val existingAfterLock = auth
            if (existingAfterLock != null) return existingAfterLock
            val created = TelegramUserAuth(context.applicationContext, apiId, apiHash)
            auth = created
            return created
        }
    }

    /** Called when a session closes (logout or fatal error) so the next attempt starts fresh
     *  instead of silently reusing a dead, disconnected client. */
    fun clearIfCurrent(instance: TelegramUserAuth) {
        synchronized(this) {
            if (auth === instance) auth = null
        }
    }
}
