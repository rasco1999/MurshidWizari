import XCTest
@preconcurrency import AVFoundation
@testable import MurshidWizari

final class V498FinalTests: XCTestCase {
    func testSpeechCleansHTMLAndPreservesArabicDiacritics() {
        XCTAssertEqual(V498SpeechText.clean(" <b>قُرْآنٌ</b>&nbsp; كريم\u{200F}\n "), "قُرْآنٌ كريم")
        XCTAssertEqual(V498SpeechText.clean("&quot;A&amp;B&quot;"), "\"A&B\"")
    }
    func testArabicAndEnglishLanguageSelection() {
        XCTAssertEqual(V498SpeechText.language("قُرْآنٌ كريم 123"), "ar-SA")
        XCTAssertEqual(V498SpeechText.language("Hello ١٢٣!"), "en-US")
        XCTAssertEqual(V498SpeechText.language("ﻻ"), "ar-SA")
    }
    func testLongExplanationIsNotTruncated() {
        let raw = Array(repeating: "تفسير الآية وفهم معناها والعمل بها.", count: 200).joined(separator: " ")
        let chunks = V498SpeechText.chunks(raw)
        XCTAssertGreaterThan(raw.count, 3200)
        XCTAssertGreaterThan(chunks.count, 1)
        XCTAssertEqual(chunks.joined(separator: " "), V498SpeechText.clean(raw))
        XCTAssertTrue(chunks.allSatisfy { !$0.isEmpty && $0.count <= 500 })
        XCTAssertTrue(V498SpeechText.chunks(" \n ").isEmpty)
    }
    func testUnbrokenTextChunksRemainComplete() {
        let text = String(repeating: "ع", count: 1251)
        XCTAssertEqual(V498SpeechText.chunks(text).joined(), text)
    }

