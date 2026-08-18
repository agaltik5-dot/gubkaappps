# Walkthrough - Fix Unresolved Reference 'view_boundary_blur'

Fixed the build error `Unresolved reference 'view_boundary_blur'` in `ScheduleFragment.kt`.

## Changes

### [ScheduleFragment.kt](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/java/com/example/myapplication/ui/ScheduleFragment.kt)

- Removed the reference to `R.id.view_boundary_blur` which was missing from the layout file.
- Removed the unused `applyBlurEffect` function and its related imports (`RenderEffect`, `Shader`, `Build`).

This cleanup was necessary because the `view_boundary_blur` View had been removed from `fragment_schedule.xml` in a previous layout refactoring to achieve a "cleaner" look, but the corresponding code was left behind.

## Verification Results

### Automated Tests
- Successfully ran `./gradlew :app:compileDebugKotlin`.
- Build status: **SUCCESSFUL**
