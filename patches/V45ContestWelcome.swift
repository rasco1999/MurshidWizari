import SwiftUI
import Foundation

// v4.5. Contest rules are loaded from the SAME published student page as the website.
// These bundled copies keep the instructions readable if the network is briefly unavailable.
@MainActor
private enum V45ContestRulesSnapshot {
    static let rules: [JSON] = [
        ["number":1,"title":"الفائز بالجائزة","text":"الفائز هو الطالب الذي يحتل المركز الأول في الترتيب النهائي عند إغلاق التحدي، بعد مراجعة الحساب والنشاط من الإدارة."],
        ["number":2,"title":"احتساب النقاط","text":"تُحتسب نقاط المسابقة المكتسبة خلال مدة التحدي فقط: +5 نقاط للإجابة الصحيحة العادية، +10 نقاط للمسائل الحسابية والوراثية، وخصم نقطتين للإجابة الخاطئة الجديدة فقط عندما يكون الرصيد أكثر من 100 نقطة، و+50 نقطة عند إكمال اختبار مؤهل. لا تمنح إعادة إرسال الإجابة نفسها نقاطاً إضافية."],
        ["number":3,"title":"ترتيب موحّد لجميع الصفوف","text":"يتنافس طلاب جميع الصفوف في قائمة واحدة. يُرتب المشاركون حسب رصيد النقاط وفق قواعد المسابقة."],
        ["number":4,"title":"حالة التعادل","text":"عند تساوي النقاط تكون الأفضلية للطالب صاحب العدد الأكبر من الإجابات الصحيحة، ثم يُرجع إلى معيار النشاط المسجل في النظام."],
        ["number":5,"title":"النزاهة ومنع التلاعب","text":"تراجع الإدارة نشاط الحساب قبل اعتماد الفائز. الحسابات المكررة أو النشاط الآلي أو أي محاولة للتلاعب بالنقاط قد تؤدي إلى الاستبعاد من المسابقة."],
        ["number":6,"title":"توثيق الفائز","text":"يجب على الفائز إثبات ملكية الحساب وتوثيق وسيلة الاتصال المطلوبة قبل تسليم الجائزة. عدم إكمال التحقق يمنع اعتماد التسليم حتى استكماله."],
        ["number":7,"title":"موعد إغلاق التحدي","text":"يُغلق التحدي يوم 1 تشرين الثاني 2026 الساعة 11:59 مساءً بتوقيت بغداد، ثم تثبت النتيجة النهائية بعد انتهاء مراجعة النزاهة."]
    ]
    static let points: [JSON] = [
        ["value":"+5 نقاط","description":"لكل إجابة صحيحة عادية جديدة"],
        ["value":"+10 نقاط","description":"للمسألة الحسابية أو الوراثية الصحيحة"],
        ["value":"-2 نقطة","description":"للإجابة الخاطئة الجديدة فقط إذا كان رصيدك أكثر من 100 نقطة"],
        ["value":"+50 نقطة","description":"عند إكمال اختبار مؤهل للمكافأة"],
        ["value":"ترتيب مباشر","description":"يتم ترتيب المشاركين حسب النقاط، ثم الإجابات الصحيحة، ثم النشاط المسجل عند التعادل."]
    ]
    static let pointsNote = "النقاط تُحتسب مرة واحدة فقط للإجابة الجديدة المقبولة؛ إعادة فتح السؤال أو إعادة إرسال نفس الإجابة لا تولد نقاطاً إضافية."
    static let footer = "مهم: النقاط الظاهرة أثناء المسابقة ترتيب مبدئي، ولا تعني اعتماد الفائز نهائياً قبل مراجعة الإدارة."
}

