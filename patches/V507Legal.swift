import SwiftUI
import Foundation

// The same neutral age bands as the Android policy; no birth date is collected.
enum V507AgeRules {
    static let policyVersion = "2026-10-17"
    static let labels = ["اختر الفئة العمرية", "أقل من ١٠ سنوات", "١٠–١٢ سنة", "١٣–١٥ سنة", "١٦–١٧ سنة", "١٨ سنة فأكثر"]
    static func eligible(_ band: Int) -> Bool { (2...5).contains(band) }
    static func minor(_ band: Int) -> Bool { (2...4).contains(band) }
    static func validationError(band: Int, terms: Bool, guardian: Bool, blocked: Bool = false) -> String? {
        if blocked || band == 1 { return "الخدمة مخصصة لعمر ١٠ سنوات فأكثر. للاستفسار: contact@mur-iq.com" }
        if !eligible(band) { return "اختر فئتك العمرية الفعلية أولًا." }
        if !terms { return "اقرأ الشروط والأحكام وسياسة الخصوصية وأكّد الموافقة عليهما." }
        if minor(band) && !guardian { return "يلزم موافقة ولي أمرك ومراجعته للشروط وسياسة الخصوصية." }
        return nil
    }
}

@MainActor
enum V507AgePolicy {
    static let blockKey = "murshid_age_under10_until"
    static var blocked: Bool { UserDefaults.standard.double(forKey: blockKey) > Date().timeIntervalSince1970 }
    static func recordUnderage() {
        UserDefaults.standard.set(Date().addingTimeInterval(86_400).timeIntervalSince1970, forKey: blockKey)
    }
    static func validate(band: Int, terms: Bool, guardian: Bool) throws {
        if band == 1 { recordUnderage() }
        if let message = V507AgeRules.validationError(band: band, terms: terms, guardian: guardian, blocked: blocked) {
            throw APIError(message: message, status: 422, paymentRequired: false)
        }
    }
}

@MainActor
struct V507AgeConsentCard: View {
    @Binding var ageBand: Int
    @Binding var termsAccepted: Bool
    @Binding var guardianAccepted: Bool
    @State private var blocked = V507AgePolicy.blocked

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("العمر وشروط الاستخدام", systemImage: "person.crop.circle.badge.checkmark")
                .font(.subheadline.bold())
            Text("اختر عمرك الفعلي. لا نطلب تاريخ ميلادك.")
                .font(.caption).foregroundStyle(.secondary)
            Menu {
                ForEach(1..<V507AgeRules.labels.count, id: \.self) { band in
                    Button(V507AgeRules.labels[band]) { ageBand = band }
                        .accessibilityIdentifier("legal-age-choice-\(band)")
                }
            } label: {
                HStack(spacing: 8) {
                    Text(V507AgeRules.labels.indices.contains(ageBand) ? V507AgeRules.labels[ageBand] : V507AgeRules.labels[0])
                        .foregroundStyle(ageBand == 0 ? Color.secondary : Color.primary)
                    Spacer(minLength: 4)
                    Image(systemName: "chevron.up.chevron.down").font(.caption.bold())
                }
                .padding(.horizontal, 12).frame(minHeight: 48)
                .background(Color(uiColor: .tertiarySystemFill), in: RoundedRectangle(cornerRadius: 12))
            }
            .accessibilityIdentifier("legal-age-picker")

            if blocked || ageBand == 1 {
                Label("الخدمة مخصصة لعمر ١٠ سنوات فأكثر.", systemImage: "exclamationmark.circle.fill")
                    .font(.footnote).foregroundStyle(Color.red)
            }
            Toggle("أوافق على الشروط والأحكام، واطّلعت على سياسة الخصوصية.", isOn: $termsAccepted)
                .accessibilityIdentifier("legal-terms-consent")
            if V507AgeRules.minor(ageBand) {
                Toggle("راجعت الشروط والخصوصية مع ولي أمري، وأستخدم المنصة بموافقته وإشرافه.", isOn: $guardianAccepted)
                    .accessibilityIdentifier("legal-guardian-consent")
            }
            V507LegalFooter(showNotice: false)
        }
        .font(.footnote)
        .tint(.murshidBlue)
        .onChange(of: ageBand) { value in
            guardianAccepted = false
            if value == 1 { V507AgePolicy.recordUnderage(); blocked = true }
        }
    }
}

