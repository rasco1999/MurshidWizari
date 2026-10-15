#if V498_QA
import SwiftUI
import Foundation

// Compiled ONLY into simulator QA. No production account or network writes.
final class V498QAURLProtocol: URLProtocol, @unchecked Sendable {
    private static let lock = NSLock()
    nonisolated(unsafe) private static var fixtureAds: [JSON] = []
    struct VerificationState: Sendable {
        var kind: String? = nil
        var mode = "normal"
        var calls: [String] = []
        var posts = 0
        var bootstraps = 0
    }
    nonisolated(unsafe) private static var verification = VerificationState()
    static func configureVerification(kind: String?, mode: String = "normal") {
        lock.lock(); defer { lock.unlock() }
        verification = VerificationState(kind: kind, mode: mode)
    }
    static func verificationSnapshot() -> VerificationState {
        lock.lock(); defer { lock.unlock() }
        return verification
    }
    private static func requestBody(_ request: URLRequest) -> JSON {
        var data = request.httpBody ?? Data()
        if data.isEmpty, let stream = request.httpBodyStream {
            stream.open(); defer { stream.close() }
            var buffer = [UInt8](repeating: 0, count: 1024)
            while data.count < 65536 {
                let count = stream.read(&buffer, maxLength: buffer.count)
                if count <= 0 { break }
                data.append(contentsOf: buffer.prefix(count))
            }
        }
        return (try? JSONSerialization.jsonObject(with: data)) as? JSON ?? [:]
    }
    private static func verificationReply(_ request: URLRequest) -> (JSON, Int)? {
        lock.lock(); defer { lock.unlock() }
        guard let kind = verification.kind else { return nil }
        let path = request.url?.path ?? ""
        let method = request.httpMethod ?? "GET"
        if path == "/verify-email.php" || path == "/verify-phone.php" {
            verification.calls.append(method + " " + path)
            return (["ok": false, "message": "Legacy HTML intentionally unavailable"], 403)
        }
        if path == "/mobile/bootstrap.php" {
            verification.bootstraps += 1
            if verification.mode == "bootstrap_unavailable" {
                return (["ok": false, "message": "Fixture bootstrap unavailable"], 503)
            }
            return nil
        }
        guard path == "/api/verify-\(kind).php" else { return nil }
        verification.calls.append(method + " " + path)
        if method == "GET" {
            if verification.mode == "expired_session" {
                return (["ok": false, "message": "انتهت جلسة التأكيد. سجّل الدخول بنفس الحساب."], 409)
            }
            return (["ok": true, "pending": true, "csrf": "fixture-\(kind)-csrf"], 200)
        }
        verification.posts += 1
        let body = requestBody(request)
        if body["csrf"] as? String != "fixture-\(kind)-csrf" || verification.mode == "csrf_expired" {
            return (["ok": false, "message": "انتهت صلاحية الطلب."], 419)
        }
        if body["action"] as? String == "resend" {
            if verification.mode == "cooldown" {
                return (["ok": false, "retry_after": 60, "message": "يمكنك إعادة إرسال الرمز بعد 60 ثانية."], 429)
            }
            return (["ok": true, "message": "أرسلنا رمز تحقق جديدًا."], 200)
        }
        if body["code"] as? String != "123456" {
            return (["ok": false, "message": "رمز التحقق غير صحيح."], 422)
        }
        return (verification.mode == "missing_receipt" ? ["ok": true] : ["ok": true, "verified": true], 200)
    }
    static func publishFixture() {
        lock.lock(); defer { lock.unlock() }
        let id = 98000 + fixtureAds.count
        fixtureAds.append(["id": id, "school": "مدرسة الاختبار \(id)", "text": "إعلان اختباري محلي للتحقق من التحديث التلقائي."])
    }
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        Self.lock.lock()
        let ads = Self.fixtureAds
        Self.lock.unlock()
        let path = request.url?.path ?? ""
        var response: JSON = ["ok": true, "ads": [], "insights": [:], "weekly": [:], "unread": 0,
                              "eligible_new_student": false, "enabled": false, "notifications": []]
        if path.hasSuffix("bootstrap.php") {
            response = ["ok": true, "csrf": "local-qa-only", "user": ["id": 987654, "name": "طالب اختبار محلي", "grade": "السادس العلمي", "grade_id": 1],
                        "subjects": [], "plans": [], "ads": ads, "subscribed": false,
                        "free_limit": 15, "free_used": 0, "free_remaining": 15, "stats": [:]]
        }
        let injected = Self.verificationReply(request)
        if let injected { response = injected.0 }
        let data = try! JSONSerialization.data(withJSONObject: response)
        let http = HTTPURLResponse(url: request.url!, statusCode: injected?.1 ?? 200, httpVersion: "HTTP/1.1",
                                   headerFields: ["Content-Type": "application/json", "Cache-Control": "no-store"])!
        client?.urlProtocol(self, didReceive: http, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}

struct V498QATools: ViewModifier {
    @State private var showSocial = false
    @State private var showMatching = false
    @State private var verification: VerificationSheet?
    @ObservedObject private var qaSession = AppSession.shared
    private struct VerificationSheet: Identifiable { let kind: String; var id: String { kind } }
    func body(content: Content) -> some View {
        content
            .onAppear { if ProcessInfo.processInfo.arguments.contains("V53_QA_MATCHING") { showMatching = true } }
            .sheet(isPresented: $showMatching) { V53MatchingFixtureView() }
            .safeAreaInset(edge: .top) {
                HStack {
                    Button("QA إعلان") { V498QAURLProtocol.publishFixture() }
                        .accessibilityIdentifier("V498_QA_PUBLISH_FIXTURE")
                    Button("QA روابط") { showSocial = true }
                        .accessibilityIdentifier("V498_QA_SOCIAL")
                    Button("QA بريد") {
                        V498QAURLProtocol.configureVerification(kind: "email")
                        verification = VerificationSheet(kind: "email")
                    }.accessibilityIdentifier("V498_QA_VERIFY_EMAIL")
                    Button("QA هاتف") {
                        V498QAURLProtocol.configureVerification(kind: "phone")
                        verification = VerificationSheet(kind: "phone")
                    }.accessibilityIdentifier("V498_QA_VERIFY_PHONE")
                }.font(.caption).padding(5).background(.thinMaterial)
            }
            .sheet(isPresented: $showSocial) {
                NavigationStack {
                    VStack {
                        V498SocialLinksFooter()
                        Spacer()
                    }.padding(20).navigationTitle("التواصل الاجتماعي")
                }.environment(\.layoutDirection, .rightToLeft)
            }
            .sheet(item: $verification, onDismiss: { V498QAURLProtocol.configureVerification(kind: nil) }) { item in
                NavigationStack {
                    VerificationView(kind: item.kind, destination: "اختبار محلي", initialMessage: "رمز تأكيد اصطناعي للاختبار فقط.", onCancel: { verification = nil })
                        .onChange(of: qaSession.lastSync) { _ in verification = nil }
                }.environment(\.layoutDirection, .rightToLeft)
            }

    }
    private func seedReview() {
        let user = UserSummary(id: 987654, name: "طالب اختبار محلي", grade: "السادس العلمي", gradeID: 1)
        V49StudyStore.shared.bind(to: user)
        let q = Question(["id": 98765001, "type": "explain", "text": "اشرح قوله تعالى: لو أنزلنا هذا القرآن على جبل لرأيته خاشعًا متصدعًا من خشية الله.",
                          "correct_answer": "لو أنزل الله القرآن على جبل مع ما فيه من قوة وشدة لخشع وتصدع من خشية الله.",
                          "explanation": "في الآية حث للإنسان على تدبر القرآن وفهم معانيه والعمل به."])
        V49StudyStore.shared.rememberGraded(q, subject: Subject(["id": 1, "name": "التربية الإسلامية"]),
                                           topic: Topic(["id": 1, "name": "الفهم والشرح"]), answer: "إجابة اختبار محلية", correct: true)
    }
}
struct V53MatchingFixtureView: View {
    @State private var values: [String: String] = [:]
    private let definition: JSON = ["left": ["1":"x-ray", "2":"security", "3":"conveyor", "4":"crime", "5":"metal", "6":"Speeding"],
        "right": ["a":"camera", "b":"belt", "c":"machine", "d":"detector", "e":"ticket", "f":"scene"]]
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    Text("صِل كل عنصر من القائمة A بما يناسبه من القائمة B.")
                    V53MatchingInput(definition: definition, values: $values)
                    Button("تسليم الإجابة") { }
                        .accessibilityIdentifier("v53-match-submit")
                        .disabled(!V53MatchingRules.complete(definition, values))
                }.padding(16)
            }.navigationTitle("التوصيل التفاعلي")
        }.environment(\.layoutDirection, .rightToLeft)
    }
}

#endif
