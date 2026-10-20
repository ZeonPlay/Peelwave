# Peelwave implementation status

Updated: 2026-10-20

This file tracks what is implemented in the current development branch. A feature is only marked as complete after it is built and verified on an Android device.

## Product decisions

- Platform: Android native.
- Processing model: offline-first; sticker editing and storage do not require a service or account.
- Local data: source images, project metadata, and rendered sticker files stay under the app's private storage. Android automatic backup is disabled; users explicitly export a ZIP backup when they want one.
- WhatsApp integration: use the public sticker-app ContentProvider contract and ask WhatsApp to confirm each pack separately.
- Static pack constraints: 3–30 stickers, each exactly 512 × 512 WebP and at most 100 KiB; tray icon is a 96 × 96 PNG and at most 50 KiB.
- The official WhatsApp sticker-app contract supports up to 10 packs per app. Peelwave currently applies this limit to pack creation and exposes only validated packs to WhatsApp.

## Current implementation

- Local pack creation, renaming, and deletion.
- Image import via Android Photo Picker.
- Basic sticker canvas, text, emoji, text outline, fit/crop mode, layer list and reordering, undo/redo history while the editor remains open.
- Render static stickers to 512 × 512 WebP with size checks.
- Select a local pack icon and render it to WhatsApp's 96 × 96 PNG tray-icon format.
- Validate sticker count, dimensions, MIME type, size, and tray-icon format before adding a pack.
- Dynamic, read-only WhatsApp ContentProvider for valid pack metadata and sticker assets.
- Explicit "Tambahkan ke WhatsApp" action, using WhatsApp's supported intent contract.
- Export project files to a ZIP backup and restore after archive path, size, schema and referenced-file validation.
- JVM tests for archive-path validation and sticker-pack rules.
- CI workflow builds a debug APK and runs unit tests.

## Not yet implemented

- Automatic or manual background removal. Do not display this as a working feature until an offline-capable model is selected, bundled, licensed, and tested on low-memory devices.
- Real object transforms on the canvas (drag, scale, rotate), richer outline controls, and text editing after placing a layer.
- Persistent undo/redo across app restarts.
- Importing third-party sticker-pack archives and preserving their original editing structure.
- GIF/animated stickers, batch editing, reusable asset library, and templates.
- End-to-end UI automation and documented device/WhatsApp smoke-test results.

## Test plan before release

1. Run the Android unit-test suite and debug APK build in CI.
2. Test image import, EXIF orientation, transparency, very large input files, corrupt images, and low-storage errors.
3. Create a pack with 2, 3, and 30 rendered stickers; confirm 2 is rejected, 3 and 30 pass, and 31 cannot be added.
4. Confirm every exported WebP is 512 × 512, has alpha transparency where expected, and is at most 100 KiB; confirm the tray icon is 96 × 96 PNG and at most 50 KiB.
5. Export a backup, restore it into a clean installation, and compare pack metadata, original images, layer data, rendered output, and icons.
6. Test ZIP path traversal, duplicate entries, oversized entries, truncated ZIP files, unsupported schema versions, and missing referenced assets.
7. On representative lower-end and current Android devices, test a full offline editing session and memory use with large photos.
8. With WhatsApp installed, verify adding a valid pack, cancelling the WhatsApp confirmation, adding an invalid pack, and updating a pack after it has already been added.

## Product UX principles

- The editor is the primary workflow; avoid turning the home screen into a catalogue.
- Tools must visibly communicate enabled/disabled state and be reachable without guesswork.
- Saving and validation need specific success/failure feedback.
- Preview sticker edges on both light and dark backgrounds.
- Keep original imported media safe when rendering fails.
- Accessibility checks include TalkBack names, contrast, text scaling, touch target size, and non-color error messages.
