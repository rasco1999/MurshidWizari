import Foundation

struct APIError: LocalizedError {
    let message: String
    let status: Int
    let paymentRequired: Bool
    var errorDescription: String? { message }
}

enum PasswordResetChannel: Sendable { case email, phone }

struct PasswordResetStart: Sendable {
    let channel: PasswordResetChannel
    let phoneToken: String?
    let message: String
}

@MainActor
final class AppSession: ObservableObject {
    static let shared = AppSession()
    @Published var authenticated = false
    @Published var v3Authenticated = false
    @Published var bootstrapping = true
    @Published var user: UserSummary?
    @Published var subjects: [Subject] = []
    @Published var plans: [SubscriptionPlan] = []
    @Published var subscribed = false
    @Published var freeLimit = 15
    @Published var freeUsed = 0
    @Published var freeRemaining: Int?
    @Published var csrf = ""
    @Published var stats: JSON = [:]
    @Published var unreadNotifications = 0
    @Published var lastSync = Date.distantPast
    @Published var sessionExpired = false
    @Published var featureFlags: JSON = [:]
    @Published var releaseInfo: JSON = [:]

    private init() {}

    var accuracy: Int {
        let total = max(0, jInt(stats["total_answers"]))
        guard total > 0 else { return 0 }
        return Int((Double(jInt(stats["correct_answers"])) / Double(total) * 100).rounded())
    }

    func reset(expired: Bool = false) {
        authenticated = false
        v3Authenticated = false
        user = nil
        subjects = []
        plans = []
        subscribed = false
        freeUsed = 0
        freeRemaining = nil
        csrf = ""
        stats = [:]
        unreadNotifications = 0
        featureFlags = [:]
        releaseInfo = [:]
        sessionExpired = expired
    }

    func applyBootstrap(_ json: JSON) {
        let u = json["user"] as? JSON ?? [:]
        user = UserSummary(id: jInt(u["id"]), name: jString(u["name"]), grade: jString(u["grade"]), gradeID: jInt(u["grade_id"]))
        subjects = jArray(json["subjects"]).map(Subject.init)
        plans = jArray(json["plans"]).map(SubscriptionPlan.init)
        subscribed = jBool(json["subscribed"])
        freeLimit = jInt(json["free_limit"], default: 15)
        freeUsed = jInt(json["free_used"])
        freeRemaining = (json["free_remaining"] is NSNull || json["free_remaining"] == nil) ? nil : jInt(json["free_remaining"])
        csrf = jString(json["csrf"])
        stats = json["stats"] as? JSON ?? [:]
        // Keep the legacy RootView on AuthFlowView; AuthFlowView owns the complete v3 shell.
        authenticated = false
        v3Authenticated = true
        sessionExpired = false
        lastSync = Date()
    func applyAppConfig(_ json: JSON) {
        featureFlags = json["features"] as? JSON ?? [:]
        releaseInfo = json["release"] as? JSON ?? [:]
    }

    func featureEnabled(_ key: String, default defaultValue: Bool = true) -> Bool {
        guard let row = featureFlags[key] as? JSON else { return defaultValue }
        return jBool(row["enabled"], default: defaultValue)
    }

    }
}

@MainActor
final class APIClient {
    static let shared = APIClient()
    let baseURL = URL(string: "https://www.mur-iq.com")!
    private let session: URLSession

    private init() {
        let config = URLSessionConfiguration.default
        config.httpCookieStorage = .shared
        config.httpShouldSetCookies = true
        config.waitsForConnectivity = false
        config.timeoutIntervalForRequest = 12
        config.timeoutIntervalForResource = 25
        config.requestCachePolicy = .useProtocolCachePolicy
        config.urlCache = URLCache(memoryCapacity: 24 * 1024 * 1024, diskCapacity: 96 * 1024 * 1024, diskPath: "MurshidHTTP")
        config.httpAdditionalHeaders = [
            "Accept": "application/json",
            "X-Murshid-App": "ios-native-4.1",
            "X-Murshid-Client": "SwiftUI"
        ]
        session = URLSession(configuration: config)
    }

