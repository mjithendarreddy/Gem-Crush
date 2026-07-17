# Development Report — Gem Crush (Match-3 Android Game)

## 1. Overview

The goal was to build a working Match-3 game for Android with the classic mechanics — a grid of
colored gems, swap-to-match, gravity and refill, a move limit with a Game Over state, a scoring
system, and a special gem created from a 4-in-a-row match.

I chose to build it as a **native Android app in Kotlin**, rendering on a
`Canvas` inside a custom `View` reason being :

- **Zero heavy dependencies.** The whole game is a handful of Kotlin files plus standard AndroidX
  libraries, so it builds fast and the APK is small (~5.6 MB).
- **Full control of the mechanics.** Match-3 logic (matching, cascading, special gems) is simple
  enough to implement directly and is easier to unit-test as plain code.
- **Easy to review.** A reviewer can read the logic top-to-bottom without learning an engine's
  scene/asset conventions.

## 2. Architecture

I split the app into three clear layers so that the rules could be tested independently of the UI:

- **`GameEngine.kt` — pure game logic, no Android imports.**
  Holds the 8×8 board (`Array<Array<Cell?>>`), the score, and the remaining moves. It exposes
  small, discrete operations: `trySwap`, `findMatchedCells`, `resolveMatches`, `applyGravity`,
  `hasPossibleMove`, and `reshuffle`. Because it has no Android dependency, it runs on the plain
  JVM and is covered by unit tests.

- **`GameView.kt` — rendering, input, and animation.**
  A custom `View` that draws the board on a `Canvas`, interprets swipe and tap gestures, and runs
  an **animation state machine** (`IDLE → SWAP → CLEAR → FALL → …`) that calls into the engine at
  each step. It drives frames with `postInvalidateOnAnimation()` and time-based interpolation.

- **`MainActivity.kt` — thin glue.**
  Inflates the layout, wires the HUD (score + moves) and the Game Over overlay to callbacks the
  `GameView` fires.

This separation was the single most useful design decision: every rule could be verified in a
fast JVM test, leaving only the visual/timing behavior to manual testing.

## 3. Key Mechanics and How They Work

**Matching & swaps.** A swap is committed to the board only if it produces at least one run of 3+.
`trySwap` performs the swap, checks for matches, and reverts if there are none — so illegal swaps
cost nothing. A move is deducted only on a successful, match-making swap, exactly as specified.

**Clear, gravity, refill, and cascades.** After a successful swap the view loops: find matches →
`resolveMatches` (clear + score) → `applyGravity` (collapse each column and spawn new gems at the
top) → check for *new* matches created by the fall. Chained clears (cascades) resolve
automatically and do **not** cost extra moves.

**Scoring.** Each cleared gem is worth 10 points multiplied by the cascade level, so longer chain
reactions are rewarded (10 for the first clear, 20 for the next cascade in the same move, etc.).

**Special Gem (4-match).** When a run is exactly four in a straight line, one of those cells is
kept and promoted to a **Special Gem** instead of being cleared. When that Special Gem is later
part of a match, it detonates its **entire row and column**, and the blast chains into any other
special gems it hits — producing satisfying combos.

**Game Over.** Moves start at 20. When they reach 0 and the board settles, the view fires
`onGameOver`, and the Activity shows the final score with a "Play Again" button.

## 4. Challenges and Solutions

**Challenge 1 — Animating a stepwise simulation.**
The engine mutates the board instantly (cells become `null`, gems teleport to their settled
positions), but the player needs to *see* gems slide, shrink, and fall. I solved this by having
the engine return the *data needed to animate a transition* rather than just the final state:
`applyGravity()` returns a list of `FallMove(finalRow, col, startRow)` describing where each gem
starts and ends, and before clearing I snapshot the colors of the cells about to disappear. The
view then interpolates between those states over a fixed duration. This kept the engine simple and
deterministic while still enabling smooth animation.

**Challenge 2 — A method-name collision with the Android framework.**
My animation callback was named `onAnimationEnd()`, which the compiler rejected because `View`
already declares a `protected onAnimationEnd()` — my `private` version "weakened access." The fix
was to rename it to `advanceState()`. A good reminder that custom `View` subclasses share a large
API surface with the framework.

**Challenge 3 — Special-gem creation vs. activation in the same step.**
A subtle ordering bug: a newly created special gem must **not** immediately detonate during the
same clear that created it. I handled this by computing the set of cells to keep as new specials
first, excluding them from the clear set, and only expanding row/column blasts for *pre-existing*
specials caught in the clear.

**Challenge 4 — Faulty test fixtures, not faulty logic.**
My first unit-test boards filled the background with an alternating two-color pattern, which
accidentally created full-row matches and made the "clear exactly 3" and "reject invalid swap"
tests fail. The engine was correct; the fixtures were not. I switched to a `(row + col) % colors`
background, which is guaranteed to have no two equal neighbors and therefore no accidental matches,
then planted the specific runs each test needed. All 8 tests then passed.

**Challenge 5 — Toolchain setup for a reproducible build.**
The build machine had Gradle 9, which is incompatible with the Android Gradle Plugin 8.5 line, and
no Android SDK. I pinned a **Gradle 8.7 wrapper** so the project builds identically regardless of
any globally installed Gradle, and installed the Android command-line tools plus
`platforms;android-34` / `build-tools;34.0.0` to compile and sign the APK.

## 5. Accessibility & Polish

Because color-only differentiation excludes color-blind players, each gem color is drawn with a
**distinct shape** (circle, rounded square, diamond, triangle, hexagon, star). The board also
supports both swipe and tap-to-select input, animates invalid swaps back so the player gets clear
feedback, and includes a pulse effect when a special gem is created.

## 6. Testing

- **Automated:** 8 JUnit tests in `GameEngineTest.kt` cover a match-free initial board, horizontal
  match detection, valid/invalid swap handling and move accounting, clearing + scoring, 4-match
  special-gem creation, and gravity fully refilling the board. Run with
  `./gradlew testDebugUnitTest`.
- **Manual:** Installed the debug APK and verified swapping, cascades, scoring, the move counter,
  the special-gem blast, and the Game Over / Play Again flow on an Android target.

## 7. Possible Future Improvements

- Sound effects and richer particle animations on clears.
- Persistent high score and a level/objective system.
- A signed release build and Play Store packaging (`assembleRelease` with a keystore).
- Additional special gems (e.g., a color-bomb from a 5-match).
