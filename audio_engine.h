#ifndef CADENCE_AUDIO_ENGINE_H
#define CADENCE_AUDIO_ENGINE_H

#include <oboe/Oboe.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaCodec.h>
#include <atomic>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <thread>
#include "ring_buffer.h"

// Owns one audio file's decode pipeline and one Oboe output stream.
//
// Design notes (why it's built this way):
//  - A dedicated decode thread pulls compressed samples through
//    AMediaExtractor + AMediaCodec (the platform's own decoders - same
//    ones every app uses, so every format Android supports works here)
//    and writes raw PCM into a ring buffer.
//  - Oboe's real-time audio callback (onAudioReady) only ever reads from
//    that ring buffer and never calls into JNI, locks, or allocates -
//    those are the well-known rules for not glitching a real-time audio
//    callback. Track-completion is detected here and reported through a
//    plain atomic flag that the Kotlin side polls, rather than the
//    callback calling back into Java directly.
//  - The Oboe stream is opened with SharingMode::Exclusive +
//    PerformanceMode::LowLatency, matched to the source's sample rate.
//    On devices/HALs that support it, this is what actually gets you a
//    dedicated, unmixed path to the DAC. If exclusive mode isn't
//    available the open call fails and this falls back to shared mode
//    automatically - still low-latency, just no longer exclusive.
class AudioEngine {
public:
    AudioEngine();
    ~AudioEngine();

    // fd must be a file descriptor the caller still owns after this call
    // returns (this engine dup()s its own copy internally). Returns false
    // if the file couldn't be opened as an audio source.
    bool openSource(int fd, int64_t offset, int64_t length);

    void play();
    void pause();
    void seekTo(int64_t positionMs);
    void close();

    int64_t getCurrentPositionMs() const;
    int64_t getDurationMs() const;
    bool isPlaying() const;
    bool hasReachedEnd() const;

    // oboe::AudioStreamDataCallback
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames);

private:
    class Callback : public oboe::AudioStreamDataCallback {
    public:
        explicit Callback(AudioEngine *engine) : engine_(engine) {}
        oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) override {
            return engine_->onAudioReady(stream, audioData, numFrames);
        }
    private:
        AudioEngine *engine_;
    };

    void decodeLoop();
    bool openOboeStream();
    void closeOboeStream();
    void teardownSource();
    void selectAudioTrack();

    Callback callback_{this};
    std::shared_ptr<oboe::AudioStream> stream_;
    std::unique_ptr<RingBuffer> ringBuffer_;

    AMediaExtractor *extractor_ = nullptr;
    AMediaCodec *codec_ = nullptr;

    int32_t sampleRate_ = 44100;
    int32_t channelCount_ = 2;
    int64_t durationUs_ = 0;

    std::atomic<int64_t> lastDecodedPtsUs_{0};
    std::atomic<bool> playing_{false};
    std::atomic<bool> decodeFinished_{false};
    std::atomic<bool> reachedEnd_{false};
    std::atomic<bool> stopDecoding_{false};
    std::atomic<bool> seekPending_{false};
    std::atomic<int64_t> seekTargetUs_{0};

    std::thread decodeThread_;
    std::mutex sourceMutex_; // guards extractor_/codec_ during seeks and teardown
    std::condition_variable decodeCv_;
    std::mutex cvMutex_;
};

#endif // CADENCE_AUDIO_ENGINE_H
