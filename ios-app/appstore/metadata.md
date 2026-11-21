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
| Support URL | `https://t.me/openflux_bot` (лучше страница на `openflux.dev/support`) |
| Marketing URL | `https://openflux.dev` |
| Privacy Policy URL | `https://openflux.dev/privacy` (опубликовать [PRIVACY.md](PRIVACY.md)) |
| Copyright | `2026 <юридическое название организации>` |
| Age rating | 17+ (Unrestricted Web Access: встроенный браузер) |

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
| Sign-in required | No (работает с профилем по ссылке, без аккаунта) |
| Contact | имя, телефон, почта ответственного (заполнить) |
| Demo profile | ссылка `openflux://…` на узел, который работает во время ревью (заполнить) |

**Notes (English, вставить в поле Notes):**

```
OpenFlux is a VPN client built on NEPacketTunnelProvider (bundle
com.pnonetwork.openflux.tunnel). It tunnels the device's traffic, encrypted
end to end (AES-GCM), to an exit node run by the user or their server
administrator. The developer does not log or inspect tunneled traffic.

How to test:
1. Launch the app, tap "+" and paste this profile link (or scan the QR code
   in the attached screenshot):
   openflux://<DEMO PROFILE — FILL IN>
2. Tap Connect and allow the VPN configuration when iOS asks.
3. The home screen shows "Connected" and the exit IP of our test node
   (<TEST NODE IP — FILL IN>). Safari now browses through the tunnel.
4. Control Center (iOS 18): the OpenFlux switch turns the same VPN on and off.

No account is needed to test. Signing in to an OpenFlux account is optional;
iOS shows no purchases, prices or payment links.

Network Extension: the packet tunnel provider carries all IPv4 traffic and
DNS through the tunnel; addresses the user marks as direct are excluded.

Encryption: the app implements standard encryption (AES-GCM, HMAC-SHA256,
scrypt) for the tunnel; it qualifies for the mass-market exemption
(EAR 740.17(b)(1)).

App Transport Security: NSAllowsArbitraryLoads is set because the app opens
addresses the user enters for their own servers (subscription and profile
URLs on plain HTTP, and the node installer's page on 127.0.0.1). The app's
own services are HTTPS only.

Camera: used only to scan profile QR codes.
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
