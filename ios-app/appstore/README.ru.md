# OpenFlux iOS — подготовка к App Store

Чек-лист публикации. Что уже сделано в коде, что остаётся сделать в аккаунте
разработчика и App Store Connect и где риски ревью. Тексты для стора лежат
рядом: [metadata.md](metadata.md) (описание и заметки для ревьюера). Политика
конфиденциальности — [PRIVACY.md](../../PRIVACY.md) в корне репозитория.

## 1. Сделано в коде (ветка `appstore-prep`)

| Что | Где |
|---|---|
| Команда `H6KHCZK2RG`, bundle id `com.pnonetwork.openflux` во всех таргетах: приложение, `.tunnel`, `.controls`, `.uitests`, keychain group, kind контрола Пункта управления | `project.yml`, entitlements, `Secrets.swift`, `Keychain.kt`, `IosConnectionService.kt`, `VPNControl.swift`, `OpenFluxApp.swift`, `DeviceTests.swift`, `ExportOptions.plist`, `build_ios_app.sh` |
| `ITSAppUsesNonExemptEncryption = false` с обоснованием. Ядро само шифрует трафик, но только стандартными алгоритмами (AES-GCM, HMAC-SHA256, scrypt), а Франции нет в списке стран. На такие ответы App Store Connect (билд 123) отвечает «документация не нужна» и сам советует поставить NO. Значение `true` без кода документации отвергается при загрузке («Invalid Export Compliance Code») | `project.yml`, `OpenFlux/Info.plist` |
| Privacy Manifest приложения: file timestamp `C617.1` и system boot time `35F9.1` (Go и Kotlin/Native: `stat`/`fstat`, `mach_absolute_time`), UserDefaults `CA92.1`; трекинга нет | `OpenFlux/PrivacyInfo.xcprivacy` |
| Privacy Manifest туннеля: file timestamp и boot time (Go-ядро) | `OpenFluxTunnel/PrivacyInfo.xcprivacy` |
| Старый («классический») интерфейс на Swift удалён целиком, вместе с экраном «О проекте» | `OpenFlux/` |
| Проверка выходного IP идёт по HTTPS | `TunnelController.swift` |

Оставлено сознательно:
- `NSAllowsArbitraryLoads = true`. Приложение открывает HTTP-адреса, которые
  вводит сам пользователь: страницы скрипта установки узла на loopback и адреса
  его серверов. Причина записана в заметках для ревьюера.

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
6. **Политика конфиденциальности**: [PRIVACY.md](../../PRIVACY.md) в корне этого
   репозитория. В App Store Connect вписан адрес
   `https://github.com/OpenFluxTeam/OpenFluxIOS/blob/main/PRIVACY.md`.
   Политика лежит рядом с кодом, а не на сайте сервиса: карточка iOS-приложения
   не должна вести на страницы, где что-то продаётся.
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
    можно открыть любой сайт. В App Store Connect итог — 16+.
12. **Сборка и загрузка**: поднять `CURRENT_PROJECT_VERSION` в `project.yml`,
    затем `./build_ios_app.sh` (для Kotlin UI нужен JDK 17: `JAVA_HOME`),
    загрузка в TestFlight, потом Submit for Review.

## 3. Что ещё может вызвать отказ (код команды, `shared/`, бэкенд)

- ~~**3.1.3(b), подписки, купленные вне приложения**~~ и ~~**5.1.1(v), удаление
  аккаунта**~~. На iOS нет ни аккаунта, ни подписок, ни ссылок сервиса: см. ниже
  «С билда 127» и «С билда 128».
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

### С билда 127: в iOS нет ничего о сервисе OpenFlux

