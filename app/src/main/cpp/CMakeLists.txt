cmake_minimum_required(VERSION 3.22.1)
project(linuxbox_pty C)

add_library(linuxbox_pty SHARED linuxbox_pty.c)

target_link_options(linuxbox_pty PRIVATE "-Wl,-z,max-page-size=16384")

find_library(log-lib log)
target_link_libraries(linuxbox_pty ${log-lib})
