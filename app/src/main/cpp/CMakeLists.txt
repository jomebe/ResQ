cmake_minimum_required(VERSION 3.22.1)
project(resqllama)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_CPP_DIR ${CMAKE_SOURCE_DIR}/llama.cpp)
if (NOT EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp source not found at ${LLAMA_CPP_DIR}. Clone https://github.com/ggerganov/llama.cpp into app/src/main/cpp/llama.cpp")
endif()

set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_CPP_DIR} llama_cpp_build)

add_library(resqllama SHARED
    llama_jni.cpp
)

target_include_directories(resqllama PRIVATE ${LLAMA_CPP_DIR})
target_link_libraries(resqllama PRIVATE llama)
target_link_libraries(resqllama PRIVATE log)
