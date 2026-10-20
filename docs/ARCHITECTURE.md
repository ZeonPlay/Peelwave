# Peelwave — Technical Architecture

Version: 0.1

## Platform and stack

- Native Android application.
- Kotlin + Jetpack Compose + Material 3.
- Minimum Android API 26; target API 35; compile SDK 36.
- Local app-private files for source images and rendered assets.
- Versioned JSON metadata for the first prototype; migrate to Room if relational queries or project scale warrant it.
- Coil for image preview and Android's system Photo Picker for importing.
- Coroutines for image decoding and output rendering off the main thread.
- Android ExifInterface for orientation-aware image handling.
- JUnit for pure unit tests and Compose/UI tests for flows.

Dependency versions live in app/build.gradle.kts and should be upgraded deliberately after CI and device testing.

## Boundaries

### UI layer
Compose screens show home, pack detail, editor, layer controls, and validation feedback. Composables should render state and dispatch actions; they should not perform image decoding or file writes on the main thread.

### Domain rules
PackRules owns requirements that can be validated without UI: minimum and maximum sticker counts, output dimensions, size limits, and export readiness. It must be deterministic and unit-tested.

### Local data layer
LocalPackStore owns:
- Pack metadata and versioned JSON encoding.
- Copying picked images into application-private storage.
- Source and exported file lookup.
- Safe write behavior and recovery from malformed metadata.
- Removal of files only after a confirmed user action.

No network service is needed for core workflows.

### Image pipeline
1. Pick an image through the Android picker.
2. Copy to app-private source storage.
3. Read bounds first and downsample for preview.
4. Respect EXIF orientation.
5. Apply crop/fit and ordered editor layers to a transparent 512 × 512 output canvas.
6. Encode WebP, trying progressively lower quality settings when needed.
7. Validate dimensions and bytes before marking the output ready.

The renderer must not claim background removal or true object-silhouette outlines until those operations actually happen in the image pipeline. A text outline is not equivalent to a sticker silhouette outline.

## Proposed project format

Use a versioned record:
- schemaVersion
- pack id, name, author, created/updated timestamps
- asset id and source filename
- export filename and export metadata
- ordered layers with type, text/emoji, normalized position, style, and visibility
- crop mode and future background-removal settings

The format must be forward-compatible: unknown optional fields should be ignored; unsupported schema versions should produce a clear error, not erase data.

## Image and memory safeguards

- Bounds-check before decoding.
- Downsample source previews and avoid keeping multiple full-size bitmaps alive.
- Use dispatchers for file I/O and bitmap work.
- Bound undo history (initial target: 30 snapshots) and consider storing operations rather than image copies.
- Dispose/recycle temporary bitmaps when no longer required.
- Use atomic metadata replacement: write a temp file, flush, then rename.
- Treat source files as immutable during editing.
- Never save exported output over the imported source.

## WhatsApp integration boundary

The official sample integration uses pack metadata, individual sticker files, a provider/API contract, and a separate add action for each pack. Do not implement a generic share intent and call it “add to WhatsApp.” Before shipping, port or adapt the current official Android sample approach, validate each pack, and test against current WhatsApp builds.

Official reference: https://github.com/WhatsApp/stickers/blob/main/Android/README.md

## Background-removal decision gate

Candidate solutions must be evaluated on:
- Fully offline operation after installation.
- Model/library license and redistribution terms.
- APK/AAB size and model footprint.
- RAM, battery, and inference time on modest devices.
- Edge quality on hair, fur, thin objects, transparent items, and cluttered backgrounds.
- A manual correction or recovery path for failures.

Keep the feature behind an interface so the implementation can change without coupling the editor UI to one model.
