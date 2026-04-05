# Exception Handling Guide - MardukAudioCore

This document provides comprehensive guidance on exception handling in the MardukAudioCore audio processing engine.

## Exception Hierarchy

```
Exception (Java)
    └── AudioException (Base class for all audio errors)
        ├── AudioFileException (File operations)
        ├── AudioDecodingException (Decoding operations)
        ├── AudioPlaybackException (Playback operations)
        ├── AudioSeekException (Seeking operations)
        ├── AudioProcessingException (Processing operations)
        └── AudioResourceException (Resource management)
```

## Exception Classes

### 1. AudioException (Base Class)

The root exception for all audio-related errors. Use this to catch all audio exceptions with a single catch block.

**When to use:** When you want to handle any audio error generically.

```java
try {
    engine.play();
} catch (AudioException e) {
    logger.error("Audio error: {}", e.getMessage());
    // Handle any audio-related error
}
```

---

### 2. AudioFileException

Thrown when audio file operations fail.

**Causes:**
- File not found or inaccessible
- Invalid file path
- File permission issues
- Corrupted audio file
- Unsupported audio format

**When to use:** When dealing with file operations like opening or loading files.

```java
try {
    decoder.open("song.mp3");
} catch (AudioFileException e) {
    logger.error("Failed to load audio file: {}", e.getMessage());
    System.err.println("Please verify the file path and try again.");
}
```

---

### 3. AudioDecodingException

Thrown when audio decoding operations fail.

**Causes:**
- Codec not found
- Failed to open codec context
- Audio stream not found
- Decoding packet/frame failure
- Resampling initialization failure
- Unsupported codec

**When to use:** When working with audio decoding operations.

```java
try {
    int samplesRead = decoder.readNextSamples(buffer);
} catch (AudioDecodingException e) {
    logger.error("Failed to decode audio: {}", e.getMessage());
    // Attempt recovery or skip this frame
}
```

---

### 4. AudioPlaybackException

Thrown when audio playback operations fail.

**Causes:**
- Audio device not available
- Failed to open audio output line
- Audio format not supported by system
- Playback thread error
- Buffer underrun
- System audio resources exhausted

**When to use:** When managing playback operations (play, pause, resume, stop).

```java
try {
    engine.play();
} catch (AudioPlaybackException e) {
    logger.error("Playback failed: {}", e.getMessage());
    System.err.println("Check audio device connection and system settings.");
}
```

---

### 5. AudioSeekException

Thrown when audio seeking operations fail.

**Causes:**
- Seek position out of bounds
- Invalid seek timestamp
- FFmpeg seek failure
- Decoder not initialized
- Seek not supported by format
- Buffer flush failure

**When to use:** When seeking within audio files.

```java
try {
    double duration = engine.getDuration();
    if (seekPosition >= 0 && seekPosition <= duration) {
        engine.seek(seekPosition);
    } else {
        throw new AudioSeekException("Seek position out of bounds: " + seekPosition);
    }
} catch (AudioSeekException e) {
    logger.error("Seek failed: {}", e.getMessage());
    System.err.println("Could not seek to the requested position.");
}
```

---

### 6. AudioProcessingException

Thrown when audio processing operations fail.

**Causes:**
- Processor initialization failure
- Invalid processor configuration
- Processing algorithm errors
- Buffer size mismatches
- Out-of-range audio values
- Custom processor errors

**When to use:** When applying audio processors.

```java
try {
    processor.process(audioSamples);
} catch (AudioProcessingException e) {
    logger.error("Processing failed: {}", e.getMessage());
    // Skip this processor or bypass processing
}
```

---

### 7. AudioResourceException

Thrown when audio resource management fails.

**Causes:**
- Failed to allocate buffers
- Failed to release resources
- Resource already closed
- Thread resource management failure
- Memory allocation failure
- FFmpeg context allocation failure

**When to use:** When managing resource lifecycle (close, cleanup).

```java
try {
    engine.close();
} catch (AudioResourceException e) {
    logger.error("Failed to release resources: {}", e.getMessage());
    // Attempt alternative cleanup
}
```

---

## Best Practices for Exception Handling

### 1. Catch Specific Exceptions First

```java
try {
    engine.play();
} catch (AudioFileException e) {
    // Handle file errors specifically
    logger.error("File error: {}", e.getMessage());
} catch (AudioPlaybackException e) {
    // Handle playback errors specifically
    logger.error("Playback error: {}", e.getMessage());
} catch (AudioException e) {
    // Handle other audio errors
    logger.error("Audio error: {}", e.getMessage());
}
```

### 2. Always Log with Context

```java
try {
    engine.seek(seekPosition);
} catch (AudioSeekException e) {
    logger.error("Seek failed for position {} seconds: {}", seekPosition, e.getMessage(), e);
    // The third parameter includes the full stack trace in logs
}
```

### 3. Use Try-with-Resources

The `FormatAudioEngine` implements `AutoCloseable`, so use try-with-resources for automatic cleanup:

```java
try (FormatAudioEngine engine = new FFmpegAudioEngine(processor, filePath)) {
    engine.play();
    // Use engine...
} catch (AudioException e) {
    logger.error("Error: {}", e.getMessage());
}
// engine.close() is automatically called
```

### 4. Validate Input Before Operations

```java
double seekPosition = getUserInput();
double duration = engine.getDuration();

try {
    if (seekPosition < 0 || seekPosition > duration) {
        throw new AudioSeekException("Seek position out of bounds: " + seekPosition);
    }
    engine.seek(seekPosition);
} catch (AudioSeekException e) {
    logger.error("Invalid seek: {}", e.getMessage());
}
```

