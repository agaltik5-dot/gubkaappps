# "Proper" Integration of Official Schedule Portal

We will move away from aggressive "keyword-based" hiding, which caused the empty screen, and instead implement a robust, structural cleanup of the WebView content. The goal is to isolate the data lists (Faculties/Teachers/Rooms) and make them appear native.

## Proposed Changes

### UI & Layout

#### [MODIFY] [fragment_search.xml](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/res/layout/fragment_search.xml)
- Wrap the `WebView` in a `SwipeRefreshLayout` to allow users to manually refresh the data "properly."

### Logic & Content Filtering

#### [MODIFY] [SearchFragment.kt](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/java/com/example/myapplication/ui/SearchFragment.kt)
- **Precise CSS Injection**: Target only known structural elements of the site:
    - `.navbar`, `.site-header`, `.page-header`, `header`, `footer`.
    - Breadcrumbs (`.breadcrumb`).
    - The site's internal navigation tabs (usually a list or button group at the top of the content).
- **Structural Cleanup Script**: Instead of just `display: none`, use a script that:
    1.  Waits for the SPA content to load.
    2.  Finds the main container (e.g., `.content-wrapper` or `.main`).
    3.  Sets the `padding-top` and `margin-top` of the container to 0 to remove gaps.
    4.  Specifically hides the "Расписание занятий" heading without hiding all `h1/h2` on the page.
- **Theme Synchronization**: Improve the background color application to prevent "white flashes" during transitions.

## Verification Plan

### Manual Verification
1.  Open the Search tab.
2.  Verify the official lists are visible and scrollable.
3.  Confirm the site's own header and duplicate tabs are hidden.
4.  Test the `SwipeRefreshLayout` functionality.
5.  Check tab switching responsiveness between "Faculties", "Teachers", and "Rooms".
