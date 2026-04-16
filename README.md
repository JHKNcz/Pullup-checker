AI-powered Pull-up Checker for Android using CameraX + MediaPipe - real-time rep counting, form feedback, and power metrics in one lightweight app.

## Features
- Real-time pull-up/chin-up detection from camera feed
- Live rep counting with phase tracking (setup, hang, concentric, peak, eccentric)
- Instant form feedback (symmetry, shrugging warnings, etc.)
- Estimated power output (watts and horsepower)
- Per-rep quality scoring and session history
- On-device processing (no backend required)

## Tech Stack
- **Android (Kotlin)**
- **CameraX** for camera pipeline
- **MediaPipe Tasks Vision** for pose landmarks
- **ViewBinding** + custom overlay rendering
- **Gradle + AGP + JDK 17**

## Project Structure
```text
app/src/main/java/com/example/pullupchecker/
  camera/         Camera pipeline and lifecycle binding
  ml/             Pose landmarker engine
  analysis/       Threshold profiles, quality gating, rep summaries
  ui/             UI state and metrics formatting
  diagnostics/    Structured internal logging helpers
  storage/        Local session summary persistence
  MainActivity.kt Coordinator/orchestrator
```

## Requirements
- Android Studio (latest stable recommended)
- **JDK 17+** (full JDK, not JRE)
- Android SDK with platform 34 installed
- Android device/emulator with camera support

## Quick Start
1. Clone the repo:
   ```bash
   git clone https://github.com/JHKNcz/Pullup-checker.git
   cd Pullup-checker
   ```
2. Ensure Gradle uses JDK 17+.
3. Build debug APK:
   ```bash
   ./gradlew :app:assembleDebug
   ```
4. Run checks:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ./gradlew :app:lintDebug
   ```

## Notes
- The app expects `pose_landmarker_full.task` in `app/src/main/assets/`.
- First launch requires camera permission.
- For stable builds, keep Gradle/AGP/Kotlin versions aligned and use a full JDK with `jlink`.

## Roadmap
- Better scoring calibration profiles
- More robust multi-angle detection
- Exportable session analytics
- UI polish for coaching mode

## License

No license file yet. Add one (for example MIT) if you plan public reuse.
