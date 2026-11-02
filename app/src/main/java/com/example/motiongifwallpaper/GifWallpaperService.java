package com.example.motiongifwallpaper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class GifWallpaperService extends WallpaperService {
    private static final long FRAME_DELAY_MS = 33;

    @Override
    public Engine onCreateEngine() {
        return new GifEngine();
    }

    private class GifEngine extends Engine {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable drawRunner = this::drawFrame;
        private final ExecutorService loader = Executors.newSingleThreadExecutor();

        // Touched on the main thread only.
        private Movie movie;
        private boolean visible;
        private boolean destroyed;
        private long startTime;
        private String loadedUri;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            requestLoad();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            if (visible) {
                requestLoad();
                drawFrame();
            } else {
                handler.removeCallbacks(drawRunner);
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            drawFrame();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            visible = false;
            handler.removeCallbacks(drawRunner);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onDestroy() {
            destroyed = true;
            visible = false;
            handler.removeCallbacksAndMessages(null);
            loader.shutdownNow();
            movie = null;
            super.onDestroy();
        }

        /** Loads the selected GIF off the main thread, only when the selection changed. */
        private void requestLoad() {
            if (destroyed) return;
            final String uriString = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                    .getString(MainActivity.KEY_GIF_URI, null);
            if (uriString == null) {
                loadedUri = null;
                movie = null;
                return;
            }
            if (uriString.equals(loadedUri)) return;
            loadedUri = uriString;
            try {
                loader.execute(() -> decode(uriString));
            } catch (RejectedExecutionException e) {
                loadedUri = null;
            }
        }

        private void decode(String uriString) {
            Movie decoded = null;
            try {
                Uri uri = Uri.parse(uriString);
                byte[] bytes = GifSource.read(getContentResolver(), uri);
                decoded = Movie.decodeByteArray(bytes, 0, bytes.length);
            } catch (Exception | OutOfMemoryError ignored) {
                decoded = null;
            }
            final Movie result = decoded;
            handler.post(() -> {
                if (destroyed || !uriString.equals(loadedUri)) return;
                if (result == null) {
                    loadedUri = null; // allow a retry next time the wallpaper becomes visible
                }
                movie = result;
                startTime = SystemClock.uptimeMillis();
                if (visible) drawFrame();
            });
        }

        private void drawFrame() {
            if (destroyed) return;
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) paint(canvas);
            } catch (RuntimeException ignored) {
                // Surface released or being resized: skip this frame.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            handler.removeCallbacks(drawRunner);
            if (visible && !destroyed) handler.postDelayed(drawRunner, FRAME_DELAY_MS);
        }

        private void paint(Canvas canvas) {
            canvas.drawColor(Color.BLACK);
            Movie m = movie;
            if (m == null || m.width() <= 0 || m.height() <= 0) return;
            int duration = m.duration() <= 0 ? 1000 : m.duration();
            int time = (int) ((SystemClock.uptimeMillis() - startTime) % duration);
            m.setTime(time);
            float scale = Math.max(canvas.getWidth() / (float) m.width(), canvas.getHeight() / (float) m.height());
            canvas.save();
            canvas.translate((canvas.getWidth() - m.width() * scale) / 2f, (canvas.getHeight() - m.height() * scale) / 2f);
            canvas.scale(scale, scale);
            m.draw(canvas, 0, 0);
            canvas.restore();
        }
    }
}
