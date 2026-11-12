# Architecture

## Modules

```
app                 Application, MainActivity (setup + settings), Hilt root
feature/panel       Overlay service, panel UI, cursor overlay, PanelViewModel, output pipeline
feature/settings    Settings UI and SettingsStateHolder (shared by the panel and the app)
feature/setup       Permission checklist, SetupViewModel
core/model          Settings and setting specs, Language, KeyCode, Modifier, DeadKey (pure Kotlin)
core/keyboard       LayoutEngine, profiles, KeyboardReducer, OutputRouter, DeadKeyComposer (pure Kotlin)
core/trackpad       TrackpadGestureDetector, Glide (pure Kotlin)
core/pinyin         PinyinDictionary, PinyinComposer (pure Kotlin)
core/swipe          SwipeLexicon, SwipeDecoder for swipe typing (pure Kotlin)
core/data           SettingsRepository on DataStore (strict JSON)
core/display        Upper/lower display pairing as a Flow
core/injection      InputInjector: Shizuku user service and Accessibility service; PanelPowerController
core/designsystem   Theme, key caps, icons, setting rows, all UI strings (7 languages)
core/testing        Fakes and the main-dispatcher rule
```

The build logic lives in `build-logic/` as convention plugins, and the versions
in `gradle/libs.versions.toml`.

## State and data flow

The panel follows MVI. `PanelViewModel` holds a `KeyboardState` and turns each
touch into an event:

1. **Ownership.** `TouchRouter` decides at touch-down whether a finger belongs
   to the keyboard or the trackpad, and that never changes.
2. **Keyboard.** Keyboard touches go to `KeyboardReducer.reduce(state, event)`.
   It is a pure function that returns the new state plus `KeyboardCommand`s
   (emit output, start or cancel a timer, mouse button, reset, persist view).
   The ViewModel carries out those commands: timers are coroutines, and output
   goes to the pipeline.
3. **Trackpad.** Trackpad touches go to `TrackpadGestureDetector`. Its outputs
   (move, scroll, click, glide) update `CursorController` and are queued, in
   order, to the `InputInjector`.
4. **Output.** `OutputPipeline` takes the keyboard's output first in, first out.
   For each event, `OutputRouter` picks the route for the current language:
   - US: physical keys;
   - other languages: Unicode through the dead-key or Pinyin composer;
   - shortcuts: the US key position, and Caps Lock never adds Shift.

   Anything still queued after 750 ms is dropped rather than sent late.
5. **Swipe typing** (optional, English (US)). When a lone finger leaves an
   unmodified letter, the reducer records a `SwipeTrace` instead of cancelling
   the key. On release it emits `DecodeSwipe`. The ViewModel runs
   `SwipeDecoder` at once on the main thread (under a millisecond) and replies
   with `SwipeDecoded`. The reducer then emits the best word as
   `OutputEvent.Word` and keeps the rest as `Suggestions` for the candidate
   strip. Any other input clears them, so picking one only ever deletes the
   word it replaces.

Drawing and hit-testing use the same `LayoutEngine` rectangles in a logical
1240 × 1080 space. Compose only scales and draws them, so there are no
per-key click handlers.

## Windows

`PanelService` is a foreground service that opens two `TYPE_APPLICATION_OVERLAY`
windows with `FLAG_NOT_FOCUSABLE`:
- the touchable panel on the lower display;
- a pass-through cursor on the upper display, at alpha 0.8 so Android's
  untrusted-touch rule lets touches through.

The game therefore keeps input focus.

## Injection

`CompositeInjector` picks a backend for each capability:

| Capability | Shizuku | Accessibility |
|---|---|---|
| Keys and chords | `InputManager.injectInputEvent` | `InputMethod.sendKeyEvent` (focused editor only) |
| Unicode text | — | `InputMethod.commitText` |
| Pointer and scroll | `SOURCE_MOUSE` motion events on the upper display | taps at the cursor |
| Clicks as touches | `SOURCE_TOUCHSCREEN` events | — |
| Lower panel power | `SurfaceControl` / `DisplayControl.setDisplayPowerMode` | black overlay |
| Hardware toggle key | — | key-event filter |

The Shizuku side, `PrivilegedInputService`, runs as the shell user in its own
process and is reached over AIDL (`IPrivilegedInput`).
