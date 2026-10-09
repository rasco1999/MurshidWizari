import SwiftUI

func murshidQuestionDisplayText(_ raw: String) -> String {
    var text = raw
        .replacingOccurrences(of: "\r\n", with: "\n")
        .trimmingCharacters(in: .whitespacesAndNewlines)
    guard !text.isEmpty else { return "" }

    // Preserve real educational verbs such as «عرّف» while removing editorial wrappers.
    text = text.replacingOccurrences(
        of: #"^\s*عرّف\s+المصطلح\s+الوزاري(?:\s+[\p{N}٠-٩]{1,4})?\s*[:：\-–—]\s*"#,
        with: "عرّف ",
        options: [.regularExpression, .caseInsensitive]
    )

    let prefixes = [
        #"^\s*\[[^\]\n]{1,80}\]\s*[:：\-–—]?\s*"#,
        #"^\s*(?:استرجاع\s+)?وزاري(?:\s+[\p{N}٠-٩]{1,4})?(?:\s*[/\-–—]\s*[^:：\n]{1,48})?\s*[:：\-–—]\s*"#,
        #"^\s*(?:سؤال\s+)?(?:تدريب|تدريبي|تدريبية)(?:\s+[\p{N}٠-٩]{1,4})?\s*[:：\-–—]\s*"#,
        #"^\s*(?:سؤال\s+)?مراجعة(?:\s+[\p{N}٠-٩]{1,4})?\s*[:：\-–—]\s*"#,
        #"^\s*اختيار\s+تحليلي(?:\s+[\p{N}٠-٩]{1,4})?\s*[:：\-–—]\s*"#,
        #"^\s*إجابة\s+مركزة(?:\s+[\p{N}٠-٩]{1,4})?\s*[:：\-–—]\s*"#,
        #"^\s*أكمل\s+المعنى\s+الوزاري(?:\s+[\p{N}٠-٩]{1,4})?\s*[:：\-–—]\s*"#,
        #"^\s*سؤال\s+[\p{N}٠-٩]{1,4}\s*[:：\-–—]\s*"#,
        #"^\s*[\p{N}٠-٩]{1,4}\s*[:：.)\-–—]\s*"#,
        #"^\s*(?:تحقق|تدقيق|مراجعة)\s*(?:بصري|لغوي|علمي|نحوي|صرفي|إملائي|املائي|نهائي|يدوي|داخلي)?(?:\s+[\p{N}٠-٩]{1,4})?\s*(?:[—–-]\s*)?(?:V\s*[\p{N}٠-٩]{1,4})?\s*[:：\-–—]\s*"#,
        #"^\s*V\s*[\p{N}٠-٩]{1,4}\s*[:：\-–—]\s*"#
    ]

    for _ in 0..<5 {
        var changed = false
        for pattern in prefixes {
            if let range = text.range(of: pattern, options: [.regularExpression, .caseInsensitive]) {
                text.removeSubrange(range)
                text = text.trimmingCharacters(in: .whitespacesAndNewlines)
                changed = true
                break
            }
        }
        if !changed { break }
    }

    // Remove QA/verification text that leaks the proposed answer.
    let answerLeakPatterns = [
        #"\s*[—–-]\s*هل\s+(?:الإجابة|الاجابة|الجواب|الحل)\s+.+?\s+(?:صحيحة|صحيح|صائب|صائبة)\s*[؟?]?\s*$"#,
        #"\s*[—–-]\s*(?:الإجابة|الاجابة|الجواب|الحل)\s*(?:الصحيحة|الصحيح)?\s*[:：].*$"#,
        #"\s*\((?:الإجابة|الاجابة|الجواب|الحل)\s*[:：][^)]*\)\s*$"#
    ]
    for pattern in answerLeakPatterns {
        text = text.replacingOccurrences(of: pattern, with: "", options: [.regularExpression, .caseInsensitive])
    }

    text = text
        .replacingOccurrences(of: #"[ \t]{2,}"#, with: " ", options: .regularExpression)
        .replacingOccurrences(of: #"\n{3,}"#, with: "\n\n", options: .regularExpression)
        .replacingOccurrences(of: #"\s+[،,]\s*$"#, with: "", options: .regularExpression)
        .trimmingCharacters(in: .whitespacesAndNewlines)

    if text.isEmpty { return "" }

    let interrogativePattern = #"(?:^|[،,:؛]\s*)(?:ما|ماذا|هل|لماذا|كيف|كم|أي|أين|اين|متى|من)\s"#
    if text.range(of: interrogativePattern, options: .regularExpression) != nil,
       !text.hasSuffix("؟"), !text.hasSuffix("?"), !text.hasSuffix(":"), !text.hasSuffix("؛") {
        text += "؟"
    }
    return text
}

func murshidQuestionIsStudentReady(_ raw: String) -> Bool {
    let text = murshidQuestionDisplayText(raw)
    guard text.count >= 4 else { return false }

    let blockedPatterns = [
        #"^\s*هل\s+(?:الإجابة|الاجابة|الجواب|الحل)\b"#,
        #"^\s*(?:صح\s+أم\s+خطأ\s*:\s*)?(?:الإجابة|الاجابة)\s+عن\s+[«"“].+?[»"”]\s+هي\s+[«"“].+?[»"”]"#,
        #"تدريب\s+تحقق\s+وزاري\s*:"#,
        #"^(?:تحقق|تدقيق)\s+(?:بصري|لغوي|علمي)\b"#
    ]
    return !blockedPatterns.contains { pattern in
        text.range(of: pattern, options: [.regularExpression, .caseInsensitive]) != nil
    }
}

func murshidQuestionRequiresChoices(_ raw: String) -> Bool {
    let text = murshidQuestionDisplayText(raw)
    guard !text.isEmpty else { return false }
    let patterns = [
        #"^(?:اختر|اختاري|حد[دّ]|عي[ّ]?ن)\b"#,
        #"^(?:أي|اي)\s+مما\s+(?:يأتي|ياتي|سبق)\b"#,
        #"^(?:أي|اي)\s+من\s+(?:الآتي|الاتي|التالي|التالية)\b"#,
        #"^(?:أي|اي)\s+(?:كلمة|كلمات|لفظ|لفظة|عبارة|جملة|صيغة|فعل|اسم)\b.*\b(?:مما\s+(?:يأتي|ياتي)|من\s+(?:الآتي|الاتي|التالي|التالية))\b"#,
        #"\b(?:الاختيار|الخيار)\s+الصحيح\b"#
    ]
    return patterns.contains { text.range(of: $0, options: [.regularExpression, .caseInsensitive]) != nil }
}

func murshidQuestionClientValid(_ q: Question) -> Bool {
    let type = q.type.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    let options = q.options.filter { !murshidAnswerKey($0.text).isEmpty }
    let unique = Set(options.map { murshidAnswerKey($0.text) }).count
    if murshidQuestionRequiresChoices(q.text) && unique < 2 { return false }
    if type == "mcq" && unique < 2 { return false }
    if type == "true_false" && unique < 2 { return false }
    if type == "match" && q.matchItems.isEmpty { return false }
    if type == "calculation" && q.stages.isEmpty { return false }
    if q.correctAnswer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return false }
    return true
}

func murshidAnswerKey(_ raw: String) -> String {
    var text = raw
        .trimmingCharacters(in: .whitespacesAndNewlines)
        .lowercased()
        .replacingOccurrences(of: "أ", with: "ا")
        .replacingOccurrences(of: "إ", with: "ا")
        .replacingOccurrences(of: "آ", with: "ا")
        .replacingOccurrences(of: "ٱ", with: "ا")
        .replacingOccurrences(of: "ى", with: "ي")
        .replacingOccurrences(of: "ـ", with: "")
    text = text.replacingOccurrences(of: #"[ًٌٍَُِّْٰ]"#, with: "", options: .regularExpression)
    text = text.replacingOccurrences(of: #"[^\p{L}\p{N}]+"#, with: "", options: .regularExpression)
    return text
}

func murshidEmbeddedProposedAnswer(_ raw: String) -> String? {
    let patterns = [
        #"هل\s+(?:الإجابة|الاجابة|الجواب|الحل)\s+[«"“]([^»"”]{1,120})[»"”]\s+(?:صحيحة|صحيح|صائب|صائبة)"#,
        #"(?:الإجابة|الاجابة|الجواب|الحل)\s+عن\s+[«"“][^»"”]+[»"”]\s+هي\s+[«"“]([^»"”]{1,120})[»"”]"#
    ]
    let ns = raw as NSString
    for pattern in patterns {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { continue }
        if let match = regex.firstMatch(in: raw, range: NSRange(location: 0, length: ns.length)), match.numberOfRanges > 1 {
            let r = match.range(at: 1)
            if r.location != NSNotFound {
                let value = ns.substring(with: r).trimmingCharacters(in: .whitespacesAndNewlines)
                if !value.isEmpty { return value }
            }
        }
    }
    return nil
}

func murshidBooleanTruth(_ raw: String) -> Bool? {
    let key = murshidAnswerKey(raw)
    if ["صح","صحيح","نعم","true","1","صائبه","صائبة"].contains(key) { return true }
    if ["خطا","خطأ","غيرصحيح","غيرصح","لا","false","0"].contains(key) { return false }
    return nil
}

func murshidEffectiveCorrectAnswer(question: String, serverAnswer: String) -> String {
    if let proposed = murshidEmbeddedProposedAnswer(question), murshidBooleanTruth(serverAnswer) == true { return proposed }
    return serverAnswer.trimmingCharacters(in: .whitespacesAndNewlines)
}

func murshidQuestionAnswerCompatible(question: String, serverAnswer: String) -> Bool {
    if murshidEmbeddedProposedAnswer(question) != nil, let truth = murshidBooleanTruth(serverAnswer) { return truth }
    return true
}

func murshidEffectiveCorrectness(question: String, submittedAnswer: String, serverAnswer: String, serverCorrect: Bool?) -> Bool? {
    if let proposed = murshidEmbeddedProposedAnswer(question), murshidBooleanTruth(serverAnswer) == true {
        let got = murshidAnswerKey(submittedAnswer)
        let expected = murshidAnswerKey(proposed)
        guard !got.isEmpty, !expected.isEmpty else { return serverCorrect }
        return got == expected
    }
    return serverCorrect
}

func murshidUsefulExplanation(_ raw: String) -> String {
    var text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !text.isEmpty else { return "" }
    let genericPrefixes = [
        "تدريب مشتق من مفهوم موثق في هذا المحور",
        "تدريب مشتق موثق",
        "هذا تدريب تحقق مشتق",
        "هذا تدريب مشتق",
        "صيغة تدريب جديدة مشتقة",
        "راجع الإجابة النموذجية",
        "تلميح:"
    ]
    if genericPrefixes.contains(where: { text.hasPrefix($0) }) { return "" }
    text = text.replacingOccurrences(
        of: #"^\s*(?:شرح\s*الإجابة|شرح\s*الاجابة|التوضيح|الشرح)\s*[:：\-–—]\s*"#,
        with: "",
        options: [.regularExpression, .caseInsensitive]
    )
    return text.trimmingCharacters(in: .whitespacesAndNewlines)
}

private func murshidQuotedTerm(_ text: String) -> String? {
    guard let range = text.range(of: #"«[^»]{1,80}»"#, options: .regularExpression) else { return nil }
    return String(text[range].dropFirst().dropLast())
}

func murshidExplanationText(question rawQuestion: String, correctAnswer rawAnswer: String, raw rawExplanation: String) -> String {
    let question = murshidQuestionDisplayText(rawQuestion)
    let answer = rawAnswer.trimmingCharacters(in: .whitespacesAndNewlines)
    let explanation = murshidUsefulExplanation(rawExplanation)
    let eKey = murshidAnswerKey(explanation)
    let aKey = murshidAnswerKey(answer)

    let trivial = explanation.isEmpty
        || (!aKey.isEmpty && eKey == aKey)
        || (!aKey.isEmpty && eKey == murshidAnswerKey("الإجابة الصحيحة هي " + answer))
        || (!aKey.isEmpty && eKey == murshidAnswerKey("الجواب الصحيح هو " + answer))

    if !trivial { return explanation }

    let qKey = murshidAnswerKey(question)
    let term = murshidQuotedTerm(question)

    if qKey.contains("الميزانالصرفي") || qKey.contains("وزنكلمه") || qKey.contains("وزنكلمة") {
        if let term, !answer.isEmpty {
            return "في الميزان الصرفي نقابل الحروف الأصلية بفاء وعين ولام، ونُبقي الحروف الزائدة في مواضعها؛ لذلك وزن «\(term)» هو «\(answer)»."
        }
        if !answer.isEmpty {
            return "في الميزان الصرفي نقابل الحروف الأصلية بفاء وعين ولام مع إبقاء الحروف الزائدة في مواضعها؛ لذلك تكون النتيجة «\(answer)»."
        }
    }
    if qKey.contains("اسمفاعل") && !answer.isEmpty {
        return "نطبّق قاعدة اسم الفاعل على الفعل المذكور في السؤال؛ ومن ثم تكون الصيغة الصحيحة «\(answer)»."
    }
    if qKey.contains("اسممفعول") && !answer.isEmpty {
        return "نطبّق قاعدة اسم المفعول على الفعل المذكور في السؤال؛ ومن ثم تكون الصيغة الصحيحة «\(answer)»."
    }
    if (qKey.contains("معني") || qKey.contains("معنى") || qKey.contains("مرادف")) && !answer.isEmpty {
        if let term { return "المعنى المقصود لكلمة «\(term)» في هذا السياق هو «\(answer)»." }
        return "المعنى المعتمد في سياق السؤال هو «\(answer)»."
    }
    if qKey.contains("ضد") && !answer.isEmpty {
        return "المطلوب هو الكلمة المقابلة في المعنى؛ لذلك يكون الضد الصحيح «\(answer)»."
    }
    if qKey.contains("جمع") && !answer.isEmpty {
        return "نحدّد صيغة الجمع المناسبة للكلمة وفق القاعدة الواردة في الدرس؛ لذلك تكون الإجابة «\(answer)»."
    }
    if qKey.contains("مفرد") && !answer.isEmpty {
        return "نردّ صيغة الجمع إلى مفردها الصحيح وفق الاستعمال اللغوي؛ لذلك تكون الإجابة «\(answer)»."
    }
    if (qKey.contains("ناتج") || question.contains("=") || question.contains("+") || question.contains("×") || question.contains("÷")) && !answer.isEmpty {
        return "نطبّق العملية المطلوبة على المعطيات بالترتيب، فنحصل على الناتج «\(answer)»."
    }
    if !answer.isEmpty {
        return "نطبّق القاعدة المطلوبة في نص السؤال على المعطى مباشرة؛ لذلك تكون الإجابة الصحيحة «\(answer)»."
    }
    return "تم تصحيح الإجابة وفق النموذج المعتمد لهذا السؤال."
}

import Combine
import UIKit
import SafariServices

private enum AuthRoute: Hashable {
    case login
    case register
    case forgot
    case verify(String, String, String)
    case phoneReset(String)
}

struct AuthFlowView: View {
    @EnvironmentObject var app: AppSession
    @State private var path: [AuthRoute] = []

    var body: some View {
        Group {
            if app.v3Authenticated {
                MainV3TabView()
            } else {
                authNavigation
            }
        }
        .animation(.easeInOut(duration: 0.22), value: app.v3Authenticated)
    }

    private var authNavigation: some View {
        NavigationStack(path: $path) {
            GuestLandingView(
                onLogin: { path.append(.login) },
                onRegister: { path.append(.register) }
            )
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: AuthRoute.self) { route in
                switch route {
                case .login:
                    LoginView(
                        onRegister: { path.append(.register) },
                        onForgot: { path.append(.forgot) },
                        onVerify: { kind, destination, message in
                            path.append(.verify(kind, destination, message))
                        }
                    )
                    .navigationTitle("تسجيل الدخول")
                    .murshidNavigation()

                case .register:
                    RegisterV3View(
                        onLogin: { path = [.login] },
                        onVerify: { kind, destination, message in
                            path.append(.verify(kind, destination, message))
                        }
                    )
                    .navigationTitle("إنشاء حساب")
                    .murshidNavigation()

                case .forgot:
                    ForgotPasswordView { token in
                        path.append(.phoneReset(token))
                    }
                    .navigationTitle("استعادة كلمة المرور")
                    .murshidNavigation()

                case let .verify(kind, destination, message):
                    VerificationView(
                        kind: kind,
                        destination: destination,
                        initialMessage: message,
                        onCancel: { path = [.login] }
                    )
                    .navigationTitle("تأكيد الحساب")
                    .murshidNavigation()

                case let .phoneReset(token):
                    PhonePasswordResetView(token: token) {
                        path = [.login]
                    }
                    .navigationTitle("كلمة مرور جديدة")
                    .murshidNavigation()
                }
            }
        }
    }
}

// MARK: - Native guest landing — same instructions and copy as mur-iq.com

private struct LandingFeature: Identifiable {
    let id = UUID()
    let title: String
    let text: String
    let icon: String
}

private struct LandingInstruction: Identifiable {
    let id: Int
    let title: String
    let text: String
    let icon: String
}

struct GuestLandingView: View {
    let onLogin: () -> Void
    let onRegister: () -> Void

    private let features: [LandingFeature] = [
        .init(title: "أسئلة مرتبة حسب الموضوع", text: "تنقّل بين المواد والمواضيع المنشورة واعرف عدد الأسئلة قبل أن تبدأ.", icon: "list.bullet.rectangle.portrait.fill"),
        .init(title: "تعلّم من إجابتك", text: "ارجع إلى الإجابات والتوضيحات المتاحة، وأبلغ عن أي ملاحظة لتُراجع.", icon: "brain.head.profile"),
        .init(title: "اختبارات سريعة ومستقرة", text: "تجربة خفيفة وواضحة تحفظ التقدم والنتائج داخل الحساب.", icon: "bolt.shield.fill"),
        .init(title: "تجربة مجانية حقيقية", text: "15 سؤالًا مجانيًا للحساب الجديد لتجربة المنصة قبل الاشتراك.", icon: "gift.fill"),
        .init(title: "دفع إلكتروني سهل", text: "خطوات اشتراك واضحة مع تأكيد حالة الدفع داخل المنصة.", icon: "creditcard.fill"),
        .init(title: "نتائج وتفسير للأخطاء", text: "متابعة المستوى ومعرفة نقاط القوة والموضوعات التي تحتاج مراجعة.", icon: "chart.bar.xaxis"),
        .init(title: "تحديثات مستمرة", text: "إضافة ومراجعة المحتوى باستمرار استعدادًا لمواعيد الامتحانات.", icon: "arrow.triangle.2.circlepath"),
        .init(title: "دعم سريع وموثوق", text: "خدمة عملاء واضحة لمتابعة مشكلات الحساب والاشتراك والاستخدام.", icon: "person.2.wave.2.fill")
    ]

    private let instructions: [LandingInstruction] = [
        .init(id: 1, title: "الحساب وتسجيل الدخول", text: "استخدم بيانات صحيحة عند إنشاء الحساب، واحفظ كلمة المرور الخاصة بك. تسجيل الدخول متاح برقم الهاتف أو البريد الإلكتروني المسجل في الحساب.", icon: "person.crop.circle.badge.checkmark"),
        .init(id: 2, title: "الاختبارات", text: "اختر الصف والمادة والموضوع ثم ابدأ الاختبار. يتم حفظ الإجابات والتقدم والنتائج داخل حساب الطالب، ويمكن متابعة الاختبار من الموضع المحفوظ.", icon: "checklist.checked"),
        .init(id: 3, title: "الأسئلة المجانية", text: "يتوفر للحساب الجديد رصيد مقداره 15 سؤالًا مجانيًا بالضبط. بعد استهلاك الرصيد المجاني يتطلب الوصول إلى الأسئلة الإضافية اشتراكًا فعالًا.", icon: "15.square.fill"),
        .init(id: 4, title: "قسم النجاح", text: "يعرض قسم النجاح أداء الطالب استنادًا إلى إجاباته وتقدمه، ويتضمن المواد الأقوى والموضوعات التي تحتاج إلى مراجعة. هذه المؤشرات خاصة بمتابعة المستوى داخل المنصة.", icon: "chart.line.uptrend.xyaxis"),
        .init(id: 5, title: "تحدي المليون", text: "يعتمد الترتيب على النقاط المسجلة وفق نظام المنصة. يستطيع الطالب متابعة مركزه والمتصدرين والفارق بينه وبين المراكز الأعلى من صفحة التحدي.", icon: "trophy.fill"),
        .init(id: 6, title: "بيانات الحساب", text: "لا تشارك كلمة المرور أو بيانات الدخول مع أي شخص. يجب استخدام الحساب من صاحبه فقط لضمان سلامة النتائج والنقاط وسجل الاختبارات.", icon: "lock.shield.fill"),
        .init(id: 7, title: "خدمة العملاء", text: "عند وجود مشكلة في الحساب أو الاشتراك أو استخدام الموقع، استخدم زر خدمة العملاء الموجود داخل المنصة بعد الدخول لإرسال طلب الدعم.", icon: "headset")
    ]

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 22) {
                    landingTopBar
                    hero(proxy: proxy)
                    jumpMenu(proxy: proxy)
                    platformValue
                    VStack(alignment: .leading, spacing: 6) {
                        Text("تعليمات الاستخدام")
                            .font(.subheadline.bold())
                            .foregroundStyle(Color.murshidBlue)
                        Text("كيف تبدأ وتستفيد من المنصة")
                            .font(.title.bold())
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .id("how")
                    instructionGrid
                    finalActions
                    footer
                }
                .padding(.horizontal, 18)
                .padding(.bottom, 26)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(
                LinearGradient(
                    colors: [Color.murshidBackground, Color.murshidNavy.opacity(0.045), Color.murshidBackground],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
            )
        }
    }

    private var landingTopBar: some View {
        HStack(spacing: 12) {
            Image("WelcomeEmblem")
                .resizable()
                .scaledToFit()
                .frame(width: 48, height: 48)

            VStack(alignment: .leading, spacing: 1) {
                Text("منصة المرشد الوزاري").font(.headline.bold())
                Text("مساحة تعلّمك").font(.caption).foregroundStyle(.secondary)
            }

            Spacer()

            Button("تسجيل الدخول", action: onLogin)
                .buttonStyle(.bordered)
                .tint(.murshidBlue)
                .font(.subheadline.weight(.semibold))
        }
        .padding(.top, 10)
    }

    private func hero(proxy: ScrollViewProxy) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 10) {
                Text("تعلّم بتركيز. تقدّم بثقة.")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(Color.murshidBlue)

                Text("خطوة واضحة،\nنحو نجاحك.")
                    .font(.system(size: 40, weight: .heavy, design: .rounded))
                    .minimumScaleFactor(0.82)
                    .fixedSize(horizontal: false, vertical: true)

                Text("رتّب دراستك، اختبر فهمك، وارجع لما يحتاج مراجعة. مساحة هادئة تجمع رحلتك الدراسية في مكان واحد.")
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .lineSpacing(5)

                HStack(spacing: 10) {
                    Button("ابدأ رحلتك", action: onRegister)
                        .buttonStyle(PrimaryButtonStyle())

                    Button("اكتشف المنصة") {
                        withAnimation(.easeInOut(duration: 0.35)) {
                            proxy.scrollTo("platform-value", anchor: .top)
                        }
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.large)
                }

                Label("منصة تعليمية مستقلة · غير تابعة لأي جهة حكومية", systemImage: "checkmark.shield.fill")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
            }

            MurshidCard {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Label("مساحة التدريب", systemImage: "book.fill")
                            .font(.headline)
                        Spacer()
                        Text("مثال توضيحي")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Text("كل سؤال، خطوة جديدة")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(Color.murshidBlue)

                    Text("ما ناتج 3² ؟")
                        .font(.title2.bold())

                    demoChoice("6", correct: false)
                    demoChoice("9", correct: true)
                    demoChoice("12", correct: false)

                    Label("افهم الفكرة، ثم جرّب بنفسك.", systemImage: "brain.head.profile")
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(.secondary)
                }
            }
            .overlay(alignment: .topLeading) {
                Label("تعلّم على مهل", systemImage: "checklist")
                    .font(.caption.bold())
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                    .background(.ultraThinMaterial, in: Capsule())
                    .offset(x: 4, y: -14)
            }
        }
        .padding(.top, 6)
    }

    private func demoChoice(_ value: String, correct: Bool) -> some View {
        HStack {
            Text(value).font(.headline)
            Spacer()
            Image(systemName: correct ? "checkmark.circle.fill" : "circle")
                .foregroundStyle(correct ? Color.green : Color.secondary)
        }
        .padding(.horizontal, 14)
        .frame(minHeight: 48)
        .background(correct ? Color.green.opacity(0.11) : Color.primary.opacity(0.035), in: RoundedRectangle(cornerRadius: 13, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 13, style: .continuous)
                .stroke(correct ? Color.green.opacity(0.30) : Color.primary.opacity(0.06), lineWidth: 1)
        )
    }

    private func jumpMenu(proxy: ScrollViewProxy) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                jumpButton("لماذا المنصة؟", id: "platform-value", proxy: proxy)
                jumpButton("كيف تبدأ", id: "how", proxy: proxy)
                jumpButton("الأسئلة المجانية", id: "instruction-3", proxy: proxy)
                jumpButton("تحدي المليون", id: "instruction-5", proxy: proxy)
                jumpButton("خدمة العملاء", id: "instruction-7", proxy: proxy)
            }
        }
    }

    private func jumpButton(_ title: String, id: String, proxy: ScrollViewProxy) -> some View {
        Button(title) {
            withAnimation(.easeInOut(duration: 0.3)) {
                proxy.scrollTo(id, anchor: .top)
            }
        }
        .font(.caption.weight(.semibold))
        .buttonStyle(.bordered)
        .tint(.secondary)
    }

    private var platformValue: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("مصممة لطريقتك في الدراسة")
                .font(.subheadline.bold())
                .foregroundStyle(Color.murshidBlue)

            Text("لماذا تختار منصة المرشد الوزاري؟")
                .font(.title.bold())

            Text("من اختيار موضوعك إلى مراجعة إجابتك، أدوات بسيطة تساعدك على معرفة خطوتك التالية.")
                .foregroundStyle(.secondary)
                .lineSpacing(4)

            ForEach(features) { item in
                MurshidCard {
                    HStack(alignment: .top, spacing: 13) {
                        Image(systemName: item.icon)
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(Color.murshidBlue)
                            .frame(width: 34, height: 34)
                            .background(Color.murshidBlue.opacity(0.10), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                        VStack(alignment: .leading, spacing: 5) {
                            Text(item.title).font(.headline)
                            Text(item.text)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .lineSpacing(3)
                        }

                        Spacer(minLength: 0)
                    }
                }
            }
        }
        .id("platform-value")
    }

    private var instructionGrid: some View {
        VStack(spacing: 13) {
            ForEach(instructions) { item in
                MurshidCard {
                    HStack(alignment: .top, spacing: 13) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(Color.murshidBlue.opacity(0.10))
                                .frame(width: 46, height: 46)
                            Text(String(format: "%02d", item.id))
                                .font(.caption.bold().monospacedDigit())
                                .foregroundStyle(Color.murshidBlue)
                        }

                        VStack(alignment: .leading, spacing: 7) {
                            Label(item.title, systemImage: item.icon)
                                .font(.headline)
                            Text(item.text)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .lineSpacing(4)
                        }

                        Spacer(minLength: 0)
                    }
                }
                .id("instruction-\(item.id)")
            }
        }
    }

    private var finalActions: some View {
        MurshidCard {
            VStack(spacing: 14) {
                Text("بعد قراءة التعليمات")
                    .font(.subheadline.bold())
                    .foregroundStyle(Color.murshidBlue)

                Text("اختر طريقة الدخول إلى المنصة")
                    .font(.title2.bold())
                    .multilineTextAlignment(.center)

                Button("إنشاء حساب", action: onRegister)
                    .buttonStyle(PrimaryButtonStyle())

                Button("تسجيل الدخول", action: onLogin)
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .foregroundStyle(Color.murshidBlue)
                    .background(Color.murshidBlue.opacity(0.10), in: RoundedRectangle(cornerRadius: 17, style: .continuous))
            }
        }
    }

    private var footer: some View {
        VStack(spacing: 10) {
            Text("© 2026 المرشد الوزاري · منصة تعليمية مستقلة")
                .font(.caption)
                .foregroundStyle(.secondary)

            HStack(spacing: 18) {
                Link(destination: URL(string: "https://www.instagram.com/murshid.iq/")!) {
                    Image(systemName: "camera")
                }
                Link(destination: URL(string: "https://www.facebook.com/Murshidiq/")!) {
                    Image(systemName: "person.2.fill")
                }
                Link(destination: URL(string: "https://t.me/El_Murshed_iq")!) {
                    Image(systemName: "paperplane.fill")
                }
            }
            .foregroundStyle(Color.murshidBlue)

            Text("الثالث المتوسط · السادس العلمي · السادس الأدبي")
                .font(.caption.weight(.medium))
                .foregroundStyle(.secondary)

            HStack(spacing: 6) {
                Link("الخصوصية", destination: URL(string: "https://mur-iq.com/privacy.php")!)
                Text("·").foregroundStyle(.secondary)
                Link("شروط الاستخدام", destination: URL(string: "https://mur-iq.com/terms.php")!)
            }
            .font(.caption)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
    }
}

