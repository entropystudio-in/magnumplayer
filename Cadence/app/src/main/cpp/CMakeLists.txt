cmake_minimum_required(VERSION 3.22.1)
project("cadence_audio")

add_library(cadence_audio SHARED
    native_bridge.cpp
    audio_engine.cpp
    ring_buffer.cpp
)

target_include_directories(cadence_audio PRIVATE .)

find_package(oboe REQUIRED CONFIG)

# mediandk = AMediaExtractor / AMediaCodec (decoding audio files)
# log      = Android logcat output for debugging
find_library(log-lib log)
find_library(mediandk-lib mediandk)

target_link_libraries(cadence_audio
    oboe::oboe
    ${mediandk-lib}
    ${log-lib}
    android
)
