# FocusField — AI-менеджер внимания

Android-приложение (Kotlin, MVVM), помогающее концентрироваться: отслеживает уровень внимания по биометрии (камера + ML Kit, Wear OS) и ограничивает доступ к отвлекающим приложениям и сайтам, когда концентрация падает. Функция «помехи» позволяет временно разрешить доступ.

## Соответствие техническому заданию

| Пункт ТЗ | Реализация |
|---|---|
| Аутентификация (Email, Google, ВКонтакте, восстановление пароля) | `data/repo/AuthRepository`: Firebase Authentication; при отсутствии `google-services.json` — прозрачный локальный режим (пароли хешируются PBKDF-подобной схемой, данные в зашифрованных SharedPreferences). Google/ВКонтакте — точки входа с понятной ошибкой, пока не выполнена настройка (см. README) |
| Сбор биометрии (камера: моргание, лицо, глаза; Wear OS: пульс) | `biometrics/FaceAnalyzer` (CameraX + ML Kit Face Detection), `biometrics/BlinkDetector`; `service/wear/WearListenerService` принимает пульс/шаги с часов по пути `/focusfield/health` |
| Анализ уровня концентрации (ML, SQLite + облако) | `biometrics/ConcentrationCalculator` — скоринг по открытости глаз, позе головы, улыбке; окно ~10 с; сессии и сэмплы хранятся в Room (SQLite), синхронизация в облако — REST/Firebase (см. Roadmap) |
| Блокировка приложений и сайтов | `service/block/BlockingAccessibilityService` (определяет текущее приложение, читает адресную строку Chrome/Firefox/Яндекс.Браузера) + оверлей `BlockOverlayActivity` |
| Функция «помехи» | Кнопка «Разрешить на N мин» в оверлее (`BlockingStateStore.grantOverride`), N настраивается; показы оверлея считаются прерываниями |
| Задачи и напоминания, интеграция с календарём | `ui/tasks` (Room + RecyclerView), напоминания через AlarmManager + уведомления; дата задачи ставится через MaterialDatePicker |
| Статистика: день/неделя/месяц, гистограммы и круговые диаграммы | `ui/stats` + `data/model/StatsAggregator` (чистая логика, покрыта тестами); MPAndroidChart |
| Material Design, настройки внешнего вида | Material 3, DayNight, выбор темы в настройках |
| Настройки (пороги, расписание, синхронизация) | `ui/settings` (PreferenceFragmentCompat): порог концентрации, длительность «помехи», расписание по времени суток, REST-URL |
| Аккаунт (аватар, ФИО, email, выход) | `ui/account` — отдельная вкладка нижнего меню (раздел вынесен из настроек); выход с подтверждением |
| MVVM, ViewModel/LiveData/Room | Везде; вручную собранный DI-контейнер без внешних фреймворков |
| CameraX/ML Kit, Room, Firebase Auth, MPAndroidChart | Да (см. таблицу версий в `gradle/libs.versions.toml`) |
| REST API | Retrofit (`data/remote/SyncApi`): отправка сессий, загрузка задач из внешнего API |
| Шифрование данных, защита доступа | Зашифрованные SharedPreferences (Android Keystore, AES-256), хеширование паролей, `allowBackup=false` |
| Юнит-тесты + Espresso | 54 юнит-теста (концентрация, моргание, домены, хеши, статистика, репозиторий задач, расписание, ФИО профиля) + 14 инструментальных (smoke, сохранность аккаунтов, флоу регистрации, аватар/Photo Picker, пропуск профиля, вкладка «Аккаунт») |

## Архитектура и поток данных

```
CameraX (ImageAnalysis) ──► FaceAnalyzer (ML Kit) ──► FocusViewModel
                                                        │  score 0..100
                                                        ▼
                                              BlockingStateStore  (StateFlow + зашифрованные prefs)
                                                        │
     BlockingAccessibilityService ◄──────────────────────┘
        │  «пользователь открыл заблокированное приложение/сайт,
        │   концентрация ниже порога, „помехи“ нет»
        ▼
     BlockOverlayActivity (оверлей) ── «Помеха»: разрешить на N минут
```

Структура пакетов:

