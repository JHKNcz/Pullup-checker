# Pullup Checker Modernization Design

Date: 2026-04-16
Status: Draft for user review
Scope: Full modernization path (Approach C), with controlled risk gates.

## Goals

- Modernize build and runtime stack to current stable tooling.
- Improve maintainability by decomposing `MainActivity` responsibilities.
- Preserve core pull-up detection behavior while creating safer extension points.
- Add test and verification coverage to reduce regressions during future updates.

## Non-goals

- No multi-module split in this pass.
- No full algorithm redesign of rep/form heuristics in this pass.
- No backend/cloud sync features.

## Current State Summary

- Build currently fails due to Java runtime mismatch:
  - Gradle/AGP stack requires newer JVM than current environment.
- Wrapper points to a milestone Gradle distribution, increasing instability risk.
- App logic is concentrated in `MainActivity` (camera setup, MediaPipe orchestration, UI formatting).
- Analyzer logic exists but has minimal formal test coverage.

## Context7-Validated Constraints

- Gradle 9 requires JVM 17+ to run.
- Stable wrapper usage is preferred for reproducible builds over milestone versions.
- For Android dependency versions (AGP/Kotlin/CameraX/MediaPipe), exact targets should be validated during implementation with compile + smoke checks instead of hardcoded speculative numbers in design.

## Target Architecture

Keep a single app module, but reorganize by responsibility:

- `camera/`
  - CameraX setup, lifecycle binding, analysis use-case config.
- `ml/`
  - Pose landmarker lifecycle and frame inference orchestration.
- `analysis/`
  - Pose correction, phase/rep/form/power logic (current `PoseAnalyzer` domain).
- `ui/`
  - Overlay rendering and metrics formatting utilities.
- `MainActivity`
  - Orchestrates permissions + wiring only; does not own detailed camera/ML/analysis logic.

## Data Flow

1. Camera frame acquired (CameraX analyzer callback).
2. Frame adapted and submitted to pose detector.
3. Detection result mapped to analyzer input.
4. Analyzer outputs `AnalysisResult` state model.
5. UI renderer applies state to text metrics + overlay.

## Modernization Strategy (Phased)

### Phase 1: Build Baseline

- Move project runtime/toolchain to JVM 17.
- Replace milestone wrapper with stable Gradle wrapper.
- Align AGP/Kotlin/JVM target to a tested compatibility set.
- Success gate:
  - `clean assembleDebug` passes.

### Phase 2: Dependency Modernization

- Upgrade dependency groups incrementally:
  - AndroidX core/UI stack
  - CameraX family (aligned set)
  - MediaPipe tasks-vision
- After each group:
  - compile check
  - run app smoke checks for camera + detection path
- Fallback rule:
  - On regression, rollback that group to last known stable and continue.

### Phase 3: Refactor for Maintainability

- Extract camera orchestration from `MainActivity`.
- Extract pose landmarker lifecycle and error handling to `ml/`.
- Extract metrics formatting to a small UI helper.
- Keep public behavior equivalent unless explicitly approved change.

### Phase 4: Tests and Verification

- Add unit tests for analyzer phase transitions and rep count edge cases.
- Add sanity tests for power/velocity ranges and reset behavior.
- Add runtime smoke checklist:
  - permission denied path
  - model init failure path
  - camera bind failure path
  - normal detection loop path
- Final success gate:
  - unit tests green
  - `clean assembleDebug` green
  - smoke checklist completed.

## Error Handling Design

- Introduce explicit detector states:
  - initializing, ready, failed.
- Ensure camera and detector cleanup is idempotent in lifecycle callbacks.
- Surface user-visible status messages for critical failures (model load/camera bind).

## Risks and Mitigations

- Risk: Breaking changes across modernized dependencies.
  - Mitigation: Grouped incremental upgrades with verification after each group.
- Risk: Behavior drift from refactor.
  - Mitigation: Preserve analyzer contract + add transition tests before deeper tuning.
- Risk: Device-specific camera behavior.
  - Mitigation: smoke checklist on at least one physical device and one emulator if available.

## Deliverables

- Updated stable build toolchain and dependency matrix.
- Refactored app structure with focused classes.
- Added analyzer tests and documented smoke checklist.
- Verification evidence in command outputs and checklist notes.

## Open Decisions (for implementation plan)

- Whether to allow minor tuning changes (smoothing thresholds/timing) during refactor, or strictly preserve outputs.
- Whether to raise `minSdk` as part of modernization, or keep current device coverage.
