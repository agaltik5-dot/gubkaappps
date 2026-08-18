# Fix Unresolved Reference `view_boundary_blur`

The project fails to build because `ScheduleFragment.kt` refers to a view with ID `view_boundary_blur` which is missing from `fragment_schedule.xml`. Based on project history found in artifacts, this view was intentionally removed from the layout to simplify the UI, but the corresponding code in the Fragment was not updated.

## Proposed Changes

### [Component Name]

#### [MODIFY] [ScheduleFragment.kt](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/java/com/example/myapplication/ui/ScheduleFragment.kt)
- Remove the initialization and usage of `boundaryBlur` in `onViewCreated`.
- Remove the unused `applyBlurEffect` helper function.
- Remove unused imports related to the blur effect (`RenderEffect`, `Shader`).

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to ensure the project builds successfully.

### Manual Verification
- Deploy the app to a device/emulator to verify that the schedule screen opens correctly without crashes.
