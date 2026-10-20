# Peelwave

**An offline-first Android sticker workshop built for people who make stickers often.**

Peelwave is a native Android app project focused on a calm, precise sticker-making workflow: create a pack, import an image, edit it, validate the pack, then add it to WhatsApp.

> Project status: early foundation. This repository is not yet a complete sticker editor or a release-ready WhatsApp integration.

## Product principles

- **Offline by default:** core editing and local projects must not require an account or network access.
- **Editor-first:** get to the canvas quickly; do not bury the main task in a feed or dashboard.
- **Non-destructive editing:** keep source images and editable project data separate from exported sticker files.
- **Predictable exports:** validate format, dimensions, file size, and pack counts before export.
- **Fast and accessible:** responsive controls, TalkBack labels, sufficient contrast, and testing on modest Android devices.

## Planned first release

1. Create, rename, and manage sticker packs.
2. Import images from Android's system photo picker.
3. Edit static stickers with text, emoji, outline, crop, layers, undo, and redo.
4. Save editable projects locally.
5. Render and validate static WebP stickers (512 × 512; at most 100 KB each).
6. Validate a pack (3–30 stickers) and offer a per-pack action to add it to WhatsApp, subject to the supported integration API.
7. Import/export Peelwave project backups.

Background removal is a key requirement but will only be marked complete after a suitable offline-capable implementation passes quality and performance tests.

## Project layout

- `app/` — Android application.
- `docs/PRODUCT_SPEC.md` — scope, user needs, and release criteria.
- `docs/UX_RESEARCH.md` — competitor review and user-research plan.
- `docs/ARCHITECTURE.md` — technical boundaries and data model.
- `docs/TEST_PLAN.md` — validation and quality gates.

## Build

Open this repository in a recent Android Studio and allow Gradle sync. The project uses Kotlin and Jetpack Compose. Build requirements and dependency versions are recorded in the Gradle files.

## Status

The first milestone establishes the Android project and product/UX/architecture documentation. Core image editing, offline background removal, and WhatsApp import integration still require implementation and device testing.

## WhatsApp compatibility

WhatsApp's public sample integration documents static pack requirements including 3–30 stickers, WebP images at exactly 512 × 512, and a maximum of 100 KB per static sticker. A finished integration must also respect the documented pack-level add flow and app compatibility requirements. See the [official sample repository](https://github.com/WhatsApp/stickers).

Peelwave is an independent project and is not affiliated with or endorsed by WhatsApp.
