#pragma once
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

int aes_encrypt_block(
        const uint8_t *src, size_t srcOff,
        uint8_t *dst, size_t dstOff,
        uint8_t *key, size_t keyLen
);

int aes_decrypt_block(
        const uint8_t *src, size_t srcOff,
        uint8_t *dst, size_t dstOff,
        uint8_t *key, size_t keyLen
);

enum { AES_BLOCK_SIZE = 16 };

#ifdef __cplusplus
}
#endif