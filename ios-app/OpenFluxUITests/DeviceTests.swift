import XCTest

/// On-device checks driven from a Mac (`xcodebuild test` on the developer's
/// iPhone): the UI is tapped as a person would, and the runner's own requests
/// go through the VPN, so it can tell the exit's address and the speed.
/// Settings come in as TEST_RUNNER_OFX_* environment variables:
///   OFX_PROFILES  connection rows to try, by their full label, "|"-separated
///   OFX_HOLD      seconds to keep each connection up and watch it (default 30)
///   OFX_MB        megabytes to download for the speed (default 5)
final class DeviceTests: XCTestCase {
    let app = XCUIApplication(bundleIdentifier: "com.p1neapplexpress-saharev.openflux")
    var env: [String: String] { ProcessInfo.processInfo.environment }

    override func setUp() {
        continueAfterFailure = true
    }

    // MARK: - Reporting

    func shot(_ name: String) {
        let a = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        a.name = name
        a.lifetime = .keepAlways
        add(a)
    }

    func note(_ name: String, _ text: String) {
        let a = XCTAttachment(string: text)
        a.name = name
        a.lifetime = .keepAlways
        add(a)
        print("OFX[\(name)] \(text)")
    }

    // MARK: - The UI

    static let states = ["Подключено", "Подключаюсь", "Переподключаюсь", "Связь прервалась", "Отключаюсь", "Отключено", "Не подключено", "Ошибка"]

    /// The home screen's state line (the first static text that is one of `states`).
    func state() -> String {
        // One query per state: enumerating every text races the screen as it changes.
        for st in Self.states {
            let e = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", st)).firstMatch
            if e.exists { return e.label }
        }
        return "?"
    }

    func waitState(_ accept: (String) -> Bool, timeout: TimeInterval) -> String {
        let end = Date().addingTimeInterval(timeout)
        var s = state()
        while !accept(s) && Date() < end {
            sleep(1)
            s = state()
        }
        return s
    }

    /// Picks a connection on Home: the row under «ПОДКЛЮЧЕНИЕ», then the one labelled `label`.
    func choose(_ label: String) -> Bool {
        let home = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Главная'")).firstMatch
        if home.exists { home.tap(); sleep(1) }
        guard let header = app.staticTexts["ПОДКЛЮЧЕНИЕ"].firstMatch as XCUIElement?, header.exists else { return false }
        let y = header.frame.maxY
        // The connection row sits right under the header (the tab bar's "X, X" buttons are far below).
        let row = app.buttons.matching(NSPredicate(format: "label CONTAINS ','")).allElementsBoundByIndex
            .first { $0.exists && $0.frame.minY >= y && $0.frame.minY < y + 120 }
        if row?.label == label { return true }
        row?.tap()
        let target = app.buttons[label].firstMatch
        guard target.waitForExistence(timeout: 5) else {
            note("missing \(label)", app.debugDescription)
            app.buttons["Close sheet"].firstMatch.tap()
            return false
        }
        target.tap()
        sleep(1)
        if app.buttons["Close sheet"].exists { app.buttons["Close sheet"].tap() }
        return true
    }

    /// The journal's visible lines (Settings → Журнал, the newest at the bottom), and two screens above.
    /// …and what it says about the extension: its memory peak and any kill. "" when it could not be read.
    @discardableResult
    func journal(_ tag: String) -> String {
        let settings = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Настройки'")).firstMatch
        guard settings.exists else { return "" }
        settings.tap()
        sleep(1)
        let j = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Журнал'")).firstMatch
        guard j.waitForExistence(timeout: 5) else { note("\(tag) journal", "no journal row"); return "" }
        j.tap()
        sleep(2)
        var pages: [String] = []
        var peak = 0.0
        var kills = 0
        for i in 0..<3 {
            let lines = app.debugDescription.split(separator: "\n")
                .filter { $0.contains("StaticText") }
                .compactMap { l -> String? in
                    guard let r = l.range(of: "label: '") else { return nil }
                    let rest = l[r.upperBound...]
                    guard let end = rest.range(of: "'", options: .backwards) else { return String(rest) }
                    return String(rest[..<end.lowerBound])
                }
            for l in lines {
                if let r = l.range(of: "пик "), let v = Double(l[r.upperBound...].prefix { "0123456789.".contains($0) }) { peak = max(peak, v) }
                if l.contains("завершено системой") { kills += 1 }
            }
            let shown = lines.filter { !$0.contains("[NE] память") }
            pages.insert("--- screen \(i) ---\n" + shown.joined(separator: "\n"), at: 0)
            app.swipeDown()
            sleep(1)
        }
        note("\(tag) journal", pages.joined(separator: "\n"))
        if app.buttons["Назад"].exists { app.buttons["Назад"].tap(); sleep(1) }
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Главная'")).firstMatch.tap()
        sleep(1)
        return String(format: "пик памяти NE %.1f МБ, убийств в журнале %d", peak, kills)
    }

    // MARK: - Through the tunnel (the runner's own traffic)

    func fetch(_ url: String, timeout: TimeInterval) -> (Data?, TimeInterval, String?) {
        let cfg = URLSessionConfiguration.ephemeral
        cfg.timeoutIntervalForRequest = timeout
        cfg.timeoutIntervalForResource = timeout
        let s = URLSession(configuration: cfg)
        let done = DispatchSemaphore(value: 0)
        var out: Data?
        var err: String?
        let t0 = Date()
        s.dataTask(with: URL(string: url)!) { d, r, e in
            out = d
            if let e = e { err = e.localizedDescription }
            else if let h = r as? HTTPURLResponse, h.statusCode >= 400 { err = "HTTP \(h.statusCode)" }
            done.signal()
        }.resume()
        _ = done.wait(timeout: .now() + timeout + 5)
        return (out, Date().timeIntervalSince(t0), err)
    }