struct V45ContestInstructionsLink: View {
    var body: some View {
        NavigationLink(destination: V45ContestInstructionsView()) {
            HStack(spacing: 13) {
                Image(systemName: "text.book.closed.fill")
                    .font(.title2)
                    .frame(width: 52, height: 52)
                    .background(Color.murshidGold.opacity(0.20), in: RoundedRectangle(cornerRadius: 14))
                VStack(alignment: .leading, spacing: 5) {
                    Text("تعليمات وقوانين تحدي المليون")
                        .font(.headline.bold())
                    Text("نظام النقاط • الجائزة • شروط الفوز • موعد الإغلاق")
                        .font(.caption).foregroundStyle(.white.opacity(0.84))
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 5)
                Image(systemName: "chevron.left").font(.subheadline.bold())
            }
            .foregroundStyle(.white)
            .padding(17)
            .background(
                LinearGradient(colors: [.murshidNavy, .murshidBlue], startPoint: .topTrailing, endPoint: .bottomLeading),
                in: RoundedRectangle(cornerRadius: 20, style: .continuous)
            )
        }
        .buttonStyle(.plain)
        .accessibilityHint("اضغط لقراءة تعليمات التحدي كاملة")
    }
}

struct V45ContestInstructionsView: View {
    @State private var rules: [JSON] = V45ContestRulesSnapshot.rules
    @State private var points: [JSON] = V45ContestRulesSnapshot.points
    @State private var pointsNote = V45ContestRulesSnapshot.pointsNote
    @State private var footer = V45ContestRulesSnapshot.footer
    @State private var prize: Int = 1_000_000
    @State private var endDate = "1 تشرين الثاني 2026 · الساعة 11:59 مساءً بتوقيت بغداد"
    @State private var isLive = false
    @State private var networkWarning = false

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                header
                if networkWarning {
                    Label("تعذر التحديث من الموقع؛ تظهر التعليمات المحفوظة في التطبيق.", systemImage: "wifi.slash")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Text("احتساب نقاط التحدي").font(.title3.bold())
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                    ForEach(Array(points.enumerated()), id: \.offset) { _, item in
                        VStack(alignment: .leading, spacing: 7) {
                            Text(jString(item["value"]))
                                .font(.headline.bold()).foregroundStyle(Color.murshidBlue)
                            Text(jString(item["description"]))
                                .font(.caption).foregroundStyle(.primary)
                                .fixedSize(horizontal: false, vertical: true)
                            Spacer(minLength: 0)
                        }
                        .frame(maxWidth: .infinity, minHeight: 105, alignment: .topLeading)
                        .padding(13)
                        .background(Color.murshidSurface, in: RoundedRectangle(cornerRadius: 15))
                    }
                }
                Text(pointsNote)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding(12)
                    .background(Color.murshidGold.opacity(0.10), in: RoundedRectangle(cornerRadius: 13))

