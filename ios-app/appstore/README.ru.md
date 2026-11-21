# OpenFlux iOS — подготовка к App Store

Чек-лист публикации. Что уже сделано в коде, что остаётся сделать в аккаунте
разработчика и App Store Connect и где риски ревью. Тексты для стора лежат
рядом: [metadata.md](metadata.md) (описание и заметки для ревьюера) и
[PRIVACY.md](PRIVACY.md) (политика конфиденциальности).

## 1. Сделано в коде (ветка `appstore-prep`)

| Что | Где |
|---|---|
| Команда `H6KHCZK2RG`, bundle id `com.pnonetwork.openflux` во всех таргетах: приложение, `.tunnel`, `.controls`, `.uitests`, keychain group, kind контрола Пункта управления | `project.yml`, entitlements, `Secrets.swift`, `Keychain.kt`, `VPNController.swift`, `IosConnectionService.kt`, `VPNControl.swift`, `OpenFluxApp.swift`, `DeviceTests.swift`, `ExportOptions.plist`, `build_ios_app.sh` |
| `ITSAppUsesNonExemptEncryption = true`: ядро само шифрует трафик (AES-GCM, ключ через scrypt), исключение «только системный TLS» к нему не подходит | `project.yml`, `OpenFlux/Info.plist` |
| Privacy Manifest приложения: file timestamp `C617.1` и system boot time `35F9.1` (Go и Kotlin/Native: `stat`/`fstat`, `mach_absolute_time`), UserDefaults `CA92.1`; трекинга нет | `OpenFlux/PrivacyInfo.xcprivacy` |
| Privacy Manifest туннеля: file timestamp и boot time (Go-ядро) | `OpenFluxTunnel/PrivacyInfo.xcprivacy` |
| Убраны крипто-адреса для донатов из классического интерфейса (правило 3.1.1) | `OpenFlux/InfoView.swift` |
| Проверка выходного IP идёт по HTTPS | `TunnelController.swift` |

Оставлено сознательно:
- `NSAllowsArbitraryLoads = true`. Приложение открывает HTTP-адреса, которые
  вводит сам пользователь: страницы скрипта установки узла на loopback, адреса
  профилей и подписок на его серверах. Причина записана в заметках для ревьюера.
- Покупки на iOS уже выключены: `purchasesInApp = false` в `iosUi/.../Platform.kt`.
  Тарифов, кнопки «Оплатить» и ссылок на оплату в iOS-сборке нет.

## 2. Аккаунт разработчика и App Store Connect

1. **Проверить тип аккаунта `H6KHCZK2RG`**: нужен Organization (правило 5.4: VPN
   публикует только организация). Developer → Membership → Entity type.
2. **Identifiers** (developer.apple.com → Certificates, IDs & Profiles):
   `com.pnonetwork.openflux`, `.tunnel`, `.controls`. Для приложения и `.tunnel`
   включить Network Extensions (Packet Tunnel). При automatic signing Xcode
   создаст их сам, но capability нужно проверить.
3. **Ключ App Store Connect API** для `build_ios_app.sh`:
   `~/.appstoreconnect/private_keys/AuthKey_<KEY_ID>.p8` и
   `~/.appstoreconnect/issuer_id`. Старый ключ команды `8GQH8GQ252` не подойдёт.
4. **Запись приложения**: My Apps → + → New App, bundle id `com.pnonetwork.openflux`,
   основной язык русский, SKU любой (например `openflux-ios`). Имя «OpenFlux»
   может быть занято: запасные варианты в `metadata.md`.
5. **Agreements, Tax, and Banking**: Free Apps Agreement подписан (для
   бесплатного приложения достаточно его).
6. **Политика конфиденциальности**: разместить [PRIVACY.md](PRIVACY.md) по
   публичному HTTPS-адресу (например `https://openflux.dev/privacy`) и вписать
   в App Information → Privacy Policy URL.
7. **App Privacy** (анкета «nutrition label») — ответы в разделе 4.
8. **Export Compliance** — ответы в разделе 5.
9. **Страны** (Pricing and Availability): решение ваше. Apple снимает VPN-приложения
   по требованию регуляторов. Уберите страны, где VPN без лицензии запрещён
   (Китай и другие). Если оставить Россию, будьте готовы к удалению из
   российского стора по требованию РКН.
10. **Скриншоты**: iPhone 6.9″ (1320×2868 или 1290×2796), минимум 3, до 10 штук.
    Остальные размеры App Store Connect берёт из 6.9″. iPad не нужен
    (`TARGETED_DEVICE_FAMILY = 1`).
11. **Возрастной рейтинг**: на все вопросы «None», а на «Unrestricted Web Access»
    ответ «Yes», потому что во встроенном браузере (вход в Яндекс и Mail.ru, капча)
    можно открыть любой сайт. Итог: 17+.
