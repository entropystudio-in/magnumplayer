#ifndef CADENCE_RING_BUFFER_H
#define CADENCE_RING_BUFFER_H

#include <atomic>
#include <cstdint>
#include <memory>

// Single-producer (decode thread), single-consumer (Oboe audio callback)
// byte ring buffer. The consumer side never locks or allocates, which is
// required for a real-time-safe audio callback - it only ever does an
// atomic load and a memcpy.
class RingBuffer {
public:
    explicit RingBuffer(size_t capacityBytes);

    // Producer-only. Returns the number of bytes actually written
    // (may be less than len if the buffer is full).
    size_t write(const uint8_t *data, size_t len);

    // Consumer-only. Returns the number of bytes actually read
    // (may be less than len if the buffer doesn't have enough data yet).
    size_t read(uint8_t *dst, size_t len);

    // Consumer-only in this app's usage (only called while the producer
    // is paused/quiesced during a seek).
    void clear();

    size_t availableToRead() const;
    size_t availableToWrite() const;

private:
    std::unique_ptr<uint8_t[]> buffer_;
    size_t capacity_;
    size_t head_ = 0; // next write position - touched only by producer
    size_t tail_ = 0; // next read position - touched only by consumer
    std::atomic<size_t> count_{0}; // bytes currently stored
};

#endif // CADENCE_RING_BUFFER_H
