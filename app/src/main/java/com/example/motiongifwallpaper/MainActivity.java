package com.example.motiongifwallpaper;

import android.app.Activity;
import android.app.WallpaperManager;
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
        title.setPadding(0, 0, 0, 12);

        TextView subtitle = new TextView(this);
        subtitle.setText("Browse any GIF and turn it into a moving live wallpaper.");
        subtitle.setTextColor(0xDDEFFFFF);
        subtitle.setTextSize(16);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, 24);

        selectedGif = new TextView(this);
        selectedGif.setTextColor(0xEFFFFFFF);
        selectedGif.setTextSize(15);
        selectedGif.setPadding(24, 24, 24, 24);
        selectedGif.setBackground(glassPanel(22, 0x33FFFFFF, 0x66FFFFFF));

        Button pick = glassButton("Browse for GIF");
        pick.setOnClickListener(v -> browseForGif());

        Button setWallpaper = glassButton("Set live wallpaper");
        setWallpaper.setOnClickListener(v -> openWallpaperPicker());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(36, 40, 36, 40);
        card.setBackground(glassPanel(32, 0x29FFFFFF, 0x80FFFFFF));
        card.addView(title, fullWidthWrap());
        card.addView(subtitle, fullWidthWrap());
        card.addView(selectedGif, fullWidthWrap());
        card.addView(pick, fullWidthWrapWithTopMargin(24));
        card.addView(setWallpaper, fullWidthWrapWithTopMargin(12));

        FrameLayout root = new FrameLayout(this);
        root.addView(new GlassBackgroundView(this), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        cardParams.setMargins(32, 32, 32, 32);
        root.addView(card, cardParams);
        setContentView(root);
        updateSelectedGifLabel();
    }

    private Button glassButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setBackground(glassPanel(18, 0x38FFFFFF, 0x88FFFFFF));
        button.setPadding(20, 14, 20, 14);
        return button;
    }

    private GradientDrawable glassPanel(int radius, int color, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(2, strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams fullWidthWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams fullWidthWrapWithTopMargin(int margin) {
        LinearLayout.LayoutParams params = fullWidthWrap();
        params.topMargin = margin;
        return params;
    }

    private void browseForGif() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/gif");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_GIF_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_GIF_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri gifUri = data.getData();
            if (gifUri == null) return;
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (flags != 0) {
                getContentResolver().takePersistableUriPermission(gifUri, flags);
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_GIF_URI, gifUri.toString()).apply();
            updateSelectedGifLabel();
            Toast.makeText(this, "GIF selected. Now set the live wallpaper.", Toast.LENGTH_LONG).show();
        }
    }

    private void updateSelectedGifLabel() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String uri = prefs.getString(KEY_GIF_URI, null);
        selectedGif.setText(uri == null ? "No GIF selected yet." : "Selected GIF:\n" + uri);
    }

    private void openWallpaperPicker() {
        Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(this, GifWallpaperService.class));
        try {
            startActivity(intent);
        } catch (Exception ex) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private static class GlassBackgroundView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        GlassBackgroundView(Activity activity) {
            super(activity);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            paint.setShader(new LinearGradient(0, 0, width, height, 0xFF111827, 0xFF312E81, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);
            paint.setShader(new RadialGradient(width * 0.25f, height * 0.2f, width * 0.55f, 0xAA22D3EE, 0x0022D3EE, Shader.TileMode.CLAMP));
            canvas.drawCircle(width * 0.25f, height * 0.2f, width * 0.55f, paint);
            paint.setShader(new RadialGradient(width * 0.85f, height * 0.8f, width * 0.6f, 0xAA8B5CF6, 0x008B5CF6, Shader.TileMode.CLAMP));
            canvas.drawCircle(width * 0.85f, height * 0.8f, width * 0.6f, paint);
            paint.setShader(null);
            paint.setColor(0x18FFFFFF);
            for (int i = 0; i < 9; i++) {
                canvas.drawCircle(width * (0.1f + i * 0.11f), height * (0.15f + (i % 3) * 0.28f), 42, paint);
            }
        }
    }
}
