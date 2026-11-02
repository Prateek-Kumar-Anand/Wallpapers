package com.example.motiongifwallpaper;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    static final String PREFS = "motion_gif_wallpaper";
    static final String KEY_GIF_URI = "gif_uri";
    private static final int PICK_GIF_REQUEST = 1001;
    private TextView selectedGif;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView title = new TextView(this);
        title.setText("Motion GIF Wallpaper");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(12));

        TextView subtitle = new TextView(this);
        subtitle.setText("Browse any GIF and turn it into a moving live wallpaper.");
        subtitle.setTextColor(0xDDEFFFFF);
        subtitle.setTextSize(16);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, dp(24));

        selectedGif = new TextView(this);
        selectedGif.setTextColor(0xEFFFFFFF);
        selectedGif.setTextSize(15);
        selectedGif.setPadding(dp(16), dp(16), dp(16), dp(16));
        selectedGif.setBackground(glassPanel(22, 0x33FFFFFF, 0x66FFFFFF));

        Button pick = glassButton("Browse for GIF");
        pick.setOnClickListener(v -> browseForGif());

        Button setWallpaper = glassButton("Set live wallpaper");
        setWallpaper.setOnClickListener(v -> openWallpaperPicker());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(24), dp(28), dp(24), dp(28));
        card.setBackground(glassPanel(32, 0x29FFFFFF, 0x80FFFFFF));
        card.addView(title, fullWidthWrap());
        card.addView(subtitle, fullWidthWrap());
        card.addView(selectedGif, fullWidthWrap());
        card.addView(pick, fullWidthWrapWithTopMargin(24));
        card.addView(setWallpaper, fullWidthWrapWithTopMargin(12));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF111827);
        root.addView(new GlassBackgroundView(this), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        int margin = dp(20);
        cardParams.setMargins(margin, margin, margin, margin);
        root.addView(card, cardParams);
        setContentView(root);
        updateSelectedGifLabel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSelectedGifLabel();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private Button glassButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setBackground(glassPanel(18, 0x38FFFFFF, 0x88FFFFFF));
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        // Ignore taps when another window overlays the button (tapjacking protection).
        button.setFilterTouchesWhenObscured(true);
        return button;
    }

    private GradientDrawable glassPanel(int radiusDp, int color, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(Math.max(1, dp(1)), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams fullWidthWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams fullWidthWrapWithTopMargin(int marginDp) {
        LinearLayout.LayoutParams params = fullWidthWrap();
        params.topMargin = dp(marginDp);
        return params;
    }

    private void browseForGif() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/gif");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, PICK_GIF_REQUEST);
        } catch (ActivityNotFoundException e) {
            toast("No file picker available on this device.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_GIF_REQUEST || resultCode != RESULT_OK || data == null) return;

        Uri gifUri = data.getData();
        if (!GifSource.isAllowedUri(gifUri)) {
            toast("That file can't be used.");
            return;
        }
        long size = GifSource.size(getContentResolver(), gifUri);
        if (size > GifSource.MAX_BYTES) {
            toast("That GIF is too large (max " + (GifSource.MAX_BYTES / (1024 * 1024)) + " MB).");
            return;
        }
        if (!GifSource.hasGifHeader(getContentResolver(), gifUri)) {
            toast("That file isn't a valid GIF.");
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String previous = prefs.getString(KEY_GIF_URI, null);

        try {
            getContentResolver().takePersistableUriPermission(gifUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // Some providers don't offer persistable grants; continue instead of crashing.
        }

        prefs.edit().putString(KEY_GIF_URI, gifUri.toString()).apply();

        // Release the old grant so permissions don't pile up against the system limit.
        if (previous != null && !previous.equals(gifUri.toString())) {
            try {
                getContentResolver().releasePersistableUriPermission(Uri.parse(previous), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
        }

        updateSelectedGifLabel();
        toast("GIF selected. Now set the live wallpaper.");
    }

    private void updateSelectedGifLabel() {
        if (selectedGif == null) return;
        String stored = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_GIF_URI, null);
        if (stored == null) {
            selectedGif.setText("No GIF selected yet.");
            return;
        }
        Uri uri = Uri.parse(stored);
        if (!GifSource.isAllowedUri(uri)) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_GIF_URI).apply();
            selectedGif.setText("No GIF selected yet.");
            return;
        }
        selectedGif.setText("Selected GIF:\n" + GifSource.displayName(getContentResolver(), uri));
    }

    private void openWallpaperPicker() {
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_GIF_URI, null) == null) {
            toast("Browse for a GIF first.");
            return;
        }
        Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(this, GifWallpaperService.class));
        try {
            startActivity(intent);
            return;
        } catch (ActivityNotFoundException ignored) {
        }
        try {
            startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            return;
        } catch (ActivityNotFoundException ignored) {
        }
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (ActivityNotFoundException e) {
            toast("Couldn't open wallpaper settings.");
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private static class GlassBackgroundView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        GlassBackgroundView(Activity activity) {
            super(activity);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) return;
            float dot = 14 * getResources().getDisplayMetrics().density;
            paint.setShader(new LinearGradient(0, 0, width, height, 0xFF111827, 0xFF312E81, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);
            paint.setShader(new RadialGradient(width * 0.25f, height * 0.2f, width * 0.55f, 0xAA22D3EE, 0x0022D3EE, Shader.TileMode.CLAMP));
            canvas.drawCircle(width * 0.25f, height * 0.2f, width * 0.55f, paint);
            paint.setShader(new RadialGradient(width * 0.85f, height * 0.8f, width * 0.6f, 0xAA8B5CF6, 0x008B5CF6, Shader.TileMode.CLAMP));
            canvas.drawCircle(width * 0.85f, height * 0.8f, width * 0.6f, paint);
            paint.setShader(null);
            paint.setColor(0x18FFFFFF);
            for (int i = 0; i < 9; i++) {
                canvas.drawCircle(width * (0.1f + i * 0.11f), height * (0.15f + (i % 3) * 0.28f), dot, paint);
            }
        }
    }
}
