cmake_minimum_required(VERSION 3.0)
project(JavaMemTraceAgent)

#include(FetchContent) only available from 3.11, not availalbe on dkr

set(CMAKE_CXX_STANDARD 17)

find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS})

set(SOURCE_FILES jmtAgent.cpp tracing.cpp jvmti_wrapper/jvmti_wrapper.cpp)

add_library(JavaMemTraceAgent SHARED ${SOURCE_FILES})

# JSON Library
# FetchContent_Declare(json
#         GIT_REPOSITORY https://github.com/ArthurSonzogni/nlohmann_json_cmake_fetchcontent
#         GIT_TAG v3.9.1)
# FetchContent_GetProperties(json)
# if(NOT json_POPULATED)
#     FetchContent_Populate(json)
#     add_subdirectory(${json_SOURCE_DIR} ${json_BINARY_DIR} EXCLUDE_FROM_ALL)
# endif()

set(JSON_BuildTests OFF CACHE INTERNAL "")
add_subdirectory(json)

target_link_libraries(JavaMemTraceAgent PRIVATE nlohmann_json::nlohmann_json)