    func request(_ path: String, method: String = "GET", body: JSON? = nil, query: [URLQueryItem] = [], cacheKey: String? = nil) async throws -> JSON {
        guard var components = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false) else {
            throw APIError(message: "تعذر تكوين عنوان الطلب.", status: 0, paymentRequired: false)
        }
        if !query.isEmpty { components.queryItems = query }
        guard let url = components.url else { throw APIError(message: "عنوان الطلب غير صالح.", status: 0, paymentRequired: false) }

        if method == "GET",
           let cacheKey,
           !DeviceServices.shared.isOnline,
           let cached = ResponseCache.shared.load(key: cacheKey) {
            return cached
        }

        var req = URLRequest(url: url)
        req.httpMethod = method
        req.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body { req.httpBody = try JSONSerialization.data(withJSONObject: body, options: []) }

        do {
            let (data, response) = try await session.data(for: req)
            guard let http = response as? HTTPURLResponse else { throw APIError(message: "استجابة الخادم غير صالحة.", status: 0, paymentRequired: false) }
            guard let object = try? JSONSerialization.jsonObject(with: data), let json = object as? JSON else {
                let raw = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                let contentType = http.value(forHTTPHeaderField: "Content-Type")?.lowercased() ?? ""
                let looksLikeHTML = contentType.contains("text/html") || raw.lowercased().contains("<html") || raw.hasPrefix("<")
                let message: String
                if looksLikeHTML {
                    message = "تعذر إكمال الطلب الآن. تحقق من الاتصال وحاول مجددًا."
                } else if raw.isEmpty {
                    message = "تعذر قراءة استجابة الخادم. حاول مجددًا."
                } else {
                    message = String(raw.prefix(240))
                }
                throw APIError(message: message, status: http.statusCode, paymentRequired: false)
            }
            if http.statusCode == 401 {
                AppSession.shared.reset(expired: true)
                throw APIError(message: "انتهت الجلسة. سجّل الدخول مجددًا.", status: 401, paymentRequired: false)
            }
            let ok = json["ok"] == nil ? (200...299).contains(http.statusCode) : jBool(json["ok"])
            if !(200...299).contains(http.statusCode) || !ok {
                throw APIError(
                    message: jString(json["message"], default: "تعذر إكمال العملية."),
                    status: http.statusCode,
                    paymentRequired: jBool(json["payment_required"])
                )
            }
            if method == "GET", let cacheKey { await ResponseCache.shared.save(json, key: cacheKey) }
            return json
        } catch let error as APIError {
            throw error
        } catch {
            if method == "GET", let cacheKey, let cached = ResponseCache.shared.load(key: cacheKey) { return cached }
            let ns = error as NSError
            let message: String
            if ns.domain == NSURLErrorDomain && ns.code == NSURLErrorTimedOut {
                message = "الاتصال بطيء الآن. حاول مرة أخرى."
            } else {
                message = "تعذر الاتصال بالإنترنت. تحقق من الشبكة وحاول مجددًا."
            }
            throw APIError(message: message, status: 0, paymentRequired: false)
        }
    }

    func formRequest(_ path: String, fields: [String: String]) async throws -> (Data, HTTPURLResponse) {
        let url = try makeURL(path: path)
        return try await formRequest(url: url, fields: fields)
    }

    private func formRequest(url: URL, fields: [String: String]) async throws -> (Data, HTTPURLResponse) {
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/x-www-form-urlencoded; charset=utf-8", forHTTPHeaderField: "Content-Type")
        req.setValue("text/html,application/xhtml+xml", forHTTPHeaderField: "Accept")
        let allowed = CharacterSet.urlQueryAllowed.subtracting(CharacterSet(charactersIn: "+&="))
        req.httpBody = fields.map { key, value in
            let k = key.addingPercentEncoding(withAllowedCharacters: allowed) ?? key
            let v = value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
            return "\(k)=\(v)"
        }.joined(separator: "&").data(using: .utf8)
        do {
            let (data, response) = try await session.data(for: req)
            guard let http = response as? HTTPURLResponse else {
                throw APIError(message: "تعذر الاتصال بالخادم.", status: 0, paymentRequired: false)
            }
            guard (200...399).contains(http.statusCode) else {
                throw APIError(message: "تعذر إكمال الطلب. حاول مجددًا.", status: http.statusCode, paymentRequired: false)
            }
            return (data, http)
        } catch let error as APIError {
            throw error
        } catch {
            throw APIError(message: "تعذر الاتصال بالإنترنت. تحقق من الشبكة وحاول مجددًا.", status: 0, paymentRequired: false)
        }
    }

    func htmlCSRF(_ path: String, query: [URLQueryItem] = []) async throws -> String {
        let url = try makeURL(path: path, query: query)
        let (data, response) = try await session.data(from: url)
        guard let http = response as? HTTPURLResponse, (200...399).contains(http.statusCode), let html = String(data: data, encoding: .utf8) else {
            throw APIError(message: "تعذر تهيئة التحقق.", status: 419, paymentRequired: false)
        }
        return try csrfToken(from: html)
    }

    func beginPasswordReset(identifier: String) async throws -> PasswordResetStart {
        let value = identifier.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else {
            throw APIError(message: "أدخل رقم الهاتف أو البريد الإلكتروني.", status: 422, paymentRequired: false)
        }
        let csrf = try await htmlCSRF("forgot-password.php")
        let (data, response) = try await formRequest("forgot-password.php", fields: ["csrf": csrf, "identifier": value])

        if let finalURL = response.url,
           let components = URLComponents(url: finalURL, resolvingAgainstBaseURL: false),
           let token = components.queryItems?.first(where: { $0.name == "phone_token" })?.value,
           token.range(of: #"^[a-f0-9]{64}$"#, options: .regularExpression) != nil {
            return PasswordResetStart(
                channel: .phone,
                phoneToken: token,
                message: "أرسلنا رمز استعادة من 6 أرقام إلى رقم WhatsApp المسجل."
            )
        }

        let parsed = htmlMessage(from: data)
        if parsed.isError {
            throw APIError(message: parsed.text.isEmpty ? "تعذر تنفيذ طلب الاستعادة." : parsed.text, status: response.statusCode, paymentRequired: false)
        }
        let message = parsed.text.isEmpty ? "إذا كانت البيانات مرتبطة بحساب صالح فستصل رسالة الاستعادة إلى البريد خلال دقائق." : parsed.text
        return PasswordResetStart(channel: .email, phoneToken: nil, message: message)
    }

    func finishPhonePasswordReset(token: String, code: String, password: String, confirm: String) async throws -> String {
        let csrf = try await htmlCSRF("reset-password.php", query: [URLQueryItem(name: "phone_token", value: token)])
        let url = try makeURL(path: "reset-password.php")
        let (data, response) = try await formRequest(url: url, fields: [
            "csrf": csrf,
            "phone_token": token,
            "action": "reset",
            "code": code,
            "password": password,
            "confirm": confirm
        ])
        let parsed = htmlMessage(from: data)
        guard !parsed.isError, parsed.text.contains("تم تغيير كلمة المرور") else {
            throw APIError(message: parsed.text.isEmpty ? "تعذر تغيير كلمة المرور." : parsed.text, status: response.statusCode, paymentRequired: false)
        }
        if let cookies = HTTPCookieStorage.shared.cookies { cookies.forEach(HTTPCookieStorage.shared.deleteCookie) }
        AppSession.shared.reset()
        return parsed.text
    }

    func resendPhonePasswordReset(token: String) async throws -> String {
        let csrf = try await htmlCSRF("reset-password.php", query: [URLQueryItem(name: "phone_token", value: token)])
        let url = try makeURL(path: "reset-password.php")
        let (data, response) = try await formRequest(url: url, fields: [
            "csrf": csrf,
            "phone_token": token,
            "action": "resend"
        ])
        let parsed = htmlMessage(from: data)
        if parsed.isError {
            throw APIError(message: parsed.text.isEmpty ? "تعذر إعادة إرسال الرمز." : parsed.text, status: response.statusCode, paymentRequired: false)
        }
        return parsed.text.isEmpty ? "تم طلب رمز جديد إلى WhatsApp." : parsed.text
    }

    func verifyPending(kind: String, code: String) async throws -> JSON {
        let path = kind == "phone" ? "verify-phone.php" : "verify-email.php"
        let csrf = try await htmlCSRF(path)
        _ = try await formRequest(path, fields: ["csrf": csrf, "action": "verify", "code": code])
        return try await bootstrap()
    }

    func resendPending(kind: String) async throws -> String {
        let path = kind == "phone" ? "verify-phone.php" : "verify-email.php"
        let csrf = try await htmlCSRF(path)
        _ = try await formRequest(path, fields: ["csrf": csrf, "action": "resend"])
        return "تم طلب رمز تحقق جديد."
    }

    func bootstrap() async throws -> JSON { try await request("mobile/bootstrap.php") }

    func login(identifier: String, password: String, remember: Bool) async throws -> JSON {
        let result = try await request("api/login.php", method: "POST", body: ["identifier": identifier, "password": password, "remember_me": remember])
        if remember { KeychainVault.shared.set(Data(identifier.utf8), for: "last_identifier") }
        return result
    }

    func grades() async throws -> [JSON] {
        jArray(try await request("api/grades.php", cacheKey: "grades")["grades"])
    }

    func register(_ body: JSON) async throws -> JSON {
        try await request("api/register.php", method: "POST", body: body)
    }

    func heartbeat() async throws {
        _ = try await request("api/session-heartbeat.php")
    }

    func logout() async throws {
        let csrf = AppSession.shared.csrf
        if !csrf.isEmpty { _ = try await request("mobile/logout.php", method: "POST", body: ["csrf": csrf]) }
        AppSession.shared.reset()
        if let cookies = HTTPCookieStorage.shared.cookies { cookies.forEach(HTTPCookieStorage.shared.deleteCookie) }
    }

    private func makeURL(path: String, query: [URLQueryItem] = []) throws -> URL {
        guard var components = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false) else {
            throw APIError(message: "تعذر تكوين عنوان الطلب.", status: 0, paymentRequired: false)
        }
        if !query.isEmpty { components.queryItems = query }
        guard let url = components.url else {
            throw APIError(message: "عنوان الطلب غير صالح.", status: 0, paymentRequired: false)
        }
        return url
    }

    private func csrfToken(from html: String) throws -> String {
        let patterns = [
            #"name=[\"']csrf[\"'][^>]*value=[\"']([^\"']+)[\"']"#,
            #"value=[\"']([^\"']+)[\"'][^>]*name=[\"']csrf[\"']"#
        ]
        for pattern in patterns {
            if let re = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
               let match = re.firstMatch(in: html, range: NSRange(html.startIndex..., in: html)),
               let range = Range(match.range(at: 1), in: html) {
                return String(html[range])
            }
        }
        throw APIError(message: "تعذر قراءة رمز التحقق.", status: 419, paymentRequired: false)
    }

    private func htmlMessage(from data: Data) -> (text: String, isError: Bool) {
        guard let html = String(data: data, encoding: .utf8) else { return ("", false) }
        let pattern = #"<div[^>]*class=[\"'][^\"']*form-message\s+([^\"']+)[\"'][^>]*>(.*?)</div>"#
        guard let re = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive, .dotMatchesLineSeparators]),
              let match = re.firstMatch(in: html, range: NSRange(html.startIndex..., in: html)),
              let typeRange = Range(match.range(at: 1), in: html),
              let textRange = Range(match.range(at: 2), in: html) else {
            return ("", false)
        }
        let type = String(html[typeRange]).lowercased()
        var text = String(html[textRange])
        text = text.replacingOccurrences(of: #"<[^>]+>"#, with: "", options: .regularExpression)
        text = text
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#039;", with: "'")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return (text, type.contains("error") || type.contains("bad"))
    }
}