# CMake build for the RikkaLLM :mnn module.
#
# Ported from MnnLlmChat (vendor/MNN/apps/Android/MnnLlmChat/app/src/main/cpp/CMakeLists.txt)
# with the following changes:
#   - Only the LLM inference JNI layer is built (diffusion/sana/video/audio/benchmark removed).
#   - libMNN.so is linked from the prebuilt artifact at <repo root>/mnn-prebuilt/<abi>/
#     instead of being built from MNN sources.
#   - Headers are consumed directly from the vendored MNN source tree.

cmake_minimum_required(VERSION 3.22.1)

project("mnnllmapp")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# <repo root>: mnn/src/main/cpp -> 4 levels up
set(RIKKAHUB_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../")
set(MNN_SOURCE_ROOT "${RIKKAHUB_ROOT}/vendor/MNN")
set(MNN_PREBUILT_DIR "${RIKKAHUB_ROOT}/mnn-prebuilt/${ANDROID_ABI}")

set(GIT_COMMIT_ID_VALUE "unknown")
execute_process(
        COMMAND git -C "${RIKKAHUB_ROOT}" rev-parse --short HEAD
        OUTPUT_VARIABLE GIT_COMMIT_ID_OUTPUT
        ERROR_QUIET
        OUTPUT_STRIP_TRAILING_WHITESPACE
)
if (GIT_COMMIT_ID_OUTPUT)
    set(GIT_COMMIT_ID_VALUE "${GIT_COMMIT_ID_OUTPUT}")
endif ()

add_library(${CMAKE_PROJECT_NAME} SHARED
        llm_mnn_jni.cpp
        mnn_wrapper_jni.cpp
        llm_session.cpp
        crash_util.cpp
        processor.cpp
)

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE GIT_COMMIT_ID="${GIT_COMMIT_ID_VALUE}")

# Add 16KB page size support (required for Android 15+ devices)
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wl,-z,max-page-size=16384")

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        # MNN public headers (MNN/MNNDefine.h, MNN/expr/*.hpp, ...)
        "${MNN_SOURCE_ROOT}/include"
        # LLM engine header (llm/llm.hpp)
        "${MNN_SOURCE_ROOT}/transformers/llm/engine/include"
        # bundled third party headers (nlohmann/json.hpp)
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party"
)

# Prebuilt MNN runtime (contains the LLM symbols), packaged into the APK via jniLibs.
add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES IMPORTED_LOCATION "${MNN_PREBUILT_DIR}/libMNN.so")

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log
        MNN
)
