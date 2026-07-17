# Gem Crush — a Match-3 Android Game

A simple **Match-3** game (in the spirit of Candy Crush / Bejeweled) written in **native Kotlin**
using a custom `Canvas`-rendered `View`. Swap adjacent gems to line up 3 or more of the same
color, clear them, and rack up points before you run out of moves.

<p align="center">
  <em>8×8 board · swap-to-match · cascading combos · move limit · special gems</em>
</p>

---

## Features

| Requirement | Implementation |
|---|---|
| **Grid & matching** | 8×8 grid of 6 colored gems. Swap two adjacent gems to make a line of 3+. |
| **Clear & refill** | Matched gems disappear; remaining gems fall down and new gems drop in from the top (animated). |
| **Cascades** | Chain reactions from falling gems are detected and cleared automatically, with a rising score multiplier. |
| **Move limit** | Starts at **20 moves**. Only a swap that *creates a match* costs a move. At 0 moves the **Game Over** screen appears. |
| **Score** | +10 points per gem cleared × the cascade level. Shown live in the HUD. |
| **Special Gem (4-match)** | Matching exactly **4 in a straight line** merges them into a **Special Gem**. When it is later cleared, it **blasts its entire row and column** (chains with other specials). |
| **Accessibility** | Each gem color also has a **distinct shape** (circle, square, diamond, triangle, hexagon, star) so the game is playable without relying on color alone. |

### Controls
- **Swipe** a gem in the direction of the neighbor you want to swap it with, **or**
- **Tap** a gem to select it, then **tap** an adjacent gem to swap.
- An invalid swap (one that makes no match) animates back and costs nothing.
- Tap **Play Again** on the Game Over screen to start a new game.

---

## Project Structure

```
Match3Game/
├── Match3Game.apk                 ← prebuilt, installable APK (main folder)
├── README.md
├── REPORT.md                      ← development process, challenges, solutions
├── settings.gradle.kts
├── build.gradle.kts               ← root build script
├── gradle.properties
├── gradlew / gradlew.bat          ← Gradle wrapper (pinned to Gradle 8.7)
├── gradle/wrapper/…
└── app/
    ├── build.gradle.kts           ← app module (AGP 8.5.2, minSdk 24, targetSdk 34)
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/match3/game/
        │   │   ├── GameEngine.kt   ← pure game logic (no Android deps)
        │   │   ├── GameView.kt     ← Canvas rendering + input + animation state machine
        │   │   └── MainActivity.kt ← HUD + Game Over overlay wiring
        │   └── res/                ← layout, colors, themes, launcher icons
        └── test/java/com/match3/game/
            └── GameEngineTest.kt   ← JVM unit tests for the game logic
```

The design deliberately separates **pure logic** (`GameEngine.kt`, fully unit-tested on the JVM)
from **rendering/input** (`GameView.kt`).

---

## How to Run

### Option A — Just install the APK (fastest)
A ready-to-install APK is in the repository root: **`Match3Game.apk`** (debug-signed).

**On a physical device**
1. Copy `Match3Game.apk` to your Android phone (minimum Android 7.0 / API 24).
2. Open it with a file manager and allow "install from unknown sources" if prompted.

**With adb (device or emulator)**
```bash
adb install -r Match3Game.apk
```

### Option B — Build from source

**Prerequisites**
- **JDK 17+** (JDK 21 works).
- **Android SDK** with `platforms;android-34` and `build-tools;34.0.0`.
  Either install **Android Studio** (recommended — it manages the SDK for you), or the
  command-line tools and run:
  ```bash
  sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
  ```
- Create a `local.properties` file in the project root pointing at your SDK:
  ```
  sdk.dir=/absolute/path/to/Android/sdk
  ```
  (Android Studio creates this automatically when you open the project.)

**Open in Android Studio**
1. `File ▸ Open…` and select the `Match3Game` folder.
2. Let Gradle sync, then press **Run ▶**.

**Build from the command line** (uses the bundled Gradle wrapper — no local Gradle needed)
```bash
# Debug APK  -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleDebug

# Run the unit tests
./gradlew testDebugUnitTest

# Install directly onto a connected device/emulator
./gradlew installDebug
```

---

## Tech Stack
- **Language:** Kotlin 1.9.24
- **Build:** Gradle 8.7 (wrapper) + Android Gradle Plugin 8.5.2
- **UI:** Custom `View` with `Canvas` 2D drawing (no game engine / no external game libraries)
- **Min / Target SDK:** 24 / 34
- **Tests:** JUnit 4 (pure-JVM tests of the game engine)

---

## Notes
- The shipped `Match3Game.apk` is a **debug** build so it can be installed and tested without a
  release keystore. To produce a signed release build, add a `signingConfig` and run
  `./gradlew assembleRelease`.
- No network, accounts, or special permissions are required — the app is fully offline.
