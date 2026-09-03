# Implementation Plan - Liquid Glass Blur Effect

The goal is to add a "frosted glass" (liquid glass) blur effect in two locations:
1.  On the boundary between the schedule cards and the calendar buttons in the schedule fragment.
2.  At the bottom of the screen in the gesture/navigation bar area.

## User Review Required

> [!IMPORTANT]
> Real-time "backdrop blur" (blurring what's behind a view) is natively supported in Android only starting from API 31 (Android 12) via `RenderEffect`. For older versions, we will use a semi-transparent gradient approximation that mimics the "glass" look.

> [!NOTE]
> For the bottom blur to work, I will modify how system insets are handled to allow the UI to flow under the navigation bar.

## Proposed Changes

### [Component] UI Resources & Drawables

#### [NEW] [bg_liquid_glass.xml](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/res/drawable/bg_liquid_glass.xml)
Create a drawable with a semi-transparent white background and a very thin light border to simulate the edge of the glass.

### [Component] Main Activity (Bottom Blur)

#### [MODIFY] [activity_main.xml](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/res/layout/activity_main.xml)
- Add a `View` named `view_bottom_blur` at the bottom of the layout.
- This view will serve as the container for the "liquid glass" effect in the gesture area.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/java/com/example/myapplication/MainActivity.kt)
- Update `OnApplyWindowInsetsListener` to:
    - Set the height of `view_bottom_blur` to match the navigation bar height.
    - Remove the bottom padding from the main container so the content (and our blur view) can go edge-to-edge.
    - Apply `RenderEffect` blur to `view_bottom_blur` if running on Android 12+.

### [Component] Schedule Fragment (Boundary Blur)

#### [MODIFY] [fragment_schedule.xml](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/res/layout/fragment_schedule.xml)
- Add a `View` named `view_boundary_blur` between `vp_calendar_header` and `vp_schedule`.
- Apply the `bg_liquid_glass` background.

#### [MODIFY] [ScheduleFragment.kt](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/java/com/example/myapplication/ui/ScheduleFragment.kt)
- Apply `RenderEffect` blur to `view_boundary_blur` if running on Android 12+.

## Verification Plan

### Manual Verification
- Deploy the app to a device/emulator (preferably Android 12+ for full effect).
- Check the boundary in the Schedule screen: there should be a semi-transparent blurred strip between the header and the list.
- Check the bottom of the screen: the area under the gesture handle should have a "glassy" blurred look.
- Ensure the floating navigation bar (`card_nav`) still looks correct and doesn't clash with the new blur.
