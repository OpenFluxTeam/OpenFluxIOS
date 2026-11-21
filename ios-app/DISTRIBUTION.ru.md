# OpenFlux iOS — распространение (TestFlight / App Store Connect)

Документ фиксирует, что вопрос сборки и распространения iOS-приложения OpenFlux
был проработан: приложение собрано с нуля, подписано и загружено в App Store Connect.

## Что сделано

1. **Go-ядро → статическая библиотека.** Добавлен cgo-слой экспортов
   (`../export_ios.go`, сборка по тегу `ios`): `OpenFluxStartClient`,
   `OpenFluxStop`, `OpenFluxIsRunning`, `OpenFluxIsConnected`,
   `OpenFluxStatsJSON`, `OpenFluxReadLog`, `OpenFluxFreeString`.
   Сборка: `../build_ios.sh` → `../output/ios/liboflux.a` (+ авто-заголовок `liboflux.h`).
   Теперь этот слой — пакет `mobile/ios` ядра OpenFlux из сабмодуля `../core`,
   а не копия в корне репозитория: тот же код, что у Android и десктопа.
2. **iOS-приложение (SwiftUI, XcodeGen).** Экран с полем Yandex.Docs URL,
   Start/Stop, индикатор состояния, живой лог и кнопка Test (проверяет тоннель
   запросом через локальный SOCKS5 `127.0.0.1:1080`). Линкует `liboflux.a`.
3. **Иконка** 1024×1024 в asset-каталоге (обязательна для загрузки).
4. **Подпись и архив.** Automatic signing, team `H6KHCZK2RG` (до билда 122 — `8GQH8GQ252`), Cloud Managed
   Apple Distribution. Экспортирован App Store `.ipa`.
5. **Загрузка в App Store Connect** через ASC API-ключ (см. ниже).
   Ключа `ITSAppUsesNonExemptEncryption` в plist нет: ядро само шифрует трафик
   (AES-GCM), поэтому `false` был бы неправдой, а `true` без кода документации
   App Store Connect отвергает при загрузке. Вопросы о шифровании задаются для
   каждой сборки, ответы — в [appstore/README.ru.md](appstore/README.ru.md).

## Параметры

| Параметр | Значение |
|---|---|
| Bundle ID | `com.pnonetwork.openflux` (`.tunnel`, `.controls`, `.uitests`) |
| Team ID | `H6KHCZK2RG` (организация; прежний `8GQH8GQ252` — Individual) |
| Marketing version | `1.2.0` |
| Deployment target | iOS 15.0 |
| Architecture | arm64 (device) |
| ASC API Key ID | `<KEY_ID>` |
| ASC Issuer ID | `<ISSUER_ID>` |
| Приватный ключ | `~/.appstoreconnect/private_keys/AuthKey_<KEY_ID>.p8` (НЕ коммитить) |

## Полная сборка одной командой

Из корня репозитория:
```bash
./build_ios_app.sh
```
Результат: `ios-app/build/export/OpenFlux.ipa` (подписан для App Store).

## Загрузка в TestFlight

```bash
xcrun altool --upload-app -f ios-app/build/export/OpenFlux.ipa -t ios \
  --apiKey <KEY_ID> --apiIssuer <ISSUER_ID>
```
Билд появляется в TestFlight через несколько минут после обработки Apple.

### Новый билд
Перед каждой новой загрузкой поднять номер сборки в `ios-app/project.yml`:
```yaml
settings:
  base:
    CURRENT_PROJECT_VERSION: "3"   # +1
```
Apple не принимает повторно тот же номер сборки.

## История загрузок

| Дата | Build | Delivery UUID | Примечание |
|---|---|---|---|
| 2026-09-10 | 1 | `<uuid-1>` | первый билд |
| 2026-09-10 | 2 | `<uuid-2>` | + флаг шифрования, без экспортной документации |
| 2026-09-10 | 3 | `<uuid-3>` | + транспорт MAX в UI, настраиваемый порт, no-crash bind |

## Ограничения и риски распространения

- Подготовка к публичному релизу в App Store (организация, Privacy Manifest,
  шифрование, тексты стора, риски ревью) — в [appstore/README.ru.md](appstore/README.ru.md).
- **TestFlight Internal Testing** (разработчик + внутренние пользователи) Beta App
  Review не проходит — работает уже сейчас. **External Testing** и релиз — проходят.
- Возможно региональное снятие (Apple удаляла обходные приложения из ряда сторов
  по требованию регуляторов).
- Тоннелирование через Яндекс.Документы / MAX вероятно нарушает их ToS.

## Безопасность

- `AuthKey_*.p8` — **секрет**, даёт доступ к App Store Connect. Не коммитить в git,
  хранить только в `~/.appstoreconnect/private_keys/`. Компрометацию — отзывать в
  App Store Connect → Users and Access → Integrations.
- Key ID и Issuer ID сами по себе не секретны, но без `.p8` бесполезны.
