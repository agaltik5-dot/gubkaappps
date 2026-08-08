# Финальное исправление загрузки изображений (Ручная регистрация Glide)

Цель: Гарантировать отображение картинок в новостях, обойдя все ограничения SSL и системные блокировки через прямой контроль сетевого уровня Glide.

## Proposed Changes

### [Networking / Helper]

#### [NEW] [NetworkUtils.kt](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/java/com/example/myapplication/data/NetworkUtils.kt)
*   Создание статического метода `getUnsafeOkHttpClient()`. Это будет единый "движок" для всего приложения, который игнорирует ошибки сертификатов и имитирует браузер.

### [UI / News]

#### [MODIFY] [NewsFragment.kt](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/java/com/example/myapplication/ui/NewsFragment.kt)
*   **Ручная настройка Glide**: В методе `onViewCreated` мы добавим код, который принудительно заменяет стандартный загрузчик Glide на наш `UnsafeOkHttpClient`. Это исключает любые конфликты с автоматической регистрацией модулей.
*   **Синхронизация Jsoup**: Перевод парсинга текста на использование того же OkHttp клиента для стабильности.

### [Cleanup]
*   Удаление неэффективного `UnsafeOkHttpGlideModule.kt` и его упоминания в Манифесте, чтобы не забивать проект лишним кодом.

## Почему это сработает?
Мы уходим от "магической" регистрации модулей Glide, которая зависит от версии библиотеки и настроек сборки, к прямому программному управлению. Теперь Glide физически не сможет использовать стандартный (строгий) клиент для загрузки фото с Губкина.

## Verification Plan

### Manual Verification
1.  Открыть вкладку "Новости".
2.  Убедиться, что картинки начали отображаться.
3.  Проверить Logcat: ошибки `SSLHandshakeException` должны исчезнуть.
