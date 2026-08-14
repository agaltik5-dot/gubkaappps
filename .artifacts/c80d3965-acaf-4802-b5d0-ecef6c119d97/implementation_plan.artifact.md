# Implementation Plan: Floating Dropdown Menu

This plan fixes the expandable header logic to ensure the dropdown menu opens correctly, is positioned "on top of everything" (z-order), and has a properly aligned arrow.

## User Review Required

> [!IMPORTANT]
> The dropdown list will be changed from an inline `RecyclerView` (which pushes content down) to a `ListPopupWindow`. This ensures it appears **on top** of the schedule and month navigation without moving them.
>
> [!NOTE]
> The arrow icon will be moved to the absolute right side of the header card.

## Proposed Changes

### UI & Layout

#### [MODIFY] [fragment_schedule.xml](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/res/layout/fragment_schedule.xml)
- Update the `RelativeLayout` inside `card_header_expandable`:
    - Set `layout_width="match_parent"` for the header card container.
    - Position `iv_header_arrow` with `layout_alignParentEnd="true"`.
    - **Remove** the inline `rv_recent_dropdown` to avoid pushing layout.

### Logic & Fragment

#### [MODIFY] [ScheduleFragment.kt](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/java/com/example/myapplication/ui/ScheduleFragment.kt)
- Replace the visibility toggle logic with `ListPopupWindow`.
- Configure `ListPopupWindow`:
    - Anchor it to `card_header_expandable`.
    - Use `RecentItemsAdapter` (or a custom adapter if required by the native popup).
    - Set the width to match the anchor's width.
    - Add a `dismissListener` to reset the arrow rotation.
- Update `setupQuickAccess` to initialize the popup.

## Verification Plan

### Manual Verification
1.  Open the Schedule tab.
2.  Verify the arrow is now at the right edge of the header.
3.  Click the header.
4.  Verify a floating list appears **over** the calendar and schedule.
5.  Select an item and verify the schedule updates and the popup closes.