struct AuthHeader: View {
    let subtitle: String

    var body: some View {
        VStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [.murshidBlue, .murshidNavy],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 82, height: 82)
                    .shadow(color: Color.murshidBlue.opacity(0.18), radius: 18, y: 9)

                Image(systemName: "graduationcap.fill")
                    .font(.system(size: 36, weight: .semibold))
                    .foregroundStyle(.white)
            }

            Text("المرشد الوزاري")
                .font(.system(.largeTitle, design: .rounded, weight: .bold))
                .multilineTextAlignment(.center)

            Text(subtitle)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .lineSpacing(3)
        }
        .accessibilityElement(children: .combine)
    }
}

struct LoginView: View {
    let onRegister: () -> Void
    let onForgot: () -> Void
    let onVerify: (String, String, String) -> Void

    @State private var identifier = ""
    @State private var password = ""
    @State private var remember = true
    @State private var revealPassword = false
    @State private var loading = false
    @State private var error = ""

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                AuthHeader(subtitle: "سجّل الدخول وواصل دراستك من حيث توقفت")
                    .padding(.top, 28)

                MurshidCard {
                    VStack(alignment: .leading, spacing: 16) {
                        Text("تسجيل الدخول")
                            .font(.title2.bold())

                        LabeledTextField(
                            title: "البريد الإلكتروني أو رقم الهاتف",
                            icon: "person.crop.circle",
                            placeholder: "example@email.com أو 07XXXXXXXXX",
                            text: $identifier,
                            keyboard: .emailAddress,
                            contentType: .username,
                            forceLTR: true
                        )

                        LabeledPasswordField(
                            title: "كلمة المرور",
                            placeholder: "أدخل كلمة المرور",
                            text: $password,
                            reveal: $revealPassword,
                            contentType: .password
                        )

                        Toggle("تذكرني على هذا الجهاز", isOn: $remember)
                            .tint(.murshidBlue)

                        if !error.isEmpty {
                            AuthMessage(text: error, kind: .error)
                        }

                        Button(action: submit) {
                            HStack(spacing: 10) {
                                if loading { ProgressView().tint(.white) }
                                Text(loading ? "جاري الدخول…" : "دخول")
                            }
                        }
                        .buttonStyle(PrimaryButtonStyle())
                        .disabled(!canSubmit || loading)
                        .opacity((!canSubmit || loading) ? 0.62 : 1)

                        Button(action: onForgot) {
                            Label("نسيت كلمة المرور؟", systemImage: "key.fill")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(Color.murshidBlue)
                    }
                }

                Button("إنشاء حساب طالب جديد", action: onRegister)
                    .font(.headline)
                    .padding(.bottom, 32)
            }
            .padding(.horizontal, 18)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Color.murshidBackground.ignoresSafeArea())
    }

    private var canSubmit: Bool {
        !identifier.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !password.isEmpty
    }

    private func submit() {
        guard canSubmit else { return }
        loading = true
        error = ""

        Task {
            do {
                let response = try await APIClient.shared.login(
                    identifier: normalizedIdentifier(identifier),
                    password: password,
                    remember: remember
                )

                if jBool(response["email_verification_required"]) {
                    loading = false
                    onVerify("email", jString(response["masked_email"]), jString(response["message"]))
                    return
                }

                if jBool(response["phone_verification_required"]) {
                    loading = false
                    onVerify("phone", jString(response["masked_phone"]), jString(response["message"]))
                    return
                }

                let bootstrap = try await APIClient.shared.bootstrap()
                AppSession.shared.applyBootstrap(bootstrap)
                loading = false
                haptic()
            } catch {
                loading = false
                self.error = error.localizedDescription
                haptic(.error)
            }
        }
    }
}

struct RegisterView: View {
    let onLogin: () -> Void
    let onVerify: (String, String, String) -> Void

    @State private var fullName = ""
    @State private var grades: [Subject] = []
    @State private var gradeID = 0
    @State private var gender = "male"
    @State private var contactType = "email"
    @State private var identifier = ""
    @State private var password = ""
    @State private var confirm = ""
    @State private var revealPassword = false
    @State private var revealConfirm = false
    @State private var loading = false
    @State private var gradesLoading = true

    @State private var nameError = ""
    @State private var gradeError = ""
    @State private var identifierError = ""
    @State private var passwordError = ""
    @State private var confirmError = ""
    @State private var generalError = ""

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                AuthHeader(subtitle: "حساب واحد يحفظ تقدمك وإجاباتك على جميع أجهزتك")
                    .padding(.top, 12)

                MurshidCard {
                    VStack(alignment: .leading, spacing: 16) {
                        Text("بيانات الطالب")
                            .font(.title2.bold())

                        LabeledTextField(
                            title: "الاسم الثلاثي",
                            icon: "person.text.rectangle",
                            placeholder: "مثال: أحمد علي حسن",
                            text: $fullName,
                            keyboard: .default,
                            contentType: .name
                        )
                        .onChange(of: fullName) { _ in
                            nameError = ""
                        }

                        if !nameError.isEmpty {
                            FieldError(nameError)
                        } else {
                            Text("اكتب اسمك الثلاثي كما هو في بياناتك الرسمية.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        VStack(alignment: .leading, spacing: 8) {
                            FieldLabel("الصف الدراسي", icon: "books.vertical.fill")

                            Menu {
                                ForEach(grades) { grade in
                                    Button(grade.name) {
                                        gradeID = grade.id
                                        gradeError = ""
                                        selectionHaptic()
                                    }
                                }
                            } label: {
                                HStack {
                                    Text(selectedGrade)
                                        .foregroundStyle(gradeID == 0 ? Color.secondary : Color.primary)
                                    Spacer()
                                    if gradesLoading {
                                        ProgressView().controlSize(.small)
                                    } else {
                                        Image(systemName: "chevron.up.chevron.down")
                                            .font(.caption.bold())
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                .authFieldStyle()
                            }
                            .buttonStyle(.plain)
                            .disabled(gradesLoading || grades.isEmpty)

                            if !gradeError.isEmpty { FieldError(gradeError) }
                        }

                        VStack(alignment: .leading, spacing: 8) {
                            FieldLabel("الجنس", icon: "person.2.fill")
                            Picker("الجنس", selection: $gender) {
                                Text("ذكر").tag("male")
                                Text("أنثى").tag("female")
                            }
                            .pickerStyle(.segmented)
                        }

                        VStack(alignment: .leading, spacing: 8) {
                            FieldLabel("طريقة التسجيل", icon: "at")
                            Picker("طريقة التسجيل", selection: $contactType) {
                                Text("البريد").tag("email")
                                Text("الهاتف").tag("phone")
                            }
                            .pickerStyle(.segmented)
                            .onChange(of: contactType) { _ in
                                identifier = ""
                                identifierError = ""
                                selectionHaptic()
                            }
                        }

                        LabeledTextField(
                            title: contactType == "email" ? "البريد الإلكتروني" : "رقم الهاتف العراقي",
                            icon: contactType == "email" ? "envelope.fill" : "phone.fill",
                            placeholder: contactType == "email" ? "example@email.com" : "07XXXXXXXXX",
                            text: $identifier,
                            keyboard: contactType == "email" ? .emailAddress : .phonePad,
                            contentType: contactType == "email" ? .emailAddress : .telephoneNumber,
                            forceLTR: true
                        )
                        .onChange(of: identifier) { _ in identifierError = "" }

                        if !identifierError.isEmpty { FieldError(identifierError) }

                        LabeledPasswordField(
                            title: "كلمة المرور",
                            placeholder: "8 أحرف على الأقل",
                            text: $password,
                            reveal: $revealPassword,
                            contentType: .newPassword
                        )
                        .onChange(of: password) { _ in
                            passwordError = ""
                            confirmError = ""
                        }

                        if !passwordError.isEmpty { FieldError(passwordError) }

                        LabeledPasswordField(
                            title: "تأكيد كلمة المرور",
                            placeholder: "أعد كتابة كلمة المرور",
                            text: $confirm,
                            reveal: $revealConfirm,
                            contentType: .newPassword
                        )
                        .onChange(of: confirm) { _ in confirmError = "" }

                        if !confirmError.isEmpty { FieldError(confirmError) }

                        if !generalError.isEmpty {
                            AuthMessage(text: generalError, kind: .error)
                        }

                        Button(action: submit) {
                            HStack(spacing: 10) {
                                if loading { ProgressView().tint(.white) }
                                Text(loading ? "جاري إنشاء الحساب…" : "إنشاء الحساب")
                            }
                        }
                        .buttonStyle(PrimaryButtonStyle())
                        .disabled(loading || gradesLoading)
                        .opacity((loading || gradesLoading) ? 0.62 : 1)

                        Text("نسخة iPhone 4.2 • Build 420")
                            .font(.caption2.monospacedDigit())
                            .foregroundStyle(.tertiary)
                            .frame(maxWidth: .infinity, alignment: .center)
                    }
                }

                Button("لدي حساب بالفعل", action: onLogin)
                    .padding(.bottom, 32)
            }
            .padding(.horizontal, 18)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Color.murshidBackground.ignoresSafeArea())
        .task { await loadGrades() }
    }

    private var selectedGrade: String {
        grades.first(where: { $0.id == gradeID })?.name
        ?? (gradesLoading ? "جاري تحميل الصفوف…" : "اختر الصف الدراسي")
    }

    private func loadGrades() async {
        gradesLoading = true
        do {
            grades = try await APIClient.shared.grades().map(Subject.init)
            if gradeID == 0 { gradeID = grades.first?.id ?? 0 }
        } catch {
            generalError = error.localizedDescription
        }
        gradesLoading = false
    }

    private func submit() {
        clearErrors()

        let name = normalizedPersonName(fullName)
        if name.isEmpty {
            nameError = "أدخل اسم الطالب."
        }

        if gradeID <= 0 {
            gradeError = "اختر الصف الدراسي."
        }

        let cleanIdentifier = normalizedIdentifier(identifier)
        if contactType == "email" {
            if !isValidEmail(cleanIdentifier) {
                identifierError = "أدخل بريدًا إلكترونيًا صحيحًا."
            }
        } else if !isValidIraqiPhone(cleanIdentifier) {
            identifierError = "أدخل رقمًا عراقيًا صحيحًا يبدأ بـ 07 ويتكون من 11 رقمًا."
        }

        if password.count < 8 {
            passwordError = "كلمة المرور يجب أن تكون 8 أحرف على الأقل."
        }

        if confirm.isEmpty {
            confirmError = "أعد كتابة كلمة المرور للتأكيد."
        } else if password != confirm {
            confirmError = "كلمتا المرور غير متطابقتين."
        }

        guard nameError.isEmpty,
              gradeError.isEmpty,
              identifierError.isEmpty,
              passwordError.isEmpty,
              confirmError.isEmpty else {
            haptic(.error)
            return
        }

        fullName = name
        identifier = cleanIdentifier
        loading = true

        Task {
            do {
                let data = try await APIClient.shared.register([
                    "full_name": name,
                    "name": name,
                    "fullName": name,
                    "grade_id": gradeID,
                    "gender": gender,
                    "contact_type": contactType,
                    "identifier": cleanIdentifier,
                    "password": password,
                    "password_confirmation": confirm,
                    "company_website": ""
                ])

                let kind = jBool(data["phone_verification_required"]) ? "phone" : "email"
                let destination = kind == "phone"
                    ? jString(data["masked_phone"])
                    : jString(data["masked_email"])

                loading = false
                onVerify(kind, destination, jString(data["message"]))
            } catch {
                loading = false
                generalError = error.localizedDescription
                haptic(.error)
            }
        }
    }

