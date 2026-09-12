#include "ring_buffer.h"
#include <algorithm>
#include <cstring>

RingBuffer::RingBuffer(size_t capacityBytes)
    : buffer_(new uint8_t[capacityBytes]), capacity_(capacityBytes) {}

size_t RingBuffer::write(const uint8_t *data, size_t len) {
    size_t freeSpace = capacity_ - count_.load(std::memory_order_acquire);
    size_t toWrite = std::min(len, freeSpace);
    if (toWrite == 0) return 0;

    size_t firstChunk = std::min(toWrite, capacity_ - head_);
    std::memcpy(buffer_.get() + head_, data, firstChunk);
    if (toWrite > firstChunk) {
        std::memcpy(buffer_.get(), data + firstChunk, toWrite - firstChunk);
    }
    head_ = (head_ + toWrite) % capacity_;
    count_.fetch_add(toWrite, std::memory_order_release);
    return toWrite;
}

size_t RingBuffer::read(uint8_t *dst, size_t len) {
    size_t available = count_.load(std::memory_order_acquire);
    size_t toRead = std::min(len, available);
    if (toRead == 0) return 0;

    size_t firstChunk = std::min(toRead, capacity_ - tail_);
    std::memcpy(dst, buffer_.get() + tail_, firstChunk);
    if (toRead > firstChunk) {
        std::memcpy(dst + firstChunk, buffer_.get(), toRead - firstChunk);
    }
    tail_ = (tail_ + toRead) % capacity_;
    count_.fetch_sub(toRead, std::memory_order_release);
    return toRead;
}

void RingBuffer::clear() {
    head_ = 0;
    tail_ = 0;
    count_.store(0, std::memory_order_release);
}

size_t RingBuffer::availableToRead() const {
    return count_.load(std::memory_order_acquire);
}

size_t RingBuffer::availableToWrite() const {
    return capacity_ - count_.load(std::memory_order_acquire);
}
