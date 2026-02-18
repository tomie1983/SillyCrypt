#pragma once
#include <stddef.h>
#include <string.h>

#if defined(__STDC_LIB_EXT1__)
#define HAVE_MEMSET_S 1
#else
#define HAVE_MEMSET_S 0
#endif

static inline void secure_zero(void *ptr, size_t len) {
    if (ptr == NULL || len == 0) return;

#if HAVE_MEMSET_S
    (void)memset_s(ptr, len, 0, len);
#elif defined(__GLIBC__) && defined(__GLIBC_PREREQ)
    #if __GLIBC_PREREQ(2,25)
        explicit_bzero(ptr, len);
    #else
        volatile unsigned char *p = (volatile unsigned char*)ptr;
        while (len--) *p++ = 0;
    #endif
#else
    volatile unsigned char *p = (volatile unsigned char*)ptr;
    while (len--) *p++ = 0;
#endif
}
