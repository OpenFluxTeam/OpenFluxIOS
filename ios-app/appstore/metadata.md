# App Store metadata — OpenFlux

Черновик карточки приложения. Ограничения App Store Connect указаны в скобках.
Формулировки нейтральные: «защищённый туннель к вашему серверу». Слова «обход
блокировок», названия Яндекса, Avito, MAX в описании — повод для вопросов на ревью
(5.2) и для удаления по требованию регуляторов.

## Общее

| Поле | Значение |
|---|---|
| Name (30) | `OpenFlux` · запасные: `OpenFlux Tunnel`, `OpenFlux VPN` |
| Primary category | Utilities |
| Secondary category | Productivity |
| Price | Free |
| Support URL | `https://github.com/OpenFluxTeam/OpenFluxIOS` (публичный репозиторий; не бот и не чат) |
| Privacy Policy URL | `https://github.com/OpenFluxTeam/OpenFluxIOS/blob/main/PRIVACY.md` ([PRIVACY.md](../../PRIVACY.md) в корне репозитория) |
| Copyright | `2026 <юридическое название организации>` |
| Age rating | 16+ (Unrestricted Web Access: встроенный браузер) |

## Русский (ru)

**Subtitle (30):** `Защищённый туннель к серверу`

**Promotional Text (170):**
Подключайтесь к своему серверу через зашифрованный туннель. Профиль добавляется
ссылкой или QR-кодом, VPN включается одной кнопкой или из Пункта управления.

**Description (4000):**
OpenFlux — клиент зашифрованного туннеля. Весь трафик устройства или отдельные
приложения идут через ваш сервер (exit-узел), а не напрямую.

Возможности:
• Системный VPN на базе Network Extension: включается в приложении, в
  Настройках → VPN или переключателем в Пункте управления (iOS 18).
• Добавление профиля ссылкой openflux:// или QR-кодом, с камеры или из фото.
• Мастер «Свой сервер»: установка узла на ваш VPS по SSH прямо с телефона.
• Несколько транспортов для одного профиля и автоматический выбор рабочего.
• Сквозное шифрование трафика между телефоном и вашим узлом.
• Маршрутизация: выбранные домены и адреса можно пустить напрямую, мимо туннеля.
• Проверка задержки профилей, IP выхода и скорости.
• Журнал подключения для диагностики, который хранится только на устройстве.

OpenFlux не показывает рекламу, не отслеживает пользователей и не хранит историю
посещённых сайтов.

Нужен собственный сервер или профиль от администратора сервера.

**Keywords (100, через запятую без пробелов):**
`vpn,туннель,прокси,socks5,шифрование,сервер,vps,приватность,сеть,безопасность`

## English (en-US)

**Subtitle (30):** `Secure tunnel to your server`

**Promotional Text (170):**
Connect to your own server over an encrypted tunnel. Add a profile by link or QR
code and turn the VPN on with one tap or from Control Center.

**Description (4000):**
OpenFlux is an encrypted tunnel client. Your device's traffic goes through your
own server (an exit node) instead of directly.

Features:
• System VPN built on Network Extension: turn it on in the app, in Settings → VPN,
  or with the Control Center switch (iOS 18).
• Add a profile with an openflux:// link or a QR code, from the camera or a photo.
• "Your own server" wizard: install the node on your VPS over SSH from the phone.
• Several transports per profile, with automatic fallback to one that works.
• End-to-end encryption between the phone and your node.
• Routing: send chosen domains and addresses directly, outside the tunnel.
• Profile latency, exit IP and speed checks.
• A connection log for troubleshooting that stays on the device.

OpenFlux shows no ads, does not track you, and keeps no browsing history.

Requires your own server or a profile from a server administrator.

**Keywords (100):**
`vpn,tunnel,proxy,socks5,encryption,server,vps,privacy,network,security,self-hosted`

## App Review Information

| Поле | Значение |
|---|---|
| Sign-in required | No (аккаунта в приложении нет; профиль добавляется ссылкой) |
| Contact | имя, телефон, почта ответственного (заполнить) |
| Demo profile | ссылка `openflux://…` на узел, который работает во время ревью (заполнить) |

**Notes (English, поле Notes; текст для билда 129):**

```
OpenFlux is a free, open-source VPN client (NEPacketTunnelProvider, com.pnonetwork.openflux.tunnel). Source: https://github.com/OpenFluxTeam/OpenFluxIOS. It encrypts the device's traffic end to end (AES-256-GCM) and sends it to a server the user runs or was given access to.

How to test:
1. Tap "+" → "Ссылка или файл" (Link or file), paste this link, tap "Добавить профиль" (Add profile):
openflux://v1/<DEMO PROFILE: в App Store Connect, в репозиторий не кладём>
2. Tap the power button and allow the VPN configuration.
3. The home screen shows "Подключено" (Connected); under "Сведения" the external IP is our test server (d1.aurorix.net, 89.35.130.1).

Business model:
The app is free and nothing is sold in it or through it: no accounts, subscriptions, purchases, prices, ads or payment links, and it unlocks no paid content or features. It has no account screen and reads no subscription keys or service links; it does not contact any server of ours. The code of those screens, of app self-updates and of the developer mode is not compiled into the iOS binary. The app cannot install or run loadable (JS) transports: build 129 contains no script engine at all (the tunnel core for iOS is built without it).
A connection profile is an openflux:// link or QR code that describes a server (address, transports, encryption key), like a WireGuard or OpenVPN configuration. Users get it by (a) installing the open-source server on their own VPS with the in-app wizard "Свой сервер" (over SSH), or (b) from whoever runs the server (a friend, an employer's administrator). The app does not provide, sell or validate profiles.
OpenFlux is not sold and will not be. Outside the App Store our team has tested a paid hosted service (a Telegram bot, for desktop and Android clients); it is being separated into a different product with its own name, app and website. The iOS app has no integration with it.

VPN data:
1. Collected using VPN: none. The developer does not receive, log, inspect or store tunneled traffic (no browsing history, DNS queries, visited addresses or content). The connection log stays on the device. No analytics or crash-reporting SDK. Outside the tunnel the app contacts only the transport services the user chose (they carry only encrypted packets) and api.ipify.org through the tunnel to show the exit IP. It does not contact any developer server.
2. Purposes: we collect nothing through the VPN. Profiles and keys stay on the device (keys in the Keychain); an optional Yandex or Mail.ru session goes to the user's own server only after explicit consent, so it can use that service as its transport.
3. Third parties: no user data is sold or shared. Traffic goes only to the server the user connected to; a document-service transport sees only encrypted packets. The developer stores nothing. Privacy policy: https://github.com/OpenFluxTeam/OpenFluxIOS/blob/main/PRIVACY.md

Encryption: standard algorithms only (AES-GCM, HMAC-SHA256, scrypt); not distributed in France.
ATS: NSAllowsArbitraryLoads is set because users enter addresses of their own servers (plain-HTTP URLs, the node installer page on 127.0.0.1).
Camera: only to scan profile QR codes.
```

## Скриншоты (6.9″, портрет)

Что показать, по порядку:
1. Главный экран: подключено, IP выхода, трафик.
2. Список профилей с задержками.
3. Добавление профиля: QR или ссылка.
4. Мастер «Свой сервер».
5. Маршрутизация (прямые домены).
6. Пункт управления с переключателем OpenFlux (iOS 18).

На скриншотах не должно быть реальных адресов, токенов, почты и чужих
логотипов (Яндекс, Avito, MAX): это 5.2.
