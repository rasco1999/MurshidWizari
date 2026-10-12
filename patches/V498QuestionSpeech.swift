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
        // Remove formatting controls, not Arabic vowels, shadda or Quranic marks.
        // A bidi control inside a word must not introduce a spoken word break.
        text = text.replacingOccurrences(of: "[\u{061C}\u{0640}\u{200B}\u{200E}\u{200F}\u{202A}-\u{202E}\u{2066}-\u{2069}\u{FEFF}]", with: "", options: .regularExpression)
        return text.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func chunks(_ raw: String, limit: Int = 500) -> [String] {
        var remaining = clean(raw)
        guard !remaining.isEmpty else { return [] }
        let size = max(32, limit)
        var result: [String] = []
        while remaining.count > size {
            let boundary = remaining.index(remaining.startIndex, offsetBy: size)
            let prefix = remaining[..<boundary]
            // Prefer the end of a complete sentence, then a clause. Do not
            // split a decimal such as 3.14 or ٣٫١٤ at its decimal separator.
            let sentence = prefix.indices.last(where: { index in
                guard ".!?؟؛".contains(remaining[index]) else { return false }
                let next = remaining.index(after: index)
                return next == remaining.endIndex || remaining[next].isWhitespace
            })
            let clause = prefix.indices.last(where: { index in
                guard ",،:".contains(remaining[index]) else { return false }
                let next = remaining.index(after: index)
                return next == remaining.endIndex || remaining[next].isWhitespace
            })
            let split = sentence ?? clause ?? prefix.lastIndex(where: { $0.isWhitespace })
            let end = split.map { remaining.index(after: $0) } ?? boundary
            let part = String(remaining[..<end]).trimmingCharacters(in: .whitespacesAndNewlines)
            if !part.isEmpty { result.append(part) }
            remaining = String(remaining[end...]).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if !remaining.isEmpty { result.append(remaining) }
        return result
    }
}

// Explicit policy instead of relying on the device's default Arabic voice.
// Gender comes from the system catalog, never from pitch shifting or a guessed name.
struct V499VoiceCandidate {
    let identifier: String
    let language: String
    let gender: AVSpeechSynthesisVoiceGender
    let quality: AVSpeechSynthesisVoiceQuality
    var restricted = false
}

enum V499ArabicNarration {
    static let preferredVoiceKey = "murshid_female_arabic_voice_id"
    static let speedKey = "murshid_female_arabic_voice_speed"
    static let defaultSpeed = 0.88
    static let unavailableMessage = "لا يوجد صوت عربي أنثوي متاح للتطبيق على هذا الجهاز. افتح الإعدادات ← تسهيلات الاستخدام ← القراءة والنطق (أو المحتوى المنطوق) ← الأصوات ← العربية، واختر صوتًا أنثويًا ونزّل جودته المحسّنة إن توفرت، ثم عُد واضغط الاستماع."

    static func localeRank(_ language: String) -> Int? {
        switch language.lowercased().replacingOccurrences(of: "_", with: "-") {
        case "ar-001": return 0 // Arabic (World), preferred for formal narration.
        case "ar-sa": return 1
        case "ar": return 2
        default: return nil
        }
    }

    static func preferredIdentifier(in candidates: [V499VoiceCandidate], preferCompact: Bool = false,
                                    excluding identifier: String? = nil, preferred preferredID: String? = nil) -> String? {
        let eligible = candidates.filter {
            $0.gender == .female && !$0.restricted && localeRank($0.language) != nil && $0.identifier != identifier
        }
        // An explicitly selected female Arabic voice wins; after a playback
        // failure the excluded voice cannot be reselected as the fallback.
        if let preferredID, eligible.contains(where: { $0.identifier == preferredID }) {
            return preferredID
        }
        return eligible.sorted { a, b in
            if preferCompact, (a.quality == .default) != (b.quality == .default) {
                return a.quality == .default
            }
            if a.quality != b.quality { return a.quality.rawValue > b.quality.rawValue }
            let left = localeRank(a.language)!, right = localeRank(b.language)!
            if left != right { return left < right }
            return a.identifier < b.identifier
        }.first?.identifier
    }

