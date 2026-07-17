# Implementation Plan - Displaying Lessons

Connect the `RecyclerView` in `MainActivity` with real data using an Adapter to display the lessons (pairs).

## User Review Required

> [!NOTE]
> I will create a simple data model and an adapter. To keep things clean, I'll put them in the same package as `MainActivity`.

## Proposed Changes

### Logic & Data

#### [NEW] [Lesson.kt](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/java/com/example/myapplication/Lesson.kt)
Create a data class to represent a lesson:
- `subject`: String
- `startTime`: String
- `endTime`: String
- `type`: String (Lecture, Seminar, etc.)
- `details`: String (Room, Professor)

#### [NEW] [LessonAdapter.kt](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/java/com/example/myapplication/LessonAdapter.kt)
Create a `RecyclerView.Adapter` to:
- Inflate `item_lesson.xml`.
- Bind `Lesson` data to the views in the card.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/java/com/example/myapplication/MainActivity.kt)
- Initialize the `rv_schedule` with a `LinearLayoutManager`.
- Create a sample list of lessons.
- Set the adapter to the `RecyclerView`.
- (Optional) Update the list when a different day is selected.

## Verification Plan

### Manual Verification
- Build and run the app.
- Verify that a list of lessons appears on the main screen.
- Verify that the lesson details (time, subject, type) match the data provided.