    private func row(_ id: Int, text: String = "إعلان جديد") -> JSON {
        ["id": id, "school": "مدرسة الأمل", "text": text]
    }
    func testInvalidAndDuplicateAdsAreExcluded() {
        let rows = [row(1), row(1), row(0), ["id": 2, "school": "", "text": "A"],
                    ["id": 3, "school": "S", "text": "A", "active": false]]
        XCTAssertEqual(V498SchoolAd.published(rows).map(\.id), [1])
    }
    func testRevisionsIncludeChangesToPublishedContent() {
        XCTAssertEqual(V498SchoolAd(row(1))?.revision, V498SchoolAd(row(1))?.revision)
        XCTAssertNotEqual(V498SchoolAd(row(1))?.revision, V498SchoolAd(row(1, text: "تعديل"))?.revision)
        XCTAssertNotEqual(V498SchoolAd(row(1))?.revision, V498SchoolAd(row(2))?.revision)
    }
    func testReadingAndWithdrawalUpdateUnreadCount() {
        let first = V498SchoolAd(row(1))!
        let second = V498SchoolAd(row(2))!
        var state = V498AdReadState()
        XCTAssertEqual(state.unread(in: [first]), 1)
        state.markRead([first])
        XCTAssertEqual(state.unread(in: [first, second]), 1)
        XCTAssertEqual(state.unread(in: [first]), 0)
        XCTAssertEqual(state.unread(in: []), 0)
        XCTAssertEqual(V498AdReadState(seen: Array(state.seen)).unread(in: [first, second]), 1)
    }
    @MainActor
    func testBadgesPersistAndAccountsAreIsolated() {
        let name = "V498-QA-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let store = V498SchoolAdsStore(defaults: defaults)
        store.bind(userID: 1)
        store.ingest([row(1)], for: 1)
        XCTAssertEqual(store.unreadCount, 1)
        store.setViewingAds(true)
        XCTAssertEqual(store.unreadCount, 0)
        store.setViewingAds(false)
        store.ingest([row(1), row(2)], for: 1)
        XCTAssertEqual(store.unreadCount, 1)
        let restored = V498SchoolAdsStore(defaults: defaults)
        restored.bind(userID: 1)
        restored.ingest([row(1), row(2)], for: 1)
        XCTAssertEqual(restored.unreadCount, 1)
        restored.bind(userID: 2)
        XCTAssertTrue(restored.ads.isEmpty)
        restored.ingest([row(1)], for: 2)
        XCTAssertEqual(restored.unreadCount, 1)
        restored.ingest([row(3)], for: 1) // stale account response
        XCTAssertEqual(restored.ads.map(\.id), [1])
        restored.bind(userID: 0)
        XCTAssertEqual(restored.unreadCount, 0)
        XCTAssertTrue(restored.ads.isEmpty)
    }
    @MainActor
    func testArrivalWhileAdsOpenIsReadWithoutStaleBadge() {
        let name = "V498-QA-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let store = V498SchoolAdsStore(defaults: defaults)
        store.bind(userID: 1)
        store.setViewingAds(true)
        store.ingest([row(1)], for: 1)
        XCTAssertEqual(store.unreadCount, 0)
        XCTAssertEqual(store.ads.count, 1)
        store.setViewingAds(false)
        store.ingest([row(1, text: "إعلان محدّث")], for: 1)
        XCTAssertEqual(store.unreadCount, 1)
    }
    @MainActor
    func testNetworkFailureDoesNotClearExistingUnreadAd() async {
        let name = "V498-QA-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let store = V498SchoolAdsStore(defaults: defaults, fetchAds: { throw URLError(.notConnectedToInternet) })
        store.bind(userID: 1)
        store.ingest([row(1)], for: 1)
        await store.refresh()
        XCTAssertEqual(store.unreadCount, 1)
        XCTAssertEqual(store.ads.count, 1)
        XCTAssertFalse(store.error.isEmpty)
        XCTAssertFalse(store.isLoading)
    }
    @MainActor
    func testAccountChangedDuringRequestRejectsOldResponse() async {
        let name = "V498-QA-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let store = V498SchoolAdsStore(defaults: defaults, fetchAds: {
            try await Task.sleep(nanoseconds: 40_000_000)
            return ["user": ["id": 1], "ads": [["id": 1, "school": "A", "text": "B"]]]
        })
        store.bind(userID: 1)
        let task = Task { await store.refresh() }
        await Task.yield()
        store.bind(userID: 2)
        await task.value
        XCTAssertTrue(store.ads.isEmpty)
        XCTAssertEqual(store.unreadCount, 0)
    }
    @MainActor
    func testSpeechConfiguresPlaybackAndRapidSwitchCanStop() {
        let speech = V49QuestionSpeech.shared
        speech.speak("هذا سؤال طويل لاختبار تشغيل الصوت.", id: "question")
        XCTAssertEqual(AVAudioSession.sharedInstance().category, .playback)
        XCTAssertEqual(AVAudioSession.sharedInstance().mode, .spokenAudio)
        speech.speak("هذا شرح الإجابة.", id: "answer")
        speech.stop(ifOwnedBy: "question")
        if speech.phase != .idle { XCTAssertEqual(speech.activeID, "answer") }
        speech.stop()
        XCTAssertEqual(speech.phase, .idle)
        XCTAssertNil(speech.activeID)
        speech.speak(" ", id: "empty")
        XCTAssertEqual(speech.phase, .idle)
        XCTAssertNotNil(speech.message)
    }
    @MainActor
    func testArabicVoiceProducesNonSilentPCM() throws {
        V49QuestionSpeech.shared.stop()
        guard let voice = V49QuestionSpeech.availableVoice(for: "ar-SA") else {
            throw XCTSkip("No Arabic voice asset installed in this CI simulator; physical-device audio QA still required")
        }
        let rendered = expectation(description: "Arabic TTS returns audible PCM")
        let probe = PCMProbe(expectation: rendered)
        let synth = AVSpeechSynthesizer()
        synth.usesApplicationAudioSession = false
        let text = AVSpeechUtterance(string: "لو أنزلنا هذا القرآن على جبل لرأيته خاشعًا من خشية الله.")
        text.voice = voice
        synth.write(text) { buffer in
            if let pcm = buffer as? AVAudioPCMBuffer { probe.consume(pcm) }
        }
        wait(for: [rendered], timeout: 35)
        synth.stopSpeaking(at: .immediate)
        XCTAssertTrue(probe.didRender)
        print("V498_ARABIC_PCM_NONZERO=\(probe.didRender) voice=\(voice.language)")
    }
}

private final class PCMProbe: @unchecked Sendable {
    private let lock = NSLock()
    private let expectation: XCTestExpectation
    private var rendered = false
    init(expectation: XCTestExpectation) { self.expectation = expectation }
    var didRender: Bool { lock.lock(); defer { lock.unlock() }; return rendered }
    func consume(_ pcm: AVAudioPCMBuffer) {
        guard pcm.frameLength > 0 else { return }
        var nonzero = false
        if let samples = pcm.floatChannelData {
            nonzero = (0..<Int(pcm.frameLength)).contains { abs(samples[0][$0]) > 0.00001 }
        } else if let samples = pcm.int16ChannelData {
            nonzero = (0..<Int(pcm.frameLength)).contains { samples[0][$0] != 0 }
        }
        lock.lock(); defer { lock.unlock() }
        if nonzero && !rendered { rendered = true; expectation.fulfill() }
    }
}
