#include "aes_blockcipher.h"
#include "secure_zero.h"
#include "aes.h"
#include <jni.h>

static int keybits_from_len(size_t keyLen) {
    switch (keyLen) {
        case 16: return 128;
        case 24: return 192;
        case 32: return 256;
        default: return 0;
    }
}

int aes_encrypt_block(
        const uint8_t *src, size_t srcOff,
        uint8_t *dst, size_t dstOff,
        uint8_t *key, size_t keyLen
) {
    if (!src || !dst || !key) return -1;
    if (srcOff > (size_t)-1 - 16) return -2;
    if (dstOff > (size_t)-1 - 16) return -3;

    const int keybits = keybits_from_len(keyLen);
    if (keybits == 0) return -4;

    aes_encrypt_ctx ctx;
    secure_zero(&ctx, sizeof(ctx));

    int rc = aes_encrypt_key(key, keybits, &ctx);
    if (rc != 0) {
        secure_zero(&ctx, sizeof(ctx));
        secure_zero(key, keyLen);
        return -5;
    }

    aes_encrypt(src + srcOff, dst + dstOff, &ctx);

    secure_zero(&ctx, sizeof(ctx));
    secure_zero(key, keyLen);

    return 0;
}

int aes_decrypt_block(
        const uint8_t *src, size_t srcOff,
        uint8_t *dst, size_t dstOff,
        uint8_t *key, size_t keyLen
) {
    if (!src || !dst || !key) return -1;
    if (srcOff > (size_t)-1 - 16) return -2;
    if (dstOff > (size_t)-1 - 16) return -3;

    const int keybits = keybits_from_len(keyLen);
    if (keybits == 0) return -4;

    aes_decrypt_ctx ctx;
    secure_zero(&ctx, sizeof(ctx));

    int rc = aes_decrypt_key(key, keybits, &ctx);
    if (rc != 0) {
        secure_zero(&ctx, sizeof(ctx));
        secure_zero(key, keyLen);
        return -5;
    }

    aes_decrypt(src + srcOff, dst + dstOff, &ctx);

    secure_zero(&ctx, sizeof(ctx));
    secure_zero(key, keyLen);

    return 0;
}

static void throwIllegalArgument(JNIEnv *env, const char *msg) {
    jclass cls = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
    if (cls) (*env)->ThrowNew(env, cls, msg);
}

static void throwRuntime(JNIEnv *env, const char *msg) {
    jclass cls = (*env)->FindClass(env, "java/lang/RuntimeException");
    if (cls) (*env)->ThrowNew(env, cls, msg);
}

static int checkBounds(JNIEnv *env, jbyteArray arr, jint off, jint need) {
    if (arr == NULL) {
        throwIllegalArgument(env, "Null array");
        return 0;
    }
    jsize len = (*env)->GetArrayLength(env, arr);
    if (off < 0 || need < 0 || off > len - need) {
        throwIllegalArgument(env, "Array bounds error");
        return 0;
    }
    return 1;
}

