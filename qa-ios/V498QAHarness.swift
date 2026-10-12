#if V498_QA
import SwiftUI
import Foundation

// Compiled ONLY into simulator QA. No production account or network writes.
final class V498QAURLProtocol: URLProtocol, @unchecked Sendable {
    private static let lock = NSLock()
    nonisolated(unsafe) private static var fixtureAds: [JSON] = []
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
        let data = try! JSONSerialization.data(withJSONObject: response)
        let http = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: "HTTP/1.1",
                                   headerFields: ["Content-Type": "application/json", "Cache-Control": "no-store"])!
        client?.urlProtocol(self, didReceive: http, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}

struct V498QATools: ViewModifier {
    @State private var showAudio = false
    @State private var showSocial = false
    @State private var showNarratorSettings = false
    func body(content: Content) -> some View {
        content
            .safeAreaInset(edge: .top) {
                HStack {
                    Button("QA إعلان") { V498QAURLProtocol.publishFixture() }
                        .accessibilityIdentifier("V498_QA_PUBLISH_FIXTURE")
                    Button("QA صوت") {
                        seedReview()
                        showAudio = true
                    }.accessibilityIdentifier("V498_QA_AUDIO")
                    Button("QA روابط") { showSocial = true }
                        .accessibilityIdentifier("V498_QA_SOCIAL")
                    Button("QA المعلّمة") { showNarratorSettings = true }
                        .accessibilityIdentifier("V498_QA_NARRATOR_SETTINGS")
                }.font(.caption).padding(5).background(.thinMaterial)
            }
            .sheet(isPresented: $showAudio) {
                NavigationStack { V49RecallView(questionIDs: [98765001]) }
                    .environment(\.layoutDirection, .rightToLeft)
            }
            .sheet(isPresented: $showSocial) {
                NavigationStack {
                    VStack {
                        V498SocialLinksFooter()
                        Spacer()
                    }.padding(20).navigationTitle("التواصل الاجتماعي")
                }.environment(\.layoutDirection, .rightToLeft)
            }
            .sheet(isPresented: $showNarratorSettings) {
                NavigationStack { V498NarrationSettingsView() }
                    .environment(\.layoutDirection, .rightToLeft)
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
#endif