    private func clearErrors() {
        nameError = ""
        gradeError = ""
        identifierError = ""
        passwordError = ""
        confirmError = ""
        generalError = ""
    }
}

struct ForgotPasswordView: View {
    let onPhoneToken: (String) -> Void

    @State private var identifier = ""
    @State private var loading = false
    @State private var error = ""
    @State private var message = ""

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                AuthHeader(subtitle: "استعد حسابك بأمان بدون مغادرة التطبيق")
                    .padding(.top, 12)

                MurshidCard {
                    VStack(alignment: .leading, spacing: 16) {
                        Label("استعادة كلمة المرور", systemImage: "key.fill")
                            .font(.title2.bold())

                        Text("أدخل البريد أو رقم الهاتف المسجل فعليًا في حسابك. لن تبدأ الاستعادة إذا لم تكن البيانات مسجلة في المنصة. للهاتف سيصل رمز عبر WhatsApp، والبريد يستلم رابط استعادة آمنًا.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .lineSpacing(3)

                        LabeledTextField(
                            title: "رقم الهاتف أو البريد الإلكتروني",
                            icon: "person.badge.key.fill",
                            placeholder: "07XXXXXXXXX أو example@email.com",
                            text: $identifier,
                            keyboard: .emailAddress,
                            contentType: .username,
                            forceLTR: true
                        )
                        .onChange(of: identifier) { _ in
                            error = ""
                            message = ""
                        }

                        if !error.isEmpty {
                            AuthMessage(text: error, kind: .error)
                        }

                        if !message.isEmpty {
                            AuthMessage(text: message, kind: .success)
                        }

                        Button(action: submit) {
                            HStack(spacing: 10) {
                                if loading { ProgressView().tint(.white) }
                                Text(loading ? "جاري التحقق…" : "متابعة الاستعادة")
                            }
                        }
                        .buttonStyle(PrimaryButtonStyle())
                        .disabled(identifier.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || loading)
                    }
                }
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 32)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Color.murshidBackground.ignoresSafeArea())
    }

    private func submit() {
        loading = true
        error = ""
        message = ""

        Task {
            do {
                let result = try await APIClient.shared.beginPasswordReset(
                    identifier: normalizedIdentifier(identifier)
                )
                loading = false

                switch result.channel {
                case .phone:
                    guard let token = result.phoneToken, !token.isEmpty else {
                        error = "تعذر تجهيز استعادة الهاتف. حاول مجددًا."
                        return
                    }
                    haptic()
                    onPhoneToken(token)

                case .email:
                    message = result.message
                    haptic()
                }
            } catch {
                loading = false
                self.error = error.localizedDescription
                haptic(.error)
            }
        }
    }
}

struct PhonePasswordResetView: View {
    let token: String
    let onDone: () -> Void

    @State private var code = ""
    @State private var password = ""
    @State private var confirm = ""
    @State private var revealPassword = false
    @State private var revealConfirm = false
    @State private var loading = false
    @State private var resending = false
    @State private var error = ""
    @State private var message = "أرسلنا رمز استعادة من 6 أرقام إلى رقم WhatsApp المسجل."
    @State private var finished = false

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                AuthHeader(subtitle: "أدخل رمز WhatsApp واختر كلمة مرور جديدة")
                    .padding(.top, 12)

                MurshidCard {
                    if finished {
                        VStack(spacing: 15) {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.system(size: 54))
                                .foregroundStyle(.green)

                            Text("تم تغيير كلمة المرور")
                                .font(.title2.bold())

                            Text(message)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.center)

                            Button("العودة لتسجيل الدخول", action: onDone)
                                .buttonStyle(PrimaryButtonStyle())
                        }
                        .frame(maxWidth: .infinity)
                    } else {
                        VStack(alignment: .leading, spacing: 16) {
                            Text("تأكيد الاستعادة")
                                .font(.title2.bold())

                            AuthMessage(text: message, kind: .info)

                            VStack(alignment: .leading, spacing: 8) {
                                FieldLabel("رمز WhatsApp", icon: "number.square.fill")
                                TextField("000000", text: $code)
                                    .keyboardType(.numberPad)
                                    .textContentType(.oneTimeCode)
                                    .font(.title3.monospacedDigit().weight(.semibold))
                                    .multilineTextAlignment(.center)
                                    .environment(\.layoutDirection, .leftToRight)
                                    .authFieldStyle()
                                    .onChange(of: code) { value in
                                        code = String(normalizedDigits(value).prefix(6))
                                        error = ""
                                    }
                            }

                            LabeledPasswordField(
                                title: "كلمة المرور الجديدة",
                                placeholder: "8 أحرف على الأقل",
                                text: $password,
                                reveal: $revealPassword,
                                contentType: .newPassword
                            )

                            LabeledPasswordField(
                                title: "تأكيد كلمة المرور",
                                placeholder: "أعد كتابة كلمة المرور",
                                text: $confirm,
                                reveal: $revealConfirm,
                                contentType: .newPassword
                            )

                            if !error.isEmpty {
                                AuthMessage(text: error, kind: .error)
                            }

                            Button(action: resetPassword) {
                                HStack(spacing: 10) {
                                    if loading { ProgressView().tint(.white) }
                                    Text(loading ? "جاري الحفظ…" : "حفظ كلمة المرور الجديدة")
                                }
                            }
                            .buttonStyle(PrimaryButtonStyle())
                            .disabled(!canSubmit || loading || resending)
                            .opacity((!canSubmit || loading || resending) ? 0.62 : 1)

                            Button(action: resend) {
                                HStack(spacing: 8) {
                                    if resending { ProgressView().controlSize(.small) }
                                    Text(resending ? "جاري إعادة الإرسال…" : "إعادة إرسال رمز WhatsApp")
                                }
                                .frame(maxWidth: .infinity)
                            }
                            .disabled(loading || resending)
                        }
                    }
                }
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 32)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Color.murshidBackground.ignoresSafeArea())
    }

    private var canSubmit: Bool {
        code.count == 6 && password.count >= 8 && password == confirm
    }

    private func resetPassword() {
        error = ""

        guard code.count == 6 else {
            error = "أدخل رمز WhatsApp المكوّن من 6 أرقام."
            return
        }
        guard password.count >= 8 else {
            error = "كلمة المرور يجب أن تكون 8 أحرف على الأقل."
            return
        }
        guard password == confirm else {
            error = "كلمتا المرور غير متطابقتين."
            return
        }

        loading = true

        Task {
            do {
                message = try await APIClient.shared.finishPhonePasswordReset(
                    token: token,
                    code: code,
                    password: password,
                    confirm: confirm
                )
                loading = false
                finished = true
                haptic()
            } catch {
                loading = false
                self.error = error.localizedDescription
                haptic(.error)
            }
        }
    }

    private func resend() {
        error = ""
        resending = true

        Task {
            do {
                message = try await APIClient.shared.resendPhonePasswordReset(token: token)
                resending = false
                haptic()
            } catch {
                resending = false
                self.error = error.localizedDescription
                haptic(.error)
            }
        }
    }
}

struct VerificationView: View {
    let kind: String
    let destination: String
    let initialMessage: String
    let onCancel: () -> Void

    @State private var code = ""
    @State private var loading = false
    @State private var message = ""
    @State private var error = ""

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                AuthHeader(subtitle: kind == "phone" ? "تأكيد رقم الهاتف" : "تأكيد البريد الإلكتروني")
                    .padding(.top, 12)

                MurshidCard {
                    VStack(alignment: .leading, spacing: 16) {
                        Text("رمز التحقق")
                            .font(.title2.bold())

                        Text(initialMessage.isEmpty ? "أدخل الرمز المرسل إلى \(destination)" : initialMessage)
                            .foregroundStyle(.secondary)

                        TextField("000000", text: $code)
                            .keyboardType(.numberPad)
                            .textContentType(.oneTimeCode)
                            .font(.title2.monospacedDigit())
                            .multilineTextAlignment(.center)
                            .environment(\.layoutDirection, .leftToRight)
                            .authFieldStyle()
                            .onChange(of: code) { value in
                                code = String(normalizedDigits(value).prefix(6))
                                error = ""
                            }

                        if !message.isEmpty {
                            AuthMessage(text: message, kind: .success)
                        }

                        if !error.isEmpty {
                            AuthMessage(text: error, kind: .error)
                        }

                        Button(action: verify) {
                            HStack(spacing: 10) {
                                if loading { ProgressView().tint(.white) }
                                Text(loading ? "جاري التحقق…" : "تأكيد الحساب")
                            }
                        }
                        .buttonStyle(PrimaryButtonStyle())
                        .disabled(loading || code.count != 6)
                        .opacity((loading || code.count != 6) ? 0.62 : 1)

                        Button("إعادة إرسال الرمز", action: resend)
                            .disabled(loading)
                            .frame(maxWidth: .infinity)

                        Button("إلغاء والعودة لتسجيل الدخول", action: onCancel)
                            .font(.footnote)
                            .frame(maxWidth: .infinity)
                    }
                }
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 32)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Color.murshidBackground.ignoresSafeArea())
    }

    private func verify() {
        loading = true
        error = ""
        message = ""

        Task {
            do {
                let bootstrap = try await APIClient.shared.verifyPending(kind: kind, code: code)
                AppSession.shared.applyBootstrap(bootstrap)
                loading = false
                haptic()
            } catch {
                loading = false
                self.error = error.localizedDescription
                haptic(.error)
            }
        }
    }

    private func resend() {
        loading = true
        error = ""
        message = ""

        Task {
            do {
                message = try await APIClient.shared.resendPending(kind: kind)
                loading = false
                haptic()
            } catch {
                loading = false
                self.error = error.localizedDescription
            }
        }
    }
}

private struct FieldLabel: View {
    let title: String
    let icon: String

    init(_ title: String, icon: String) {
        self.title = title
        self.icon = icon
    }

    var body: some View {
        Label(title, systemImage: icon)
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(.primary)
    }
}

private struct LabeledTextField: View {
    let title: String
    let icon: String
    let placeholder: String
    @Binding var text: String
    let keyboard: UIKeyboardType
    let contentType: UITextContentType?
    var forceLTR = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            FieldLabel(title, icon: icon)

            TextField(placeholder, text: $text)
                .keyboardType(keyboard)
                .textContentType(contentType)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .environment(\.layoutDirection, forceLTR ? .leftToRight : .rightToLeft)
                .authFieldStyle()
        }
    }
}

private struct LabeledPasswordField: View {
    let title: String
    let placeholder: String
    @Binding var text: String
    @Binding var reveal: Bool
    let contentType: UITextContentType?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            FieldLabel(title, icon: "lock.fill")

            HStack(spacing: 10) {
                Group {
                    if reveal {
                        TextField(placeholder, text: $text)
                    } else {
                        SecureField(placeholder, text: $text)
                    }
                }
                .textContentType(contentType)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)

                Button {
                    reveal.toggle()
                    selectionHaptic()
                } label: {
                    Image(systemName: reveal ? "eye.slash.fill" : "eye.fill")
                        .foregroundStyle(.secondary)
                        .frame(width: 28, height: 28)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(reveal ? "إخفاء كلمة المرور" : "إظهار كلمة المرور")
            }
            .authFieldStyle()
        }
    }
}

private struct FieldError: View {
    let text: String
    init(_ text: String) { self.text = text }

    var body: some View {
        Label(text, systemImage: "exclamationmark.circle.fill")
            .font(.caption)
            .foregroundStyle(.red)
            .fixedSize(horizontal: false, vertical: true)
    }
}

private enum AuthMessageKind {
    case error
    case success
    case info
}

private struct AuthMessage: View {
    let text: String
    let kind: AuthMessageKind

    private var color: Color {
        switch kind {
        case .error: return .red
        case .success: return .green
        case .info: return .murshidBlue
        }
    }

    private var icon: String {
        switch kind {
        case .error: return "exclamationmark.circle.fill"
        case .success: return "checkmark.circle.fill"
        case .info: return "info.circle.fill"
        }
    }

    var body: some View {
        Label(text, systemImage: icon)
            .font(.footnote)
            .foregroundStyle(color)
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(color.opacity(0.10), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .fixedSize(horizontal: false, vertical: true)
    }
}

private extension View {
    func authFieldStyle() -> some View {
        self
            .padding(.horizontal, 13)
            .frame(minHeight: 52)
            .background(Color(uiColor: .secondarySystemGroupedBackground))
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(Color.primary.opacity(0.11), lineWidth: 1)
            )
    }
}

private func normalizedPersonName(_ value: String) -> String {
    // Transport-safe normalization only. The backend is the single authority
    // for deciding whether the name is three-part and acceptable.
    let hidden: Set<UInt32> = [
        0x200E, 0x200F, 0x061C,
        0x202A, 0x202B, 0x202C, 0x202D, 0x202E,
        0x2066, 0x2067, 0x2068, 0x2069,
        0xFEFF
    ]

    var result = ""
    var pendingSpace = false

    for scalar in value.precomposedStringWithCanonicalMapping.unicodeScalars {
        if hidden.contains(scalar.value) { continue }

        if CharacterSet.whitespacesAndNewlines.contains(scalar) {
            if !result.isEmpty { pendingSpace = true }
            continue
        }

        if pendingSpace {
            result.append(" ")
            pendingSpace = false
        }

        result.unicodeScalars.append(scalar)
    }

    return result.trimmingCharacters(in: .whitespacesAndNewlines)
}

private func normalizedIdentifier(_ value: String) -> String {
    normalizedDigits(value)
        .trimmingCharacters(in: .whitespacesAndNewlines)
        .replacingOccurrences(of: " ", with: "")
}

private func normalizedDigits(_ value: String) -> String {
    let arabic = Array("٠١٢٣٤٥٦٧٨٩")
    let persian = Array("۰۱۲۳۴۵۶۷۸۹")
    var result = ""

    for character in value {
        if let index = arabic.firstIndex(of: character) {
            result.append(String(index))
        } else if let index = persian.firstIndex(of: character) {
            result.append(String(index))
        } else {
            result.append(character)
        }
    }

    return result
}

private func isValidEmail(_ value: String) -> Bool {
    value.range(
        of: #"^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$"#,
        options: [.regularExpression, .caseInsensitive]
    ) != nil
}

private func isValidIraqiPhone(_ value: String) -> Bool {
    value.range(of: #"^07\d{9}$"#, options: .regularExpression) != nil
}


struct SafariSheet: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        SFSafariViewController(url: url)
    }

    func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {}
}

// MARK: - Complete Native iOS v3 Experience

// MARK: - V3 Main Navigation

struct MainV3TabView: View {
    @EnvironmentObject var app: AppSession
    @State private var selection = 0

    var body: some View {
        TabView(selection: $selection) {
            NavigationStack { V3HomeView() }
                .tabItem { Label("الرئيسية", systemImage: "house.fill") }
                .tag(0)

            NavigationStack { V3TestsView() }
                .tabItem { Label("الاختبارات", systemImage: "checklist.checked") }
                .tag(1)

            NavigationStack { V3SuccessView() }
                .tabItem { Label("النجاح", systemImage: "chart.line.uptrend.xyaxis") }
                .tag(2)

            NavigationStack { V3AccountView() }
                .tabItem {
                    if let image = app.avatarTabImage { Image(uiImage: image); Text("حسابي") }
                    else { Label("حسابي", systemImage: "person.crop.circle.fill") }
                }
                .tag(3)
        }
        .tint(.murshidBlue)
        .toolbarBackground(.visible, for: .tabBar)
        .environment(\.layoutDirection, .rightToLeft)
        .onChange(of: selection) { _ in selectionHaptic() }
        .task { await keepSessionAlive() }
        .onOpenURL { url in
            switch (url.host ?? url.path.replacingOccurrences(of: "/", with: "")).lowercased() {
            case "tests", "exam", "subjects": selection = 1
            case "success", "progress": selection = 2
            case "account", "subscription": selection = 3
            default: selection = 0
            }
            selectionHaptic()
        }
    }

    private func keepSessionAlive() async {
        while !Task.isCancelled && app.v3Authenticated {
            try? await APIClient.shared.heartbeat()
            try? await Task.sleep(nanoseconds: 60_000_000_000)
        }
    }
}

// MARK: - Home

struct V3HomeView: View {
    @EnvironmentObject var app: AppSession
    @EnvironmentObject var device: DeviceServices
    @State private var insights: JSON = [:]
    @State private var weekly: JSON = [:]
    @State private var loadingInsights = true
    @State private var error = ""

