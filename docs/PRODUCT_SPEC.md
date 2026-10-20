# Peelwave — Product Specification

Version: 0.1 · Status: working draft for the first Android milestone

## Product intent

Peelwave helps people who regularly make WhatsApp stickers turn images into clean, reusable sticker packs without ads, account creation, or a required internet connection. It should feel like a focused creative tool, not a social feed or an admin dashboard.

## Primary users

1. **Frequent sticker makers** who repeat the same steps and care about speed.
2. **Meme and chat users** who want precise text, emoji, cropping, and outlines.
3. **Organizers of personal sticker packs** who need to rename packs, revisit drafts, and validate exports.

The initial release is Android-only and focused on static stickers.

## Core user journey

1. Create or choose a pack.
2. Import an image from the device.
3. Edit on a canvas with undo and redo.
4. Save a draft without losing its editable structure.
5. Preview on light and dark backgrounds.
6. Validate each rendered sticker and the overall pack.
7. Add one pack at a time to WhatsApp when the official integration is implemented and compatibility-tested.

## Functional requirements

### Pack management
- Create, rename, and delete local packs.
- Keep the pack name and creator/author metadata.
- Display draft count separately from rendered, validated sticker count.
- Allow at most 30 sticker items per pack; a pack cannot be exported to WhatsApp until it has at least 3 valid stickers.
- Never delete pack files silently. Confirm destructive actions and handle missing/corrupt files.

### Editor
- Import images through Android's system picker; copy selected files into app-private storage so access survives restarts.
- Support text, emoji, text outline, crop/fit, layers, undo, and redo.
- Keep source images separate from rendered output.
- Preserve editable layers when saving a draft.
- Make background removal a core feature, but do not claim it is complete until a tested, offline-capable solution is integrated.
- Provide preview backgrounds for checking sticker legibility.

### Output and validation
- Render static output to WebP at exactly 512 × 512 pixels.
- Target a maximum of 100 KB per static sticker.
- Verify output existence, dimensions, format, and file size.
- Validate pack count (3–30) and explain every blocking issue.
- Treat WhatsApp integration as incomplete until a pack provider and add-pack flow have been tested against current WhatsApp versions.

### Import and backup
- Keep originals and editable project state locally by default.
- Define a versioned Peelwave project archive for backup and restore.
- Make imported archives validate before any files replace current project data.
- Never require cloud upload for local editing.

## Non-functional requirements

- Core functions work with airplane mode enabled.
- No analytics, ad SDK, account, or network permission in the MVP.
- Decode large images off the main thread and downsample for preview/rendering.
- Avoid loading full-resolution image sets into memory simultaneously.
- Autosave project metadata after meaningful changes.
- Provide clear, recoverable errors for storage exhaustion and invalid files.
- Support Android dark theme; design for large text, TalkBack, and minimum 48 dp touch targets.
- Test on modest devices as well as current flagship devices.

## MVP scope

**In the first usable prototype:** local pack creation, local pack listing, image import into private storage, editor canvas prototype, basic text/emoji layers, crop/fit toggle, undo/redo, and a static WebP rendering pipeline with validation.

**Not to be represented as complete until implemented and verified:** robust background removal, silhouette outline around cut-out objects, persistent versioned layer projects, portable project archive import/export, and the WhatsApp provider/add-pack flow.

## Acceptance criteria for a release candidate

- A new user can create a pack and import a photo without creating an account.
- A reopened app still lists the pack and retains imported image files.
- Editing controls have visible names, accessible labels, and consistent behavior.
- Undo and redo restore previous editing states without corrupting the original image.
- Save/export runs off the main thread and fails gracefully when the output cannot meet constraints.
- A valid static pack contains 3–30 sticker files; each file is 512 × 512 WebP and at most 100 KB.
- The WhatsApp add action is enabled only after the actual integration is implemented and tested.
- All critical test-plan cases pass on at least two Android device classes.
