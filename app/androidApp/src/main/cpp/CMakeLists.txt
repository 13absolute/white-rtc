cmake_minimum_required(VERSION 3.22.1)

project("whitertc_io")

add_library(
    whitertc_io
    SHARED
    whitertc_io.c
)

find_library(
    log-lib
    log
)

target_link_libraries(
    whitertc_io
    ${log-lib}
)
