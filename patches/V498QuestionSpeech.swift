import SwiftUI
@preconcurrency import AVFoundation
import UIKit

// One retained controller for every Listen button. No server, microphone,
// speech-recognition permission, or paid entitlement is needed for playback.
enum V498SpeechText {
    static func clean(_ raw: String) -> String {
        var text = raw.replacingOccurrences(of: "<[^>]+>", with: " ", options: .regularExpression)
        for (entity, replacement) in [("&nbsp;", " "), ("&amp;", "&"), ("&quot;", "\""),
                                      ("&#39;", "'"), ("&lt;", "<"), ("&gt;", ">") ] {
            text = text.replacingOccurrences(of: entity, with: replacement)
        }
        return text.replacingOccurrences(of: "[\\s\u{200B}\u{200E}\u{200F}]+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func language(_ text: String) -> String {
        let arabic = text.unicodeScalars.contains {
            CharacterSet.letters.contains($0) &&
            ((0x0600...0x06FF).contains($0.value) || (0x0750...0x077F).contains($0.value) ||
             (0x08A0...0x08FF).contains($0.value) || (0xFB50...0xFDFF).contains($0.value) ||
             (0xFE70...0xFEFF).contains($0.value))
        }
        return arabic ? "ar-SA" : "en-US"
    }

    static func chunks(_ raw: String, limit: Int = 500) -> [String] {
        var remaining = clean(raw)
        guard !remaining.isEmpty else { return [] }
        let size = max(32, limit)
        var result: [String] = []
        while remaining.count > size {
            let boundary = remaining.index(remaining.startIndex, offsetBy: size)
            let prefix = remaining[..<boundary]
            let split = prefix.lastIndex(where: { $0.isWhitespace || ".!?؟؛،".contains($0) })
            let end = split.map { remaining.index(after: $0) } ?? boundary
            let part = String(remaining[..<end]).trimmingCharacters(in: .whitespacesAndNewlines)
            if !part.isEmpty { result.append(part) }
            remaining = String(remaining[end...]).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if !remaining.isEmpty { result.append(remaining) }
        return result
    }
}

@MainActor
final class V49QuestionSpeech: NSObject, ObservableObject, @preconcurrency AVSpeechSynthesizerDelegate {
    enum Phase { case idle, preparing, speaking }
    static let shared = V49QuestionSpeech()
    @Published private(set) var phase: Phase = .idle
    @Published private(set) var activeID: String?
    @Published private(set) var message: String?
    @Published private(set) var messageID: String?

    private var synthesizer = AVSpeechSynthesizer()
    private var utterance: AVSpeechUtterance?
    private var pending: [String] = []
    private var chunkIndex = 0
    private var retryCount = 0
    private var watchdog: Task<Void, Never>?
    private var releaseTask: Task<Void, Never>?
    private let session = AVAudioSession.sharedInstance()
    private var ownsAudioSession = false

    private override init() {
        super.init()
        configureSynthesizer()
        let center = NotificationCenter.default
        center.addObserver(self, selector: #selector(interrupted(_:)), name: AVAudioSession.interruptionNotification, object: session)
        center.addObserver(self, selector: #selector(routeChanged(_:)), name: AVAudioSession.routeChangeNotification, object: session)
        center.addObserver(self, selector: #selector(mediaReset(_:)), name: AVAudioSession.mediaServicesWereResetNotification, object: session)
        center.addObserver(self, selector: #selector(backgrounded(_:)), name: UIApplication.didEnterBackgroundNotification, object: nil)
    }

    func toggle(_ text: String, id: String) {
        if activeID == id, phase != .idle { stop(); return }
        speak(text, id: id)
    }

    func speak(_ text: String, id: String = "speech") {
        stop()
        releaseTask?.cancel()
        message = nil
        messageID = nil
        pending = V498SpeechText.chunks(text)
        guard !pending.isEmpty else {
            messageID = id
            message = "لا يوجد نص قابل للقراءة."
            return
        }
        activeID = id
        chunkIndex = 0
        retryCount = 0
        // Recreate after cancellation: iOS can leave a stopped synthesizer's
        // queue stalled when stopSpeaking/speak are called in the same turn.
        replaceSynthesizer()
        do {
            try activatePlayback()
            if session.outputVolume < 0.01 {
                messageID = id
                message = "صوت الوسائط مكتوم. ارفع الصوت بأزرار iPhone أثناء الاستماع."
            }
            speakNextChunk()
        } catch {
            fail("تعذر تشغيل الصوت الآن. أغلق أي مكالمة جارية ثم اضغط الاستماع مجددًا.")
        }
    }

    private func activatePlayback() throws {
        releaseTask?.cancel()
        // Playback is audible with the Ring/Silent switch on. Never use a
        // recording category, and respect the user's Bluetooth/headphone route.
        try session.setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
        try session.setActive(true)
        ownsAudioSession = true
    }

    private func configureSynthesizer() {
        synthesizer.usesApplicationAudioSession = true
        synthesizer.delegate = self
    }

    private func replaceSynthesizer() {
        synthesizer.delegate = nil
        synthesizer.stopSpeaking(at: .immediate)
        utterance = nil
        synthesizer = AVSpeechSynthesizer()
        configureSynthesizer()
    }

    static func availableVoice(for language: String, preferCompact: Bool = false) -> AVSpeechSynthesisVoice? {
        let prefix = String(language.prefix(2))
        let voices = AVSpeechSynthesisVoice.speechVoices().filter { $0.language.hasPrefix(prefix) }
        if preferCompact, let compact = voices.first(where: { $0.quality == .default }) { return compact }
        return AVSpeechSynthesisVoice(language: language) ?? voices.first
    }

    private func speakNextChunk() {
        guard pending.indices.contains(chunkIndex), activeID != nil else { stop(); return }
        let text = pending[chunkIndex]
        guard let voice = Self.availableVoice(for: V498SpeechText.language(text), preferCompact: retryCount > 0) else {
            fail("الصوت المطلوب غير مثبت على iPhone. نزّل صوت العربية من الإعدادات ← تسهيلات الاستخدام ← المحتوى المنطوق (أو القراءة والنطق) ← الأصوات، ثم حاول مجددًا.")
            return
        }
        let next = AVSpeechUtterance(string: text)
        next.voice = voice
        next.volume = 1
        next.rate = AVSpeechUtteranceDefaultSpeechRate * 0.88
        next.preUtteranceDelay = 0
        next.postUtteranceDelay = 0.05
        utterance = next
        phase = .preparing
        synthesizer.speak(next)
        watchdog?.cancel()
        watchdog = Task { @MainActor [weak self] in
            do { try await Task.sleep(nanoseconds: 8_000_000_000) } catch { return }
            guard let self, self.phase == .preparing else { return }
            if self.retryCount == 0 {
                self.retryCount = 1
                self.replaceSynthesizer()
                do { try self.activatePlayback(); self.speakNextChunk() }
                catch { self.fail("تعذر تهيئة الصوت. حاول مجددًا بعد إنهاء المكالمة أو تغيير مخرج الصوت.") }
            } else {
                self.fail("لم يستجب صوت الجهاز. تحقق من تنزيل الصوت العربي من تسهيلات الاستخدام، ثم حاول مجددًا.")
            }
        }
    }

    func stop(ifOwnedBy id: String? = nil) {
        if let id, id != activeID { return }
        watchdog?.cancel()
        watchdog = nil
        utterance = nil // Ignore delayed callbacks from an older utterance.
        synthesizer.stopSpeaking(at: .immediate)
        pending = []
        activeID = nil
        phase = .idle
        releaseAudioSession()
    }

    private func releaseAudioSession() {
        releaseTask?.cancel()
        releaseTask = Task { @MainActor [weak self] in
            do { try await Task.sleep(nanoseconds: 200_000_000) } catch { return }
            guard let self, self.phase == .idle, self.ownsAudioSession else { return }
            do {
                try self.session.setActive(false, options: [.notifyOthersOnDeactivation])
                self.ownsAudioSession = false
            } catch {
                // A system interruption may already have deactivated the session.
                // The next user request always reconfigures it explicitly.
            }
        }
    }

    private func fail(_ text: String) {
        let owner = activeID
        stop()
        messageID = owner
        message = text
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didStart utterance: AVSpeechUtterance) {
        guard self.synthesizer === synthesizer, self.utterance === utterance else { return }
        watchdog?.cancel()
        phase = .speaking
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        guard self.synthesizer === synthesizer, self.utterance === utterance else { return }
        watchdog?.cancel()
        chunkIndex += 1
        retryCount = 0
        speakNextChunk()
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        guard self.synthesizer === synthesizer, self.utterance === utterance else { return }
        fail("توقف الاستماع. اضغط زر الاستماع لإعادة التشغيل.")
    }

    @objc nonisolated private func interrupted(_ notification: Notification) {
        let began = (notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt) == AVAudioSession.InterruptionType.began.rawValue
        guard began else { return }
        Task { @MainActor [weak self] in
            guard let self, self.activeID != nil else { return }
            self.fail("توقف الاستماع بسبب مكالمة أو مقاطعة صوتية. اضغط الاستماع عند الانتهاء.")
        }
    }

    @objc nonisolated private func routeChanged(_ notification: Notification) {
        let unplugged = (notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt) == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue
        guard unplugged else { return }
        Task { @MainActor [weak self] in
            guard let self, self.activeID != nil else { return }
            self.fail("تم فصل السماعة. اضغط الاستماع للتشغيل على مخرج الصوت الحالي.")
        }
    }

    @objc nonisolated private func mediaReset(_ notification: Notification) {
        Task { @MainActor [weak self] in
            guard let self else { return }
            if self.activeID != nil { self.fail("أُعيد تشغيل خدمة الصوت. اضغط الاستماع مجددًا.") }
            self.replaceSynthesizer()
        }
    }

    @objc nonisolated private func backgrounded(_ notification: Notification) {
        Task { @MainActor [weak self] in self?.stop() }
    }
}

struct V498SpeechButton: View {
    let text: String
    let title: String
    let speechID: String
    @ObservedObject private var speech = V49QuestionSpeech.shared
    private var active: Bool { speech.activeID == speechID && speech.phase != .idle }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button { speech.toggle(text, id: speechID) } label: {
                HStack(spacing: 8) {
                    if active && speech.phase == .preparing {
                        ProgressView().controlSize(.small)
                    } else {
                        Image(systemName: active ? "stop.circle.fill" : "speaker.wave.2.fill")
                    }
                    Text(active ? (speech.phase == .preparing ? "تجهيز الصوت… إيقاف" : "إيقاف الاستماع") : title)
                }
                .font(.subheadline)
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .foregroundStyle(Color.murshidBlue)
            .accessibilityIdentifier(speechID)
            .accessibilityValue(active ? "قيد التشغيل" : "متوقف")
            if speech.messageID == speechID, let message = speech.message {
                Text(message).font(.caption).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier(speechID + "-message")
            }
        }
        .onDisappear { speech.stop(ifOwnedBy: speechID) }
    }
}