App Review (2.1(b)) спросил про платные подписки и ключи. iOS-версия — бесплатный
open-source клиент для своих серверов и ссылок `openflux://`
(`serviceInApp = false`, [OpenFluxClientShared#7](https://github.com/OpenFluxTeam/OpenFluxClientShared/pull/7)):
нет раздела аккаунта, ключей подписки, ссылок бота и слов «подписка», «бот»,
«сервис». К `api.openflux.dev` iOS-приложение не обращается (API бота и подписки —
заглушки). Support URL в карточке — GitHub-репозиторий, а не бот. Анкета App Privacy
для iOS теперь должна быть «данные не собираются».

### С билда 128: кода сервиса нет в бинарнике

Билд 127 только скрывал экраны сервиса флагами `PlatformServices`. Сам код и его
строки («Подписка», «Оплатить», `openflux_bot`, «Режим разработчика») оставались в
приложении, а автоматическая проверка App Store читает и строки. С билда 128
([OpenFluxClientShared#8](https://github.com/OpenFluxTeam/OpenFluxClientShared/pull/8)):

- Экраны аккаунта и оплаты, ключи подписки и ссылки бота, каталог JS-транспортов,
  обновления приложения и режим разработчика доступны остальному коду только через
  `buildExtras` и `serviceExtras` (`expect val`). На iOS оба равны `null`, поэтому
  Kotlin/Native не включает этот код в бинарник. Desktop и Android не меняются.
- Старый интерфейс на Swift (`ContentView`, `InfoView`, `VPNController` и другие)
  удалён: он не открывался с билда 126, но компилировался.
- «О программе» ведёт на `OpenFluxTeam/OpenFluxIOS`.

Проверка перед загрузкой: в бинарнике `OpenFlux.app/OpenFlux` не должно быть строк
«Подписка», «Оплатить», «Купить», «тариф», `openflux_bot`, `api.openflux.dev`,
`link.openflux.dev`, «Режим разработчика», «Экспериментальн» (UTF-8 и UTF-16LE).

Что остаётся и почему: слова про JS-транспорт в редакторе профиля (чужой профиль
может его содержать, приложение честно пишет, что на этом устройстве он не
поддерживается).

### С билда 129: в ядре для iOS нет JS-движка

До билда 128 включительно библиотека ядра `liboflux.a` содержала интерпретатор
JavaScript (goja) и код script-транспортов: их тянул общий реестр транспортов, хотя
iOS-приложение не могло их вызвать. Для правила 2.5.2 важно, чтобы в приложении не
было способа исполнить код, полученный позже, поэтому с билда 129 ядро для iOS
собирается без движка (`//go:build !ios` в `transport/registry` и `mobile/script.go`
ядра). Тип транспорта `script` на iOS возвращает ошибку.

Проверка перед загрузкой: в `OpenFlux` и `OpenFluxTunnel` не должно быть строк
`dop251/goja` и `OpenFlux/transport/script`; для ядра команда
`GOOS=ios go list -deps -tags ios ./ios` в `core/mobile` не должна выводить `goja`.

## 4. Анкета App Privacy (App Store Connect → App Privacy)

Ответ: **Data Not Collected** («Сбор данных не ведётся»), опубликован 27.11.2026.

Основание: в iOS-приложении нет аккаунта, аналитики и сборщиков сбоев, к серверам
разработчика оно не обращается. Профили, ключи и журнал хранятся на устройстве.
Сессия Яндекса или Mail.ru уходит только на сервер, который подключил сам
пользователь, и только после его согласия; разработчик её не получает. Адрес
выхода приложение узнаёт у `api.ipify.org` через туннель.

Если в iOS появится аккаунт, аналитика или любой запрос к серверам разработчика,
анкету и политику нужно пересмотреть до загрузки такого билда.

## 5. Export Compliance (TestFlight → сборка → «Управлять соответствием»)

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
Для билда 123 ответы даны так: стандартные алгоритмы, Франции нет. App Store Connect
ответил, что документация не нужна, поэтому начиная с билда 124 в plist стоит
`ITSAppUsesNonExemptEncryption: false`, и вопросы больше не задаются. Если появится
собственный (нестандартный) алгоритм или Франция — ответьте на вопросы заново и
загрузите документацию.