                Text("الشروط الرسمية للتحدي")
                    .font(.title3.bold())
                    .padding(.top, 4)
                ForEach(Array(rules.enumerated()), id: \.offset) { i, item in
                    HStack(alignment: .top, spacing: 14) {
                        Text(String(format: "%02d", jInt(item["number"], default: i + 1)))
                            .font(.headline.bold().monospacedDigit())
                            .foregroundStyle(Color.murshidGold)
                            .frame(width: 40, height: 40)
                            .background(Color.murshidBlue, in: RoundedRectangle(cornerRadius: 12))
                        VStack(alignment: .leading, spacing: 8) {
                            Text(jString(item["title"]))
                                .font(.headline.bold()).foregroundStyle(.primary)
                            Text(jString(item["text"]))
                                .font(.subheadline).foregroundStyle(.secondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(15)
                    .background(Color.murshidSurface, in: RoundedRectangle(cornerRadius: 17))
                }
                Label(footer, systemImage: "checkmark.shield.fill")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(Color.murshidBlue)
                    .padding(15)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.murshidBlue.opacity(0.08), in: RoundedRectangle(cornerRadius: 15))
            }
            .padding(16)
            .padding(.bottom, 28)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .environment(\.layoutDirection, .rightToLeft)
        .navigationTitle("تعليمات التحدي")
        .navigationBarTitleDisplayMode(.inline)
        .task { await refreshInstructions() }
        .refreshable { await refreshInstructions() }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 14) {
            Label("تحدي المليون", systemImage: "trophy.fill")
                .font(.subheadline.bold())
                .foregroundStyle(Color.murshidGold)
            Text("اعرف قوانين التحدي قبل المنافسة")
                .font(.title2.bold()).foregroundStyle(.white)
            Text("\(prize.formatted()) د.ع")
                .font(.system(size: 34, weight: .heavy, design: .rounded))
                .foregroundStyle(Color.murshidGold)
            Text("جائزة المركز الأول").font(.subheadline).foregroundStyle(.white.opacity(0.9))
            Label("الإغلاق: \(endDate)", systemImage: "calendar")
                .font(.footnote).foregroundStyle(.white.opacity(0.89))
                .fixedSize(horizontal: false, vertical: true)
            if isLive {
                Label("المسابقة نشطة", systemImage: "circle.fill")
                    .font(.caption.bold()).foregroundStyle(.green)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(21)
        .background(
            LinearGradient(colors: [.murshidNavy, .murshidBlue], startPoint: .topLeading, endPoint: .bottomTrailing),
            in: RoundedRectangle(cornerRadius: 22, style: .continuous)
        )
    }

    @MainActor
    private func refreshInstructions() async {
        do {
            let response = try await APIClient.shared.request("mobile/contest-instructions.php")
            let updatedRules = jArray(response["rules"])
            let updatedPoints = jArray(response["points"])
            guard updatedRules.count == 7, updatedPoints.count == 5 else {
                networkWarning = true
                return
            }
            rules = updatedRules
            points = updatedPoints
            pointsNote = jString(response["points_note"], default: pointsNote)
            footer = jString(response["footer_note"], default: footer)
            prize = jInt(response["prize_iqd"], default: prize)
            isLive = jBool(response["active"])
            let date = jString(response["end_at"])
            if !date.isEmpty { endDate = date + " · بتوقيت بغداد" }
            networkWarning = false
        } catch {
            networkWarning = true
        }
    }
}

// Premium, lightweight startup greeting. Never interrupts sign-in/Face ID with an extra dialog.
// Understated, civic-inspired design. Iraqi identity without implying government affiliation.
// Scope intentionally limited to the launch welcome; existing app theme is untouched.
struct V45WelcomeView: View {
    @EnvironmentObject private var app: AppSession
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var appeared = false
    @State private var progressComplete = false

    private var ink: Color { Color(red: 0.039, green: 0.098, blue: 0.161) }
    private var slate: Color { Color(red: 0.063, green: 0.161, blue: 0.231) }
    private var emerald: Color { Color(red: 0.153, green: 0.51, blue: 0.392) }
    private var softEmerald: Color { Color(red: 0.60, green: 0.85, blue: 0.74) }
    private var ivory: Color { Color(red: 0.96, green: 0.97, blue: 0.96) }

    private var studentFirstName: String {
        let first = app.user?.name.split(separator: " ").first.map(String.init) ?? ""
        return first.isEmpty ? "أهلًا بك" : "أهلًا، \(first)"
    }

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [ink, slate, ink],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            // Geometric watermark; intentionally subtle and static.
            Circle()
                .stroke(ivory.opacity(0.045), lineWidth: 1)
                .frame(width: 370, height: 370)
                .offset(x: 175, y: -250)
            Circle()
                .stroke(ivory.opacity(0.035), lineWidth: 1)
                .frame(width: 500, height: 500)
                .offset(x: -210, y: 350)