12. **Сборка и загрузка**: поднять `CURRENT_PROJECT_VERSION` в `project.yml`,
    затем `./build_ios_app.sh` (для Kotlin UI нужен JDK 17: `JAVA_HOME`),
    загрузка в TestFlight, потом Submit for Review.

## 3. Что ещё может вызвать отказ (код команды, `shared/`, бэкенд)

- **3.1.3(b), подписки, купленные вне приложения.** Пользователь входит в
  аккаунт OpenFlux и получает подключения по подписке, купленной в боте или на
  сайте. Apple разрешает доступ к купленному на других платформах, если то же
  самое можно купить и через In-App Purchase. Варианты: добавить IAP для тех же
  тарифов или подать iOS-версию как клиент к собственным серверам пользователя
  и скрыть вход в аккаунт OpenFlux. Это самый вероятный повод для отказа.
- **5.1.1(v), удаление аккаунта.** Если аккаунт можно создать из приложения
  (вход по почте создаёт новый аккаунт), удалить его тоже должно быть можно
  из приложения. Для бот-аккаунтов (`api.openflux.dev`) такого пути сейчас нет:
  `deleteAccount` есть только у старого `SubscriptionApi`, и на iOS это заглушка.
  Нужен эндпоинт удаления и кнопка в «Аккаунте», или хотя бы ссылка на страницу
  удаления в один шаг.
- ~~**5.1.1 / 5.1.2, вход в Яндекс и Mail.ru и «Передать вход ноде».**~~ Сделано
  ([OpenFluxClientShared#3](https://github.com/OpenFluxTeam/OpenFluxClientShared/pull/3)):
  перед первой передачей входа ноде, отдельно для каждого сервиса, показывается
  вопрос, что получит нода и как отозвать доступ. Без ответа «да» ничего не
  отправляется. Об этом же говорят политика конфиденциальности и анкета App Privacy.
- **5.2.2 / 5.2.3, сторонние сервисы как транспорт.** Яндекс Документы, Avito,
  MAX и cups используются как канал передачи данных. Ревьюер может спросить о
  разрешении этих сервисов. В описании для стора упор на «защищённый туннель к
  вашему серверу», без слов про «обход блокировок».
- **2.1, полнота работы.** Ревьюеру нужен рабочий профиль. Положите в заметки
  ссылку `openflux://`, которая подключается без входа в чужие аккаунты, и держите
  этот узел включённым на время ревью.

## 4. Анкета App Privacy (App Store Connect → App Privacy)

Предполагается, что приложение отправляет данные только на `api.openflux.dev`
(аккаунт и подписка), на узлы, которые выбрал пользователь, и в сервисы, которые
служат транспортом. Аналитики и сборщиков сбоев в коде нет. Если это изменится,
ответы нужно пересмотреть.

Do you or your third-party partners collect data from this app? — **Yes**.

| Тип данных | Собираем | Связаны с пользователем | Трекинг | Цель |
|---|---|---|---|---|
| Contact Info → Email Address | да (вход по почте) | да | нет | App Functionality |
| Identifiers → User ID | да (номер аккаунта или Telegram id) | да | нет | App Functionality |
| Other Data Types → Other (сессии Яндекса и Mail.ru, переданные узлу сервиса) | только если узел — сервера OpenFlux | да | нет | App Functionality |

Не собираем: Location, Contacts, Browsing History (трафик через туннель не
логируется), Health, Financial Info (оплата идёт вне приложения), Usage Data,
Diagnostics. Если exit-узлы сервиса пишут логи соединений, ответ про Browsing
History и политика должны говорить об этом честно.

## 5. Export Compliance (на каждую сборку, если ключ не зафиксирован)

Не юридическая консультация. Правильная классификация — ответственность компании.

1. Does your app use encryption? — **Yes**.
2. Does your app qualify for any of the exemptions provided in Category 5, Part 2
   of the U.S. EAR? — **Yes**: mass market, § 740.17(b)(1). Используются только
   стандартные алгоритмы: AES-GCM, HMAC-SHA256, scrypt, TLS.
3. Does your app implement any encryption algorithms that are proprietary or not
   accepted as standards? — **No**.
4. Does your app implement standard encryption algorithms instead of, or in
   addition to, using or accessing the encryption within Apple's operating
   system? — **Yes**.
5. Is your app going to be available on the App Store in France? — если **да**,
   App Store Connect попросит французскую декларацию (ANSSI). Проще убрать
   Францию из стран до её получения.

После публикации: **ежегодный self-classification report** в BIS и NSA до 1 февраля
(требование § 740.17(b)(1) для mass-market продуктов с несистемным шифрованием).
Чтобы не отвечать на вопросы для каждой сборки, после одобрения можно добавить в
`project.yml` ключ `ITSEncryptionExportComplianceCode`.
