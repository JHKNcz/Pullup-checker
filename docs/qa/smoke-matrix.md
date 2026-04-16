# Pullup Checker Smoke Matrix

Date: 2026-04-16

## Environment

- Host OS: Windows
- Build runtime observed: Java 8 (1.8.0_481, 32-bit)
- Expected for current toolchain baseline: Java 17+

## Build and Test Gates

- [x] IDE lint diagnostics on changed files: no linter errors reported.
- [ ] `./gradlew assembleDebug`: blocked in current environment (requires Java 11+/17+ compatible runtime for AGP baseline).
- [ ] `./gradlew testDebugUnitTest`: blocked for same Java runtime reason.

## Runtime Smoke Matrix

- [ ] Permission denied/accepted flow
- [ ] Camera unavailable/in-use flow
- [ ] Model missing/corrupted flow
- [ ] Normal detection loop with realistic motion

## Blockers and Next Actions

1. Install Java 17 (64-bit) and point Gradle to it (`JAVA_HOME` or `org.gradle.java.home`).
2. Re-run:
   - `./gradlew assembleDebug`
   - `./gradlew testDebugUnitTest`
3. Execute runtime smoke matrix on one emulator and one physical device if available.
