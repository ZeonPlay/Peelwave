# Peelwave — UI/UX Research Plan

Version: 0.1 · Status: hypotheses awaiting user validation

## Research question

How can a frequent sticker maker go from an image to a clean, saved sticker with fewer confusing steps than their current app, while retaining enough editing control for precise results?

## Competitor review

This is a desk-research starting point, not a substitute for moderated usability testing. Feature availability and app behavior can change by version.

### Sticker.ly
Official site: https://www.sticker.ly/
Google Play listing: https://play.google.com/store/apps/details?id=com.snowcorp.stickerly.android

What to inspect:
- Time and number of steps from opening the app to importing a photo.
- How editing and pack management compete for attention.
- Whether users can tell when export succeeded and how they get back to a draft.
- Ads, prompts, and network-dependent surfaces encountered during the core flow.
- How pack sharing/importing is explained.

### Wemoji
Google Play listing: https://play.google.com/store/apps/details?id=com.picmax.wemojiapp

What to inspect:
- Crop precision and ease of repeated cutouts.
- Text, emoji, and multi-image composition.
- Reuse of an existing cutout or saved element.
- Whether the editor communicates what will be lost or preserved on save.

### WhatsApp's built-in sticker tools
Official help entry point: https://faq.whatsapp.com/

What to inspect on supported versions:
- Fastest path for the simplest sticker task.
- How the UI explains the boundary between editing and sending.
- Which editing controls are familiar enough that Peelwave should not reinvent them.

## Initial hypotheses (to validate)

1. Frequent makers prefer opening a recent pack or starting directly with image selection over passing through a content feed.
2. Undo/redo should remain visible because experimentation is common and recovery needs to be immediate.
3. The canvas should remain the largest visual region; tools should not cover the actual sticker.
4. Text and outline controls need immediate visual feedback and should not require a separate preview screen.
5. The pack screen should distinguish drafts from export-ready stickers to avoid a false sense of readiness.
6. Background-removal quality and edge cleanup may be a stronger differentiator than the number of filters.
7. A visible save state and predictable back behavior matter more than ornamental animations.

These are hypotheses, not established findings.

## Small-study plan

Recruit 5–8 people who have used a sticker editor in the last month. Include at least two people who edit frequently and two who mostly use quick templates. Ask for consent before recording; avoid collecting private photos or chats.

### Interview prompts
- “Ceritakan terakhir kali kamu membuat sticker, dari awal sampai dikirim.”
- “Bagian mana yang paling sering bikin kamu mengulang langkah?”
- “Pernah kehilangan hasil edit atau gagal memasukkan pack? Apa yang terjadi?”
- “Fitur apa yang benar-benar dipakai setiap kali, dan mana yang jarang disentuh?”
- “Apa yang kamu harapkan tetap bisa diedit setelah aplikasi ditutup?”

Ask about a recent concrete event rather than asking only what features they would like.

### Prototype tasks
1. Create a pack named “Reaksi”.
2. Import a photo and crop it.
3. Add text with a readable outline.
4. Undo the last change and redo it.
5. Save the draft and return to the pack.
6. Identify why the pack is not yet ready for WhatsApp.
7. Resume editing after closing and reopening the app.

### Measures
- Task completion without help.
- Time to first image and time to saved draft.
- Mis-taps and navigation backtracks.
- Errors users can recover from without assistance.
- Confidence about whether work was saved.
- Qualitative comments about tool labels and control placement.

Do not declare the design successful from a single participant. Record patterns, separate observations from interpretation, and revise the prototype before the next round.

## Accessibility checks
- Aim for at least 48 × 48 dp interactive targets.
- Use semantic labels for icon-only controls.
- Check contrast in both light and dark preview backgrounds.
- Do not rely on color alone to indicate selected, disabled, or invalid states.
- Test TalkBack navigation and large font/display scaling.
- Support non-color cues and visible focus/selection states.

## Design direction

- Dark-first, quiet surface colors, one recognizable accent, generous canvas space.
- An editorial/creative-tool feel rather than generic dashboard cards.
- Explicit labels for important actions; icons may reinforce but must not replace unfamiliar labels.
- Motion should clarify selection, layer reordering, or save feedback, not delay task completion.
- Prefer direct manipulation on canvas but retain precise controls for users who need them.
