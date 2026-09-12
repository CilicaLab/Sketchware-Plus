#include <jni.h>
#include <string>
#include <vector>

// Simple XOR obfuscation helper
std::string decrypt(const unsigned char* data, size_t len) {
    std::string result;
    for (size_t i = 0; i < len; ++i) {
        result += (char)(data[i] ^ 0x55);
    }
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_sketchware_plus_ai_AiClient_getPrefName(JNIEnv *env, jclass clazz) {
    unsigned char data[] = {0x05, 0x64, 0x67};
    return env->NewStringUTF(decrypt(data, 3).c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_sketchware_plus_ai_AiClient_getApiKeyPrefKey(JNIEnv *env, jclass clazz) {
    unsigned char data[] = {0x05, 0x64, 0x67, 0x1C, 0x66};
    return env->NewStringUTF(decrypt(data, 5).c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_sketchware_plus_ai_AiClient_getEndpointPrefKey(JNIEnv *env, jclass clazz) {
    unsigned char data[] = {0x05, 0x64, 0x67, 0x1C, 0x61};
    return env->NewStringUTF(decrypt(data, 5).c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_sketchware_plus_ai_AiClient_getModelPrefKey(JNIEnv *env, jclass clazz) {
    unsigned char data[] = {0x05, 0x64, 0x67, 0x1C, 0x60};
    return env->NewStringUTF(decrypt(data, 5).c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_sketchware_plus_ai_AiClient_getTempKeyError(JNIEnv *env, jclass clazz) {
    unsigned char data[] = {0x05, 0x64, 0x67, 0x1C, 0x62};
    return env->NewStringUTF(decrypt(data, 5).c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_sketchware_plus_ai_AiClient_getTempKeyGen(JNIEnv *env, jclass clazz) {
    unsigned char data[] = {0x05, 0x64, 0x67, 0x1C, 0x6D};
    return env->NewStringUTF(decrypt(data, 5).c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_sketchware_plus_ai_AiClient_getTempKeyAssistant(JNIEnv *env, jclass clazz) {
    unsigned char data[] = {0x05, 0x64, 0x67, 0x1C, 0x6C};
    return env->NewStringUTF(decrypt(data, 5).c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_sketchware_plus_ai_AiClient_buildRequestUrl(JNIEnv *env, jclass clazz, jstring endpoint) {
    const char *nativeEndpoint = env->GetStringUTFChars(endpoint, nullptr);
    std::string url = nativeEndpoint;
    env->ReleaseStringUTFChars(endpoint, nativeEndpoint);

    if (!url.empty() && url.back() != '/') {
        url += "/";
    }
    url += "chat/completions";
    return env->NewStringUTF(url.c_str());
}