```
ru.seleznev.focusfield
├─ FocusFieldApp.kt          — Application + DI-контейнер (AppContainer)
├─ MainActivity.kt           — навигация, гейт по аутентификации, тема
├─ biometrics/               — FaceAnalyzer, BlinkDetector, ConcentrationCalculator, FaceFrame
├─ data/
│  ├─ db/                    — Room: entities, dao, FocusFieldDatabase (схема экспортируется в app/schemas)
│  ├─ model/                 — доменные модели + StatsAggregator
│  ├─ repo/                  — Auth, Tasks, Focus, Block, Settings, RemoteSync
│  ├─ remote/                — Retrofit SyncApi + DTO
│  ├─ prefs/                 — SecurePrefs (EncryptedSharedPreferences)
│  ├─ crypto/                — PasswordHasher
│  └─ state/                 — BlockingStateStore (общее состояние блокировки)
├─ service/
│  ├─ block/                 — BlockingAccessibilityService, BlockOverlayActivity, ScheduleWindow
│  ├─ reminder/              — ReminderScheduler, ReminderReceiver, BootReceiver, NotificationHelper
│  ├─ sync/                  — SyncWorker (WorkManager)
│  └─ wear/                  — WearListenerService, WearHealthStore
└─ ui/                       — auth, focus, tasks, stats, settings, blocked (+ adapters, viewmodels)
```

## Быстрый старт

Требования: JDK 17+ (например, JBR из Android Studio), Android SDK 37.