### 5. Provide User-Friendly Error Messages

```java
try {
    engine.play();
} catch (AudioFileException e) {
    System.err.println("❌ File Error: Could not load the audio file.");
    System.err.println("   Please verify the file path and try again.");
} catch (AudioPlaybackException e) {
    System.err.println("❌ Playback Error: Check your audio device.");
    System.err.println("   Ensure audio is not muted and device is connected.");
} catch (AudioException e) {
    System.err.println("❌ Audio Error: " + e.getMessage());
}
```

### 6. Handle Exceptions Gracefully

```java
private void handlePlayback() {
    try {
        engine.play();
    } catch (AudioFileException e) {
        logger.error("File not found, attempting alternative...", e);
        // Try alternative file or skip
    } catch (AudioPlaybackException e) {
        logger.warn("Playback unavailable, retrying...", e);
        // Retry after delay
        Thread.sleep(1000);
        try {
            engine.play();
        } catch (AudioException retry) {
            logger.error("Playback retry failed", retry);
        }
    }
}
```

---

## Exception Usage Examples

### Example 1: Complete Playback with Exception Handling

```java
public void playAudioFile(String filePath) {
    try {
        GainProcessor gainProcessor = new GainProcessor(0.5f);
        
        try (FormatAudioEngine engine = new FFmpegAudioEngine(gainProcessor, filePath)) {
            engine.play();
            logger.info("Playback started for: {}", filePath);
            
            // Wait for playback to complete
            while (engine.isPlaying()) {
                Thread.sleep(100);
            }
            
            logger.info("Playback completed");
        }
    } catch (AudioFileException e) {
        logger.error("Audio file error: {}", e.getMessage());
        System.err.println("ERROR: Could not load the audio file.");
    } catch (AudioDecodingException e) {
        logger.error("Audio decoding error: {}", e.getMessage());
        System.err.println("ERROR: Audio format not supported.");
    } catch (AudioPlaybackException e) {
        logger.error("Playback error: {}", e.getMessage());
        System.err.println("ERROR: Audio device not available.");
    } catch (AudioException e) {
        logger.error("Unknown audio error: {}", e.getMessage(), e);
        System.err.println("ERROR: An unexpected audio error occurred.");
    } catch (InterruptedException e) {
        logger.warn("Playback interrupted");
        Thread.currentThread().interrupt();
    }
}
```

### Example 2: Seeking with Validation

```java
public void seekToPosition(FormatAudioEngine engine, double seconds) throws AudioSeekException {
    try {
        double duration = engine.getDuration();
        
        if (duration <= 0) {
            throw new AudioSeekException("Cannot determine file duration");
        }
        
        if (seconds < 0) {
            throw new AudioSeekException("Seek position cannot be negative: " + seconds);
        }
        
        if (seconds > duration) {
            throw new AudioSeekException(
                String.format("Seek position (%.2f) exceeds file duration (%.2f)", seconds, duration)
            );
        }
        
        engine.seek(seconds);
        logger.info("Successfully seeked to {} seconds", seconds);
    } catch (AudioSeekException e) {
        logger.error("Seek operation failed: {}", e.getMessage());
        throw e;
    }
}
```

### Example 3: Processing Chain with Error Recovery

```java
public void processAudioWithRecovery(float[] samples, List<AudioProcessor> processors) {
    for (AudioProcessor processor : processors) {
        try {
            processor.process(samples);
            logger.debug("Processor applied successfully");
        } catch (AudioProcessingException e) {
            logger.warn("Processor failed, skipping: {}", e.getMessage());
            // Skip this processor but continue with others
        } catch (Exception e) {
            logger.error("Unexpected error in processor: {}", e.getMessage());
            throw new AudioProcessingException("Processing pipeline failed", e);
        }
    }
}
```

---

## Creating Custom Exceptions

When extending the framework, you can create specialized exceptions:

```java
package exceptions;

/**
 * Exception for custom audio effects operations.
 */
public class AudioEffectException extends AudioProcessingException {
    
    public AudioEffectException(String message) {
        super(message);
    }
    
    public AudioEffectException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

---

## Testing Exception Handling

```java
@Test
public void testAudioFileException() {
    try {
        FFmpegAudioDecoder decoder = new FFmpegAudioDecoder();
        decoder.open("nonexistent_file.mp3");
        fail("Should have thrown AudioFileException");
    } catch (AudioFileException e) {
        assertEquals("File not found", e.getMessage());
        logger.info("AudioFileException correctly thrown");
    }
}

@Test
public void testAudioSeekException() {
    try {
        engine.seek(-5.0); // Invalid position
        fail("Should have thrown AudioSeekException");
    } catch (AudioSeekException e) {
        assertTrue(e.getMessage().contains("out of bounds"));
        logger.info("AudioSeekException correctly thrown");
    }
}
```

---

## Summary Table

| Exception | Cause | Handle By | Recovery |
|-----------|-------|-----------|----------|
| AudioFileException | File errors | Check path, verify permissions | Retry or use alternative file |
| AudioDecodingException | Codec errors | Verify format support | Skip to next file |
| AudioPlaybackException | Device errors | Check audio settings | Retry or disable audio |
| AudioSeekException | Seek errors | Validate position | Reset position or skip seek |
| AudioProcessingException | Processing errors | Check configuration | Skip processor or bypass |
| AudioResourceException | Resource errors | Check system resources | Restart application |

---

**For more information, see the JavaDoc documentation for each exception class.**

