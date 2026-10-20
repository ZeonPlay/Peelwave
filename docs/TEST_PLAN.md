# Peelwave — Test Plan

## Automated checks

- Unit tests for pack count boundaries (2, 3, 30, 31).
- Unit tests for exact dimensions and the 100 KB static-sticker limit.
- Unit tests for malformed metadata and unsupported schema versions.
- Renderer tests for transparent and opaque inputs, crop and fit, and text overlays.
- State tests for undo/redo boundaries and layer order.
- CI must run unit tests and assemble a debug APK.

## Functional test matrix

| Area | Cases | Expected result |
|---|---|---|
| Pack management | Create, rename, delete, duplicate names | Metadata stays consistent; destructive actions require confirmation |
| Photo import | PNG, JPEG, WebP; canceled picker; missing stream | File copied locally or clear recoverable error |
| Image orientation | Portrait photo with EXIF rotation | Preview and output show correct orientation |
| Editor | Add text/emoji, crop/fit, outline text | Canvas matches saved result within defined tolerances |
| Undo/redo | Undo first action, redo last action, alternate changes | No invalid index, history cap respected |
| Persistence | Close/reopen, low storage, malformed metadata | Drafts retained or recovery is explained; no silent wipe |
| Output | 512 × 512 WebP, alpha, 100 KB boundary | Valid outputs pass; failures explain which rule failed |
| Pack rules | 2, 3, 30, 31 ready stickers | Only 3–30 passes count validation |
| Offline | Airplane mode across core journey | Create, edit, save, and validate work without a network |
| WhatsApp integration | Missing/incompatible WhatsApp, valid/invalid pack | Proper package/status handling and clear actionable message |
| Accessibility | TalkBack, large text, contrast, touch targets | Critical tasks remain operable and understandable |
| Performance | 12 MP / 48 MP photos, repeated edits, 30 stickers | No main-thread decoding, runaway memory, or unexplained crashes |

## Manual release gate

Before a release candidate:
- Verify the APK installs on at least one API 26–29 device/emulator and one modern Android device.
- Test on a low-memory device class; a high-end emulator alone is not sufficient.
- Confirm no network permission or ad/analytics SDK is present unless the product decision changes.
- Verify image and project data remain app-local.
- Test the real “add to WhatsApp” path with the current public integration sample and record WhatsApp version.
- Run accessibility checks and a 5–8 participant usability study.
- Review all third-party licenses and include required notices.