    func measure(_ tag: String) -> String {
        var lines: [String] = []
        let (ip, tIP, eIP) = fetch("https://api.ipify.org", timeout: 20)
        lines.append("exit IP: \(ip.flatMap { String(data: $0, encoding: .utf8) } ?? "-") (\(String(format: "%.1f", tIP)) s\(eIP.map { ", " + $0 } ?? ""))")
        let (_, tTG, eTG) = fetch("https://telegram.org", timeout: 20)
        lines.append("telegram.org: \(eTG ?? "ok") \(String(format: "%.1f", tTG)) s")
        let mb = Int(env["OFX_MB"] ?? "") ?? 5
        let (d, t, e) = fetch("https://speed.cloudflare.com/__down?bytes=\(mb * 1_000_000)", timeout: 90)
        let got = d?.count ?? 0
        lines.append(String(format: "download: %d B in %.1f s = %.0f KB/s%@", got, t, Double(got) / 1024 / max(t, 0.01), e.map { ", " + $0 } ?? ""))
        let text = lines.joined(separator: "\n")
        note("\(tag) measure", text)
        return text
    }

    // MARK: - The run

    func testProfiles() {
        let profiles = (env["OFX_PROFILES"] ?? "").split(separator: "|").map(String.init).filter { !$0.isEmpty }
        let hold = Int(env["OFX_HOLD"] ?? "") ?? 30
        app.launch()
        sleep(3)
        var summary: [String] = []
        for p in profiles {
            let tag = String(p.prefix(40))
            if state().hasPrefix("Подключено") || state().hasPrefix("Подключаюсь") {
                app.buttons["Отключить"].firstMatch.tap()
                _ = waitState({ !$0.hasPrefix("Подключ") && !$0.hasPrefix("Отключаюсь") }, timeout: 20)
            }
            guard choose(p) else { summary.append("\(tag): не найден в списке"); continue }
            app.buttons["Подключить"].firstMatch.tap()
            let t0 = Date()
            let s = waitState({ $0.hasPrefix("Подключено") || $0.hasPrefix("Ошибка") || $0.hasPrefix("Не подключено") }, timeout: 60)
            let took = Date().timeIntervalSince(t0)
            shot("\(tag) after connect")
            guard s.hasPrefix("Подключено") else {
                note("\(tag) screen", app.debugDescription)
                let mem = journal(tag)
                summary.append(String(format: "%@: НЕ подключился за %.0f с (%@); %@", tag, took, s, mem))
                if app.buttons["Отключить"].exists { app.buttons["Отключить"].firstMatch.tap() }
                sleep(3)
                continue
            }
            let m = measure(tag)
            // Keep it up and watch: a drop or a restart shows as a state other than «Подключено».
            var drops = 0
            for _ in 0..<(hold / 5) {
                sleep(5)
                if !state().hasPrefix("Подключено") { drops += 1 }
            }
            shot("\(tag) after hold")
            var mem = ""
            if env["OFX_JOURNAL"] == "1" || drops > 0 || !state().hasPrefix("Подключено") || m.contains("timed out") || m.contains("TLS") || m.contains("offline") {
                mem = journal(tag)
            }
            summary.append(String(format: "%@: подключился за %.0f с; %@; за %d с вне «Подключено» %d раз; %@", tag, took,
                                  m.replacingOccurrences(of: "\n", with: "; "), hold, drops, mem))
            if app.buttons["Отключить"].exists { app.buttons["Отключить"].firstMatch.tap() }
            _ = waitState({ !$0.hasPrefix("Подключ") && !$0.hasPrefix("Отключаюсь") }, timeout: 20)
        }
        note("SUMMARY", summary.joined(separator: "\n"))
    }

    /// «Свой сервер»: an address and a password typed in must end in a message on the
    /// form, not in the app closing (TestFlight crash reports, builds 115-116).
    func testAddServerDoesNotCrash() {
        app.launch()
        sleep(3)
        app.buttons["Добавить"].firstMatch.tap()
        sleep(2)
        note("add sheet", app.debugDescription)
        shot("add sheet")
        let own = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Свой сервер'")).firstMatch
        guard own.waitForExistence(timeout: 5) else { XCTFail("no «Свой сервер»"); return }
        own.tap()
        sleep(2)
        note("server form", app.debugDescription)
        shot("server form")
        let fields = app.textFields.allElementsBoundByIndex + app.secureTextFields.allElementsBoundByIndex
        note("fields", fields.map { "\($0.elementType.rawValue) '\($0.label)' '\($0.placeholderValue ?? "")'" }.joined(separator: "\n"))
        // Compose's fields come through as elements labelled like their hint: tap, then type.
        func type(_ label: String, nth: Int, _ text: String) {
            let all = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).allElementsBoundByIndex.filter { $0.exists && $0.isHittable }
            guard all.count > nth else { note("no field \(label)", "\(all.count)"); return }
            all[nth].tap()
            sleep(1)
            app.typeText(text)
        }
        type("Адрес сервера", nth: 0, "192.0.2.10")
        type("Пароль", nth: 1, "not-a-real-password")
        let go = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Подключ'")).firstMatch
        if go.exists { go.tap() }
        sleep(5)
        shot("after connect")
        note("after connect", app.debugDescription)
        XCTAssertEqual(app.state, .runningForeground, "the app went away")
    }
}
