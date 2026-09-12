#include "audio_engine.h"
#include <android/log.h>
#include <unistd.h>
#include <cstring>
#include <chrono>

#define LOG_TAG "CadenceAudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {
constexpr int64_t kDequeueTimeoutUs = 10000; // 10ms
constexpr size_t kRingBufferBytes = 1024 * 1024; // ~a few seconds of buffered audio
}

AudioEngine::AudioEngine() {
    ringBuffer_ = std::make_unique<RingBuffer>(kRingBufferBytes);
}

AudioEngine::~AudioEngine() {
    close();
}

void AudioEngine::selectAudioTrack() {
    int trackCount = AMediaExtractor_getTrackCount(extractor_);
    for (int i = 0; i < trackCount; i++) {
        AMediaFormat *format = AMediaExtractor_getTrackFormat(extractor_, i);
        const char *mime = nullptr;
        if (AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime) &&
            mime != nullptr && std::strncmp(mime, "audio/", 6) == 0) {

            AMediaExtractor_selectTrack(extractor_, i);
            AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, &sampleRate_);
            AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &channelCount_);

            int64_t duration = 0;
            if (AMediaFormat_getInt64(format, AMEDIAFORMAT_KEY_DURATION, &duration)) {
                durationUs_ = duration;
            }

            codec_ = AMediaCodec_createDecoderByType(mime);
            if (codec_ != nullptr) {
                AMediaCodec_configure(codec_, format, nullptr, nullptr, 0);
                AMediaCodec_start(codec_);
            } else {
                LOGW("No decoder available for mime type %s", mime);
            }

            AMediaFormat_delete(format);
            return;
        }
        AMediaFormat_delete(format);
    }
    LOGW("No audio track found in source");
}

bool AudioEngine::openSource(int fd, int64_t offset, int64_t length) {
    close(); // tear down any previous source/stream first

    {
        std::lock_guard<std::mutex> lock(sourceMutex_);
        extractor_ = AMediaExtractor_new();

        int localFd = dup(fd);
        if (localFd < 0) {
            LOGW("dup(fd) failed");
            AMediaExtractor_delete(extractor_);
            extractor_ = nullptr;
            return false;
        }

        media_status_t status = AMediaExtractor_setDataSourceFd(extractor_, localFd, offset, length);
        if (status != AMEDIA_OK) {
            LOGW("setDataSourceFd failed: %d", status);
            ::close(localFd);
            AMediaExtractor_delete(extractor_);
            extractor_ = nullptr;
            return false;
        }

        selectAudioTrack();
        if (codec_ == nullptr) {
            teardownSource();
            return false;
        }
    }

    lastDecodedPtsUs_.store(0);
    decodeFinished_.store(false);
    reachedEnd_.store(false);
    ringBuffer_->clear();

    if (!openOboeStream()) {
        teardownSource();
        return false;
    }

    stopDecoding_.store(false);
    decodeThread_ = std::thread(&AudioEngine::decodeLoop, this);
    return true;
}

bool AudioEngine::openOboeStream() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::I16)
        ->setChannelCount(channelCount_)
        ->setSampleRate(sampleRate_)
        ->setUsage(oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Music)
        ->setDataCallback(&callback_);

    std::shared_ptr<oboe::AudioStream> newStream;
    oboe::Result result = builder.openStream(newStream);

    if (result != oboe::Result::OK) {
        LOGW("Exclusive-mode stream open failed (%s), falling back to shared mode",
             oboe::convertToText(result));
        builder.setSharingMode(oboe::SharingMode::Shared);
        result = builder.openStream(newStream);
    }

    if (result != oboe::Result::OK) {
        LOGW("Shared-mode stream open also failed (%s)", oboe::convertToText(result));
        return false;
    }

    LOGI("Opened Oboe stream: sharing=%d rate=%d channels=%d",
         static_cast<int>(newStream->getSharingMode()), sampleRate_, channelCount_);

    stream_ = newStream;
    stream_->setBufferSizeInFrames(stream_->getFramesPerBurst() * 2);
    return true;
}

void AudioEngine::closeOboeStream() {
    if (stream_) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }
}

void AudioEngine::teardownSource() {
    std::lock_guard<std::mutex> lock(sourceMutex_);
    if (codec_ != nullptr) {
        AMediaCodec_stop(codec_);
        AMediaCodec_delete(codec_);
        codec_ = nullptr;
    }
    if (extractor_ != nullptr) {
        AMediaExtractor_delete(extractor_);
        extractor_ = nullptr;
    }
}

