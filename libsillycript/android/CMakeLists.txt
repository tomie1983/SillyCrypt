cmake_minimum_required(VERSION 3.4.1)
project(cryptaes C)
add_definitions(-DANDROID )
include_directories(src/main/native/aes)
find_library( log-lib
        log )
add_library( cryptaes
        SHARED
        src/main/native/aes/aes_blockcipher.c
        src/main/native/aes/aescrypt.c
        src/main/native/aes/aeskey.c
        src/main/native/aes/aestab.c )
target_link_libraries( cryptaes
        ${log-lib} )