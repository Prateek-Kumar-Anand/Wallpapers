package com.example.motiongifwallpaper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.net.Uri;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.io.InputStream;

public class GifWallpaperService extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new GifEngine();
    }

    private class GifEngine extends Engine {
        private final Handler handler = new Handler();
        private final Runnable drawRunner = this::drawFrame;
        private Movie movie;
        private boolean visible;
        private long startTime;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            loadSelectedGif();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            if (visible) {
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
        public void onDestroy() {
            super.onDestroy();
            handler.removeCallbacks(drawRunner);
        }

        private void loadSelectedGif() {
            String uriString = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getString(MainActivity.KEY_GIF_URI, null);
            if (uriString == null) return;
            try (InputStream input = getContentResolver().openInputStream(Uri.parse(uriString))) {
                movie = Movie.decodeStream(input);
                startTime = android.os.SystemClock.uptimeMillis();
            } catch (Exception ignored) {
                movie = null;
            }
        }

        private void drawFrame() {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) return;
                canvas.drawColor(Color.BLACK);
                if (movie != null) {
                    int duration = movie.duration() == 0 ? 1000 : movie.duration();
                    int time = (int) ((android.os.SystemClock.uptimeMillis() - startTime) % duration);
                    movie.setTime(time);
                    float scale = Math.max(canvas.getWidth() / (float) movie.width(), canvas.getHeight() / (float) movie.height());
                    canvas.save();
                    canvas.translate((canvas.getWidth() - movie.width() * scale) / 2f, (canvas.getHeight() - movie.height() * scale) / 2f);
                    canvas.scale(scale, scale);
                    movie.draw(canvas, 0, 0);
                    canvas.restore();
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
            handler.removeCallbacks(drawRunner);
            if (visible) handler.postDelayed(drawRunner, 33);
        }
    }
}