    static func pauseAfter(_ text: String) -> TimeInterval {
        let last = text.last(where: { !$0.isWhitespace && !"»”\"')]}".contains($0) })
        guard let last else { return 0 }
        if ".!?؟…".contains(last) { return 0.24 }
        if ",،؛:".contains(last) { return 0.14 }
        return 0.04
    }

    static func normalizedSpeed(_ requested: Double) -> Float {
        Float(min(1.10, max(0.75, requested)))
    }

    static func utterance(_ text: String, voice: AVSpeechSynthesisVoice, first: Bool) -> AVSpeechUtterance {
        let result = AVSpeechUtterance(string: text)
        result.voice = voice
        let savedSpeed = UserDefaults.standard.object(forKey: speedKey) as? Double ?? defaultSpeed
        result.rate = AVSpeechUtteranceDefaultSpeechRate * normalizedSpeed(savedSpeed)
        result.pitchMultiplier = 1.0 // Preserve the female voice's natural timbre.
        result.volume = 1
        result.prefersAssistiveTechnologySettings = false
        result.preUtteranceDelay = first ? 0.06 : 0
        result.postUtteranceDelay = pauseAfter(text)
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
    private var selectedVoice: AVSpeechSynthesisVoice?

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
        // One Arabic female voice is retained for the entire reading, even
        // when a later chunk contains only numbers or a foreign abbreviation.
        selectedVoice = Self.availableVoice()
        guard selectedVoice != nil else {
            fail(V499ArabicNarration.unavailableMessage)
            return
        }
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

    static func availableVoice(preferCompact: Bool = false, excluding identifier: String? = nil) -> AVSpeechSynthesisVoice? {
        let voices = AVSpeechSynthesisVoice.speechVoices()
        let candidates = voices.map { voice in
            var candidate = V499VoiceCandidate(identifier: voice.identifier, language: voice.language,
                                               gender: voice.gender, quality: voice.quality)
            if #available(iOS 17.0, *) {
                candidate.restricted = voice.voiceTraits.contains(.isNoveltyVoice) || voice.voiceTraits.contains(.isPersonalVoice)
            }
            return candidate
        }
        let preferred = UserDefaults.standard.string(forKey: V499ArabicNarration.preferredVoiceKey)
        guard let selected = V499ArabicNarration.preferredIdentifier(in: candidates, preferCompact: preferCompact,
                                                                   excluding: identifier, preferred: preferred) else { return nil }
        return voices.first(where: { $0.identifier == selected })
    }

