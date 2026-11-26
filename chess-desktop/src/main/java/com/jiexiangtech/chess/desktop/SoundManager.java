package com.jiexiangtech.chess.desktop;

import java.io.BufferedInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineEvent;
import org.slf4j.LoggerFactory;

final class SoundManager implements AutoCloseable {
    private final ExecutorService sound = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "royal-audio");
        thread.setDaemon(true);
        return thread;
    });

    void play(String name) {
        sound.execute(() -> {
            try (var resource = SoundManager.class.getResourceAsStream("/assets/sounds/" + name + ".wav")) {
                if (resource == null) return;
                try (var audio = AudioSystem.getAudioInputStream(new BufferedInputStream(resource))) {
                    var clip = AudioSystem.getClip();
                    clip.addLineListener(event -> { if (event.getType() == LineEvent.Type.STOP) clip.close(); });
                    try {
                        clip.open(audio);
                        clip.start();
                    } catch (Exception failure) {
                        clip.close();
                        throw failure;
                    }
                }
            } catch (Exception e) {
                LoggerFactory.getLogger(SoundManager.class).debug("Audio cue unavailable", e);
            }
        });
    }

    @Override public void close() { sound.shutdownNow(); }
}
