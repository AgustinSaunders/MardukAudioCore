# MardukAudioCore

A high-performance, modular Java audio processing engine designed for music players, Digital Audio Workstations (DAWs), and professional audio applications.

![Version](https://img.shields.io/badge/version-1.0-blue)
![Language](https://img.shields.io/badge/language-Java-orange)
![License](https://img.shields.io/badge/license-MIT-green)

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Audio Processing Pipeline](#audio-processing-pipeline)
- [Usage Examples](#usage-examples)
- [API Reference](#api-reference)
- [Supported Audio Formats](#supported-audio-formats)
- [Contributing](#contributing)
- [License](#license)

## Overview

MardukAudioCore is a professional-grade audio processing library built in Java. It leverages FFmpeg for robust multi-format audio decoding and provides a flexible, extensible architecture for real-time audio processing. Whether you're building a music player, a DAW, or any audio-centric application, MardukAudioCore provides the foundation you need.

### Key Strengths

- **Multi-Format Support**: Decode virtually any audio format via FFmpeg
- **Real-Time Processing**: Low-latency audio processing with dedicated threads
- **Modular Architecture**: Extensible processor interface for custom effects
- **Thread-Safe Operations**: Safe concurrent access from UI and audio threads
- **Professional Quality**: 16-bit PCM stereo output at 44.1kHz (32-bit can be implemented with small changes)

## Features

### Core Audio Playback

✅ **Multi-Format Decoding**
- Support for MP3, WAV, FLAC, OGG, AAC, WMA, and more
- Automatic format detection via FFmpeg
- Graceful error handling and fallback mechanisms

✅ **Playback Control**
- Play, pause, resume, and stop operations
- Precision seeking within audio files
- Real-time playback state monitoring

✅ **Volume Management**
- Smooth fade transitions for volume changes
- Gain control (0% - 100%)
- Anti-click/anti-pop audio transitions

✅ **Audio Processing Pipeline**
- Modular processor interface for custom effects
- Built-in gain processor with smooth transitions
- Extensible architecture for equalizers, compressors, and more

### Technical Features

✅ **Professional Audio Quality**
- 32-bit floating-point internal processing
- 16-bit PCM stereo output
- 44.1kHz standard sample rate
- Stereo interleaved format

✅ **Performance Optimization**
- Low-latency 32KB output buffer
- High-priority playback thread
- Efficient memory management
- Minimal CPU overhead

✅ **Logging & Debugging**
- Comprehensive SLF4J logging
- Detailed error diagnostics
- Performance monitoring capabilities
- Configurable log levels

### Build Tools

- Maven 3.6+
- Git (for version control)

## Installation

### Step 1: Install FFmpeg

FFmpeg is **required** for audio decoding. Choose your operating system below:

#### Debian-based Distributions (Ubuntu, Linux Mint, etc.)

```bash
sudo apt update
sudo apt install ffmpeg
```

#### Arch-based Distributions (Arch Linux, Manjaro, etc.)

```bash
sudo pacman -S ffmpeg
```

#### RHEL-based Distributions (Red Hat, CentOS, Fedora)

```bash
sudo yum install ffmpeg
# or for newer systems:
sudo dnf install ffmpeg
```

#### macOS

```bash
# Using Homebrew
brew install ffmpeg
```

#### Windows

Download and install from: https://ffmpeg.org/download.html

Or using package managers:
```powershell
# Using Chocolatey
choco install ffmpeg

# Using Scoop
scoop install ffmpeg
```

### Verify FFmpeg Installation

After installation, verify that FFmpeg is properly installed:

```bash
ffmpeg -version
ffprobe -version
```

Both commands should output version information. If not, FFmpeg is not properly installed or not in your PATH.

### Step 2: Clone the Repository

```bash
git clone https://github.com/AgustinSaunders/MardukAudioCore
cd MardukAudioCore
```

### Step 3: Build the Project

Using Maven:

```bash
mvn clean install
```

This will:
- Download all dependencies
- Compile the Java source code
- Run any tests
- Create the JAR file in the `target/` directory

### Step 4: Run the Application

```bash
mvn exec:java -Dexec.mainClass="Main"
```

Or directly with Java (after building):

```bash
java -cp target/MardukAudioCore-1.0.jar Main
```

## Quick Start

### Basic Audio Playback

```java
import engine.*;
import processors.*;

public class AudioPlayerExample {
    public static void main(String[] args) throws Exception {
        // Create a gain processor
        GainProcessor gainProcessor = new GainProcessor(0.5f);
        
        // Create the audio engine
        try (FormatAudioEngine engine = new FFmpegAudioEngine(gainProcessor, "song.mp3")) {
            // Start playback
            engine.play();
            
            // Play for 10 seconds
            Thread.sleep(10000);
            
            // Pause playback
            engine.pause();
            Thread.sleep(2000);
            
            // Resume playback
            engine.resume();
            
            // Increase volume to 80%
            engine.setVolume(0.8f);
            
            // Wait until playback ends
            while (engine.isPlaying()) {
                Thread.sleep(100);
            }
        } catch (Exception e) {
            System.err.println("Playback error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

### Interactive Music Player

```java
import utils.Player;

public class InteractivePlayerExample {
    public static void main(String[] args) {
        Player player = new Player();
        player.start();  // Starts the interactive console interface
    }
}
```

## Architecture

### High-Level Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    MardukAudioCore Engine                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────┐      ┌─────────────────┐   ┌────────────┐  │
│  │   Player    │───▶ │FormatAudioEngine│─▶│  Controls  │  │
│  └─────────────┘      └─────────────────┘   └────────────┘  │
│                             │                               │
│      ┌──────────────────────┼──────────────────────┐        │
│      │                      │                      │        │
│      ▼                      ▼                      ▼        │
│  ┌──────────┐         ┌──────────┐         ┌──────────┐     │
│  │  Decoder │         │Processors│         │  Output  │     │
│  │ (FFmpeg) │         │ (Gain,   │         │  (Audio  │     │
│  │          │         │ EQ, etc) │         │   Line)  │     │
│  └──────────┘         └──────────┘         └──────────┘     │
│      │                      │                      │        │
│      ▼                      ▼                      ▼        │
│   Float32             Float32 Processed      PCM 16-bit     │
│   Samples             Samples                Bytes          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Component Layers

**Playback Layer**
- `Player` - Interactive console interface
- `FormatAudioEngine` - Main engine interface

**Core Processing Layer**
- `FFmpegAudioEngine` - FFmpeg-based implementation
- `FFmpegAudioDecoder` - Audio format decoding
- `AudioProcessor` - Processing interface

**Audio Processors**
- `GainProcessor` - Volume control with smooth fading

**Utility Layer**
- `AudioUtils` - Format conversion utilities
- `FileLoader` - File path management

## Project Structure

```
MardukAudioCore/
├── pom.xml                          # Maven configuration
├── README.md                        # This file
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── Main.java           # Application entry point
│   │   │   ├── engine/
│   │   │   │   ├── FormatAudioEngine.java      # Engine interface
│   │   │   │   ├── FFmpegAudioEngine.java      # FFmpeg implementation
│   │   │   │   └── FFmpegAudioDecoder.java     # Decoder wrapper
│   │   │   ├── processors/
│   │   │   │   ├── AudioProcessor.java         # Processor interface
│   │   │   │   └── GainProcessor.java          # Gain/volume control
│   │   │   └── utils/
│   │   │       ├── AudioUtils.java             # Audio conversion utilities
│   │   │       ├── FileLoader.java             # File path management
│   │   │       └── Player.java                 # Interactive player
│   │   └── resources/
│   │       └── logback.xml                     # Logging configuration
│   └── test/
│       └── java/                              # Unit tests (future)
└── target/                          # Build output directory
    ├── classes/                     # Compiled classes
    └── MardukAudioCore-1.0.jar     # Packaged JAR
```

## Audio Processing Pipeline

### Signal Flow

```
1. Audio File Input
   ↓
2. FFmpeg Decoding
   - Format detection
   - Stream parsing
   - Codec initialization
   ↓
3. Resampling
   - Convert to stereo
   - Standardize sample rate (44.1 kHz)
   - Float32 normalization (-1.0 to 1.0)
   ↓
4. Processing Chain
   - GainProcessor (volume control)
   - [Future] EqualizerProcessor (frequency shaping)
   - [Future] CompressorProcessor (dynamic range)
   - [Future] Custom processors
   ↓
5. Format Conversion
   - Float32 → PCM 16-bit
   - Byte order handling (little-endian)
   ↓
6. Audio Output
   - SourceDataLine buffering
   - System audio playback
   ↓
7. Speaker Output
```

### Data Formats

**Internal Processing Format**
```
Type:           32-bit IEEE floating-point (float)
Range:          -1.0 to 1.0 (normalized)
Channels:       2 (stereo)
Sample Rate:    44100 Hz
Format:         Interleaved (LRLRLR...)
Buffer Size:    1024-4096 samples per block
```

**Output Format**
```
Type:           16-bit signed integer (PCM_SIGNED)
Range:          -32768 to 32767
Channels:       2 (stereo)
Sample Rate:    44100 Hz
Format:         4 bytes per frame (2 channels × 2 bytes)
Buffer Size:    32 KB
```

## Usage Examples

### Example 1: Basic Playback with Volume Control

```java
import engine.FFmpegAudioEngine;
import processors.GainProcessor;

public class BasicPlaybackExample {
    public static void main(String[] args) throws Exception {
        GainProcessor gain = new GainProcessor(0.5f);
        
        try (var engine = new FFmpegAudioEngine(gain, "music.mp3")) {
            engine.play();
            
            // Gradually increase volume
            for (float vol = 0.5f; vol <= 1.0f; vol += 0.1f) {
                engine.setVolume(vol);
                Thread.sleep(1000);
            }
            
            // Wait for playback to complete
            while (engine.isPlaying()) {
                Thread.sleep(100);
            }
        }
    }
}
```

### Example 2: Seeking and Time Navigation

```java
import engine.FFmpegAudioEngine;
import processors.GainProcessor;

public class SeekingExample {
    public static void main(String[] args) throws Exception {
        GainProcessor gain = new GainProcessor(0.7f);
        
        try (var engine = new FFmpegAudioEngine(gain, "audio.wav")) {
            engine.play();
            
            // Get total duration
            double duration = engine.getDuration();
            System.out.printf("Track duration: %.2f seconds%n", duration);
            
            // Seek to 30 seconds
            engine.seek(30.0);
            Thread.sleep(5000);
            
            // Seek to 50% of the track
            engine.seek(duration / 2.0);
            
            // Wait for playback
            while (engine.isPlaying()) {
                Thread.sleep(100);
            }
        }
    }
}
```

### Example 3: Processing Chain

```java
import processors.*;

public class ProcessingChainExample {
    public static void main(String[] args) throws Exception {
        // Create processors
        AudioProcessor gainControl = new GainProcessor(0.8f);
        
        // Create audio samples (normally from decoder)
        float[] samples = new float[1024];
        // ... fill with audio data ...
        
        // Apply processing chain
        gainControl.process(samples);
        
        // Continue with audio output...
    }
}
```

## API Reference

### FormatAudioEngine Interface

The main interface for audio playback control.

#### Methods

| Method | Description |
|--------|-------------|
| `void play()` | Start audio playback |
| `void pause()` | Pause current playback |
| `void resume()` | Resume from pause |
| `void stop()` | Stop playback completely |
| `void seek(double seconds)` | Seek to specific time |
| `void setVolume(float volume)` | Set volume (0.0 to 1.0) |
| `float getVolume()` | Get current volume |
| `boolean isPlaying()` | Check if audio is playing |
| `boolean isPaused()` | Check if audio is paused |
| `double getDuration()` | Get total track duration in seconds |
| `void close()` | Close engine and release resources |

### AudioProcessor Interface

Interface for implementing custom audio processors.

#### Methods

| Method | Description |
|--------|-------------|
| `void process(float[] samples)` | Process audio samples in-place |

### GainProcessor Class

Volume control with smooth fade transitions.

#### Constructors

```java
GainProcessor(float initialGain)  // 0.0 to 1.0
```

#### Methods

```java
void setGain(float gain)          // Set target volume
float getGain()                   // Get current volume
void process(float[] samples)     // Apply gain to samples
boolean isFinished()              // Check if fade completed
```

## Supported Audio Formats

MardukAudioCore supports any format that FFmpeg can decode:

### Common Formats

| Format | Extension | Codec | Notes |
|--------|-----------|-------|-------|
| MPEG-1 Audio Layer III | .mp3 | MP3 | Wide compatibility |
| Waveform Audio | .wav | PCM, ADPCM | Uncompressed or compressed |
| Free Lossless Audio | .flac | FLAC | Lossless compression |
| Ogg Vorbis | .ogg | Vorbis | Open-source codec |
| Advanced Audio Coding | .aac | AAC | Apple iTunes standard |
| Windows Media Audio | .wma | WMA | Microsoft format |
| Apple Lossless | .m4a | ALAC | Apple format |
| Opus | .opus | Opus | Modern codec |
| MIDI | .mid | MIDI | Music notation format |

### Format Detection

Formats are detected automatically by FFmpeg based on file extension and content analysis.

## Performance

## Common Issues & Troubleshooting

### Issue: "FFmpeg not found" or "libavformat not found"

**Solution**: Ensure FFmpeg is installed and in your system PATH.

```bash
# Verify installation
ffmpeg -version

# Add to PATH if needed (Linux/macOS)
export PATH=$PATH:/usr/local/bin
```

### Issue: Audio playback cuts off prematurely

**Solution**: Check for ArrayIndexOutOfBoundsException in logs. Ensure buffer sizes match sample counts.

### Issue: No audio output

**Solution**: 
1. Verify FFmpeg is installed
2. Check that audio file is valid: `ffprobe filename.mp3`
3. Ensure system audio is not muted
4. Check logs for errors

## Contributing

We welcome contributions! Here's how you can help:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

### Development Setup

```bash
# Clone your fork
git clone https://github.com/AgustinSaunders/MardukAudioCore

# Build and test
mvn clean test

# Format code (if using IDE)
# Configure your IDE to match project style
```

### Future Features

- ✨ Real-time equalizer processor
- ✨ Dynamic range compressor
- ✨ Reverb/echo effects
- ✨ Audio visualization
- ✨ Waveform caching for faster seeking
- ✨ Metadata extraction (ID3 tags, etc.)
- ✨ Playlist management
- ✨ Audio recording support
- ✨ Plugin system for custom processors

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues, questions, or suggestions:

- **GitHub Issues**: [Report a bug](https://github.com/AgustinSaunders/MardukAudioCore/issues)
- **Documentation**: Check the API reference and examples above
- **Logs**: Enable debug logging in `logback.xml`

## Credits

Built with ❤️ by the Marduk Audio Team

### Technologies Used

- **Java**: Core language
- **FFmpeg**: Audio decoding
- **JavaCPP**: FFmpeg bindings
- **SLF4J**: Logging framework
- **Maven**: Build automation

---

**MardukAudioCore** - Professional Audio Processing for Java Applications
