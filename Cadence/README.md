# Cadence — local music player

A native Android music player (Kotlin + Jetpack Compose UI, C++/Oboe audio
engine) that plays audio files straight from your device's storage, with
an Apple Music-inspired UI.

## What's implemented
- Scans your device's storage for every audio file via MediaStore
- A **native audio engine** (`app/src/main/cpp`) — NDK MediaCodec/MediaExtractor
  decode into a lock-free ring buffer, fed to an Oboe/AAudio output stream
  opened in **exclusive, low-latency mode**, matched to the source file's
  sample rate. This is the actual decode+output path now — ExoPlayer isn't
  used anymore. If a device/HAL doesn't support exclusive mode, it falls
  back to shared mode automatically; either way nothing is re-encoded or
  lossily touched.
- Background playback via a foreground service + MediaSession (built on
  androidx.media, with a manually-built notification), so lock-screen /
  notification controls work
- Play / pause, next / previous, ±15s skip, seek bar, shuffle, repeat
  (off / all / one), Up Next queue, search

## On audio quality — what this actually gets you
- **Exclusive AAudio mode**: on supported devices, your audio gets a
  dedicated stream to the DAC rather than being mixed with system sounds.
  Falls back to shared mode gracefully where unsupported (varies by phone/
  chipset — there's no reliable way to know in advance).
- **No lossy re-encoding**: decoding goes through Android's own platform
  decoders (the same ones every app uses), so FLAC/WAV/MP3/AAC/OGG all play
  as their real content, sample-accurately.
- **Honest limit**: the decoded PCM format used here is 16-bit
  (`AudioFormat::I16`), which is what Android's platform decoders reliably
  output across devices. True 24-bit/float passthrough for hi-res sources
  is decoder- and device-dependent and would need per-format handling I
  didn't build here — say the word if you want that pushed further for a
  specific format/device.

## New build requirement: the NDK
Because there's now C++ code, Android Studio needs the NDK and CMake
installed (it usually prompts for this automatically):
- **Tools → SDK Manager → SDK Tools tab** → check **NDK (Side by side)**
  and **CMake** → Apply.
If Studio shows a banner during sync offering to install missing SDK
components, just click **Install**.

## How to build and install it on your phone
1. Install **Android Studio**: https://developer.android.com/studio
2. Open Android Studio → **Open** → select this `Cadence` folder.
3. Let it sync. First sync will download Gradle, the NDK, and CMake —
   needs internet, can take 10–15 minutes the first time because of the NDK.
4. Plug your phone in via USB. Enable **Developer Options → USB debugging**
   if you haven't (Settings → About phone → tap "Build number" 7 times,
   then Settings → Developer options → USB debugging).
5. Pick your phone in the device dropdown, hit the green **Run ▶** button.
6. Grant the audio permission when the app asks.

## A heads-up on the native code specifically
This is the part I'm least able to guarantee compiles on the first try
without ever running it myself — NDK/JNI/Oboe glue code has more sharp
edges than pure Kotlin. If Android Studio's Build window shows an error
when you first build, paste it back to me (the exact red text in the
"Build" tab) and I'll fix it directly — that's a normal part of native
Android development, not a sign anything is fundamentally wrong.

## What's NOT implemented
- Cross-device sync — this build is local-only by design (your last
  choice). Real sync needs a backend, which is a separate project.

## Project layout
- `app/src/main/cpp/` — the native audio engine (Oboe + NDK MediaCodec)
- `data/` — Song model + MediaStore scanning
- `playback/` — NativeAudioEngine (JNI wrapper), PlaybackService (queue +
  MediaSession + notification), PlayerConnection (what the UI binds to)
- `ui/theme/` — the three color schemes + theme persistence
- `ui/screens/` — Library, Now Playing, Permission screens
- `ui/components/` — reusable song row + mini player
