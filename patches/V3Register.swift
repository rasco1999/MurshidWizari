import SwiftUI

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

                            Text("نسخة iPhone 3.0 • Build 300")
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