struct V507LegalFooter: View {
    var showNotice = true
    @State private var showAgePolicy = false
    private let columns = [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)]

    var body: some View {
        VStack(spacing: 10) {
            if showNotice {
                Divider().padding(.bottom, 4)
                Text("الشروط والخصوصية")
                    .font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            }
            LazyVGrid(columns: columns, spacing: 4) {
                Link(destination: URL(string: "https://www.mur-iq.com/terms.php")!) {
                    footerLabel("الشروط والأحكام", icon: "doc.text")
                }.accessibilityIdentifier("legal-terms")
                Link(destination: URL(string: "https://www.mur-iq.com/privacy.php")!) {
                    footerLabel("سياسة الخصوصية", icon: "hand.raised")
                }.accessibilityIdentifier("legal-privacy")
                Button { showAgePolicy = true } label: {
                    footerLabel("ضوابط العمر", icon: "person.crop.circle.badge.checkmark")
                }.accessibilityIdentifier("legal-age")
                Link(destination: URL(string: "https://www.mur-iq.com/guardian-notice.php")!) {
                    footerLabel("إرشادات ولي الأمر", icon: "figure.2.and.child.holdinghands")
                }.accessibilityIdentifier("legal-guardian")
            }
            .buttonStyle(.plain)
            .font(.caption.weight(.medium))
            .foregroundStyle(Color.murshidBlue)
            if showNotice {
                Text("لعمر ١٠ سنوات فأكثر · دون ١٨ سنة بموافقة ولي الأمر")
                    .font(.caption2).foregroundStyle(.secondary)
                    .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity)
        .sheet(isPresented: $showAgePolicy) {
            V507AgePolicyView()
                .environment(\.layoutDirection, .rightToLeft)
        }
    }

    private func footerLabel(_ title: String, icon: String) -> some View {
        Label(title, systemImage: icon)
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, minHeight: 44)
            .contentShape(Rectangle())
    }
}

struct V507AgePolicyView: View {
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Image(systemName: "checkmark.shield.fill")
                        .font(.system(size: 36)).foregroundStyle(Color.murshidBlue)
                    Text("دراسة آمنة وواضحة للجميع").font(.title2.bold())
                    section("١٠ سنوات فأكثر", "الخدمة مخصصة للطلاب بعمر ١٠ سنوات فأكثر. لا يُسمح بإنشاء حساب أو تسجيل الدخول عند اختيار عمر أقل من ذلك.")
                    section("دون ١٨ سنة", "راجع الشروط والأحكام وسياسة الخصوصية مع ولي أمرك قبل إنشاء الحساب أو استخدامه، واستخدم المنصة بموافقته وإشرافه.")
                    section("خصوصية العمر", "نطلب الفئة العمرية فقط، ولا نطلب تاريخ ميلادك الكامل. اختر عمرك الفعلي عند التسجيل أو الدخول.")
                    section("الإشعارات والصورة", "الصورة الشخصية اختيارية. يمكن رفض إذن إشعارات الآيفون أو إيقافها من إعدادات iOS، مع استمرار الدراسة داخل التطبيق.")
                    section("حقوق ولي الأمر", "يمكن لولي الأمر التواصل مع الإدارة لطلب الاطلاع أو التصحيح أو الحذف، بعد التحقق من صفته وملكية الحساب.")
                    Link("قراءة إرشادات ولي الأمر", destination: URL(string: "https://www.mur-iq.com/guardian-notice.php")!)
                        .frame(minHeight: 44)
                    Link("contact@mur-iq.com", destination: URL(string: "mailto:contact@mur-iq.com")!)
                        .font(.footnote).environment(\.layoutDirection, .leftToRight)
                        .frame(minHeight: 44)
                }
                .padding(22)
            }
            .background(Color.murshidBackground.ignoresSafeArea())
            .navigationTitle("ضوابط العمر")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("تم") { dismiss() }.accessibilityIdentifier("legal-close")
                }
            }
        }
    }
    private func section(_ title: String, _ text: String) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(title).font(.headline)
            Text(text).font(.subheadline).foregroundStyle(.secondary)
                .lineSpacing(4).fixedSize(horizontal: false, vertical: true)
        }
    }
}
