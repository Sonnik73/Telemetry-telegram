# Модуль :tdl — vendored TDL 14.0.0

Это исходники библиотеки [g000sha256/tdl](https://central.sonatype.com/artifact/dev.g000sha256/tdl-coroutines)
версии **14.0.0** (TDLib 1.8.66), лицензия Apache 2.0, © Georgii Ippolitov —
заголовки лицензии сохранены в каждом файле. Исходники взяты из официального
sources-jar, нативные библиотеки (`src/main/jniLibs`) — из официального AAR
без изменений.

## Зачем vendored и что изменено

Опубликованный артефакт падает с
`NoSuchElementException: Key background is missing in the map` при
десериализации служебного сообщения `messageChatSetBackground`, если Telegram
не прислал поле `background` (фон был удалён). Локальный фикс:

- `dto/MessageChatSetBackground.kt` — поле `background` сделано nullable;
- `TdlDeserializer.kt` — чтение поля через `getObjectNullable` вместо `getObject`.

Когда фикс появится в апстриме, модуль можно удалить и вернуть зависимость
`dev.g000sha256:tdl-coroutines` в `app/build.gradle.kts`.