static int isValidKeyLen(jsize keyLen) {
    return (keyLen == 16 || keyLen == 24 || keyLen == 32);
}
JNIEXPORT void JNICALL
Java_com_dev_libsillycript_android_blockCiphers_AesNative_encryptBlock(JNIEnv *env, jobject thiz,
                                                                       jbyteArray src, jint src_off,
                                                                       jbyteArray dst, jint dst_off,
                                                                       jbyteArray key) {
    (void)thiz;

    if (!checkBounds(env, src, src_off, AES_BLOCK_SIZE)) return;
    if (!checkBounds(env, dst, dst_off, AES_BLOCK_SIZE)) return;
    if (key == NULL) { throwIllegalArgument(env, "key is null"); return; }

    jsize keyLen = (*env)->GetArrayLength(env, key);

    // Доступ к src/dst
    jboolean isCopySrc = JNI_FALSE, isCopyDst = JNI_FALSE;
    jbyte *srcPtr = (*env)->GetByteArrayElements(env, src, &isCopySrc);
    jbyte *dstPtr = (*env)->GetByteArrayElements(env, dst, &isCopyDst);
    if (!srcPtr || !dstPtr) {
        if (srcPtr) (*env)->ReleaseByteArrayElements(env, src, srcPtr, JNI_ABORT);
        if (dstPtr) (*env)->ReleaseByteArrayElements(env, dst, dstPtr, 0);
        throwRuntime(env, "GetByteArrayElements failed");
        return;
    }

    // Временная копия ключа в нативной памяти
    uint8_t *keyCopy = (uint8_t*)malloc((size_t)keyLen);
    if (!keyCopy) {
        (*env)->ReleaseByteArrayElements(env, src, srcPtr, JNI_ABORT);
        (*env)->ReleaseByteArrayElements(env, dst, dstPtr, 0);
        throwRuntime(env, "malloc failed");
        return;
    }

    // Копируем key из Java -> keyCopy
    (*env)->GetByteArrayRegion(env, key, 0, keyLen, (jbyte*)keyCopy);
    if ((*env)->ExceptionCheck(env)) {
        secure_zero(keyCopy, (size_t)keyLen);
        free(keyCopy);
        (*env)->ReleaseByteArrayElements(env, src, srcPtr, JNI_ABORT);
        (*env)->ReleaseByteArrayElements(env, dst, dstPtr, 0);
        return; // исключение уже выставлено JVM
    }

    // Шифрование (aes_encrypt_block затрёт keyCopy внутри — это ок)
    int rc = aes_encrypt_block(
            (const uint8_t*)srcPtr, (size_t)src_off,
            (uint8_t*)dstPtr, (size_t)dst_off,
            keyCopy, (size_t)keyLen
    );

    // На всякий случай дополнительно затрём копию и освободим
    secure_zero(keyCopy, (size_t)keyLen);
    free(keyCopy);

    // src не меняли, dst меняли
    (*env)->ReleaseByteArrayElements(env, src, srcPtr, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, dst, dstPtr, 0);

    if (rc != 0) {
        throwRuntime(env, "aes_encrypt_block failed");
    }
}

JNIEXPORT void JNICALL
Java_com_dev_libsillycript_android_blockCiphers_AesNative_decryptBlock(JNIEnv *env, jobject thiz,
                                                                       jbyteArray src, jint src_off,
                                                                       jbyteArray dst, jint dst_off,
                                                                       jbyteArray key) {

    (void)thiz;

    if (!checkBounds(env, src, src_off, AES_BLOCK_SIZE)) return;
    if (!checkBounds(env, dst, dst_off, AES_BLOCK_SIZE)) return;
    if (key == NULL) { throwIllegalArgument(env, "key is null"); return; }

    jsize keyLen = (*env)->GetArrayLength(env, key);
    if (!isValidKeyLen(keyLen)) {
        throwIllegalArgument(env, "key length must be 16/24/32 bytes");
        return;
    }

    jboolean isCopySrc = JNI_FALSE, isCopyDst = JNI_FALSE;
    jbyte *srcPtr = (*env)->GetByteArrayElements(env, src, &isCopySrc);
    jbyte *dstPtr = (*env)->GetByteArrayElements(env, dst, &isCopyDst);
    if (!srcPtr || !dstPtr) {
        if (srcPtr) (*env)->ReleaseByteArrayElements(env, src, srcPtr, JNI_ABORT);
        if (dstPtr) (*env)->ReleaseByteArrayElements(env, dst, dstPtr, 0);
        throwRuntime(env, "GetByteArrayElements failed");
        return;
    }

    uint8_t *keyCopy = (uint8_t*)malloc((size_t)keyLen);
    if (!keyCopy) {
        (*env)->ReleaseByteArrayElements(env, src, srcPtr, JNI_ABORT);
        (*env)->ReleaseByteArrayElements(env, dst, dstPtr, 0);
        throwRuntime(env, "malloc failed");
        return;
    }

    (*env)->GetByteArrayRegion(env, key, 0, keyLen, (jbyte*)keyCopy);
    if ((*env)->ExceptionCheck(env)) {
        secure_zero(keyCopy, (size_t)keyLen);
        free(keyCopy);
        (*env)->ReleaseByteArrayElements(env, src, srcPtr, JNI_ABORT);
        (*env)->ReleaseByteArrayElements(env, dst, dstPtr, 0);
        return;
    }

    int rc = aes_decrypt_block(
            (const uint8_t*)srcPtr, (size_t)src_off,
            (uint8_t*)dstPtr, (size_t)dst_off,
            keyCopy, (size_t)keyLen
    );

    secure_zero(keyCopy, (size_t)keyLen);
    free(keyCopy);

    (*env)->ReleaseByteArrayElements(env, src, srcPtr, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, dst, dstPtr, 0);

    if (rc != 0) {
        throwRuntime(env, "aes_decrypt_block failed");
    }
}