#include <jni.h>
#include "audio_engine.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_cadence_player_playback_NativeAudioEngine_nativeCreate(JNIEnv *, jobject) {
    return reinterpret_cast<jlong>(new AudioEngine());
}

JNIEXPORT jboolean JNICALL
Java_com_cadence_player_playback_NativeAudioEngine_nativeOpenSource(
        JNIEnv *, jobject, jlong handle, jint fd, jlong offset, jlong length) {
    auto *engine = reinterpret_cast<AudioEngine *>(handle);
    return engine->openSource(fd, offset, length) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_cadence_player_playback_NativeAudioEngine_nativePlay(JNIEnv *, jobject, jlong handle) {
    reinterpret_cast<AudioEngine *>(handle)->play();
}

JNIEXPORT void JNICALL
Java_com_cadence_player_playback_NativeAudioEngine_nativePause(JNIEnv *, jobject, jlong handle) {
    reinterpret_cast<AudioEngine *>(handle)->pause();
}

JNIEXPORT void JNICALL
Java_com_cadence_player_playback_NativeAudioEngine_nativeSeekTo(
        JNIEnv *, jobject, jlong handle, jlong positionMs) {
    reinterpret_cast<AudioEngine *>(handle)->seekTo(positionMs);
}

JNIEXPORT jlong JNICALL
Java_com_cadence_player_playback_NativeAudioEngine_nativeGetPositionMs(JNIEnv *, jobject, jlong handle) {
    return reinterpret_cast<AudioEngine *>(handle)->getCurrentPositionMs();
}

JNIEXPORT jlong JNICALL
Java_com_cadence_player_playback_NativeAudioEngine_nativeGetDurationMs(JNIEnv *, jobject, jlong handle) {
    return reinterpret_cast<AudioEngine *>(handle)->getDurationMs();
}

JNIEXPORT jboolean JNICALL
Java_com_cadence_player_playback_NativeAudioEngine_nativeIsPlaying(JNIEnv *, jobject, jlong handle) {
    return reinterpret_cast<AudioEngine *>(handle)->isPlaying() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_cadence_player_playback_NativeAudioEngine_nativeHasReachedEnd(JNIEnv *, jobject, jlong handle) {
    return reinterpret_cast<AudioEngine *>(handle)->hasReachedEnd() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_cadence_player_playback_NativeAudioEngine_nativeClose(JNIEnv *, jobject, jlong handle) {
    reinterpret_cast<AudioEngine *>(handle)->close();
}

JNIEXPORT void JNICALL
Java_com_cadence_player_playback_NativeAudioEngine_nativeDestroy(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<AudioEngine *>(handle);
}

} // extern "C"
