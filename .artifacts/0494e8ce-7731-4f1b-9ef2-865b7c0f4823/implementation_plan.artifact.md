# Выбор даты и подсветка текущего дня

Добавление кнопки выбора даты с календарем и визуальное выделение сегодняшнего дня в ленте расписания.

## Proposed Changes

### [Layout / XML]

#### [MODIFY] [fragment_schedule.xml](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/res/layout/fragment_schedule.xml)
*   Переработка `layout_header` для размещения кнопки выбора даты слева от заголовка.
*   Добавление `ImageButton` с `id/btn_open_calendar`.

#### [NEW] [bg_day_today.xml](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/res/drawable/bg_day_today.xml)
*   Создание фона для сегодняшнего дня, когда он не выбран.
*   Стиль: белый/поверхностный фон со скруглением 16dp и обводкой цвета `ui_primary`.

### [Logic / Kotlin]

#### [MODIFY] [ScheduleFragment.kt](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/java/com/example/myapplication/ui/ScheduleFragment.kt)
1.  **Выбор даты**:
    *   Реализация клика по `btn_open_calendar`.
    *   Использование `MaterialDatePicker` для отображения красивого календаря.
    *   После выбора даты: вычисление смещения относительно `today` и переход `viewPager?.setCurrentItem(START_INDEX + offset, true)`.
2.  **Подсветка текущего дня**:
    *   Рефакторинг `updateUIForPosition`: теперь функция будет определять состояние каждого дня (выбран, сегодня, обычный) и применять соответствующий фон.
    *   Удаление `selectDayVisuals` в пользу новой унифицированной функции обновления стиля дня.

## Verification Plan

### Manual Verification
*   **Кнопка календаря**: Нажать кнопку слева от "Расписание", выбрать дату в календаре и убедиться, что расписание перелисталось на этот день.
*   **Подсветка "Сегодня"**:
    *   Найти сегодняшний день в ленте. Если он не выбран, он должен иметь синюю обводку.
    *   Выбрать сегодняшний день — он должен стать полностью синим (градиент).
    *   Выбрать другой день — сегодняшний должен снова вернуться к состоянию с обводкой.
*   **Темы**: Проверить видимость обводки в темной и светлой темах.
