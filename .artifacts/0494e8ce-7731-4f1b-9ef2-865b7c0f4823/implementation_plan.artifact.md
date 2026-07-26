# Цветовая кодировка типов занятий

Цель: Сделать расписание более наглядным, выделив разные типы занятий (лекции, семинары, лабораторные и т.д.) разными цветами.

## Proposed Changes

### [UI/UX Concept]
*   **Цветовая палитра**: Каждому типу занятия будет присвоен свой уникальный цвет.
    *   **Лекция**: Синий (основной).
    *   **Семинар / Практика**: Зеленый.
    *   **Лабораторная**: Оранжевый / Красный.
    *   **Другое**: Серый.
*   **Элементы окрашивания**:
    *   Вертикальный индикатор (`v_indicator`) слева.
    *   Текст типа занятия (`tv_type`).
    *   Возможно, очень легкий фоновый тинт карточки (опционально, для лучшей читаемости).

### [Resources]

#### [MODIFY] [colors.xml](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/res/values/colors.xml)
Добавление новых цветов:
*   `type_lecture`: `#0061A4` (Синий)
*   `type_seminar`: `#00875A` (Зеленый)
*   `type_lab`: `#D97706` (Оранжевый)
*   `type_other`: `#70777C` (Серый)

### [Logic / UI]

#### [MODIFY] [LessonAdapter.kt](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/java/com/example/myapplication/LessonAdapter.kt)
*   Добавление функции `getColorForType(type: String): Int`.
*   В `onBindViewHolder` установка цвета для `v_indicator` и `tv_type` программно.
*   Использование `ColorStateList` или `setBackgroundColor` для динамической смены цветов.

## Verification Plan

### Manual Verification
*   Открыть день, где есть разные типы занятий (например, Лекция и Семинар).
*   Убедиться, что цвета соответствуют типам.
*   Проверить читаемость текста на разных цветовых фонах (если будет использоваться заливка).
*   Проверить отображение в темной теме (соответствие `values-night/colors.xml`).