    private let columns = [GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                hero
                if !device.isOnline { offlineBanner }
                subscriptionStrip
                performanceStrip
                continueCard
                quickTools
                subjectsSection
                if !error.isEmpty { V3InlineMessage(text: error, icon: "wifi.exclamationmark", tone: .warning) }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 28)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("المرشد الوزاري")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                NavigationLink(destination: NotificationsView()) {
                    ZStack(alignment: .topTrailing) {
                        Image(systemName: "bell.fill")
                            .font(.headline)
                        if app.unreadNotifications > 0 {
                            Text("\(min(app.unreadNotifications, 99))")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(.white)
                                .padding(4)
                                .background(.red, in: Circle())
                                .offset(x: 9, y: -8)
                        }
                    }
                }
                .accessibilityLabel("الإشعارات")
            }
        }
        .refreshable { await refreshAll() }
        .task { await refreshAll() }
    }

    private var hero: some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(
                colors: [.murshidNavy, Color(red: 0.05, green: 0.23, blue: 0.52), .murshidBlue],
                startPoint: .topTrailing,
                endPoint: .bottomLeading
            )
            .overlay(alignment: .topTrailing) {
                Circle().fill(.white.opacity(0.07)).frame(width: 200, height: 200).offset(x: 60, y: -90)
            }

            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 10) {
                    Image(systemName: "graduationcap.fill")
                        .font(.title3)
                        .foregroundStyle(.white)
                        .frame(width: 42, height: 42)
                        .background(.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("أهلًا، \(firstName)")
                            .font(.title2.bold())
                            .foregroundStyle(.white)
                        Text(app.user?.grade ?? "")
                            .font(.subheadline)
                            .foregroundStyle(.white.opacity(0.78))
                    }
                    Spacer()
                    if app.subscribed {
                        Label("مشترك", systemImage: "checkmark.seal.fill")
                            .font(.caption.bold())
                            .foregroundStyle(.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 7)
                            .background(.white.opacity(0.13), in: Capsule())
                    }
                }

                Text(motivation)
                    .font(.headline)
                    .foregroundStyle(.white.opacity(0.94))
                    .lineLimit(2)

                HStack(spacing: 9) {
                    V3HeroMetric(title: "الدقة", value: "\(app.accuracy)%")
                    V3HeroMetric(title: "XP", value: "\(jInt(app.stats["xp"]))")
                    V3HeroMetric(title: "السلسلة", value: "\(jInt(app.stats["streak"])) يوم")
                }
            }
            .padding(20)
        }
        .frame(minHeight: 205)
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .shadow(color: Color.murshidNavy.opacity(0.15), radius: 20, y: 10)
        .padding(.top, 6)
    }

    private var offlineBanner: some View {
        V3InlineMessage(
            text: "أنت غير متصل الآن. سنعرض البيانات المحفوظة متى كانت متاحة.",
            icon: "wifi.slash",
            tone: .warning
        )
    }

    @ViewBuilder
    private var subscriptionStrip: some View {
        if app.subscribed {
            MurshidCard {
                HStack(spacing: 12) {
                    ZStack {
                        Circle().fill(Color.green.opacity(0.12)).frame(width: 45, height: 45)
                        Image(systemName: "checkmark.seal.fill").foregroundStyle(.green)
                    }
                    VStack(alignment: .leading, spacing: 3) {
                        Text("كل الأسئلة مفتوحة").font(.headline)
                        Text("اشتراكك فعّال — واصل التقدم بدون حدود الرصيد المجاني.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                }
            }
        } else {
            NavigationLink(destination: V31SubscriptionView()) {
                MurshidCard {
                    HStack(spacing: 13) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(Color.murshidGold.opacity(0.14))
                                .frame(width: 48, height: 48)
                            Image(systemName: "gift.fill").foregroundStyle(Color.murshidGold)
                        }
                        VStack(alignment: .leading, spacing: 5) {
                            Text("رصيد التجربة المجانية").font(.headline).foregroundStyle(.primary)
                            Text("متبقي \(app.freeRemaining ?? 0) من \(app.freeLimit) سؤال")
                                .font(.subheadline).foregroundStyle(.secondary)
                            ProgressView(value: Double(app.freeUsed), total: Double(max(1, app.freeLimit)))
                                .tint(.murshidGold)
                        }
                        Spacer()
                        Image(systemName: "chevron.left").foregroundStyle(.secondary)
                    }
                }
            }
            .buttonStyle(.plain)
        }
    }

    private var performanceStrip: some View {
        LazyVGrid(columns: columns, spacing: 12) {
            V3MetricCard(title: "الإجابات", value: "\(jInt(app.stats["total_answers"]))", icon: "checkmark.circle.fill")
            V3MetricCard(title: "اختبارات مكتملة", value: "\(jInt(app.stats["exams_completed"]))", icon: "flag.checkered")
            V3MetricCard(title: "هذا الأسبوع", value: "\(jInt(weekly["answers"]))", icon: "calendar")
            V3MetricCard(title: "مؤشر النجاح", value: "\(jInt(insights["success_index"]))%", icon: "sparkles")
        }
    }

    @ViewBuilder
    private var continueCard: some View {
        if loadingInsights {
            V3SkeletonCard(height: 130)
        } else if let topic = suggestedTopic {
            NavigationLink(destination: V3ExamView(topic: topic.topic, subject: topic.subject)) {
                MurshidCard {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Label("خطوتك التالية", systemImage: "play.circle.fill")
                                .font(.headline)
                                .foregroundStyle(Color.murshidBlue)
                            Spacer()
                            Text("مقترح لك").font(.caption.bold()).foregroundStyle(.secondary)
                        }
                        Text(topic.topic.name).font(.title3.bold()).foregroundStyle(.primary)
                        Text(topic.subject.name).font(.subheadline).foregroundStyle(.secondary)
                        HStack {
                            ProgressView(value: Double(topic.coverage), total: 100).tint(.murshidBlue)
                            Text("\(topic.coverage)%").font(.caption.bold().monospacedDigit()).foregroundStyle(.secondary)
                        }
                        Label("ابدأ الآن", systemImage: "arrow.left.circle.fill")
                            .font(.subheadline.bold())
                            .foregroundStyle(Color.murshidBlue)
                    }
                }
            }
            .buttonStyle(.plain)
        } else if !app.subjects.isEmpty {
            NavigationLink(destination: V3TopicsView(subject: app.subjects[0])) {
                MurshidCard {
                    HStack(spacing: 13) {
                        Image(systemName: "play.circle.fill").font(.largeTitle).foregroundStyle(Color.murshidBlue)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("ابدأ أول مراجعة").font(.headline).foregroundStyle(.primary)
                            Text("اختر موضوعك وسنحفظ تقدمك تلقائيًا.").font(.subheadline).foregroundStyle(.secondary)
                        }
                        Spacer(); Image(systemName: "chevron.left").foregroundStyle(.secondary)
                    }
                }
            }.buttonStyle(.plain)
        }
    }

    private var quickTools: some View {
        VStack(alignment: .leading, spacing: 12) {
            V3SectionHeader(title: "أدواتك السريعة", subtitle: "كل ما تحتاجه خلال ثوانٍ", icon: "bolt.fill")
            LazyVGrid(columns: columns, spacing: 12) {
                NavigationLink(destination: ReviewView()) {
                    V3ToolCard(title: "راجع أخطاءك", subtitle: "مراجعة ذكية", icon: "brain.head.profile")
                }
                NavigationLink(destination: V3FocusView()) {
                    V3ToolCard(title: "جلسة تركيز", subtitle: "25 · 45 · 60 دقيقة", icon: "timer")
                }
                NavigationLink(destination: V3ContestView()) {
                    V3ToolCard(title: "تحدي المليون", subtitle: "ترتيب ونقاط", icon: "trophy.fill")
                }
                NavigationLink(destination: StoriesView()) {
                    V3ToolCard(title: "غيّر جو", subtitle: "استراحة قصيرة", icon: "sparkles")
                }
            }
            .buttonStyle(.plain)
        }
    }

    private var subjectsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            V3SectionHeader(title: "موادك", subtitle: "اختر المادة ثم الموضوع", icon: "books.vertical.fill")
            ForEach(app.subjects) { subject in
                NavigationLink(destination: V3TopicsView(subject: subject)) {
                    V3SubjectRow(subject: subject)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var firstName: String {
        guard let value = app.user?.name.split(separator: " ").first else { return "طالبنا" }
        return String(value)
    }

    private var motivation: String {
        let total = jInt(app.stats["total_answers"])
        if app.accuracy >= 85 && total >= 10 { return "مستواك ممتاز. حافظ على إيقاعك وراجع الأخطاء القليلة." }
        if total > 0 { return "كل إجابة اليوم تقرّبك أكثر من الدرجة التي تريدها." }
        return "ابدأ بخطوة صغيرة اليوم، وسنبني تقدمك معك سؤالًا بعد سؤال."
    }

    private var suggestedTopic: (topic: Topic, subject: Subject, coverage: Int)? {
        let rows = jArray(insights["daily_topics"])
        guard let row = rows.first else { return nil }
        let sid = jInt(row["subject_id"])
        let sname = jString(row["subject_name"])
        let topic = Topic([
            "id": jInt(row["id"]),
            "name": jString(row["name"], default: jString(row["display_name"])),
            "count": jInt(row["questions_count"]),
            "theme": jString(row["theme"])
        ])
        let subject = app.subjects.first(where: { $0.id == sid }) ?? Subject(["id": sid, "name": sname])
        guard topic.id > 0, subject.id > 0 else { return nil }
        return (topic, subject, jInt(row["coverage"]))
    }

    private func refreshAll() async {
        await MainActor.run { error = "" }
        do {
            let b = try await APIClient.shared.bootstrap()
            let i = try await APIClient.shared.request("mobile/insights.php", cacheKey: "insights")
            let n = try await APIClient.shared.request("mobile/notifications.php")
            await MainActor.run {
                app.applyBootstrap(b)
                insights = i["insights"] as? JSON ?? [:]
                weekly = i["weekly"] as? JSON ?? [:]
                app.unreadNotifications = jInt(n["unread"])
                loadingInsights = false
            }
        } catch {
            do {
                let i = try await APIClient.shared.request("mobile/insights.php", cacheKey: "insights")
                await MainActor.run {
                    insights = i["insights"] as? JSON ?? [:]
                    weekly = i["weekly"] as? JSON ?? [:]
                    loadingInsights = false
                }
            } catch {
                await MainActor.run { loadingInsights = false; self.error = error.localizedDescription }
            }
        }
    }
}

// MARK: - Tests / Subjects

struct V3TestsView: View {
    @EnvironmentObject var app: AppSession
    @State private var search = ""

    private var filtered: [Subject] {
        let q = search.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return app.subjects }
        return app.subjects.filter { $0.name.localizedCaseInsensitiveContains(q) }
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 14) {
                V3IntroCard(
                    eyebrow: "اختباراتك",
                    title: "اختر المادة وابدأ بثقة",
                    text: "المواضيع مرتبة حسب المنهج، وتقدمك محفوظ داخل حسابك.",
                    icon: "checklist.checked"
                )

                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                    TextField("ابحث عن مادة", text: $search)
                        .textInputAutocapitalization(.never)
                }
                .padding(13)
                .background(Color.murshidSurface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                if filtered.isEmpty {
                    EmptyStateView(systemImage: "magnifyingglass", title: "لا توجد نتيجة", message: "جرّب كتابة اسم المادة بطريقة مختلفة.")
                        .frame(minHeight: 280)
                } else {
                    ForEach(Array(filtered.enumerated()), id: \.element.id) { index, subject in
                        NavigationLink(destination: V3TopicsView(subject: subject)) {
                            V3SubjectRow(subject: subject, number: index + 1)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(16)
            .padding(.bottom, 22)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("الاختبارات")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct V3TopicsView: View {
    let subject: Subject
    @State private var topics: [Topic] = []
    @State private var metrics: [Int: JSON] = [:]
    @State private var loading = true
    @State private var error = ""
    @State private var search = ""

    private var filteredTopics: [Topic] {
        let q = search.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return topics }
        return topics.filter { $0.name.localizedCaseInsensitiveContains(q) }
    }

    var body: some View {
        Group {
            if loading {
                ScrollView { LazyVStack(spacing: 13) { ForEach(0..<5, id: \.self) { _ in V3SkeletonCard(height: 105) } }.padding(16) }
                    .background(Color.murshidBackground)
            } else if !error.isEmpty {
                ErrorStateView(message: error, retry: { Task { await load() } })
            } else if topics.isEmpty {
                EmptyStateView(systemImage: "tray", title: "لا توجد مواضيع", message: "لا توجد مواضيع جاهزة للاختبار في هذه المادة حاليًا.")
            } else {
                ScrollView {
                    LazyVStack(spacing: 13) {
                        V3IntroCard(
                            eyebrow: subject.name,
                            title: "اختر الموضوع",
                            text: "شاهد تقدمك وعدد الأسئلة قبل البدء.",
                            icon: "book.pages.fill"
                        )
                        if filteredTopics.isEmpty {
                            EmptyStateView(systemImage: "magnifyingglass", title: "لا توجد نتيجة", message: "جرّب البحث باسم آخر من مواضيع \(subject.name).")
                                .frame(minHeight: 240)
                        } else {
                            ForEach(Array(filteredTopics.enumerated()), id: \.element.id) { index, topic in
                                NavigationLink(destination: V3ExamView(topic: topic, subject: subject)) {
                                    topicRow(topic, index: index)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .padding(16)
                    .padding(.bottom, 24)
                }
                .background(Color.murshidBackground)
            }
        }
        .navigationTitle(subject.name)
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $search, placement: .navigationBarDrawer(displayMode: .always), prompt: "ابحث عن موضوع")
        .task { await load() }
        .refreshable { await load() }
    }

    private func topicRow(_ topic: Topic, index: Int) -> some View {
        let m = metrics[topic.id] ?? [:]
        let coverage = jInt(m["coverage"])
        let accuracy = jInt(m["accuracy"])
        let completed = jBool(m["completed"])
        return MurshidCard {
            VStack(alignment: .leading, spacing: 11) {
                HStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 13, style: .continuous)
                            .fill(completed ? Color.green.opacity(0.12) : Color.murshidBlue.opacity(0.10))
                            .frame(width: 47, height: 47)
                        Image(systemName: completed ? "checkmark.seal.fill" : "doc.text.fill")
                            .foregroundStyle(completed ? .green : Color.murshidBlue)
                    }
                    VStack(alignment: .leading, spacing: 4) {
                        Text(topic.name).font(.headline).foregroundStyle(.primary)
                        Text("\(topic.count) سؤال")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    if completed {
                        Text("مكتمل").font(.caption.bold()).foregroundStyle(.green)
                    } else if coverage > 0 {
                        Text("قيد التقدم").font(.caption.bold()).foregroundStyle(Color.murshidBlue)
                    } else {
                        Text("لم يبدأ").font(.caption.bold()).foregroundStyle(.secondary)
                    }
                    Image(systemName: "chevron.left").font(.caption.bold()).foregroundStyle(.tertiary)
                }
                if coverage > 0 || accuracy > 0 {
                    HStack(spacing: 10) {
                        ProgressView(value: Double(coverage), total: 100).tint(completed ? .green : .murshidBlue)
                        Text("\(coverage)%").font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                        if jInt(m["attempts"]) > 0 {
                            Text("دقة \(accuracy)%").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
    }

    private func load() async {
        await MainActor.run { loading = true; error = "" }
        do {
            let t = try await APIClient.shared.request(
                "mobile/topics.php",
                query: [URLQueryItem(name: "subject_id", value: "\(subject.id)")],
                cacheKey: "topics-\(subject.id)"
            )
            let i = try await APIClient.shared.request("mobile/insights.php", cacheKey: "insights")
            let loaded = jArray(t["topics"]).map(Topic.init)
            let ins = i["insights"] as? JSON ?? [:]
            var map: [Int: JSON] = [:]
            for row in jArray(ins["chapters"]) where jInt(row["subject_id"]) == subject.id {
                map[jInt(row["id"])] = row
            }
            await MainActor.run { topics = loaded; metrics = map; loading = false }
        } catch {
            await MainActor.run { loading = false; self.error = error.localizedDescription }
        }
    }
}

// MARK: - Success Hub

struct V3SuccessView: View {
    @EnvironmentObject var app: AppSession
    @State private var insights: JSON = [:]
    @State private var weekly: JSON = [:]
    @State private var loading = true
    @State private var error = ""

    var body: some View {
        Group {
            if loading {
                ScrollView { LazyVStack(spacing: 13) { V3SkeletonCard(height: 220); V3SkeletonCard(height: 125); V3SkeletonCard(height: 180) }.padding(16) }
                    .background(Color.murshidBackground)
            } else if !error.isEmpty {
                ErrorStateView(message: error, retry: { Task { await load() } })
            } else {
                ScrollView {
                    LazyVStack(spacing: 16) {
                        successHero
                        weeklyCard
                        strengthCards
                        dailyPlan
                        improvementTools
                    }
                    .padding(16)
                    .padding(.bottom, 26)
                }
                .background(Color.murshidBackground)
            }
        }
        .navigationTitle("النجاح")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
    }

    private var successHero: some View {
        MurshidCard {
            HStack(spacing: 20) {
                V3ProgressRing(value: jInt(insights["success_index"]), label: "مؤشر النجاح")
                VStack(alignment: .leading, spacing: 9) {
                    Text("صورتك الدراسية الآن").font(.title3.bold())
                    Label("الدقة \(jInt(insights["accuracy"]))%", systemImage: "scope")
                    Label("التغطية \(jInt(insights["coverage"]))%", systemImage: "books.vertical")
                    Label("\(jInt(insights["distinct_answered"])) سؤال مختلف", systemImage: "checkmark.circle")
                }
                .font(.subheadline)
                .foregroundStyle(.secondary)
                Spacer(minLength: 0)
            }
        }
    }

    private var weeklyCard: some View {
        MurshidCard {
            VStack(alignment: .leading, spacing: 12) {
                V3SectionHeader(title: "آخر 7 أيام", subtitle: "نشاطك الحديث", icon: "calendar.badge.clock")
                HStack(spacing: 10) {
                    V3MiniMetric(value: "\(jInt(weekly["answers"]))", label: "إجابة")
                    V3MiniMetric(value: "\(jInt(weekly["accuracy"]))%", label: "دقة")
                    V3MiniMetric(value: signedChange, label: "عن الأسبوع السابق")
                }
            }
        }
    }

    private var strengthCards: some View {
        VStack(alignment: .leading, spacing: 12) {
            V3SectionHeader(title: "نقاط القوة والتحسين", subtitle: "اعرف أين تركز", icon: "target")
            if let strong = insights["strongest_subject"] as? JSON {
                V3InsightRow(title: "أقوى مادة", value: jString(strong["name"]), detail: "دقة \(jInt(strong["accuracy"]))%", icon: "arrow.up.right.circle.fill", positive: true)
            }
            if let weak = insights["weakest_subject"] as? JSON {
                V3InsightRow(title: "تحتاج متابعة", value: jString(weak["name"]), detail: "دقة \(jInt(weak["accuracy"]))%", icon: "scope", positive: false)
            }
        }
    }

    @ViewBuilder
    private var dailyPlan: some View {
        let topics = jArray(insights["daily_topics"])
        if !topics.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                V3SectionHeader(title: "خطة اليوم", subtitle: "\(jInt(insights["daily_questions"], default: 20)) سؤال مقترح", icon: "list.bullet.clipboard.fill")
                ForEach(Array(topics.prefix(3).enumerated()), id: \.offset) { index, row in
                    let topic = Topic(["id": jInt(row["id"]), "name": jString(row["name"]), "count": jInt(row["questions_count"]), "theme": jString(row["theme"])])
                    let sid = jInt(row["subject_id"])
                    let subject = app.subjects.first(where: { $0.id == sid }) ?? Subject(["id": sid, "name": jString(row["subject_name"])])
                    NavigationLink(destination: V3ExamView(topic: topic, subject: subject)) {
                        MurshidCard {
                            HStack(spacing: 12) {
                                Text("\(index + 1)").font(.headline.monospacedDigit()).foregroundStyle(Color.murshidBlue).frame(width: 38, height: 38).background(Color.murshidBlue.opacity(0.10), in: Circle())
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(topic.name).font(.headline).foregroundStyle(.primary)
                                    Text(subject.name + " • إنجاز \(jInt(row["coverage"]))%").font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer(); Image(systemName: "play.circle.fill").foregroundStyle(Color.murshidBlue)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var improvementTools: some View {
        VStack(alignment: .leading, spacing: 12) {
            V3SectionHeader(title: "طوّر مستواك", subtitle: "أدوات مبنية على بياناتك", icon: "wand.and.stars")
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                NavigationLink(destination: ReviewView()) { V3ToolCard(title: "المراجعة الذكية", subtitle: "آخر الأخطاء", icon: "brain.head.profile") }
                NavigationLink(destination: StudyPlanView()) { V3ToolCard(title: "خطة الدراسة", subtitle: "هدف يومي", icon: "calendar") }
                NavigationLink(destination: AchievementsView()) { V3ToolCard(title: "الإنجازات", subtitle: "XP والمستوى", icon: "medal.fill") }
                NavigationLink(destination: V3ContestView()) { V3ToolCard(title: "تحدي المليون", subtitle: "المتصدرون", icon: "trophy.fill") }
            }
            .buttonStyle(.plain)
        }
    }

    private var signedChange: String {
        let change = jInt(weekly["change"])
        if change > 0 { return "+\(change)" }
        return "\(change)"
    }

    private func load() async {
        await MainActor.run { loading = true; error = "" }
        do {
            let d = try await APIClient.shared.request("mobile/insights.php", cacheKey: "insights")
            await MainActor.run {
                insights = d["insights"] as? JSON ?? [:]
                weekly = d["weekly"] as? JSON ?? [:]
                loading = false
            }
        } catch {
            await MainActor.run { loading = false; self.error = error.localizedDescription }
        }
    }
}

// MARK: - Focus session

struct V3FocusView: View {
    @EnvironmentObject var app: AppSession
    @State private var selectedMinutes = 25
    @State private var running = false
    @State private var endAt: Date?
    @State private var remainingSeconds = 25 * 60
    @State private var message = "اختر المدة وابدأ جلسة هادئة بلا مشتتات."
    @State private var busy = false
    private let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                V3IntroCard(eyebrow: "جلسة تركيز", title: "وقت هادئ لدراستك", text: "ابدأ جلسة، اترك الهاتف على هذه الشاشة، وسنسجل دقائقك المكتملة.", icon: "timer")

                MurshidCard {
                    VStack(spacing: 20) {
                        ZStack {
                            Circle().stroke(Color.primary.opacity(0.06), lineWidth: 14).frame(width: 205, height: 205)
                            Circle()
                                .trim(from: 0, to: progress)
                                .stroke(Color.murshidBlue, style: StrokeStyle(lineWidth: 14, lineCap: .round))
                                .rotationEffect(.degrees(-90))
                                .frame(width: 205, height: 205)
                                .animation(.linear(duration: 0.2), value: remainingSeconds)
                            VStack(spacing: 5) {
                                Text(clockText).font(.system(size: 42, weight: .bold, design: .rounded)).monospacedDigit()
                                Text(running ? "ركّز على هدف واحد" : "جاهز للبدء").font(.caption).foregroundStyle(.secondary)
                            }
                        }

                        Picker("المدة", selection: $selectedMinutes) {
                            Text("25 دقيقة").tag(25)
                            Text("45 دقيقة").tag(45)
                            Text("60 دقيقة").tag(60)
                        }
                        .pickerStyle(.segmented)
                        .disabled(running || busy)
                        .onChange(of: selectedMinutes) { value in if !running { remainingSeconds = value * 60 } }

                        if running {
                            Button("إنهاء الجلسة وحفظها") { Task { await finish() } }
                                .buttonStyle(PrimaryButtonStyle())
                                .disabled(busy)
                        } else {
                            Button("ابدأ جلسة التركيز") { Task { await start() } }
                                .buttonStyle(PrimaryButtonStyle())
                                .disabled(busy)
                        }

                        Text(message).font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
                    }
                }
            }
            .padding(16)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("جلسة التركيز")
        .navigationBarTitleDisplayMode(.inline)
        .onReceive(timer) { now in
            guard running, let endAt else { return }
            remainingSeconds = max(0, Int(endAt.timeIntervalSince(now).rounded(.up)))
            if remainingSeconds == 0 { Task { await finish() } }
        }
    }

    private var progress: CGFloat {
        let total = max(1, selectedMinutes * 60)
        return CGFloat(total - remainingSeconds) / CGFloat(total)
    }

    private var clockText: String {
        String(format: "%02d:%02d", remainingSeconds / 60, remainingSeconds % 60)
    }

    private func start() async {
        busy = true
        do {
            _ = try await APIClient.shared.request("mobile/study-session.php", method: "POST", body: ["csrf": app.csrf, "action": "start", "planned": selectedMinutes])
            await MainActor.run {
                remainingSeconds = selectedMinutes * 60
                endAt = Date().addingTimeInterval(TimeInterval(remainingSeconds))
                running = true
                busy = false
                message = "بدأت الجلسة. أغلق المشتتات وركز على هدف واحد فقط."
                haptic()
            }
        } catch {
            await MainActor.run { busy = false; message = error.localizedDescription; haptic(.error) }
        }
    }

    private func finish() async {
        guard running else { return }
        busy = true
        do {
            let d = try await APIClient.shared.request("mobile/study-session.php", method: "POST", body: ["csrf": app.csrf, "action": "finish"])
            let minutes = jInt(d["minutes"])
            let b = try? await APIClient.shared.bootstrap()
            await MainActor.run {
                if let b { app.applyBootstrap(b) }
                running = false
                endAt = nil
                remainingSeconds = selectedMinutes * 60
                busy = false
                message = "أحسنت! تم تسجيل \(minutes) دقيقة في نشاطك الدراسي."
                haptic()
            }
        } catch {
            await MainActor.run { busy = false; message = error.localizedDescription; haptic(.error) }
        }
    }
}

// MARK: - Contest & seasonal ranking

struct V3ContestView: View {
    @State private var contest: JSON = [:]
    @State private var season: JSON = [:]
    @State private var loading = true
    @State private var error = ""

    var body: some View {
        Group {
            if loading { LoadingView(text: "جاري تحميل الترتيب…") }
            else if !error.isEmpty { ErrorStateView(message: error, retry: { Task { await load() } }) }
            else {
                ScrollView {
                    LazyVStack(spacing: 16) {
                        V3IntroCard(eyebrow: "تحدي المليون", title: "تقدمك بين الطلاب", text: "الترتيب يعتمد على نقاط المنصة والإجابات الصحيحة ضمن فترة التحدي.", icon: "trophy.fill")
                        myRankCard
                        leaderboard(title: "متصدرو تحدي المليون", rows: jArray(contest["leaderboard"]), rankKey: "rank", xpKey: "xp")
                        leaderboard(title: "ترتيب الموسم", rows: jArray(season["leaderboard"]), rankKey: "rank", xpKey: "xp")
                    }
                    .padding(16)
                    .padding(.bottom, 24)
                }
                .background(Color.murshidBackground)
            }
        }
        .navigationTitle("التحدي")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
    }

    private var myRankCard: some View {
        MurshidCard {
            HStack(spacing: 14) {
                V3RankingAvatar(url: jString((contest["mine"] as? JSON)?["avatar_url"], default: appAvatarURL), size: 54)
                VStack(alignment: .leading, spacing: 5) {
                    Text("مركزك الحالي").font(.subheadline).foregroundStyle(.secondary)
                    if let mine = contest["mine"] as? JSON {
                        Text("#\(jInt(mine["rank"]))").font(.largeTitle.bold()).foregroundStyle(Color.murshidBlue)
                        Text("\(jInt(mine["xp"])) XP · \(jInt(mine["correct"])) إجابة صحيحة").font(.caption).foregroundStyle(.secondary)
                    } else {
                        Text("ابدأ الإجابة لتدخل الترتيب").font(.headline)
                    }
                }
                Spacer()
                if jBool((contest["config"] as? JSON)?["active"]) {
                    Text("نشط").font(.caption.bold()).foregroundStyle(.green).padding(.horizontal, 10).padding(.vertical, 6).background(Color.green.opacity(0.10), in: Capsule())
                }
            }
        }
    }

    private var appAvatarURL: String { AppSession.shared.avatarURL }

    private func leaderboard(title: String, rows: [JSON], rankKey: String, xpKey: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            V3SectionHeader(title: title, subtitle: rows.isEmpty ? "لا توجد بيانات بعد" : "أعلى المراكز", icon: "list.number")
            if rows.isEmpty {
                MurshidCard { Text("سيظهر الترتيب هنا عند توفر البيانات.").foregroundStyle(.secondary) }
            } else {
                ForEach(Array(rows.prefix(10).enumerated()), id: \.offset) { _, row in
                    MurshidCard {
                        HStack(spacing: 12) {
                            Text("#\(jInt(row[rankKey]))").font(.headline.monospacedDigit()).foregroundStyle(Color.murshidBlue).frame(width: 42)
                            V3RankingAvatar(url: jString(row["avatar_url"]), size: 40)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(jString(row["name"])).font(.headline)
                                if !jString(row["grade"]).isEmpty { Text(jString(row["grade"])).font(.caption).foregroundStyle(.secondary) }
                            }
                            Spacer()
                            Text("\(jInt(row[xpKey])) XP").font(.subheadline.bold()).foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
    }

    private func load() async {
        await MainActor.run { loading = true; error = "" }
        do {
            let contestData = try await APIClient.shared.request("mobile/contest.php")
            let seasonData = try await APIClient.shared.request("mobile/season.php")
            await MainActor.run { contest = contestData; season = seasonData; loading = false }
        } catch {
            await MainActor.run { loading = false; self.error = error.localizedDescription }
        }
    }
}

private struct V3RankingAvatar: View {
    let url: String
    let size: CGFloat
    var body: some View {
        Group {
            if let parsed = URL(string: url), parsed.scheme == "https" {
                AsyncImage(url: parsed) { phase in
                    if case .success(let image) = phase { image.resizable().scaledToFill() }
                    else { Image(systemName: "person.crop.circle.fill").resizable().foregroundStyle(.secondary) }
                }
            } else {
                Image(systemName: "person.crop.circle.fill").resizable().foregroundStyle(.secondary)
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }
}

// MARK: - V3 Visual Components

private struct V3HeroMetric: View {
    let title: String
    let value: String
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(.subheadline.bold()).foregroundStyle(.white)
            Text(title).font(.caption2).foregroundStyle(.white.opacity(0.72))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(.white.opacity(0.09), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

struct V3MetricCard: View {
    let title: String
    let value: String
    let icon: String
    var body: some View {
        MurshidCard {
            VStack(alignment: .leading, spacing: 9) {
                Image(systemName: icon)
                    .font(.headline)
                    .foregroundStyle(Color.murshidBlue)
                    .frame(width: 36, height: 36)
                    .background(Color.murshidBlue.opacity(0.10), in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                Text(value).font(.title2.bold()).foregroundStyle(.primary)
                Text(title).font(.caption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

struct V3ToolCard: View {
    let title: String
    let subtitle: String
    let icon: String
    var body: some View {
        MurshidCard {
            VStack(alignment: .leading, spacing: 8) {
                Image(systemName: icon).font(.title2).foregroundStyle(Color.murshidBlue)
                Text(title).font(.headline).foregroundStyle(.primary)
                Text(subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(2)
            }
            .frame(maxWidth: .infinity, minHeight: 96, alignment: .leading)
        }
    }
}

struct V3SubjectRow: View {
    let subject: Subject
    var number: Int? = nil
    var body: some View {
        MurshidCard {
            HStack(spacing: 13) {
                ZStack {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(Color.murshidBlue.opacity(0.10))
                        .frame(width: 50, height: 50)
                    if let number {
                        Text("\(number)").font(.headline.monospacedDigit()).foregroundStyle(Color.murshidBlue)
                    } else {
                        Image(systemName: subjectSymbol(subject.name)).font(.headline).foregroundStyle(Color.murshidBlue)
                    }
                }
                VStack(alignment: .leading, spacing: 3) {
                    Text(subject.name).font(.headline).foregroundStyle(.primary)
                    Text("المواضيع والاختبارات").font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.left").font(.caption.bold()).foregroundStyle(.tertiary)
            }
        }
    }

    private func subjectSymbol(_ name: String) -> String {
        if name.contains("رياض") { return "function" }
        if name.contains("فيزياء") { return "atom" }
        if name.contains("كيمي") { return "flask.fill" }
        if name.contains("أحياء") { return "leaf.fill" }
        if name.contains("إنك") || name.contains("انك") { return "globe" }
        if name.contains("إسلام") || name.contains("اسلام") { return "book.closed.fill" }
        if name.contains("تاريخ") || name.contains("اجتماع") || name.contains("جغراف") { return "map.fill" }
        return "book.closed.fill"
    }
}

struct V3SectionHeader: View {
    let title: String
    let subtitle: String
    let icon: String
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon).foregroundStyle(Color.murshidBlue).frame(width: 30, height: 30).background(Color.murshidBlue.opacity(0.09), in: RoundedRectangle(cornerRadius: 9))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.title3.bold())
                Text(subtitle).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
        }
    }
}

struct V3IntroCard: View {
    let eyebrow: String
    let title: String
    let text: String
    let icon: String
    var body: some View {
        MurshidCard {
            HStack(alignment: .top, spacing: 14) {
                Image(systemName: icon).font(.title2).foregroundStyle(.white).frame(width: 52, height: 52).background(LinearGradient(colors: [.murshidBlue, .murshidNavy], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                VStack(alignment: .leading, spacing: 5) {
                    Text(eyebrow).font(.caption.bold()).foregroundStyle(Color.murshidBlue)
                    Text(title).font(.title3.bold())
                    Text(text).font(.subheadline).foregroundStyle(.secondary).lineSpacing(3)
                }
                Spacer(minLength: 0)
            }
        }
    }
}

struct V3ProgressRing: View {
    let value: Int
    let label: String
    var body: some View {
        ZStack {
            Circle().stroke(Color.primary.opacity(0.06), lineWidth: 12)
            Circle()
                .trim(from: 0, to: CGFloat(max(0, min(100, value))) / 100)
                .stroke(Color.murshidBlue, style: StrokeStyle(lineWidth: 12, lineCap: .round))
                .rotationEffect(.degrees(-90))
            VStack(spacing: 2) {
                Text("\(value)%").font(.title2.bold().monospacedDigit())
                Text(label).font(.caption2).foregroundStyle(.secondary)
            }
        }
        .frame(width: 126, height: 126)
    }
}

private struct V3MiniMetric: View {
    let value: String
    let label: String
    var body: some View {
        VStack(spacing: 4) {
            Text(value).font(.title3.bold()).foregroundStyle(Color.murshidBlue)
            Text(label).font(.caption2).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(Color.primary.opacity(0.035), in: RoundedRectangle(cornerRadius: 13, style: .continuous))
    }
}

private struct V3InsightRow: View {
    let title: String
    let value: String
    let detail: String
    let icon: String
    let positive: Bool
    var body: some View {
        MurshidCard {
            HStack(spacing: 12) {
                Image(systemName: icon).font(.title2).foregroundStyle(positive ? Color.green : Color.orange).frame(width: 46, height: 46).background((positive ? Color.green : Color.orange).opacity(0.10), in: RoundedRectangle(cornerRadius: 13))
                VStack(alignment: .leading, spacing: 3) {
                    Text(title).font(.caption).foregroundStyle(.secondary)
                    Text(value).font(.headline)
                    Text(detail).font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
            }
        }
    }
}

enum V3MessageTone { case info, warning, success }

struct V3InlineMessage: View {
    let text: String
    let icon: String
    let tone: V3MessageTone
    private var color: Color {
        switch tone { case .info: return .murshidBlue; case .warning: return .orange; case .success: return .green }
    }
    var body: some View {
        Label(text, systemImage: icon)
            .font(.footnote.weight(.medium))
            .foregroundStyle(color)
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(color.opacity(0.09), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

struct V3SkeletonCard: View {
    let height: CGFloat
    @State private var phase = false
    var body: some View {
        RoundedRectangle(cornerRadius: 22, style: .continuous)
            .fill(Color.primary.opacity(phase ? 0.07 : 0.035))
            .frame(height: height)
            .overlay(alignment: .leading) {
                VStack(alignment: .leading, spacing: 10) {
                    RoundedRectangle(cornerRadius: 6).fill(Color.primary.opacity(0.08)).frame(width: 150, height: 16)
                    RoundedRectangle(cornerRadius: 6).fill(Color.primary.opacity(0.05)).frame(width: 220, height: 12)
                }.padding(18)
            }
            .onAppear { withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { phase = true } }
            .accessibilityHidden(true)
    }
}

struct V3ExamView: View {
    let topic: Topic
    let subject: Subject

    @EnvironmentObject var app: AppSession
    @Environment(\.dismiss) private var dismiss

    @State private var questions: [Question] = []
    @State private var current = 0
    @State private var loading = true
    @State private var submitting = false
    @State private var error = ""
    @State private var answers: [Int: String] = [:]
    @State private var interactive: [Int: [String: String]] = [:]
    @State private var matches: [Int: [Int: MatchDraft]] = [:]
    @State private var english: [Int: [String: String]] = [:]
    @State private var grades: [Int: JSON] = [:]
    @State private var showSubscription = false
    @State private var alertMessage = ""
    @State private var showAlert = false
    @State private var showResult = false
    @State private var startedAt = Date()
    @State private var showReport = false
    @State private var reportReason = "wrong_answer"
    @State private var reportMessage = ""
    @State private var reporting = false
    @State private var showFilters = false
    @State private var filterType = "all"
    @State private var filterYear = "all"
    @State private var filterRound = "all"
    @State private var filterDifficulty = "all"
    @State private var filterState = "all"
    @State private var filterShuffle = false
    @State private var availableFilterTypes: [String] = []
    @State private var availableFilterYears: [String] = []
    @State private var availableFilterRounds: [String] = []
    @State private var availableFilterDifficulties: [String] = []
    @State private var showNote = false
    @State private var showMoreQuestions = false
    @State private var noteText = ""
    @State private var savingNote = false
    @FocusState private var textAnswerFocused: Bool

    var body: some View {
        Group {
            if loading {
                ScrollView {
                    LazyVStack(spacing: 14) {
                        V3SkeletonCard(height: 72)
                        V3SkeletonCard(height: 155)
                        V3SkeletonCard(height: 230)
                    }.padding(16)
                }.background(Color.murshidBackground)
            } else if !error.isEmpty && questions.isEmpty {
                ErrorStateView(message: error, retry: { Task { await load() } })
            } else if questions.isEmpty {
                EmptyStateView(
                    systemImage: "doc.questionmark",
                    title: "لا توجد أسئلة متاحة",
                    message: app.subscribed ? "لا توجد أسئلة منشورة في هذا الموضوع حاليًا." : "قد تكون استهلكت رصيدك المجاني. يمكنك الاشتراك لفتح بقية الأسئلة."
                )
            } else if showResult {
                V3ExamResultView(
                    topic: topic,
                    subject: subject,
                    answered: answeredCount,
                    correct: correctCount,
                    total: questions.count,
                    duration: Date().timeIntervalSince(startedAt),
                    onReview: { showResult = false; current = firstWrongIndex ?? 0 },
                    onDone: { dismiss() }
                )
            } else {
                examContent
            }
        }
        .navigationTitle(showResult ? "نتيجة الاختبار" : topic.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .toolbar {
            if !questions.isEmpty && !showResult {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button(action: toggleFavorite) {
                        Image(systemName: questions[current].favorite ? "heart.fill" : "heart")
                            .foregroundStyle(questions[current].favorite ? .red : Color.murshidBlue)
                    }
                    .disabled(submitting)
                    .accessibilityLabel(questions[current].favorite ? "إزالة من المفضلة" : "إضافة إلى المفضلة")

                    Button { showFilters = true } label: {
                        Image(systemName: activeFilterCount > 0 ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
                    }
                    .disabled(submitting)
                    .accessibilityLabel("تصفية الأسئلة")

                    Menu {
                        Button {
                            Task { await loadCurrentNote() }
                        } label: {
                            Label("ملاحظتي الخاصة", systemImage: "note.text")
                        }
                        Button { showReport = true } label: {
                            Label("الإبلاغ عن السؤال", systemImage: "exclamationmark.bubble")
                        }
                        Button { showMoreQuestions = true } label: {
                            Label("طلب أسئلة جديدة", systemImage: "plus.bubble")
                        }
                        ShareLink(item: murshidQuestionDisplayText(questions[current].text) + "\n\nمنصة المرشد الوزاري") {
                            Label("مشاركة السؤال", systemImage: "square.and.arrow.up")
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                    .disabled(submitting)
                    .accessibilityLabel("أدوات السؤال")
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("تم") { textAnswerFocused = false }
                        .font(.headline)
                }
            }
        }
        .task { await load() }
        .alert("المرشد الوزاري", isPresented: $showAlert) {
            Button("حسنًا", role: .cancel) {}
        } message: { Text(alertMessage) }
        .sheet(isPresented: $showSubscription) {
            NavigationStack {
                V31SubscriptionView()
                    .toolbar { ToolbarItem(placement: .topBarLeading) { Button("إغلاق") { showSubscription = false } } }
            }
            .environmentObject(app)
        }
        .sheet(isPresented: $showReport) { reportSheet }
        .sheet(isPresented: $showFilters) {
            V40ExamFilterSheet(
                type: $filterType,
                year: $filterYear,
                round: $filterRound,
                difficulty: $filterDifficulty,
                state: $filterState,
                shuffle: $filterShuffle,
                availableTypes: availableFilterTypes,
                availableYears: availableFilterYears,
                availableRounds: availableFilterRounds,
                availableDifficulties: availableFilterDifficulties,
                onApply: { Task { await load() } }
            )
        }
        .sheet(isPresented: $showNote) { noteSheet }
        .sheet(isPresented: $showMoreQuestions) {
            NavigationStack {
                V40MoreQuestionsRequestView(topic: topic, subject: subject)
                    .toolbar {
                        ToolbarItem(placement: .topBarLeading) {
                            Button("إغلاق") { showMoreQuestions = false }
                        }
                    }
            }
            .environmentObject(app)
        }
    }

    private var examContent: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(spacing: 15) {
                        progressHeader.id("top")
                        questionCard(questions[current])
                        answerArea(questions[current])
                        feedbackArea(questions[current])
                            .id("feedback")
                            .transition(.move(edge: .bottom).combined(with: .opacity))
                        if !error.isEmpty {
                            V3InlineMessage(text: error, icon: "exclamationmark.triangle.fill", tone: .warning)
                        }
                    }
                    .padding(16)
                }
                .onChange(of: current) { _ in
                    withAnimation(.easeOut(duration: 0.22)) { proxy.scrollTo("top", anchor: .top) }
                }
                .onChange(of: grades.count) { _ in
                    guard !submitting else { return }
                    withAnimation(.easeInOut(duration: 0.28)) { proxy.scrollTo("feedback", anchor: .center) }
                }
            }
            Divider()
            bottomBar
        }
        .background(Color.murshidBackground)
    }

    private var progressHeader: some View {
        MurshidCard {
            VStack(spacing: 10) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(subject.name).font(.caption.bold()).foregroundStyle(Color.murshidBlue)
                        Text(topic.name).font(.subheadline.weight(.semibold)).lineLimit(1)
                    }
                    Spacer()
                    Text("\(current + 1) / \(questions.count)")
                        .font(.subheadline.bold().monospacedDigit())
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(Color.murshidBlue.opacity(0.09), in: Capsule())
                }

                ProgressView(value: Double(current + 1), total: Double(max(1, questions.count)))
                    .tint(.murshidBlue)

                HStack {
                    Label("تمت الإجابة: \(answeredCount)", systemImage: "checkmark.circle")
                    Spacer()
                    if !app.subscribed {
                        Button { showSubscription = true } label: {
                            Label("متبقي \(app.freeRemaining ?? 0)", systemImage: "gift.fill")
                        }
                        .foregroundStyle(Color.murshidGold)
                    } else {
                        Label("مشترك", systemImage: "checkmark.seal.fill").foregroundStyle(.green)
                    }
                }
                .font(.caption.weight(.medium))
                .foregroundStyle(.secondary)
            }
        }
    }

    private func questionCard(_ q: Question) -> some View {
        MurshidCard {
            VStack(alignment: .leading, spacing: 13) {
                HStack(spacing: 8) {
                    Text("سؤال \(current + 1)")
                        .font(.caption.bold())
                        .foregroundStyle(Color.murshidBlue)
                    Text("وزاري")
                        .font(.caption.bold())
                        .foregroundStyle(Color.murshidBlue)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.murshidBlue.opacity(0.10), in: Capsule())
                    Spacer()
                }
                Text(murshidQuestionDisplayText(q.text))
                    .font(.title3.weight(.semibold))
                    .lineSpacing(5)
                    .fixedSize(horizontal: false, vertical: true)
                    .textSelection(.enabled)
                let meta = [q.examYear, q.examRound].filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
                if !meta.isEmpty {
                    Label(meta.joined(separator: " • "), systemImage: "calendar")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    @ViewBuilder
    private func answerArea(_ q: Question) -> some View {
        MurshidCard {
            let options = visibleOptions(q)
            VStack(alignment: .leading, spacing: 13) {
                HStack {
                    Label("إجابتك", systemImage: "pencil.and.list.clipboard")
                        .font(.headline)
                    Spacer()
                    if isAnswered(q) {
                        Text("تم الحفظ").font(.caption.bold()).foregroundStyle(.green)
                    }
                }

                if !options.isEmpty {
                    VStack(spacing: 10) {
                        ForEach(Array(options.enumerated()), id: \.element.id) { index, option in
                            optionButton(q: q, option: option, index: index)
                        }
                    }
                } else if isAnswered(q) {
                    Text(answerDisplay(q))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.primary.opacity(0.035), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .textSelection(.enabled)
                } else if let em = q.englishMatch {
                    EnglishMatchInput(definition: em, values: bindingEnglish(q.id))
                } else if q.type == "match" && !q.matchItems.isEmpty {
                    MatchInput(items: q.matchItems, drafts: bindingMatches(q.id))
                } else if !q.stages.isEmpty {
                    InteractiveInput(stages: q.stages, values: bindingInteractive(q.id))
                } else {
                    TextEditor(text: bindingAnswer(q.id))
                        .focused($textAnswerFocused)
                        .frame(minHeight: 120)
                        .padding(10)
                        .scrollContentBackground(.hidden)
                        .background(Color(uiColor: .tertiarySystemFill), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }

                if !isAnswered(q) && options.isEmpty {
                    Button(action: { submitCurrent() }) {
                        HStack(spacing: 9) {
                            if submitting { ProgressView().tint(.white) }
                            Image(systemName: submitting ? "hourglass" : "paperplane.fill")
                            Text(submitting ? "جاري تصحيح الإجابة…" : "إرسال الإجابة")
                        }
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    .disabled(submitting || !hasAnswer(q))
                    .opacity(hasAnswer(q) ? 1 : 0.55)

                } else if !isAnswered(q) && !options.isEmpty && submitting {
                    HStack(spacing: 8) {
                        ProgressView()
                        Text("جاري تصحيح اختيارك…").font(.caption).foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .center)
                }
            }
        }
    }

    private func optionButton(q: Question, option: QuestionOption, index: Int) -> some View {
        let selected = answers[q.id] == option.text
        let graded = isAnswered(q)
        let rawCorrectText = jString(grades[q.id]?["correct_answer"], default: q.correctAnswer)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let correctText = murshidEffectiveCorrectAnswer(question: q.text, serverAnswer: rawCorrectText)
        let optionText = option.text.trimmingCharacters(in: .whitespacesAndNewlines)
        let correctOption = graded && !correctText.isEmpty && murshidAnswerKey(optionText) == murshidAnswerKey(correctText)
        let wrongSelected = graded && selected && !correctOption
        let accent: Color = correctOption ? .green : (wrongSelected ? .red : Color.murshidBlue)
        let icon = correctOption ? "checkmark.circle.fill" : (wrongSelected ? "xmark.circle.fill" : (selected ? "checkmark.circle.fill" : "circle"))

        return Button {
            guard !graded, !submitting else { return }
            answers[q.id] = option.text
            selectionHaptic()
            submitCurrent(answerOverride: option.text)
        } label: {
            HStack(spacing: 12) {
                Text(optionLetter(index))
                    .font(.subheadline.bold())
                    .frame(width: 34, height: 34)
                    .foregroundStyle((selected || correctOption) ? .white : accent)
                    .background((selected || correctOption) ? accent : accent.opacity(0.10), in: Circle())
                Text(option.text)
                    .font(.body.weight(.medium))
                    .multilineTextAlignment(.leading)
                    .foregroundStyle(.primary)
                Spacer(minLength: 0)
                Image(systemName: icon)
                    .foregroundStyle((selected || correctOption) ? accent : Color.secondary)
            }
            .padding(12)
            .background((selected || correctOption) ? accent.opacity(0.10) : Color.primary.opacity(0.025), in: RoundedRectangle(cornerRadius: 15, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 15, style: .continuous).stroke((selected || correctOption) ? accent.opacity(0.42) : Color.primary.opacity(0.05), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(graded || submitting)
        .animation(.easeInOut(duration: 0.2), value: graded)
    }

    @ViewBuilder
    private func feedbackArea(_ q: Question) -> some View {
        if isAnswered(q) {
            let correct = correctness(q)
            MurshidCard {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Label(correct == true ? "إجابة صحيحة" : "راجع الإجابة", systemImage: correct == true ? "checkmark.seal.fill" : "xmark.octagon.fill")
                            .font(.headline)
                            .foregroundStyle(correct == true ? .green : .orange)
                        Spacer()
                        if correct == true {
                            Text("أحسنت").font(.caption.bold()).foregroundStyle(.green)
                        }
                    }
                    let rawServerCorrectAnswer = jString(grades[q.id]?["correct_answer"], default: q.correctAnswer)
                        .trimmingCharacters(in: .whitespacesAndNewlines)
                    let serverCorrectAnswer = murshidEffectiveCorrectAnswer(question: q.text, serverAnswer: rawServerCorrectAnswer)
                    let serverExplanation = murshidExplanationText(
                        question: q.text,
                        correctAnswer: serverCorrectAnswer,
                        raw: jString(grades[q.id]?["explanation"], default: q.explanation)
                    )
                    if !serverCorrectAnswer.isEmpty {
                        Text("الإجابة النموذجية")
                            .font(.caption.bold())
                            .foregroundStyle(.secondary)
                        Text(serverCorrectAnswer)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.green)
                    }
                    Divider()
                    Text("لماذا؟")
                        .font(.caption.bold())
                        .foregroundStyle(Color.murshidBlue)
                    Text(serverExplanation)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineSpacing(5)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private var bottomBar: some View {
        HStack(spacing: 12) {
            Button {
                guard current > 0 else { return }
                current -= 1
                selectionHaptic()
            } label: {
                Label("السابق", systemImage: "chevron.right")
                    .frame(minWidth: 88, minHeight: 46)
            }
            .buttonStyle(.bordered)
            .disabled(current == 0)

            Spacer()

            if current == questions.count - 1 {
                Button {
                    showResult = true
                    haptic()
                } label: {
                    Label("النتيجة", systemImage: "chart.bar.fill")
                        .font(.headline)
                        .frame(minHeight: 46)
                }
                .buttonStyle(.borderedProminent)
                .tint(.murshidBlue)
                .disabled(!isAnswered(questions[current]) || submitting)
            } else {
                Button {
                    guard isAnswered(questions[current]), !submitting else { return }
                    current += 1
                    selectionHaptic()
                } label: {
                    Label("التالي", systemImage: "chevron.left")
                        .frame(minWidth: 88, minHeight: 46)
                }
                .buttonStyle(.borderedProminent)
                .tint(.murshidBlue)
                .disabled(!isAnswered(questions[current]) || submitting)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 11)
        .background(.ultraThinMaterial)
    }

    private var reportSheet: some View {
        NavigationStack {
            Form {
                Section("سبب البلاغ") {
                    Picker("السبب", selection: $reportReason) {
                        Text("الإجابة غير صحيحة").tag("wrong_answer")
                        Text("نوع السؤال خاطئ").tag("wrong_type")
                        Text("الخيارات ناقصة").tag("missing_options")
                        Text("السؤال مكرر").tag("duplicate")
                        Text("السؤال في موضوع غير صحيح").tag("wrong_topic")
                        Text("الشرح غير صحيح").tag("wrong_explanation")
                        Text("السؤال غير واضح").tag("unclear")
                        Text("مشكلة في المصدر").tag("source")
                        Text("سبب آخر").tag("other")
                    }
                }
                Section("ملاحظة اختيارية") {
                    TextField("اكتب ملاحظتك للمراجعة", text: $reportMessage, axis: .vertical)
                        .lineLimit(2...6)
                }
                Section {
                    Button {
                        reportCurrent()
                    } label: {
                        HStack {
                            if reporting { ProgressView() }
                            Text(reporting ? "جاري الإرسال…" : "إرسال البلاغ")
                        }
                    }
                    .disabled(reporting)
                }
            }
            .navigationTitle("الإبلاغ عن السؤال")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("إغلاق") { showReport = false } }
            }
        }
    }

    private var noteSheet: some View {
        NavigationStack {
            Form {
                Section("ملاحظة خاصة") {
                    TextField("مثال: راجع هذه القاعدة قبل الامتحان", text: $noteText, axis: .vertical)
                        .lineLimit(3...8)
                }
                Section {
                    Text("هذه الملاحظة خاصة بك ولا تظهر لأي طالب آخر.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("ملاحظتي")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("إغلاق") { showNote = false }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(savingNote ? "جاري الحفظ…" : "حفظ") {
                        Task { await saveCurrentNote() }
                    }
                    .disabled(savingNote)
                }
            }
        }
    }

    private var activeFilterCount: Int {
        var n = 0
        if filterType != "all" { n += 1 }
        if filterYear != "all" { n += 1 }
        if filterRound != "all" { n += 1 }
        if filterDifficulty != "all" { n += 1 }
        if filterState != "all" { n += 1 }
        if filterShuffle { n += 1 }
        return n
    }

    private var answeredCount: Int {
        questions.filter { isAnswered($0) }.count
    }

    private var correctCount: Int {
        questions.filter { correctness($0) == true }.count
    }

    private var firstWrongIndex: Int? {
        questions.firstIndex(where: { isAnswered($0) && correctness($0) == false })
    }

    private func isAnswered(_ q: Question) -> Bool {
        q.submitted || grades[q.id] != nil
    }

    private func correctness(_ q: Question) -> Bool? {
        let serverAnswer = jString(grades[q.id]?["correct_answer"], default: q.correctAnswer)
        let serverCorrect: Bool? = grades[q.id].map { jBool($0["is_correct"]) } ?? q.storedCorrect
        let submitted = !q.storedAnswer.isEmpty ? q.storedAnswer : (answers[q.id] ?? encodedAnswer(q))
        return murshidEffectiveCorrectness(question: q.text, submittedAnswer: submitted, serverAnswer: serverAnswer, serverCorrect: serverCorrect)
    }

    private func visibleOptions(_ q: Question) -> [QuestionOption] {
        var seen = Set<String>()
        return q.options.filter { option in
            let key = murshidAnswerKey(option.text)
            guard !key.isEmpty else { return false }
            return seen.insert(key).inserted
        }
    }

    private func optionLetter(_ index: Int) -> String {
        let letters = ["أ", "ب", "ج", "د", "هـ", "و"]
        return index < letters.count ? letters[index] : "\(index + 1)"
    }

    private func load() async {
        await MainActor.run { loading = true; error = ""; startedAt = Date() }
        do {
            var query: [URLQueryItem] = [URLQueryItem(name: "chapter_id", value: "\(topic.id)")]
            if filterType != "all" { query.append(URLQueryItem(name: "type", value: filterType)) }
            if filterYear != "all" { query.append(URLQueryItem(name: "year", value: filterYear)) }
            if filterRound != "all" { query.append(URLQueryItem(name: "round", value: filterRound)) }
            if filterDifficulty != "all" { query.append(URLQueryItem(name: "difficulty", value: filterDifficulty)) }
            if filterState != "all" { query.append(URLQueryItem(name: "state", value: filterState)) }
            if filterShuffle { query.append(URLQueryItem(name: "shuffle", value: "1")) }
            let d = try await APIClient.shared.request("mobile/exam.php", query: query)
            var seenQuestions = Set<String>()
            let qs = jArray(d["questions"])
                .map(Question.init)
                .filter { question in
                    guard murshidQuestionIsStudentReady(question.text) else { return false }
                    guard murshidQuestionClientValid(question) else { return false }
                    guard murshidQuestionAnswerCompatible(question: question.text, serverAnswer: question.correctAnswer) else { return false }
                    let key = murshidAnswerKey(murshidQuestionDisplayText(question.text))
                    guard !key.isEmpty else { return false }
                    return seenQuestions.insert(key).inserted
                }
            let filterRoot = d["filters"] as? JSON ?? [:]
            let available = filterRoot["available"] as? JSON ?? [:]
            let typeValues = (available["types"] as? [String]) ?? jArray(available["types"]).map { jString($0["value"], default: jString($0["type"])) }.filter { !$0.isEmpty }
            let yearValues = (available["years"] as? [String]) ?? []
            let roundValues = (available["rounds"] as? [String]) ?? []
            let difficultyValues = (available["difficulties"] as? [String]) ?? []
            await MainActor.run {
                questions = qs
                current = 0
                availableFilterTypes = typeValues
                availableFilterYears = yearValues
                availableFilterRounds = roundValues
                availableFilterDifficulties = difficultyValues
                for q in qs where !q.storedAnswer.isEmpty { answers[q.id] = q.storedAnswer }
                app.subscribed = jBool(d["subscribed"])
                app.freeUsed = jInt(d["free_used"])
                app.freeRemaining = d["free_remaining"] is NSNull ? nil : jInt(d["free_remaining"])
                app.csrf = jString(d["csrf"], default: app.csrf)
                loading = false
            }
        } catch {
            await MainActor.run { loading = false; self.error = error.localizedDescription }
        }
    }

    private func bindingAnswer(_ id: Int) -> Binding<String> {
        Binding(get: { answers[id] ?? "" }, set: { answers[id] = $0 })
    }

    private func bindingInteractive(_ id: Int) -> Binding<[String: String]> {
        Binding(get: { interactive[id] ?? [:] }, set: { interactive[id] = $0 })
    }

    private func bindingMatches(_ id: Int) -> Binding<[Int: MatchDraft]> {
        Binding(get: { matches[id] ?? [:] }, set: { matches[id] = $0 })
    }

    private func bindingEnglish(_ id: Int) -> Binding<[String: String]> {
        Binding(get: { english[id] ?? [:] }, set: { english[id] = $0 })
    }

    private func hasAnswer(_ q: Question) -> Bool {
        if q.englishMatch != nil { return !(english[q.id] ?? [:]).isEmpty }
        if !visibleOptions(q).isEmpty { return !(answers[q.id] ?? "").isEmpty }
        if q.type == "match" {
            return q.matchItems.allSatisfy {
                let d = matches[q.id]?[$0.id]
                return !(d?.type ?? "").isEmpty && !(d?.reason ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            }
        }
        if !q.stages.isEmpty {
            let editable = q.stages.filter { $0.kind != "info" }
            return editable.allSatisfy { !(interactive[q.id]?[$0.key] ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        }
        return !(answers[q.id] ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func encodedAnswer(_ q: Question) -> String {
        if let em = q.englishMatch {
            let left = (em["left"] as? JSON ?? [:]).keys.sorted { (Int($0) ?? 0) < (Int($1) ?? 0) }
            return left.compactMap { key in
                guard let val = english[q.id]?[key], !val.isEmpty else { return nil }
                return "\(key)-\(val)"
            }.joined(separator: ", ")
        }
        if q.type == "match" {
            var items: JSON = [:]
            for item in q.matchItems {
                let d = matches[q.id]?[item.id] ?? MatchDraft()
                items[String(item.id)] = ["type": d.type, "reason": d.reason]
            }
            if let data = try? JSONSerialization.data(withJSONObject: ["items": items]),
               let text = String(data: data, encoding: .utf8) { return text }
        }
        if !q.stages.isEmpty {
            let stages = interactive[q.id] ?? [:]
            if let data = try? JSONSerialization.data(withJSONObject: ["stages": stages]),
               let text = String(data: data, encoding: .utf8) { return text }
        }
        return answers[q.id] ?? ""
    }

    private func answerDisplay(_ q: Question) -> String {
        if !q.storedAnswer.isEmpty { return q.storedAnswer }
        return encodedAnswer(q)
    }

    private func submitCurrent(answerOverride: String? = nil) {
        guard !questions.isEmpty else { return }
        let q = questions[current]
        let rawAnswer = answerOverride ?? encodedAnswer(q)
        let answer = rawAnswer.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !answer.isEmpty else { return }
        if let answerOverride { answers[q.id] = answerOverride }
        submitting = true
        error = ""
        let payloadAnswers: JSON = [String(q.id): ["text": answer, "selected_text": answer, "submitted": true]]

        Task {
            do {
                let d = try await APIClient.shared.request(
                    "api/progress.php",
                    method: "POST",
                    body: [
                        "csrf": app.csrf,
                        "chapter_id": topic.id,
                        "question_index": current + 1,
                        "answers": payloadAnswers,
                        "completed": current == questions.count - 1
                    ]
                )
                var grade: JSON? = nil
                if let g = d["grades"] as? JSON { grade = g[String(q.id)] as? JSON }
                await MainActor.run {
                    textAnswerFocused = false
                    if let grade {
                        withAnimation(.easeInOut(duration: 0.22)) { grades[q.id] = grade }
                    }
                    app.subscribed = jBool(d["subscribed"], default: app.subscribed)
                    app.freeUsed = jInt(d["free_used"], default: app.freeUsed)
                    app.freeRemaining = d["free_remaining"] is NSNull ? nil : jInt(d["free_remaining"], default: app.freeRemaining ?? 0)
                    submitting = false
                    if jBool(grade?["is_correct"]) { haptic(.success) } else { haptic(.warning) }
                }
            } catch let e as APIError {
                await MainActor.run {
                    submitting = false
                    if e.status == 402 {
                        alertMessage = e.localizedDescription
                        showAlert = true
                        showSubscription = true
                    } else {
                        error = e.localizedDescription
                        haptic(.error)
                    }
                }
            } catch {
                await MainActor.run { submitting = false; self.error = error.localizedDescription; haptic(.error) }
            }
        }
    }

    private func loadCurrentNote() async {
        guard !questions.isEmpty else { return }
        let q = questions[current]
        do {
            let d = try await APIClient.shared.request(
                "mobile/question-notes.php",
                query: [URLQueryItem(name: "question_id", value: "\(q.id)")]
            )
            await MainActor.run {
                noteText = jString(d["note"])
                showNote = true
            }
        } catch {
            await MainActor.run {
                alertMessage = error.localizedDescription
                showAlert = true
            }
        }
    }

    private func saveCurrentNote() async {
        guard !questions.isEmpty else { return }
        let q = questions[current]
        await MainActor.run { savingNote = true }
        do {
            _ = try await APIClient.shared.request(
                "mobile/question-notes.php",
                method: "POST",
                body: ["csrf": app.csrf, "question_id": q.id, "note": noteText]
            )
            await MainActor.run {
                savingNote = false
                showNote = false
                haptic()
            }
        } catch {
            await MainActor.run {
                savingNote = false
                alertMessage = error.localizedDescription
                showAlert = true
                haptic(.error)
            }
        }
    }

    private func toggleFavorite() {
        guard !questions.isEmpty else { return }
        let idx = current
        let q = questions[idx]
        let action = q.favorite ? "unfavorite" : "favorite"
        Task {
            do {
                _ = try await APIClient.shared.request("api/question-tools.php", method: "POST", body: ["csrf": app.csrf, "question_id": q.id, "action": action])
                await MainActor.run { questions[idx].favorite.toggle(); selectionHaptic() }
            } catch {
                await MainActor.run { alertMessage = error.localizedDescription; showAlert = true; haptic(.error) }
            }
        }
    }

    private func reportCurrent() {
        guard !questions.isEmpty else { return }
        reporting = true
        let q = questions[current]
        Task {
            do {
                let d = try await APIClient.shared.request("api/question-tools.php", method: "POST", body: [
                    "csrf": app.csrf,
                    "question_id": q.id,
                    "action": "report",
                    "reason": reportReason,
                    "message": reportMessage.trimmingCharacters(in: .whitespacesAndNewlines)
                ])
                await MainActor.run {
                    reporting = false
                    showReport = false
                    reportMessage = ""
                    alertMessage = jString(d["message"], default: "تم إرسال البلاغ للمراجعة.")
                    showAlert = true
                    haptic()
                }
            } catch {
                await MainActor.run {
                    reporting = false
                    showReport = false
                    alertMessage = error.localizedDescription
                    showAlert = true
                    haptic(.error)
                }
            }
        }
    }
}

struct V3ExamResultView: View {
    let topic: Topic
    let subject: Subject
    let answered: Int
    let correct: Int
    let total: Int
    let duration: TimeInterval
    let onReview: () -> Void
    let onDone: () -> Void

    private var wrong: Int { max(0, answered - correct) }
    private var percent: Int { answered > 0 ? Int((Double(correct) / Double(answered) * 100).rounded()) : 0 }
    private var durationText: String {
        let minutes = max(0, Int(duration) / 60)
        let seconds = max(0, Int(duration) % 60)
        return String(format: "%02d:%02d", minutes, seconds)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                MurshidCard {
                    VStack(spacing: 18) {
                        ZStack {
                            Circle().fill(resultColor.opacity(0.10)).frame(width: 116, height: 116)
                            Image(systemName: resultIcon).font(.system(size: 46, weight: .semibold)).foregroundStyle(resultColor)
                        }
                        Text(resultTitle).font(.title2.bold())
                        Text("\(subject.name) • \(topic.name)").font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                        Text("\(percent)%").font(.system(size: 52, weight: .heavy, design: .rounded)).foregroundStyle(resultColor).monospacedDigit()
                    }
                    .frame(maxWidth: .infinity)
                }

                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                    V3MetricCard(title: "الصحيحة", value: "\(correct)", icon: "checkmark.circle.fill")
                    V3MetricCard(title: "الخاطئة", value: "\(wrong)", icon: "xmark.circle.fill")
                    V3MetricCard(title: "تمت الإجابة", value: "\(answered)/\(total)", icon: "list.number")
                    V3MetricCard(title: "الوقت", value: durationText, icon: "clock.fill")
                }

                MurshidCard {
                    VStack(alignment: .leading, spacing: 11) {
                        Label("خطوتك التالية", systemImage: "sparkles").font(.headline).foregroundStyle(Color.murshidBlue)
                        Text(nextAdvice).font(.subheadline).foregroundStyle(.secondary).lineSpacing(4)
                    }
                }

                if wrong > 0 {
                    Button(action: onReview) {
                        Label("راجع الأسئلة التي تحتاج انتباهًا", systemImage: "arrow.counterclockwise.circle.fill")
                    }
                    .buttonStyle(PrimaryButtonStyle())
                }

                NavigationLink(destination: ReviewView()) {
                    Text("فتح المراجعة الذكية")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .foregroundStyle(Color.murshidBlue)
                        .background(Color.murshidBlue.opacity(0.10), in: RoundedRectangle(cornerRadius: 17, style: .continuous))
                }
                .buttonStyle(.plain)

                Button("العودة إلى المواضيع", action: onDone)
                    .font(.headline)
                    .foregroundStyle(.secondary)
                    .padding(.top, 4)
            }
            .padding(16)
            .padding(.bottom, 28)
        }
        .background(Color.murshidBackground)
    }

    private var resultColor: Color {
        if percent >= 85 { return .green }
        if percent >= 60 { return .murshidBlue }
        return .orange
    }

    private var resultIcon: String {
        if percent >= 85 { return "trophy.fill" }
        if percent >= 60 { return "checkmark.seal.fill" }
        return "book.fill"
    }

    private var resultTitle: String {
        if percent >= 85 { return "أداء ممتاز" }
        if percent >= 60 { return "تقدم جيد" }
        return "راجع الفكرة وحاول مرة أخرى"
    }

    private var nextAdvice: String {
        if percent >= 85 { return "انتقل إلى الموضوع التالي، وارجع إلى أخطائك القليلة في المراجعة الذكية لاحقًا." }
        if percent >= 60 { return "راجع الإجابات غير الصحيحة ثم أكمل الموضوع التالي حتى تثبت الفكرة." }
        return "الأفضل مراجعة الأخطاء الآن ثم العودة لنفس الموضوع بعد فترة قصيرة."
    }
}

struct V3AccountView: View {
    @EnvironmentObject var app: AppSession
    @EnvironmentObject var device: DeviceServices
    @AppStorage("appearance") private var appearance = "system"
    @AppStorage("biometric_lock") private var biometricLock = false
    @AppStorage("study_reminder") private var studyReminder = false
    @AppStorage("study_reminder_hour") private var reminderHour = 19
    @AppStorage("study_reminder_minute") private var reminderMinute = 0

    @State private var account: JSON = [:]
    @State private var loading = true
    @State private var error = ""
    @State private var showRename = false
    @State private var newName = ""
    @State private var loggingOut = false
    @State private var showDelete = false

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                profileCard
                if loading { V3SkeletonCard(height: 90) }
                subscriptionCard
                servicesSection
                securitySection
                appearanceSection
                accountManagement
                appInfo
                if !error.isEmpty { V3InlineMessage(text: error, icon: "exclamationmark.triangle.fill", tone: .warning) }
            }
            .padding(16)
            .padding(.bottom, 28)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("حسابي")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
        .alert("تغيير الاسم", isPresented: $showRename) {
            TextField("الاسم الثلاثي", text: $newName)
            Button("حفظ") { Task { await rename() } }
            Button("إلغاء", role: .cancel) {}
        } message: {
            Text("يمكن تغيير الاسم مرة واحدة فقط حسب إعدادات المنصة.")
        }
        .alert("طلب حذف الحساب", isPresented: $showDelete) {
            Button("إلغاء", role: .cancel) {}
            Button("إرسال الطلب", role: .destructive) { Task { await requestDelete() } }
        } message: {
            Text("سنرسل طلب الحذف للمراجعة. لن يُحذف الحساب فورًا قبل معالجة الطلب.")
        }
    }

    private var profileCard: some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(colors: [.murshidNavy, .murshidBlue], startPoint: .topTrailing, endPoint: .bottomLeading)
            Circle().fill(.white.opacity(0.06)).frame(width: 180, height: 180).offset(x: 70, y: -80)
            HStack(spacing: 15) {
                avatar
                VStack(alignment: .leading, spacing: 5) {
                    Text(app.user?.name ?? "")
                        .font(.title3.bold())
                        .foregroundStyle(.white)
                    Text(app.user?.grade ?? "")
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.78))
                    if app.subscribed {
                        Label("اشتراك فعّال", systemImage: "checkmark.seal.fill")
                            .font(.caption.bold()).foregroundStyle(.white)
                    } else {
                        Label("\(app.freeRemaining ?? 0) سؤال مجاني متبقٍ", systemImage: "gift.fill")
                            .font(.caption.bold()).foregroundStyle(.white.opacity(0.86))
                    }
                }
                Spacer()
            }
            .padding(20)
        }
        .frame(minHeight: 138)
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .shadow(color: Color.murshidNavy.opacity(0.14), radius: 18, y: 8)
    }

    @ViewBuilder
    private var avatar: some View {
        let avatarURL = jString(account["avatar_url"])
        if let url = URL(string: avatarURL), !avatarURL.isEmpty {
            AsyncImage(url: url) { phase in
                if case let .success(image) = phase {
                    image.resizable().scaledToFill()
                } else {
                    Image(systemName: "person.crop.circle.fill").resizable().foregroundStyle(.white.opacity(0.86))
                }
            }
            .frame(width: 72, height: 72)
            .clipShape(Circle())
            .overlay(Circle().stroke(.white.opacity(0.30), lineWidth: 2))
        } else {
            Image(systemName: "person.crop.circle.fill")
                .resizable()
                .frame(width: 72, height: 72)
                .foregroundStyle(.white.opacity(0.92))
        }
    }

    private var subscriptionCard: some View {
        NavigationLink(destination: V31SubscriptionView()) {
            MurshidCard {
                HStack(spacing: 13) {
                    Image(systemName: app.subscribed ? "checkmark.seal.fill" : "creditcard.fill")
                        .font(.title2)
                        .foregroundStyle(app.subscribed ? .green : Color.murshidBlue)
                        .frame(width: 48, height: 48)
                        .background((app.subscribed ? Color.green : Color.murshidBlue).opacity(0.10), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    VStack(alignment: .leading, spacing: 4) {
                        Text(app.subscribed ? "الاشتراك" : "افتح كامل المنصة").font(.headline).foregroundStyle(.primary)
                        Text(app.subscribed ? "اشتراكك مفعّل حاليًا." : "اختر الباقة وطريقة الدفع المناسبة.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer(); Image(systemName: "chevron.left").foregroundStyle(.secondary)
                }
            }
        }
        .buttonStyle(.plain)
    }

    private var servicesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            V3SectionHeader(title: "الحساب والخدمات", subtitle: "إدارة كل ما يخص حسابك", icon: "square.grid.2x2.fill")
            MurshidCard {
                VStack(spacing: 0) {
                    if jBool(account["can_change_name"]) {
                        V3AccountRow(title: "تغيير الاسم", subtitle: "متاح مرة واحدة", icon: "pencil") {
                            newName = app.user?.name ?? ""
                            showRename = true
                        }
                        Divider().padding(.leading, 48)
                    }
                    NavigationLink(destination: NotificationsView()) {
                        V3AccountRowLabel(title: "الإشعارات", subtitle: app.unreadNotifications > 0 ? "لديك \(app.unreadNotifications) غير مقروء" : "لا توجد إشعارات جديدة", icon: "bell.fill")
                    }
                    Divider().padding(.leading, 48)
                    NavigationLink(destination: SupportView()) {
                        V3AccountRowLabel(title: "خدمة العملاء", subtitle: "محادثة مباشرة مع الدعم", icon: "message.fill")
                    }
                    Divider().padding(.leading, 48)
                    NavigationLink(destination: StoriesView()) {
                        V3AccountRowLabel(title: "غيّر جو", subtitle: "رسائل وقصص قصيرة", icon: "sparkles")
                    }
                }
            }
        }
    }

    private var securitySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            V3SectionHeader(title: "الأمان والتذكير", subtitle: "خيارات خاصة بجهازك", icon: "lock.shield.fill")
            MurshidCard {
                VStack(spacing: 14) {
                    Toggle(isOn: $biometricLock) {
                        Label(device.biometricAvailable ? "قفل Face ID / Touch ID" : "قفل برمز الجهاز", systemImage: "faceid")
                    }
                    Divider()
                    Toggle(isOn: Binding(get: { studyReminder }, set: { value in
                        studyReminder = value
                        Task {
                            if value { await device.requestStudyReminder(hour: reminderHour, minute: reminderMinute) }
                            else { device.disableStudyReminder() }
                        }
                    })) {
                        Label("تذكير دراسة يومي", systemImage: "bell.badge.fill")
                    }
                    if studyReminder {
                        DatePicker("وقت التذكير", selection: reminderDateBinding, displayedComponents: .hourAndMinute)
                            .onChange(of: reminderHour) { _ in rescheduleReminder() }
                            .onChange(of: reminderMinute) { _ in rescheduleReminder() }
                    }
                    Divider()
                    NavigationLink(destination: V3PasswordRecoveryView()) {
                        HStack {
                            Label("استعادة / تغيير كلمة المرور", systemImage: "key.fill")
                            Spacer(); Image(systemName: "chevron.left").font(.caption.bold()).foregroundStyle(.secondary)
                        }
                        .foregroundStyle(.primary)
                    }
                }
            }
        }
    }

    private var appearanceSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            V3SectionHeader(title: "المظهر", subtitle: "اختر الشكل المريح لك", icon: "circle.lefthalf.filled")
            MurshidCard {
                Picker("المظهر", selection: $appearance) {
                    Label("النظام", systemImage: "iphone").tag("system")
                    Label("فاتح", systemImage: "sun.max.fill").tag("light")
                    Label("داكن", systemImage: "moon.fill").tag("dark")
                }
                .pickerStyle(.segmented)
            }
        }
    }

    private var accountManagement: some View {
        VStack(alignment: .leading, spacing: 12) {
            V3SectionHeader(title: "إدارة الحساب", subtitle: "خيارات حساسة", icon: "person.crop.circle.badge.gearshape")
            MurshidCard {
                VStack(spacing: 0) {
                    Button(role: .destructive) { logout() } label: {
                        HStack {
                            Label("تسجيل الخروج", systemImage: "rectangle.portrait.and.arrow.right")
                            Spacer()
                            if loggingOut { ProgressView() }
                        }
                        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                    }
                    .disabled(loggingOut)
                    Divider()
                    Button(role: .destructive) { showDelete = true } label: {
                        HStack { Label("طلب حذف الحساب", systemImage: "trash"); Spacer() }
                            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                    }
                }
            }
        }
    }

    private var appInfo: some View {
        MurshidCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Label("منصة المرشد الوزاري", systemImage: "graduationcap.fill").font(.headline)
                    Spacer()
                    Text("3.0 • 300").font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                }
                Text("تطبيق iPhone أصلي مبني بـ SwiftUI، مرتبط مباشرة بحسابك في المنصة، ولا يستخدم WebView لواجهة الدراسة.")
                    .font(.footnote).foregroundStyle(.secondary).lineSpacing(3)
                HStack(spacing: 14) {
                    Link("الخصوصية", destination: URL(string: "https://www.mur-iq.com/privacy.php")!)
                    Link("شروط الاستخدام", destination: URL(string: "https://www.mur-iq.com/terms.php")!)
                }
                .font(.caption.bold())
            }
        }
    }

    private var reminderDateBinding: Binding<Date> {
        Binding {
            Calendar.current.date(from: DateComponents(hour: reminderHour, minute: reminderMinute)) ?? Date()
        } set: { date in
            let comps = Calendar.current.dateComponents([.hour, .minute], from: date)
            reminderHour = comps.hour ?? 19
            reminderMinute = comps.minute ?? 0
        }
    }

    private func rescheduleReminder() {
        guard studyReminder else { return }
        Task { await device.requestStudyReminder(hour: reminderHour, minute: reminderMinute) }
    }

    private func load() async {
        do {
            let d = try await APIClient.shared.request("mobile/account.php")
            await MainActor.run { account = d; app.setAvatarURL(jString(d["avatar_url"])); loading = false; error = "" }
        } catch {
            await MainActor.run { loading = false; self.error = error.localizedDescription }
        }
    }

    private func rename() async {
        let name = v3NormalizePersonName(newName)
        guard name.split(separator: " ").count >= 3 else {
            await MainActor.run { error = "اكتب الاسم الثلاثي على الأقل."; haptic(.error) }
            return
        }
        do {
            _ = try await APIClient.shared.request("mobile/account.php", method: "POST", body: ["csrf": app.csrf, "action": "change_name", "name": name])
            let b = try await APIClient.shared.bootstrap()
            await MainActor.run { app.applyBootstrap(b); error = ""; haptic() }
            await load()
        } catch {
            await MainActor.run { self.error = error.localizedDescription; haptic(.error) }
        }
    }

    private func requestDelete() async {
        do {
            _ = try await APIClient.shared.request("mobile/account.php", method: "POST", body: ["csrf": app.csrf, "action": "request_delete", "confirmation": "حذف حسابي"])
            await MainActor.run { error = "تم إرسال طلب حذف الحساب للمراجعة."; haptic() }
            await load()
        } catch {
            await MainActor.run { self.error = error.localizedDescription; haptic(.error) }
        }
    }

    private func logout() {
        loggingOut = true
        Task {
            do {
                try await APIClient.shared.logout()
                await MainActor.run { loggingOut = false; haptic() }
            } catch {
                await MainActor.run { loggingOut = false; self.error = error.localizedDescription; haptic(.error) }
            }
        }
    }
}

struct V3SubscriptionView: View {
    @EnvironmentObject var app: AppSession
    @AppStorage("last_payment_order") private var storedOrderID = 0

    @State private var plan = ""
    @State private var method = "zaincash"
    @State private var name = ""
    @State private var phone = ""
    @State private var loading = false
    @State private var error = ""
    @State private var checkoutURL: URL?
    @State private var showCheckout = false
    @State private var statusMessage = ""

    private let methods: [(id: String, title: String, subtitle: String, icon: String)] = [
        ("zaincash", "زين كاش", "دفع إلكتروني سريع", "iphone.gen3"),
        ("rafidain", "مصرف الرافدين", "عبر بوابة المصرف", "building.columns.fill"),
        ("rasheed", "مصرف الرشيد", "عبر بوابة المصرف", "creditcard.fill")
    ]

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                V3IntroCard(eyebrow: "الاشتراك", title: app.subscribed ? "اشتراكك مفعّل" : "افتح كامل الأسئلة", text: app.subscribed ? "يمكنك الوصول إلى كامل المحتوى المتاح في صفك." : "اختر الباقة وطريقة الدفع، ثم أكمل الخطوة الآمنة لدى مزود الدفع.", icon: app.subscribed ? "checkmark.seal.fill" : "creditcard.fill")

                if app.subscribed {
                    MurshidCard {
                        Label("لا تحتاج إلى إجراء أي دفع الآن.", systemImage: "checkmark.circle.fill")
                            .font(.headline).foregroundStyle(.green)
                    }
                } else {
                    plansSection
                    methodsSection
                    contactSection
                    actionSection
                }
            }
            .padding(16)
            .padding(.bottom, 28)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("الاشتراك والدفع")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if plan.isEmpty { plan = app.plans.first?.id ?? "month1" }
            if name.isEmpty { name = app.user?.name ?? "" }
        }
        .sheet(isPresented: $showCheckout, onDismiss: { Task { await checkStatus() } }) {
            if let checkoutURL { SafariSheet(url: checkoutURL) }
        }
    }

    private var plansSection: some View {
        VStack(alignment: .leading, spacing: 11) {
            V3SectionHeader(title: "اختر الباقة", subtitle: "يمكنك تغييرها قبل الدفع", icon: "calendar.badge.plus")
            ForEach(app.plans) { p in
                Button {
                    plan = p.id
                    selectionHaptic()
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: plan == p.id ? "checkmark.circle.fill" : "circle")
                            .foregroundStyle(plan == p.id ? Color.murshidBlue : Color.secondary)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(p.label).font(.headline).foregroundStyle(.primary)
                            Text("وصول كامل حسب مدة الباقة").font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text("\(p.amount.formatted()) د.ع").font(.headline).foregroundStyle(Color.murshidBlue)
                    }
                    .padding(15)
                    .background(plan == p.id ? Color.murshidBlue.opacity(0.08) : Color.murshidSurface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(plan == p.id ? Color.murshidBlue.opacity(0.30) : Color.primary.opacity(0.05)))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var methodsSection: some View {
        VStack(alignment: .leading, spacing: 11) {
            V3SectionHeader(title: "طريقة الدفع", subtitle: "اختر الجهة المناسبة", icon: "wallet.pass.fill")
            ForEach(methods, id: \.id) { item in
                Button {
                    method = item.id
                    selectionHaptic()
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: item.icon).foregroundStyle(Color.murshidBlue).frame(width: 38, height: 38).background(Color.murshidBlue.opacity(0.09), in: RoundedRectangle(cornerRadius: 11))
                        VStack(alignment: .leading, spacing: 3) {
                            Text(item.title).font(.headline).foregroundStyle(.primary)
                            Text(item.subtitle).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Image(systemName: method == item.id ? "checkmark.circle.fill" : "circle")
                            .foregroundStyle(method == item.id ? Color.murshidBlue : Color.secondary)
                    }
                    .padding(14)
                    .background(method == item.id ? Color.murshidBlue.opacity(0.06) : Color.murshidSurface, in: RoundedRectangle(cornerRadius: 17, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var contactSection: some View {
        VStack(alignment: .leading, spacing: 11) {
            V3SectionHeader(title: "بيانات التواصل", subtitle: "لاستكمال الطلب والتحقق", icon: "person.text.rectangle")
            MurshidCard {
                VStack(spacing: 12) {
                    TextField("الاسم الكامل", text: $name)
                        .textContentType(.name)
                        .padding(12).background(Color(uiColor: .tertiarySystemFill), in: RoundedRectangle(cornerRadius: 13))
                    TextField("رقم الهاتف 07XXXXXXXXX", text: $phone)
                        .keyboardType(.phonePad)
                        .textContentType(.telephoneNumber)
                        .environment(\.layoutDirection, .leftToRight)
                        .multilineTextAlignment(.leading)
                        .padding(12).background(Color(uiColor: .tertiarySystemFill), in: RoundedRectangle(cornerRadius: 13))
                }
            }
        }
    }

    private var actionSection: some View {
        VStack(spacing: 12) {
            if !statusMessage.isEmpty { V3InlineMessage(text: statusMessage, icon: "checkmark.circle.fill", tone: .success) }
            if !error.isEmpty { V3InlineMessage(text: error, icon: "exclamationmark.triangle.fill", tone: .warning) }

            Button(action: startCheckout) {
                HStack(spacing: 9) {
                    if loading { ProgressView().tint(.white) }
                    Image(systemName: "lock.shield.fill")
                    Text(loading ? "جاري تجهيز الدفع…" : "المتابعة إلى الدفع الآمن")
                }
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(loading || plan.isEmpty)

            if storedOrderID > 0 {
                Button("تحقق من حالة آخر عملية") { Task { await checkStatus() } }
                    .font(.headline)
                    .foregroundStyle(Color.murshidBlue)
                    .padding(.vertical, 8)
            }

            Label("بيانات بطاقتك أو محفظتك لا تمر عبر التطبيق؛ تتم الخطوة الحساسة لدى مزود الدفع الآمن.", systemImage: "lock.fill")
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
    }

    private func startCheckout() {
        error = ""
        statusMessage = ""
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanPhone = phone.filter(\.isNumber)
        guard cleanName.count >= 3 else { error = "أدخل الاسم الكامل."; haptic(.error); return }
        guard cleanPhone.count == 11, cleanPhone.hasPrefix("07") else { error = "أدخل رقم هاتف عراقيًا صحيحًا من 11 رقمًا يبدأ بـ 07."; haptic(.error); return }
        loading = true

        Task {
            do {
                let d = try await APIClient.shared.request("api/payment-checkout.php", method: "POST", body: [
                    "csrf": app.csrf,
                    "action": "checkout",
                    "plan": plan,
                    "payment_method": method,
                    "customer_name": cleanName,
                    "customer_phone": cleanPhone
                ])
                if jBool(d["subscribed"]) {
                    let b = try await APIClient.shared.bootstrap()
                    await MainActor.run { app.applyBootstrap(b); loading = false; statusMessage = "اشتراكك مفعّل بالفعل."; haptic() }
                    return
                }
                guard let url = URL(string: jString(d["redirect"])) else {
                    throw APIError(message: "تعذر فتح بوابة الدفع الآن.", status: 422, paymentRequired: false)
                }
                await MainActor.run {
                    checkoutURL = url
                    storedOrderID = jInt(d["order_id"])
                    loading = false
                    showCheckout = true
                }
            } catch {
                await MainActor.run { loading = false; self.error = error.localizedDescription; haptic(.error) }
            }
        }
    }

    private func checkStatus() async {
        guard storedOrderID > 0 else { return }
        do {
            let d = try await APIClient.shared.request("api/payment-checkout.php", method: "POST", body: ["csrf": app.csrf, "action": "status", "order_id": storedOrderID])
            let subscribed = jBool(d["subscribed"])
            let message = jString(d["message"], default: subscribed ? "تم تفعيل الاشتراك." : "الدفع قيد التحقق.")
            if subscribed {
                let b = try await APIClient.shared.bootstrap()
                await MainActor.run { app.applyBootstrap(b); storedOrderID = 0; haptic() }
            }
            await MainActor.run { statusMessage = message; error = "" }
        } catch {
            await MainActor.run { self.error = error.localizedDescription; haptic(.error) }
        }
    }
}

struct V3PasswordRecoveryView: View {
    @EnvironmentObject var app: AppSession
    @State private var phoneToken: String?
    @State private var finished = false

    var body: some View {
        Group {
            if let token = phoneToken {
                PhonePasswordResetView(token: token) {
                    finished = true
                    phoneToken = nil
                }
            } else if finished {
                VStack(spacing: 16) {
                    Image(systemName: "checkmark.circle.fill").font(.system(size: 54)).foregroundStyle(.green)
                    Text("تم تحديث كلمة المرور").font(.title2.bold())
                    Text("لأمان حسابك ستحتاج إلى تسجيل الدخول من جديد.").foregroundStyle(.secondary).multilineTextAlignment(.center)
                }
                .padding(28)
            } else {
                ForgotPasswordView { token in phoneToken = token }
            }
        }
        .navigationTitle("كلمة المرور")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct V3AccountRowLabel: View {
    let title: String
    let subtitle: String
    let icon: String
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon).foregroundStyle(Color.murshidBlue).frame(width: 34, height: 34).background(Color.murshidBlue.opacity(0.09), in: RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.body.weight(.medium)).foregroundStyle(.primary)
                Text(subtitle).font(.caption).foregroundStyle(.secondary)
            }
            Spacer(); Image(systemName: "chevron.left").font(.caption.bold()).foregroundStyle(.tertiary)
        }
        .frame(minHeight: 54)
    }
}

private struct V3AccountRow: View {
    let title: String
    let subtitle: String
    let icon: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            V3AccountRowLabel(title: title, subtitle: subtitle, icon: icon)
        }
        .buttonStyle(.plain)
    }
}

struct RegisterV3View: View {
    let onLogin: () -> Void
    let onVerify: (String, String, String) -> Void

    @State private var step = 1
    @State private var fullName = ""
    @State private var grades: [Subject] = []
    @State private var gradeID = 0
    @State private var gender = "male"
    @State private var contactType = "email"
    @State private var identifier = ""
    @State private var password = ""
    @State private var confirm = ""
    @State private var revealPassword = false
    @State private var revealConfirm = false
    @State private var loading = false
    @State private var gradesLoading = true
    @State private var fieldError = ""
    @State private var generalError = ""

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 18) {
                    AuthHeader(subtitle: "أنشئ حسابك بخطوات قصيرة وواضحة")
                        .padding(.top, 12)

                    stepHeader
                        .id("register-top")

                    MurshidCard {
                        VStack(alignment: .leading, spacing: 17) {
                            HStack {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(stepTitle).font(.title2.bold())
                                    Text(stepSubtitle).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                Text("\(step) من 3")
                                    .font(.caption.bold().monospacedDigit())
                                    .foregroundStyle(Color.murshidBlue)
                                    .padding(.horizontal, 10).padding(.vertical, 6)
                                    .background(Color.murshidBlue.opacity(0.09), in: Capsule())
                            }

                            if !generalError.isEmpty {
                                V3InlineMessage(text: generalError, icon: "exclamationmark.triangle.fill", tone: .warning)
                            }

                            switch step {
                            case 1: studentStep
                            case 2: contactStep
                            default: passwordStep
                            }

                            if !fieldError.isEmpty {
                                V3InlineMessage(text: fieldError, icon: "exclamationmark.circle.fill", tone: .warning)
                            }

                            navigationButtons(proxy: proxy)

                            Text("نسخة iPhone 4.2 • Build 420")
                                .font(.caption2.monospacedDigit())
                                .foregroundStyle(.tertiary)
                                .frame(maxWidth: .infinity, alignment: .center)
                        }
                    }

                    Button("لدي حساب بالفعل", action: onLogin)
                        .font(.headline)
                        .padding(.bottom, 32)
                }
                .padding(.horizontal, 18)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Color.murshidBackground.ignoresSafeArea())
            .task { await loadGrades() }
        }
    }

    private var stepHeader: some View {
        HStack(spacing: 8) {
            ForEach(1...3, id: \.self) { index in
                HStack(spacing: 6) {
                    ZStack {
                        Circle()
                            .fill(index <= step ? Color.murshidBlue : Color.primary.opacity(0.07))
                            .frame(width: 30, height: 30)
                        if index < step {
                            Image(systemName: "checkmark").font(.caption.bold()).foregroundStyle(.white)
                        } else {
                            Text("\(index)").font(.caption.bold().monospacedDigit()).foregroundStyle(index <= step ? .white : .secondary)
                        }
                    }
                    if index < 3 {
                        Capsule()
                            .fill(index < step ? Color.murshidBlue : Color.primary.opacity(0.08))
                            .frame(height: 4)
                    }
                }
            }
        }
        .padding(.horizontal, 8)
        .accessibilityLabel("الخطوة \(step) من 3")
    }

    private var studentStep: some View {
        VStack(alignment: .leading, spacing: 15) {
            V3AuthTextField(
                title: "الاسم الثلاثي",
                icon: "person.text.rectangle",
                placeholder: "مثال: يونس محمد عباس",
                text: $fullName,
                keyboard: .default,
                forceLTR: false
            )
            Text("اكتب اسمك الثلاثي كما هو في بياناتك الرسمية. نتعامل تلقائيًا مع المسافات والرموز المخفية في لوحة المفاتيح.")
                .font(.caption).foregroundStyle(.secondary).lineSpacing(3)

            VStack(alignment: .leading, spacing: 8) {
                Label("الصف الدراسي", systemImage: "books.vertical.fill").font(.subheadline.bold())
                Menu {
                    ForEach(grades) { grade in
                        Button(grade.name) {
                            gradeID = grade.id
                            fieldError = ""
                            selectionHaptic()
                        }
                    }
                } label: {
                    HStack {
                        Text(selectedGrade).foregroundStyle(gradeID == 0 ? Color.secondary : Color.primary)
                        Spacer()
                        if gradesLoading { ProgressView().controlSize(.small) }
                        else { Image(systemName: "chevron.up.chevron.down").font(.caption.bold()).foregroundStyle(.secondary) }
                    }
                    .padding(.horizontal, 14)
                    .frame(minHeight: 54)
                    .background(Color(uiColor: .tertiarySystemFill), in: RoundedRectangle(cornerRadius: 15, style: .continuous))
                }
                .buttonStyle(.plain)
                .disabled(gradesLoading || grades.isEmpty)
            }

            VStack(alignment: .leading, spacing: 8) {
                Label("الجنس", systemImage: "person.2.fill").font(.subheadline.bold())
                Picker("الجنس", selection: $gender) {
                    Text("ذكر").tag("male")
                    Text("أنثى").tag("female")
                }
                .pickerStyle(.segmented)
                .onChange(of: gender) { _ in selectionHaptic() }
            }
        }
    }

    private var contactStep: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 8) {
                Label("طريقة التسجيل", systemImage: "at").font(.subheadline.bold())
                Picker("طريقة التسجيل", selection: $contactType) {
                    Text("البريد الإلكتروني").tag("email")
                    Text("رقم الهاتف").tag("phone")
                }
                .pickerStyle(.segmented)
                .onChange(of: contactType) { _ in
                    identifier = ""
                    fieldError = ""
                    selectionHaptic()
                }
            }

            V3AuthTextField(
                title: contactType == "email" ? "البريد الإلكتروني" : "رقم الهاتف العراقي",
                icon: contactType == "email" ? "envelope.fill" : "phone.fill",
                placeholder: contactType == "email" ? "example@email.com" : "07XXXXXXXXX",
                text: $identifier,
                keyboard: contactType == "email" ? .emailAddress : .phonePad,
                forceLTR: true
            )

            V3InlineMessage(
                text: contactType == "email" ? "سيصل رمز/رابط التأكيد إلى هذا البريد، لذلك استخدم بريدًا يمكنك فتحه." : "استخدم رقمك العراقي الفعلي حتى تتمكن من استعادة الحساب لاحقًا.",
                icon: "checkmark.shield.fill",
                tone: .info
            )
        }
    }

    private var passwordStep: some View {
        VStack(alignment: .leading, spacing: 15) {
            V3AuthPasswordField(title: "كلمة المرور", placeholder: "8 أحرف على الأقل", text: $password, reveal: $revealPassword)
            passwordStrength
            V3AuthPasswordField(title: "تأكيد كلمة المرور", placeholder: "أعد كتابة كلمة المرور", text: $confirm, reveal: $revealConfirm)
            if !confirm.isEmpty {
                Label(password == confirm ? "كلمتا المرور متطابقتان" : "كلمتا المرور غير متطابقتين", systemImage: password == confirm ? "checkmark.circle.fill" : "xmark.circle.fill")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(password == confirm ? .green : .orange)
            }

            V3InlineMessage(text: "لا تشارك كلمة المرور مع أي شخص. يمكن استعادة الحساب لاحقًا من داخل التطبيق.", icon: "lock.shield.fill", tone: .info)
        }
    }

    private var passwordStrength: some View {
        let score = passwordScore
        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("قوة كلمة المرور").font(.caption).foregroundStyle(.secondary)
                Spacer()
                Text(score.label).font(.caption.bold()).foregroundStyle(score.color)
            }
            HStack(spacing: 5) {
                ForEach(0..<4, id: \.self) { index in
                    Capsule().fill(index < score.value ? score.color : Color.primary.opacity(0.08)).frame(height: 5)
                }
            }
        }
    }

    private func navigationButtons(proxy: ScrollViewProxy) -> some View {
        VStack(spacing: 10) {
            if step < 3 {
                Button {
                    guard validateCurrentStep() else { return }
                    withAnimation(.easeInOut(duration: 0.22)) { step += 1 }
                    selectionHaptic()
                    withAnimation { proxy.scrollTo("register-top", anchor: .top) }
                } label: {
                    Label("التالي", systemImage: "arrow.left")
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(gradesLoading && step == 1)
            } else {
                Button(action: submit) {
                    HStack(spacing: 9) {
                        if loading { ProgressView().tint(.white) }
                        Image(systemName: loading ? "hourglass" : "person.badge.plus")
                        Text(loading ? "جاري إنشاء الحساب…" : "إنشاء الحساب")
                    }
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(loading)
            }

            if step > 1 && !loading {
                Button {
                    fieldError = ""
                    generalError = ""
                    withAnimation(.easeInOut(duration: 0.22)) { step -= 1 }
                    selectionHaptic()
                    withAnimation { proxy.scrollTo("register-top", anchor: .top) }
                } label: {
                    Label("رجوع للخطوة السابقة", systemImage: "arrow.right")
                }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)
            }
        }
    }

    private var selectedGrade: String {
        grades.first(where: { $0.id == gradeID })?.name
        ?? (gradesLoading ? "جاري تحميل الصفوف…" : "اختر الصف الدراسي")
    }

    private var stepTitle: String {
        switch step { case 1: return "بيانات الطالب"; case 2: return "طريقة التسجيل"; default: return "حماية الحساب" }
    }

    private var stepSubtitle: String {
        switch step { case 1: return "الاسم والصف والجنس"; case 2: return "البريد أو رقم الهاتف"; default: return "اختر كلمة مرور قوية" }
    }

    private var passwordScore: (value: Int, label: String, color: Color) {
        var value = 0
        if password.count >= 8 { value += 1 }
        if password.rangeOfCharacter(from: .decimalDigits) != nil { value += 1 }
        if password.rangeOfCharacter(from: .letters) != nil { value += 1 }
        if password.count >= 12 || password.rangeOfCharacter(from: CharacterSet.alphanumerics.inverted) != nil { value += 1 }
        switch value {
        case 0...1: return (max(1, value), "ضعيفة", .orange)
        case 2: return (2, "مقبولة", .yellow)
        case 3: return (3, "جيدة", .murshidBlue)
        default: return (4, "قوية", .green)
        }
    }

    private func loadGrades() async {
        gradesLoading = true
        do {
            grades = try await APIClient.shared.grades().map(Subject.init)
            if gradeID == 0 { gradeID = grades.first?.id ?? 0 }
            generalError = ""
        } catch {
            generalError = error.localizedDescription
        }
        gradesLoading = false
    }

    private func validateCurrentStep() -> Bool {
        fieldError = ""
        generalError = ""
        if step == 1 {
            let name = v3NormalizePersonName(fullName)
            if name.split(separator: " ").count < 3 {
                fieldError = "اكتب الاسم الثلاثي على الأقل، مثل: يونس محمد عباس."
                haptic(.error)
                return false
            }
            if gradeID <= 0 {
                fieldError = "اختر الصف الدراسي."
                haptic(.error)
                return false
            }
            fullName = name
            return true
        }
        if step == 2 {
            let value = v3NormalizeIdentifier(identifier)
            if contactType == "email" {
                guard v3IsValidEmail(value) else { fieldError = "أدخل بريدًا إلكترونيًا صحيحًا."; haptic(.error); return false }
            } else {
                guard v3IsValidIraqiPhone(value) else { fieldError = "أدخل رقمًا عراقيًا صحيحًا يبدأ بـ 07 ويتكون من 11 رقمًا."; haptic(.error); return false }
            }
            identifier = value
            return true
        }
        if password.count < 8 {
            fieldError = "كلمة المرور يجب أن تكون 8 أحرف على الأقل."
            haptic(.error)
            return false
        }
        if confirm.isEmpty || password != confirm {
            fieldError = "تأكد من تطابق كلمتي المرور."
            haptic(.error)
            return false
        }
        return true
    }

    private func submit() {
        guard validateCurrentStep() else { return }
        let name = v3NormalizePersonName(fullName)
        let cleanIdentifier = v3NormalizeIdentifier(identifier)
        loading = true
        generalError = ""

        Task {
            do {
                let data = try await APIClient.shared.register([
                    "full_name": name,
                    "name": name,
                    "fullName": name,
                    "grade_id": gradeID,
                    "gender": gender,
                    "contact_type": contactType,
                    "identifier": cleanIdentifier,
                    "password": password,
                    "password_confirmation": confirm,
                    "company_website": ""
                ])

                let kind = jBool(data["phone_verification_required"]) ? "phone" : "email"
                let destination = kind == "phone" ? jString(data["masked_phone"]) : jString(data["masked_email"])
                await MainActor.run {
                    loading = false
                    haptic()
                    onVerify(kind, destination, jString(data["message"]))
                }
            } catch {
                await MainActor.run {
                    loading = false
                    generalError = error.localizedDescription
                    haptic(.error)
                }
            }
        }
    }
}

private struct V3AuthTextField: View {
    let title: String
    let icon: String
    let placeholder: String
    @Binding var text: String
    let keyboard: UIKeyboardType
    let forceLTR: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(title, systemImage: icon).font(.subheadline.bold())
            TextField(placeholder, text: $text)
                .keyboardType(keyboard)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(keyboard == .emailAddress || keyboard == .phonePad)
                .environment(\.layoutDirection, forceLTR ? .leftToRight : .rightToLeft)
                .multilineTextAlignment(forceLTR ? .leading : .trailing)
                .padding(.horizontal, 14)
                .frame(minHeight: 54)
                .background(Color(uiColor: .tertiarySystemFill), in: RoundedRectangle(cornerRadius: 15, style: .continuous))
        }
    }
}

private struct V3AuthPasswordField: View {
    let title: String
    let placeholder: String
    @Binding var text: String
    @Binding var reveal: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(title, systemImage: "lock.fill").font(.subheadline.bold())
            HStack(spacing: 8) {
                Group {
                    if reveal { TextField(placeholder, text: $text) }
                    else { SecureField(placeholder, text: $text) }
                }
                .textContentType(.newPassword)
                .environment(\.layoutDirection, .leftToRight)
                .multilineTextAlignment(.leading)
                Button {
                    reveal.toggle(); selectionHaptic()
                } label: {
                    Image(systemName: reveal ? "eye.slash.fill" : "eye.fill").foregroundStyle(.secondary).frame(width: 30, height: 30)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 14)
            .frame(minHeight: 54)
            .background(Color(uiColor: .tertiarySystemFill), in: RoundedRectangle(cornerRadius: 15, style: .continuous))
        }
    }
}

func v3NormalizePersonName(_ value: String) -> String {
    let hidden: Set<UInt32> = [
        0x200E, 0x200F, 0x061C,
        0x202A, 0x202B, 0x202C, 0x202D, 0x202E,
        0x2066, 0x2067, 0x2068, 0x2069,
        0xFEFF
    ]
    var result = ""
    var pendingSpace = false
    for scalar in value.precomposedStringWithCanonicalMapping.unicodeScalars {
        if hidden.contains(scalar.value) { continue }
        if CharacterSet.whitespacesAndNewlines.contains(scalar) {
            if !result.isEmpty { pendingSpace = true }
            continue
        }
        if pendingSpace { result.append(" "); pendingSpace = false }
        result.unicodeScalars.append(scalar)
    }
    return result.trimmingCharacters(in: .whitespacesAndNewlines)
}

private func v3NormalizeIdentifier(_ value: String) -> String {
    let digitMap: [Character: Character] = [
        "٠":"0", "١":"1", "٢":"2", "٣":"3", "٤":"4", "٥":"5", "٦":"6", "٧":"7", "٨":"8", "٩":"9",
        "۰":"0", "۱":"1", "۲":"2", "۳":"3", "۴":"4", "۵":"5", "۶":"6", "۷":"7", "۸":"8", "۹":"9"
    ]
    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
    return String(trimmed.map { digitMap[$0] ?? $0 })
}

private func v3IsValidEmail(_ value: String) -> Bool {
    value.range(of: #"^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$"#, options: [.regularExpression, .caseInsensitive]) != nil
}

private func v3IsValidIraqiPhone(_ value: String) -> Bool {
    let digits = value.filter(\.isNumber)
    return digits.count == 11 && digits.hasPrefix("07")
}
