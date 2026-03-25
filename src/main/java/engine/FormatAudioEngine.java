package engine;

public interface FormatAudioEngine extends AutoCloseable{

    void play() throws Exception;
    void pause();
    void resume();
    void stop();
    void setVolume(float volume);
    float getVolume();
    void close();
    boolean isPlaying();
    boolean isPaused();
}