            VStack(spacing: 0) {
                HStack(spacing: 9) {
                    Circle()
                        .fill(emerald)
                        .frame(width: 8, height: 8)
                    Text("منصة تعليمية عراقية")
                        .font(.caption.weight(.semibold))
                        .tracking(0.3)
                        .foregroundStyle(ivory.opacity(0.84))
                    Spacer()
                    HStack(spacing: 3) {
                        Capsule().fill(Color(red: 0.64, green: 0.20, blue: 0.23))
                        Capsule().fill(ivory)
                        Capsule().fill(emerald)
                    }
                    .frame(width: 35, height: 3)
                    .accessibilityLabel("ألوان مستوحاة من العراق")
                }
                .padding(.top, 37)

                Spacer(minLength: 28)

                VStack(spacing: 22) {
                    ZStack {
                        Circle()
                            .fill(ivory.opacity(0.055))
                            .frame(width: 188, height: 188)
                        Circle()
                            .stroke(ivory.opacity(0.17), lineWidth: 1)
                            .frame(width: 188, height: 188)
                        Circle()
                            .stroke(emerald.opacity(0.50), lineWidth: 2)
                            .frame(width: 154, height: 154)
                        Image("WelcomeEmblem")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 151, height: 151)
                            .accessibilityHidden(true)
                    }
                    .frame(height: 196)
                    .opacity(appeared ? 1 : 0)
                    .scaleEffect(appeared ? 1 : 0.93)

                    VStack(spacing: 12) {
                        Text(studentFirstName)
                            .font(.system(.title3, design: .rounded, weight: .medium))
                            .foregroundStyle(softEmerald)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)

                        Text("منصة المرشد الوزاري")
                            .font(.system(.largeTitle, design: .rounded, weight: .bold))
                            .foregroundStyle(ivory)
                            .multilineTextAlignment(.center)
                            .minimumScaleFactor(0.78)
                            .lineLimit(2)

                        RoundedRectangle(cornerRadius: 2)
                            .fill(emerald)
                            .frame(width: 62, height: 3)
                            .padding(.vertical, 4)

                        Text("تعلّم بثقة، واصنع مستقبلك")
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(ivory.opacity(0.81))
                            .multilineTextAlignment(.center)
                    }
                    .opacity(appeared ? 1 : 0)
                    .offset(y: appeared ? 0 : 12)
                }
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 5)

                Spacer(minLength: 34)

                VStack(spacing: 19) {
                    HStack(spacing: 10) {
                        Image(systemName: "books.vertical.fill")
                            .foregroundStyle(softEmerald)
                        Text("المنهج العراقي • اختبارات وزارية • مراجعة ذكية")
                            .font(.footnote.weight(.medium))
                            .foregroundStyle(ivory.opacity(0.88))
                            .multilineTextAlignment(.center)
                            .minimumScaleFactor(0.79)
                            .lineLimit(2)
                    }
                    .padding(.vertical, 15)
                    .frame(maxWidth: .infinity)
                    .background(
                        RoundedRectangle(cornerRadius: 15)
                            .fill(ivory.opacity(0.055))
                            .overlay(
                                RoundedRectangle(cornerRadius: 15)
                                    .stroke(ivory.opacity(0.10), lineWidth: 1)
                            )
                    )

                    GeometryReader { proxy in
                        ZStack(alignment: .leading) {
                            Capsule().fill(ivory.opacity(0.16))
                            Capsule()
                                .fill(emerald)
                                .frame(width: progressComplete ? proxy.size.width : 0)
                        }
                    }
                    .frame(height: 3)
                    .clipShape(Capsule())
                    .accessibilityHidden(true)

                    Text("المرشد الوزاري • العراق")
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(ivory.opacity(0.61))
                }
                .padding(.bottom, 29)
            }
            .padding(.horizontal, 25)
            .frame(maxWidth: 580)
        }
        .environment(\.layoutDirection, .rightToLeft)
        .onAppear {
            withAnimation(reduceMotion ? .none : .easeOut(duration: 0.72)) {
                appeared = true
            }
            withAnimation(reduceMotion ? .none : .easeInOut(duration: 2.08)) {
                progressComplete = true
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("مرحبًا بك في منصة المرشد الوزاري، منصة تعليمية عراقية")
    }
}