    static func availableFemaleVoices() -> [AVSpeechSynthesisVoice] {
        AVSpeechSynthesisVoice.speechVoices().filter { voice in
            guard voice.gender == .female, V499ArabicNarration.localeRank(voice.language) != nil else { return false }
            if #available(iOS 17.0, *) {
                return !voice.voiceTraits.contains(.isNoveltyVoice) && !voice.voiceTraits.contains(.isPersonalVoice)
            }
            return true
        }.sorted { a, b in
            if a.quality != b.quality { return a.quality.rawValue > b.quality.rawValue }
            if a.language != b.language {
                return V499ArabicNarration.localeRank(a.language)! < V499ArabicNarration.localeRank(b.language)!
            }
            return a.name.localizedStandardCompare(b.name) == .orderedAscending
        }
    }

    private func speakNextChunk() {
        guard pending.indices.contains(chunkIndex), activeID != nil else { stop(); return }
        let text = pending[chunkIndex]
        guard let voice = selectedVoice else {
            fail(V499ArabicNarration.unavailableMessage)
            return
        }
        let next = V499ArabicNarration.utterance(text, voice: voice, first: chunkIndex == 0)
        utterance = next
        phase = .preparing
        synthesizer.speak(next)
        watchdog?.cancel()
        watchdog = Task { @MainActor [weak self] in
            do { try await Task.sleep(nanoseconds: 8_000_000_000) } catch { return }
            guard let self, self.phase == .preparing else { return }
            if self.retryCount == 0 {
                self.retryCount = 1
                // Recovery also respects the female/Arabic requirement. Never
                // fall back silently to a male or English system default.
                self.selectedVoice = Self.availableVoice(preferCompact: true, excluding: self.selectedVoice?.identifier)
                    ?? Self.availableVoice(preferCompact: true)
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
        selectedVoice = nil
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

// All settings are local to the device. Never suggest that an arbitrary audio
// clip can be imported as an iOS TTS voice: Apple exposes installed voices only.
struct V498NarrationSettingsView: View {
    @AppStorage(V499ArabicNarration.preferredVoiceKey) private var preferredVoiceID = ""
    @AppStorage(V499ArabicNarration.speedKey) private var speechSpeed = V499ArabicNarration.defaultSpeed
    @ObservedObject private var speech = V49QuestionSpeech.shared
    @State private var voices: [AVSpeechSynthesisVoice] = []

    private let previewID = "narration-settings-preview"

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                MurshidCard {
                    VStack(alignment: .leading, spacing: 14) {
                        Label("صوت الشرح", systemImage: "waveform")
                            .font(.headline).foregroundStyle(Color.murshidBlue)
                        Text("اختر صوتًا أنثويًا عربيًا مثبتًا على جهازك. الاختيار لا يغيّر الأسئلة أو بيانات الطالب.")
                            .font(.subheadline).foregroundStyle(.secondary)
                        if voices.isEmpty {
                            Text(V499ArabicNarration.unavailableMessage)
                                .font(.footnote).foregroundStyle(.secondary)
                        } else {
                            Picker("المعلّمة الصوتية", selection: $preferredVoiceID) {
                                Text("الأفضل تلقائيًا").tag("")
                                ForEach(voices, id: \.identifier) { voice in
                                    Text("\(voice.name) — \(voice.quality == .premium ? "ممتاز" : voice.quality == .enhanced ? "محسّن" : "أساسي")")
                                        .tag(voice.identifier)
                                }
                            }
                            .pickerStyle(.menu)
                            .accessibilityIdentifier("narration-female-voice-picker")
                        }
                        Divider()
                        HStack {
                            Label("سرعة القراءة", systemImage: "speedometer")
                            Spacer()
                            Text("\(Int((speechSpeed * 100).rounded()))٪")
                                .monospacedDigit().foregroundStyle(.secondary)
                        }.font(.subheadline)
                        Slider(value: $speechSpeed, in: 0.75...1.10, step: 0.05)
                            .accessibilityIdentifier("narration-speed-slider")
                        Button {
                            speech.toggle("مَرْحَبًا بِكَ فِي مِنَصَّةِ الْمُرْشِدِ الْوِزَارِيِّ. سَنَفْهَمُ السُّؤَالَ خُطْوَةً خُطْوَةً، ثُمَّ نُوَضِّحُ الْإِجَابَةَ بِأُسْلُوبٍ سَهْلٍ وَدَقِيقٍ.", id: previewID)
                        } label: {
                            Label(speech.activeID == previewID ? "إيقاف التجربة" : "جرّب صوت المعلّمة", systemImage: speech.activeID == previewID ? "stop.circle.fill" : "play.circle.fill")
                                .frame(maxWidth: .infinity, minHeight: 44)
                        }
                        .buttonStyle(PrimaryButtonStyle())
                        .disabled(voices.isEmpty)
                        .accessibilityIdentifier("narration-preview-button")
                        if speech.messageID == previewID, let message = speech.message {
                            Text(message).font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                }
                Text("لإضافة صوت بجودة محسّنة، نزّله أولًا من إعدادات iPhone ← تسهيلات الاستخدام ← المحتوى المنطوق ← الأصوات ← العربية، ثم ارجع لهذه الصفحة. لا يمكن تحويل تسجيل صوتي عادي إلى صوت قارئ داخل التطبيق مباشرة.")
                    .font(.footnote).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }.padding(16)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("صوت المعلّمة")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { voices = V49QuestionSpeech.availableFemaleVoices() }
        .onChange(of: preferredVoiceID) { _ in speech.stop(ifOwnedBy: previewID) }
        .onChange(of: speechSpeed) { _ in speech.stop(ifOwnedBy: previewID) }
        .onDisappear { speech.stop(ifOwnedBy: previewID) }
    }
}

