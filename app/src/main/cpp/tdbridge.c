#include <jni.h>
#include <stdlib.h>

/*
 * tdjson's C API (stable across TDLib versions, documented at
 * https://core.telegram.org/tdlib/docs/td__json__client_8h.html):
 * we declare the five functions ourselves rather than depending on locating
 * TDLib's own header inside the CI-built output tree.
 */
extern void *td_json_client_create(void);
extern void td_json_client_destroy(void *client);
extern void td_json_client_send(void *client, const char *request);
extern const char *td_json_client_receive(void *client, double timeout);
extern const char *td_json_client_execute(void *client, const char *request);

JNIEXPORT jlong JNICALL
Java_com_tuned_app_telegram_TdJsonClient_nativeCreate(JNIEnv *env, jobject thiz) {
    (void) env; (void) thiz;
    return (jlong) (intptr_t) td_json_client_create();
}

JNIEXPORT void JNICALL
Java_com_tuned_app_telegram_TdJsonClient_nativeDestroy(JNIEnv *env, jobject thiz, jlong client) {
    (void) env; (void) thiz;
    td_json_client_destroy((void *) (intptr_t) client);
}

JNIEXPORT void JNICALL
Java_com_tuned_app_telegram_TdJsonClient_nativeSend(JNIEnv *env, jobject thiz, jlong client, jstring request) {
    (void) thiz;
    const char *req = (*env)->GetStringUTFChars(env, request, NULL);
    td_json_client_send((void *) (intptr_t) client, req);
    (*env)->ReleaseStringUTFChars(env, request, req);
}

JNIEXPORT jstring JNICALL
Java_com_tuned_app_telegram_TdJsonClient_nativeReceive(JNIEnv *env, jobject thiz, jlong client, jdouble timeout) {
    (void) thiz;
    const char *result = td_json_client_receive((void *) (intptr_t) client, timeout);
    if (result == NULL) return NULL;
    return (*env)->NewStringUTF(env, result);
}

JNIEXPORT jstring JNICALL
Java_com_tuned_app_telegram_TdJsonClient_nativeExecute(JNIEnv *env, jobject thiz, jlong client, jstring request) {
    const char *req = (*env)->GetStringUTFChars(env, request, NULL);
    const char *result = td_json_client_execute((void *) (intptr_t) client, req);
    (*env)->ReleaseStringUTFChars(env, request, req);
    if (result == NULL) return NULL;
    return (*env)->NewStringUTF(env, result);
}
