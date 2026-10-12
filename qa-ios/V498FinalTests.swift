import XCTest
@preconcurrency import AVFoundation
@testable import MurshidWizari

final class V498FinalTests: XCTestCase {
    func testSpeechCleansHTMLAndPreservesArabicDiacritics() {
        XCTAssertEqual(V498SpeechText.clean(" <b>قُرْآنٌ</b>&nbsp; كريم\u{200F}\n "), "قُرْآنٌ كريم")
        XCTAssertEqual(V498SpeechText.clean("&quot;A&amp;B&quot;"), "\"A&B\"")
    }
    private func voice(_ id: String, language: String = "ar-001", gender: AVSpeechSynthesisVoiceGender = .female,
                       quality: AVSpeechSynthesisVoiceQuality = .default, restricted: Bool = false) -> V499VoiceCandidate {
        V499VoiceCandidate(identifier: id, language: language, gender: gender, quality: quality, restricted: restricted)
    }
    func testHighestQualityFemaleArabicVoiceWins() {
        let voices = [voice("male", gender: .male, quality: .premium),
                      voice("compact"), voice("enhanced", quality: .enhanced), voice("premium", quality: .premium)]
        XCTAssertEqual(V499ArabicNarration.preferredIdentifier(in: voices), "premium")
        XCTAssertEqual(V499ArabicNarration.preferredIdentifier(in: Array(voices.reversed())), "premium")
    }
    func testNeverFallsBackToMaleEnglishUnknownOrNoveltyVoice() {
        let unsupported = [voice("male", gender: .male), voice("unknown", gender: .unspecified),
                           voice("english", language: "en-US"), voice("dialect", language: "ar-EG"),
                           voice("personal", restricted: true)]
        XCTAssertNil(V499ArabicNarration.preferredIdentifier(in: unsupported))
        XCTAssertNil(V499ArabicNarration.preferredIdentifier(in: unsupported, preferCompact: true))
        XCTAssertNil(V499ArabicNarration.preferredIdentifier(in: []))
    }
    func testArabicWorldLocaleWinsQualityTieAndRecoveryStaysFemale() {
        let voices = [voice("sa", language: "ar-SA", quality: .enhanced),
                      voice("world", quality: .enhanced), voice("basic")]
        XCTAssertEqual(V499ArabicNarration.preferredIdentifier(in: voices), "world")
        XCTAssertEqual(V499ArabicNarration.preferredIdentifier(in: voices, preferCompact: true), "basic")
        XCTAssertEqual(V499ArabicNarration.preferredIdentifier(in: voices, excluding: "world"), "sa")
        XCTAssertEqual(V499ArabicNarration.localeRank("ar_SA"), 1)
    }
    func testArabicDiacriticsAndQuranicMarksRemainIntact() {
        let text = "لَوْ أَنْزَلْنَا هَٰذَا الْقُرْآنَ عَلَىٰ جَبَلٍ"
        XCTAssertEqual(V498SpeechText.clean(text), text)
        XCTAssertEqual(V498SpeechText.clean("قُرْ\u{200F}آنٌ"), "قُرْآنٌ")
        XCTAssertEqual(V498SpeechText.clean("الـعَرَبِيَّةُ"), "العَرَبِيَّةُ")
    }
    func testSentenceBoundaryAndDecimalNumbersArePreserved() {
        let text = "هذه جملة تامة. " + Array(repeating: "القيمة 3.14 و٣٫١٤ و12:30 و1,234 صحيحة", count: 6).joined(separator: " ")
        let chunks = V498SpeechText.chunks(text, limit: 60)
        XCTAssertEqual(chunks.first, "هذه جملة تامة.")
        XCTAssertEqual(chunks.joined(separator: " "), text)
        XCTAssertTrue(chunks.allSatisfy { $0.count <= 60 })
    }
    func testNarrationPausesFollowArabicPunctuation() {
        XCTAssertEqual(V499ArabicNarration.pauseAfter("هل فهمتَ؟»"), 0.24)
        XCTAssertEqual(V499ArabicNarration.pauseAfter("أولًا،"), 0.14)
        XCTAssertEqual(V499ArabicNarration.pauseAfter("والمعنى"), 0.04)
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
        if V49QuestionSpeech.availableVoice() != nil {
            XCTAssertEqual(AVAudioSession.sharedInstance().category, .playback)
            XCTAssertEqual(AVAudioSession.sharedInstance().mode, .spokenAudio)
        } else {
            XCTAssertEqual(speech.phase, .idle)
            XCTAssertEqual(speech.message, V499ArabicNarration.unavailableMessage)
            XCTAssertEqual(speech.messageID, "question")
        }
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
    func testFemaleArabicVoiceProducesNonSilentPCM() throws {
        V49QuestionSpeech.shared.stop()
        for voice in AVSpeechSynthesisVoice.speechVoices().filter({ $0.language.hasPrefix("ar") }) {
            print("V499_ARABIC_CATALOG id=\(voice.identifier) language=\(voice.language) gender=\(voice.gender.rawValue) quality=\(voice.quality.rawValue)")
        }
        guard let voice = V49QuestionSpeech.availableVoice() else {
            throw XCTSkip("No female Arabic voice installed on the simulator. The app shows setup guidance and never substitutes a male voice.")
        }
        XCTAssertEqual(voice.gender, .female)
        XCTAssertNotNil(V499ArabicNarration.localeRank(voice.language))
        let rendered = expectation(description: "Arabic TTS returns audible PCM")
        let probe = PCMProbe(expectation: rendered)
        let synth = AVSpeechSynthesizer()
        synth.usesApplicationAudioSession = false
        let text = V499ArabicNarration.utterance("لَوْ أَنْزَلْنَا هَٰذَا الْقُرْآنَ عَلَىٰ جَبَلٍ لَرَأَيْتَهُ خَاشِعًا مِنْ خَشْيَةِ اللَّهِ.", voice: voice, first: true)
        XCTAssertEqual(text.pitchMultiplier, 1.0)
        XCTAssertFalse(text.prefersAssistiveTechnologySettings)
        XCTAssertEqual(text.rate, AVSpeechUtteranceDefaultSpeechRate * 0.88, accuracy: 0.001)
        synth.write(text) { buffer in
            if let pcm = buffer as? AVAudioPCMBuffer { probe.consume(pcm) }
        }
        wait(for: [rendered], timeout: 35)
        synth.stopSpeaking(at: .immediate)
        XCTAssertTrue(probe.didRender)
        print("V499_FEMALE_ARABIC_PCM_NONZERO=\(probe.didRender) voice=\(voice.identifier) gender=\(voice.gender.rawValue)")
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

