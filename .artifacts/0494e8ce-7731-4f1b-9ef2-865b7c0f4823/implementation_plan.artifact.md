# Исправление загрузки изображений в новостях

Цель: Устранить проблему, при которой картинки в разделе новостей не отображаются из-за недоверенного SSL-сертификата сайта `gubkin.ru` в библиотеке Glide.

## Решение

Библиотека Glide использует собственный механизм загрузки изображений, который по умолчанию блокирует соединения с недоверенными SSL-сертификатами. Мы настроим Glide на использование OkHttp с отключенной проверкой сертификатов для этого сайта.

## Proposed Changes

### [Dependencies]

#### [MODIFY] [build.gradle.kts](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/build.gradle.kts)
*   Добавление интеграции Glide с OkHttp: `implementation("com.github.bumptech.glide:okhttp3-integration:4.16.0")`.
*   Добавление OkHttp (если еще не добавлен).

### [Networking / Glide]

#### [NEW] [UnsafeOkHttpGlideModule.kt](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/java/com/example/myapplication/data/UnsafeOkHttpGlideModule.kt)
*   Создание класса, наследующего `AppGlideModule`.
*   Настройка OkHttp клиента, который игнорирует ошибки SSL (аналогично тому, что мы сделали для Jsoup).
*   Регистрация этого клиента в Glide для всех сетевых запросов изображений.

### [UI / News]

#### [MODIFY] [NewsFragment.kt](file:///C:/Users/agalt/AndroidStudioProjects/MyApplication5/app/src/main/java/com/example/myapplication/ui/NewsFragment.kt)
*   Небольшая корректировка ссылок на изображения (убедимся, что протокол всегда `https://` или `http://` в зависимости от доступности).

## Verification Plan

### Manual Verification
1.  Открыть вкладку "Новости".
2.  Проверить, что теперь рядом с заголовками и датами отображаются реальные фотографии с сайта университета.
3.  Убедиться, что при нажатии на новость картинка в браузере также открывается корректно.

> [!IMPORTANT]
> После добавления `UnsafeOkHttpGlideModule` проект может потребовать полной пересборки (Rebuild Project).

**Подтверждай, и я заставлю картинки грузиться!**
