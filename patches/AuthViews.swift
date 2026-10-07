import SwiftUI
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
    @State private var path: [AuthRoute] = []

    var body: some View {
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
                    RegisterView(
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
                        .onChange(of: fullName) { value in
                            let count = personNameWords(value).count
                            if count >= 3 {
                                nameError = ""
                            } else if !nameError.isEmpty {
                                nameError = "اكتب الاسم الثلاثي على الأقل، مثل: أحمد علي حسن."
                            }
                        }

                        if !nameError.isEmpty {
                            FieldError(nameError)
                        } else if personNameWords(fullName).count > 0 && personNameWords(fullName).count < 3 {
                            Text("أدخل ثلاثة أسماء على الأقل.")
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

        let words = personNameWords(fullName)
        let name = words.joined(separator: " ")
        if words.count < 3 {
            nameError = "اكتب الاسم الثلاثي على الأقل، مثل: أحمد علي حسن."
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

private func personNameWords(_ value: String) -> [String] {
    let canonical = value.precomposedStringWithCanonicalMapping
    guard let regex = try? NSRegularExpression(
        pattern: #"[\p{L}\p{M}]+(?:['’-][\p{L}\p{M}]+)*"#,
        options: []
    ) else { return [] }

    let range = NSRange(canonical.startIndex..., in: canonical)
    return regex.matches(in: canonical, range: range).compactMap { match in
        guard let r = Range(match.range, in: canonical) else { return nil }
        let token = String(canonical[r]).trimmingCharacters(in: .whitespacesAndNewlines)
        return token.isEmpty ? nil : token
    }
}

private func normalizedPersonName(_ value: String) -> String {
    personNameWords(value).joined(separator: " ")
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
