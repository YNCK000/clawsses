# Scroll UX Analysis for Rokid AR Glasses Chat HUD

## Rokid Glasses Hardware Specs

| Spec | Value |
|------|-------|
| Display | Monochrome green Micro LED + diffractive waveguide |
| Resolution | 480×640 portrait (per eye: 480×398) |
| Field of View | 30° (Rokid Glasses model) |
| Brightness | Up to 1500 nits |
| Eye relief | 18mm |
| Weight | 49g |

**Key constraint:** The 30° FOV means the virtual display is like a small window in your vision. Content at the edges is easily clipped or missed.

## Human Ergonomics for AR Reading

### Comfort Zones (from Magic Leap HIG + academic research)

- **Optimal eye rotation:** ±15° from center of line of sight. Beyond this, users need to move their head.
- **Natural line of sight:** slopes 10-15° below horizontal. Center the FOV at or slightly below natural gaze.
- **Comfortable reading zone:** 30×30° area centered around the line of sight. Place most important content here.
- **Avoid:** Top-right and corners — frequent upward/diagonal head movements are uncomfortable and cause eye strain.

### Text Position Research (Rzayev et al., HoloLens study)

- **Center and bottom-center** positions yield the highest text comprehension
- **Top-right** (like Google Glass) results in significantly lower comprehension and higher workload
- **While walking:** scrolling yields higher comprehension than RSVP (word-by-word)
- **While sitting:** RSVP yields higher comprehension (focused reading mode)
- **Reading speed:** 144 WPM scrolling vs 113 WPM RSVP

### Key Takeaway for Our HUD

Our HUD is already center/bottom-aligned, which is correct. The issue isn't position — it's **scroll behavior**.

## Current Problems

1. **Auto-scroll during streaming yanks user down** — If reading a long message, the user gets pulled to the bottom every time a new chunk arrives. They lose their place.
2. **Int.MAX_VALUE pixel snapping** — The scroll jumps to absolute pixel-bottom, which is aggressive and disorienting.
3. **No reading pace control** — The system decides when to scroll, not the user.
4. **All-or-nothing scroll** — Either scrolls to the very end or doesn't scroll at all. No smooth incremental tracking.

## Recommended Scroll Behaviors

### Priority 1: Respectful Auto-Scroll (ALREADY IMPLEMENTED)

**Rule:** Only auto-scroll to new content if the user is already at the bottom of the chat. If they've scrolled up to read, leave them alone.

**Implementation:**
- Track `isScrolledToEnd` state
- During streaming (`chat_stream`), check `isScrolledToEnd` before auto-scrolling
- Use `isScrolledToEnd` as a LaunchedEffect key so re-evaluation happens naturally

**Status:** ✅ Done on `staging` branch

### Priority 2: Smooth Scroll Without Pixel Snapping (ALREADY IMPLEMENTED)

**Rule:** Don't use `Int.MAX_VALUE` offset to force pixel-perfect bottom alignment. Let Compose determine natural scroll position.

**Implementation:**
- Replace `scrollToItem(pos, Int.MAX_VALUE)` with `animateScrollToItem(pos)`
- Remove instant `scrollToItem` during streaming — always use smooth animation

**Status:** ✅ Done on `staging` branch

### Priority 3: Reading-Mode Pause Indicator

**Rule:** When user has scrolled up during streaming, show a subtle indicator that new content is available below.

**Suggested UX:**
- Small "▼ new content" indicator at bottom edge of viewport
- Tap to jump to latest (scroll to bottom)
- Fade in/out gently, don't flash

**Implementation notes:**
- Add `hasNewContentWhileReading: Boolean` to ChatHudState
- Set to `true` when streaming chunks arrive while `isScrolledToEnd == false`
- Reset to `false` when user scrolls to bottom
- Render a small overlay in ChatContentArea when active

**Status:** 📋 Not yet implemented

### Priority 4: Page-by-Page Scroll Option

**Rule:** For long messages on a 30° FOV display, continuous smooth scroll can be disorienting. Consider page-based scrolling for long content.

**Research basis:**
- Head-worn displays with narrow FOV benefit from chunked content delivery
- Line-by-line or page-by-page is preferred over continuous scroll while moving
- Users naturally want to "finish reading what's visible" before seeing more

**Suggested UX:**
- Option in More menu: "Page Scroll" vs "Smooth Scroll"
- In page mode: swipe forward/backward scrolls by viewport height, not by message
- Auto-scroll during streaming only advances when current viewport is "full"

**Status:** 📋 Not yet implemented

### Priority 5: RSVP Mode (Advanced / Future)

**Rule:** Rapid Serial Visual Presentation — show one word at a time in a fixed position.

**Research basis:**
- RSVP yields higher comprehension while sitting (Rzayev et al.)
- Eliminates all scrolling entirely
- Works well on narrow FOV displays (each word fits comfortably)
- Eye strain is reduced — no eye movement needed

**Suggested UX:**
- Long-press to enter RSVP mode
- Words appear one at a time at a fixed position
- Speed adjustable (swipe forward = faster, backward = slower)
- Tap to pause/resume

**Status:** 💡 Future consideration

### Priority 6: Content-Aware Line Breaking

**Rule:** AI responses often have soft line breaks from the model. Unwrap these so Compose can re-flow text to the actual display width.

**Implementation notes:**
- `unwrapContent()` function already exists in HudActivity.kt
- Called on `chat_stream_end` to finalize unwrapping
- During streaming, text shows with original line breaks; final unwrap happens at stream end

**Status:** ✅ Already implemented

## Display Size Interaction

The current `HudDisplaySize` presets (COMPACT/NORMAL/COMFORTABLE/LARGE) directly affect how much text fits on screen:

| Size | Font | Approx chars/line | Lines on 85% viewport |
|------|------|-------------------|-----------------------|
| COMPACT | 10sp | ~70 | ~20 |
| NORMAL | 12sp | ~60 | ~16 |
| COMFORTABLE | 14sp | ~50 | ~13 |
| LARGE | 16sp | ~40 | ~10 |

**Recommendation:** For long AI responses, NORMAL or COMFORTABLE strikes the best balance. LARGE should only be used for short interactions.

## Summary of Recommendations

| Priority | Feature | Status | Impact |
|----------|---------|--------|--------|
| 1 | Respectful auto-scroll | ✅ Done | High |
| 2 | Smooth scroll animation | ✅ Done | High |
| 3 | "New content" indicator | 📋 TODO | Medium |
| 4 | Page-by-page scroll option | 📋 TODO | Medium |
| 5 | RSVP mode | 💡 Future | Low |
| 6 | Content-aware line breaking | ✅ Done | Medium |

## References

- Zimmermann et al., "Human Interface Guidelines for Interaction Zones in AR" (Magic Leap, AHFE 2022)
- Rzayev et al., "Reading on Smart Glasses — The Effect of Text Position, Presentation Type, and Walking" (HoloLens study)
- Haynes et al., display comfort zone research (right-offset reading on HWDs)
- Song & Arora, comfortable reading angular range: -24.6° to +19.6° from center
- Rokid official specs: global.rokid.com/products/rokid-glasses
- VR Wiki — Reading in AR: vrwiki.cs.brown.edu
