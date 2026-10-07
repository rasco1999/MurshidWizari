import SwiftUI
import UIKit

private enum AuthRoute: Hashable {
    case register
    case forgot
    case verify(String, String, String)
    case phoneReset(String)
}

struct AuthFlowView: View {
    @State private var path: [AuthRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            LoginView(
                onRegister: { path.append(.register) },
                onForgot: { path.append(.forgot) },
                onVerify: { kind, destination, message in
                    path.append(.verify(kind, destination, message))
                }
            )
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: AuthRoute.self) { route in
                switch route {
                case .register:
                    RegisterView(
                        onLogin: { path.removeAll() },
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
                        onCancel: { path.removeAll() }
                    )
                    .navigationTitle("تأكيد الحساب")
                    .murshidNavigation()

                case let .phoneReset(token):
                    PhonePasswordResetView(token: token) {
                        path.removeAll()
                    }
                    .navigationTitle("كلمة مرور جديدة")
                    .murshidNavigation()
                }
            }
        }
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
                        .onChange(of: fullName) { _ in nameError = "" }

                        if !nameError.isEmpty { FieldError(nameError) }

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

        let name = normalizedPersonName(fullName)
        let words = name.split(whereSeparator: { $0.isWhitespace }).filter { !$0.isEmpty }
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

                        Text("أدخل رقم الهاتف أو البريد المرتبط بالحساب. للهاتف سيصل رمز عبر WhatsApp، والبريد يستلم رابط استعادة آمنًا.")
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
    let mapped = value
        .replacingOccurrences(of: "\u{00A0}", with: " ")
        .replacingOccurrences(of: "\u{2007}", with: " ")
        .replacingOccurrences(of: "\u{202F}", with: " ")
        .replacingOccurrences(of: "\u{200E}", with: "")
        .replacingOccurrences(of: "\u{200F}", with: "")
        .replacingOccurrences(of: "\u{061C}", with: "")

    return mapped
        .components(separatedBy: .whitespacesAndNewlines)
        .filter { !$0.isEmpty }
        .joined(separator: " ")
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
