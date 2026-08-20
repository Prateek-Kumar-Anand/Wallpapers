# Motion GIF Wallpaper

A simple Android live-wallpaper application that lets you browse for a GIF from any document provider available on the device, persist access to that GIF, and set it as an animated wallpaper.

## Features

- Browse the device, cloud providers, or any app exposed through Android's document picker for an `image/gif` file.
- Persist read access to the selected GIF URI so the live wallpaper can keep playing after restart.
- Render the GIF as a center-cropped animated live wallpaper.
- GitHub Actions workflow builds a debug APK and uploads it as an artifact.

## Build locally

```bash
gradle --no-daemon :app:assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Build in GitHub Actions

Open the **Android APK** workflow in GitHub Actions and run it manually, or push to `main`/`master`. Download the `motion-gif-wallpaper-debug-apk` artifact from the completed workflow run.
