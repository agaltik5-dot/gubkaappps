# Implementation Plan - Refine Header, Dropdown, and Fix News Loading

Refine the "Recent Groups" dropdown and header card, make the arrow bolder and black, and investigate/fix the news loading issue.

## User Review Required

> [!IMPORTANT]
> The dropdown width will now be locked to the header card's width.
> The header arrow will be replaced with a bolder version and tinted black.
> Investigation of the news loading issue is ongoing; it might be related to network changes or the target website's structure.

## Proposed Changes

### UI Layouts

#### [MODIFY] [dialog_recent_items.xml](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/res/layout/dialog_recent_items.xml)
- Set `android:layout_width="match_parent"` for the root and the `RecyclerView`.
- Remove all side padding to align perfectly with the card edges.

#### [MODIFY] [item_recent_access.xml](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/res/layout/item_recent_access.xml)
- Increase text size to `16sp`.
- Adjust vertical padding to `12dp`.

#### [MODIFY] [fragment_schedule.xml](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/res/layout/fragment_schedule.xml)
- Update `iv_header_arrow`:
    - Set `app:tint="@color/black"` (or `@color/ui_text_main`).
    - Use a bolder arrow drawable.

#### [NEW] [ic_arrow_drop_down_bold.xml](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/res/drawable/ic_arrow_drop_down_bold.xml)
- A custom vector drawable for a thicker arrow.

### Animation Resources

#### [MODIFY] [popup_enter.xml](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/res/anim/popup_enter.xml)
- Simplify to a snappier slide-down and fade-in (200ms).

### Logic

#### [MODIFY] [ScheduleFragment.kt](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/java/com/example/myapplication/ui/ScheduleFragment.kt)
- Update `showRecentItemsPopup`:
    - Set `PopupWindow` width to `anchor.width`.
    - Set `xOffset` to `0`.

### News Loading Fix

#### [INVESTIGATE] [NewsFragment.kt](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/java/com/example/myapplication/ui/NewsFragment.kt)
- Check Jsoup selectors and network logs.
- Verify if `https://www.gubkin.ru/news/` changed its HTML structure.
- Ensure SSL bypass is still working.

## Verification Plan

### Manual Verification
- Verify the dropdown matches the card width and has correct text size.
- Verify the arrow is bold and black.
- Verify news loading works again.
