package com.tuned.app.telegram

/** Thin wrapper over the 5-function tdjson C API via our own small JNI bridge (tdbridge.c). */
class TdJsonClient {
    private var handle: Long = 0

    init {
        System.loadLibrary("tdbridge")
        handle = nativeCreate()
    }

    fun send(request: String) {
        if (handle != 0L) nativeSend(handle, request)
    }

    /** Blocks up to [timeoutSeconds] waiting for the next update/response; null if none arrived. */
    fun receive(timeoutSeconds: Double): String? =
        if (handle != 0L) nativeReceive(handle, timeoutSeconds) else null

    fun execute(request: String): String? =
        if (handle != 0L) nativeExecute(handle, request) else null

    fun destroy() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0
        }
    }

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(client: Long)
    private external fun nativeSend(client: Long, request: String)
    private external fun nativeReceive(client: Long, timeout: Double): String?
    private external fun nativeExecute(client: Long, request: String): String?
}
