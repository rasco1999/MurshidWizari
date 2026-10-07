import SwiftUI

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
        NavigationLink(destination: V3SubscriptionView()) {
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
            await MainActor.run { account = d; loading = false; error = "" }
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