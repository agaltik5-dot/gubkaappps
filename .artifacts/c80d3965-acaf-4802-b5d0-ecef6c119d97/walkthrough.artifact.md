# Expandable Header Widget Walkthrough

I have updated the **Schedule** tab to include a modern, expandable header widget that replaces the previous floating button.

## New UI Experience

### 1. Expandable Header Card
- **Unified Design**: The "Schedule" title and group name are now contained within a sleek, rounded `MaterialCardView`.
- **Visual Cues**: An arrow icon on the right indicates that the header is interactive and can be expanded.
- **Interactive Dropdown**: Clicking anywhere on the header card triggers a smooth "accordion" expansion downwards.

### 2. Quick-Access Dropdown
- **Recent Groups**: When expanded, a list of your recently viewed groups appears directly within the header.
- **Seamless Switching**: Tapping a group in the list instantly updates the schedule and collapses the menu back to its compact state.
- **Smooth Animations**: Used `TransitionManager` to ensure the expansion and rotation of the arrow look fluid and professional.

### 3. Decorative Background (Maintained)
- **Aesthetic Depth**: The large, semi-transparent "РАСПИСАНИЕ" and "ГРУППЫ" text remains in the background, providing context and style while the header card floats above it.

## Technical Improvements
- **Optimized Layout**: Removed the separate "Quick Access" widget to reduce UI clutter.
- **Robust Logic**: The header now handles its own state (expanded/collapsed) and refreshes the schedule data efficiently when a new group is selected.

## Verification Results
- **Build Status**: ✅ `SUCCESS`
- **UI Interaction**: Verified smooth expansion/collapse logic and correct group switching.

> [!TIP]
> Just tap on the header area (where it says "Расписание") to quickly see your recent groups and switch between them without leaving the screen!
