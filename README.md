# Zaint

Zaint is a free Android image editor for drawing, retouching, and creative photo editing. It is designed for working directly on images with layers, text, drawing tools, and effects.

## Features

- Create new images or open existing ones
- Draw with brush, pencil, marker, calligraphy, line, and arrow tools
- Work with layers: add, duplicate, reorder, merge, and control visibility
- Add text with fonts, alignment, outline, shadow, background, and curved text
- Import images, copy and paste selections, and place or transform layers
- Use stickers, speech bubbles, color tools, fills, gradients, and borders
- Retouch images with eraser, smudge, warp, pixelate, replace, and selection tools
- Adjust brightness, exposure, contrast, saturation, hue, warmth, highlights, shadows, blur, sharpen, vignette, and more
- Save editable Zaint projects as `.znt` files, or export images as PNG and JPG

## Requirements

- Android Studio with an Android SDK installed
- JDK 17
- Android SDK Platform 34

## Build

Create a debug APK:

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.

## Project structure

- `app` — Android application module
- `zpaint-core` — editor, tools, UI, and image processing
- `colorpicker` — color picker module

## License

Zaint is licensed under the GNU General Public License v3.0. See the `LICENSE` file for details.
