cmake_minimum_required(VERSION 4.2)
project(Bitcask_Service)

set(CMAKE_CXX_STANDARD 20)

add_executable(Bitcask_Service main.cpp
        include/Segment.h
        main.cpp)
