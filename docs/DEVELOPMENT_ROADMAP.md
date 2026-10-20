# Peelwave — Development Roadmap

## Milestone 0 — Foundations
- [x] Create repository identity and Android/Compose build configuration.
- [x] Write product scope, UX study plan, architecture boundaries, and test plan.
- [ ] Run first CI build and resolve any toolchain issues.

## Milestone 1 — Local sticker workshop
- [ ] Stable pack creation, rename, and delete.
- [ ] Photo Picker import copied to app-private storage.
- [ ] Recoverable local project storage and atomic saves.
- [ ] Editor canvas, crop/fit, text, emoji, layers, undo/redo.
- [ ] Real renderer with EXIF handling and output verification.

## Milestone 2 — Sticker quality
- [ ] Transparent object cutout/background removal using an evaluated offline model.
- [ ] Manual edge refinement and true silhouette outline.
- [ ] Light/dark preview and accessibility polishing.
- [ ] Quality/size tuning across varied images and low-memory devices.

## Milestone 3 — WhatsApp integration
- [ ] Adapt the official Android integration sample to dynamic local packs.
- [ ] Implement provider metadata and per-pack add action.
- [ ] Validate pack count, WebP dimensions, file size, and app compatibility.
- [ ] Test with current WhatsApp versions and graceful failure states.

## Milestone 4 — Portability and release
- [ ] Versioned project archive import/export and backups.
- [ ] Privacy, license, and Play listing review.
- [ ] Test matrix, usability study, crash/performance profiling.
- [ ] Signed release build and installation/update rehearsal.

## Release policy
Do not mark a feature complete because a button exists. Completion requires implementation, automated tests where practical, manual device verification, and updated documentation.
