package com.jiexiangtech.chess.desktop;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

final class UiJobs implements AutoCloseable {
    private final ExecutorService data = Executors.newSingleThreadExecutor(task -> new Thread(task, "royal-data"));

    <T> void submit(Callable<T> work, Consumer<T> success, Consumer<Exception> failure) {
        data.execute(() -> {
            try {
                T result = work.call();
                SwingUtilities.invokeLater(() -> success.accept(result));
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() -> failure.accept(exception));
            }
        });
    }

    @Override public void close() { data.shutdown(); }
}