```bat
:: Windows (JAVA_HOME указать на JDK 17+)
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat :app:assembleDebug        # сборка APK
gradlew.bat :app:testDebugUnitTest    # юнит-тесты
gradlew.bat :app:connectedDebugAndroidTest  # Espresso (нужно устройство/эмулятор; ⚠️ стирает данные приложения — см. раздел «Тесты»)
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Настройка Firebase (опционально)

Без Firebase всё работает локально. Для облачной аутентификации:

1. В [Firebase Console](https://console.firebase.google.com) создайте проект с пакетом `ru.seleznev.focusfield`.
2. Скачайте `google-services.json` → положите в `app/`.
3. В **корневой** `build.gradle.kts` и `app/build.gradle.kts` подключите плагин `com.google.gms.google-services` (версия 4.4.x).
4. В консоли включите провайдеров: **Email/Password**, при желании **Google** (для Google нужен OAuth-клиент веб-приложения).

Пока `google-services.json` отсутствует, экран входа показывает пометку о локальном режиме; кнопки социального входа возвращают сообщение о требуемой настройке.

### Вход через ВКонтакте (VK ID)

Кнопка «Войти через ВКонтакте» пока возвращает сообщение о требуемой настройке.
Для включения реального входа:

1. Зарегистрируйте приложение в [vk.com/editapp](https://vk.com/editapp?act=app_create) (платформа Web), получите `client_id`;
2. подключите [VKID Android SDK](https://dev.vk.com/ru/mini-apps/sdk/android) к проекту;
3. в `AuthRepository.signInWithVk()` замените заглушку на поток `VKID.authorize(...)` и обмен кода на токен.

## Разрешения и зачем они

| Разрешение | Назначение |
|---|---|
| `CAMERA` | Анализ моргания/лица для оценки концентрации (только во время сессии, кадры не сохраняются) |
| AccessibilityService | Определение текущего приложения/адреса для блокировки (включается пользователем в системных настройках) |
| `SYSTEM_ALERT_WINDOW` | Оверлей «Доступ заблокирован» поверх других приложений |
| `QUERY_ALL_PACKAGES` | Список установленных приложений для выбора в списке блокировки. **Внимание:** для публикации в Google Play требуется обоснование |
| `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED` | Напоминания по задачам |
| `INTERNET` | Firebase / REST-синхронизация |

## Ручная проверка (тест-план)

1. Запуск → экран входа → «Нет аккаунта? Создать» → регистрация (локальный режим).
2. Сразу после регистрации открывается **«Редактирование профиля»**: email аккаунта показывается (не редактируется), ФИО предзаполнено; кнопки «Сохранить и продолжить» и «Пропустить» закреплены внизу экрана. Сохранение («Сохранить и продолжить») → переход в приложение; «Пропустить» — сразу на экран «Фокус» без редактирования. Кнопка «назад» на этом экране тоже ведёт в приложение, а не на экран входа.
3. Аватар: круг перед email → нажать → открывается системный **Photo Picker** (разрешение не нужно) → выбрать фото → фото обрезается в круг и сохраняется для аккаунта (повторный вход показывает его снова).
4. Вкладка «Фокус» → разрешить камеру → «Начать сессию» → индикатор концентрации оживает; уберите лицо — score падает.
5. Настройки → «Сервис доступности» → включить FocusField; «Оверлей» → разрешить.
6. «Список блокировки» → добавить YouTube/VK и сайт `vk.com`.
7. Откройте заблокированное приложение при активной сессии → оверлей; «Разрешить на 5 мин» — доступ открывается, счётчик «Помех» растёт.
8. «Статистика» → гистограмма/диаграмма по сессиям.
9. Вкладка «Аккаунт» (нижнее меню) → карточка аккаунта (аватар, ФИО, email) → «Выйти из аккаунта» → подтверждение → экран входа: email последнего аккаунта подставлен автоматически (список аккаунтов не отображается), повторный вход работает. В настройках раздел «Аккаунт» отсутствует.

### Камера на эмуляторе

У эмулятора нет настоящей камеры: фронтальная по умолчанию показывает встроенную
3D-сцену («виртуальную комнату»), поэтому ML Kit не находит лицо и концентрация
остаётся 0%. Чтобы приложение получало кадры с веб-камеры ПК:

1. В `~/.android/avd/<AVD>.avd/config.ini` установите `hw.camera.front=webcam0`
   (по умолчанию `emulated`);
2. выполните **холодный запуск** эмулятора (например, `emulator -avd <AVD> -no-snapshot`),
   конфиг камер читается только при старте;
3. первые секунды превью может быть чёрным — веб-камере нужно время на захват
   (в логе эмулятора это сопровождается предупреждениями `convert_to_i420`);
4. зелёный индикатор камеры в статус-баре означает, что эмулятор захватывает
   веб-камеру ПК.

На реальном устройстве камера работает без дополнительных настроек.

## Тесты

- **Юнит-тесты** (`app/src/test`): ConcentrationCalculator, BlinkDetector, DomainMatcher, PasswordHasher, StatsAggregator, ScheduleWindow, TaskRepository (fake DAO), RegistrationValidator, ProfileForm — 54 кейса, все зелёные.
- **Инструментальные** (`app/src/androidTest`, 14 кейсов): smoke-тест MainActivity (Espresso), `AuthPersistenceTest` (сохранность аккаунта после выхода, реальные EncryptedSharedPreferences), `AvatarStoreTest` (сохранение фото, даунсемплинг ≤512 px, имя файла по аккаунту), `RegistrationFlowTest` (регистрация → экран профиля → аватар открывает Photo Picker → сохранение/пропуск → приложение; вкладка «Аккаунт» с выходом; кириллица вводится через Espresso `replaceText`).

⚠️ `gradlew connectedDebugAndroidTest` переустанавливает приложение на устройстве **вместе с данными**. Инструментальные тесты запускайте вручную:

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -e class ru.seleznev.focusfield.RegistrationFlowTest -w ru.seleznev.focusfield.test/androidx.test.runner.AndroidJUnitRunner
adb uninstall ru.seleznev.focusfield.test
```

## Отклонения от ТЗ (осознанные)

- **minSdk 24 вместо API 21.** Весь актуальный стек AndroidX (navigation 2.10, activity 1.13, fragment 1.9) требует API 24; для API 21 пришлось бы даунгрейдить библиотеки на версии 2022–2023 гг.
- **MPAndroidChart v3.2.0** — тег v3.1.2 недоступен на JitPack.
- **Jetpack Security (EncryptedSharedPreferences) deprecated upstream** — API остаётся рабочим; миграция на DataStore + Keystore в roadmap.
- **Google/ВКонтакте вход** — каркас готов: Google нужен Firebase-проект, ВКонтакте — зарегистрированное приложение VK ID (инструкции выше).
- **Wear OS** — реализована приёмная сторона (приложение-компаньон для часов — отдельный проект).

## Roadmap

- Foreground-сервис камера-мониторинга для работы фоном + ML-модель внимания (индивидуальная калибровка).
- Шифрование БД (SQLCipher) вместо открытого Room.
- Синхронизация сессий в Firestore; облачный бэкап.
- Блокировка по геолокации («дома/на работе»).
- Экран-компаньон Wear OS и учёт пульса в скоринге концентрации.