void AudioEngine::decodeLoop() {
    bool sawInputEOS = false;

    while (!stopDecoding_.load()) {

        if (seekPending_.load()) {
            std::lock_guard<std::mutex> lock(sourceMutex_);
            int64_t target = seekTargetUs_.load();
            AMediaExtractor_seekTo(extractor_, target, AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC);
            AMediaCodec_flush(codec_);
            ringBuffer_->clear();
            lastDecodedPtsUs_.store(target);
            decodeFinished_.store(false);
            reachedEnd_.store(false);
            sawInputEOS = false;
            seekPending_.store(false);
        }

        if (!sawInputEOS) {
            ssize_t inIdx = AMediaCodec_dequeueInputBuffer(codec_, kDequeueTimeoutUs);
            if (inIdx >= 0) {
                size_t bufSize = 0;
                uint8_t *inBuf = AMediaCodec_getInputBuffer(codec_, inIdx, &bufSize);
                ssize_t sampleSize = AMediaExtractor_readSampleData(extractor_, inBuf, bufSize);
                if (sampleSize < 0) {
                    AMediaCodec_queueInputBuffer(codec_, inIdx, 0, 0, 0,
                                                  AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                    sawInputEOS = true;
                } else {
                    int64_t presentationTimeUs = AMediaExtractor_getSampleTime(extractor_);
                    AMediaCodec_queueInputBuffer(codec_, inIdx, 0, sampleSize, presentationTimeUs, 0);
                    AMediaExtractor_advance(extractor_);
                }
            }
        }

        AMediaCodecBufferInfo info;
        ssize_t outIdx = AMediaCodec_dequeueOutputBuffer(codec_, &info, kDequeueTimeoutUs);

        if (outIdx >= 0) {
            if (info.size > 0) {
                size_t outBufSize = 0;
                uint8_t *outBuf = AMediaCodec_getOutputBuffer(codec_, outIdx, &outBufSize);
                size_t written = 0;
                size_t remaining = static_cast<size_t>(info.size);

                while (remaining > 0 && !stopDecoding_.load() && !seekPending_.load()) {
                    size_t chunk = ringBuffer_->write(outBuf + written, remaining);
                    written += chunk;
                    remaining -= chunk;
                    if (remaining > 0) {
                        std::unique_lock<std::mutex> lk(cvMutex_);
                        decodeCv_.wait_for(lk, std::chrono::milliseconds(10));
                    }
                }
                lastDecodedPtsUs_.store(info.presentationTimeUs);
            }

            bool isEos = (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) != 0;
            AMediaCodec_releaseOutputBuffer(codec_, outIdx, false);
            if (isEos) {
                decodeFinished_.store(true);
            }
        } else if (outIdx == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat *newFormat = AMediaCodec_getOutputFormat(codec_);
            int32_t newRate = sampleRate_;
            int32_t newChannels = channelCount_;
            AMediaFormat_getInt32(newFormat, AMEDIAFORMAT_KEY_SAMPLE_RATE, &newRate);
            AMediaFormat_getInt32(newFormat, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &newChannels);
            AMediaFormat_delete(newFormat);

            if (newRate != sampleRate_ || newChannels != channelCount_) {
                sampleRate_ = newRate;
                channelCount_ = newChannels;
                closeOboeStream();
                openOboeStream();
                if (playing_.load()) {
                    stream_->requestStart();
                }
            }
        }

        if (decodeFinished_.load() && ringBuffer_->availableToRead() == 0) {
            reachedEnd_.store(true);
            std::unique_lock<std::mutex> lk(cvMutex_);
            decodeCv_.wait_for(lk, std::chrono::milliseconds(50));
        }
    }
}

void AudioEngine::play() {
    if (!stream_) return;
    playing_.store(true);
    stream_->requestStart();
}

void AudioEngine::pause() {
    if (!stream_) return;
    playing_.store(false);
    stream_->requestPause();
}

void AudioEngine::seekTo(int64_t positionMs) {
    seekTargetUs_.store(positionMs * 1000LL);
    seekPending_.store(true);
    lastDecodedPtsUs_.store(positionMs * 1000LL); // optimistic, for instant UI feedback
    decodeCv_.notify_all();
}

void AudioEngine::close() {
    stopDecoding_.store(true);
    decodeCv_.notify_all();
    if (decodeThread_.joinable()) {
        decodeThread_.join();
    }
    closeOboeStream();
    teardownSource();
    playing_.store(false);
}

int64_t AudioEngine::getCurrentPositionMs() const {
    size_t bufferedBytes = ringBuffer_->availableToRead();
    size_t bytesPerFrame = static_cast<size_t>(channelCount_) * sizeof(int16_t);
    int64_t bufferedUs = 0;
    if (bytesPerFrame > 0 && sampleRate_ > 0) {
        int64_t bufferedFrames = static_cast<int64_t>(bufferedBytes / bytesPerFrame);
        bufferedUs = bufferedFrames * 1000000LL / sampleRate_;
    }
    int64_t posUs = lastDecodedPtsUs_.load() - bufferedUs;
    if (posUs < 0) posUs = 0;
    return posUs / 1000;
}

int64_t AudioEngine::getDurationMs() const {
    return durationUs_ / 1000;
}

bool AudioEngine::isPlaying() const {
    return playing_.load();
}

bool AudioEngine::hasReachedEnd() const {
    return reachedEnd_.load();
}

oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) {
    size_t bytesPerFrame = static_cast<size_t>(channelCount_) * sizeof(int16_t);
    size_t bytesRequested = static_cast<size_t>(numFrames) * bytesPerFrame;
    auto *out = reinterpret_cast<uint8_t *>(audioData);

    size_t bytesRead = ringBuffer_->read(out, bytesRequested);
    if (bytesRead < bytesRequested) {
        std::memset(out + bytesRead, 0, bytesRequested - bytesRead);
    }
    return oboe::DataCallbackResult::Continue;
}
