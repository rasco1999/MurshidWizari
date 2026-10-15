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
        ["number":7,"title":"موعد إغلاق التحدي","text":"تحدد إدارة المنصة موعد إغلاق التحدي، ويظهر الموعد المحدّث هنا عند الاتصال بالموقع. تثبت النتيجة النهائية بعد انتهاء مراجعة النزاهة."]
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
    @State private var endDate = "حسب إعدادات إدارة المنصة"
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
// Three-second cinematic Iraqi educational launch. Native SwiftUI animation,
// no WebView, looping video, external images, or fabricated official seals.
// Motion honors Accessibility > Reduce Motion and does not alter app identity.
struct V45WelcomeView: View {
    @EnvironmentObject private var app: AppSession
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var phase = 0
    @State private var animatedProgress: CGFloat = 0

    private let midnight = Color(red: 0.020, green: 0.064, blue: 0.133)
    private let royalBlue = Color(red: 0.052, green: 0.145, blue: 0.258)
    private let emerald = Color(red: 0.06, green: 0.48, blue: 0.35)
    private let paleGold = Color(red: 0.98, green: 0.80, blue: 0.42)
    private let ivory = Color(red: 0.98, green: 0.97, blue: 0.92)

    private var welcomeText: String {
        let first = app.user?.name.split(separator: " ").first.map(String.init) ?? ""
        return first.isEmpty ? "أهلًا بك" : "أهلًا، \(first)"
    }

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                atmosphericBackground
                skyline
                    .frame(height: geometry.size.height * 0.38)
                    .frame(maxHeight: .infinity, alignment: .bottom)
                    .opacity(phase >= 2 ? 0.58 : 0.0)
                    .offset(y: phase >= 2 ? 0 : 38)

                // A precise initial vertical light reveal; folds back when the logo lands.
                Capsule()
                    .fill(LinearGradient(
                        colors: [.clear, paleGold.opacity(0.95), ivory, emerald.opacity(0.70), .clear],
                        startPoint: .top, endPoint: .bottom
                    ))
                    .frame(width: phase >= 2 ? 1 : 5, height: geometry.size.height * 0.64)
                    .blur(radius: phase >= 2 ? 19 : 4)
                    .shadow(color: paleGold.opacity(0.7), radius: 24)
                    .scaleEffect(y: phase >= 1 ? 1 : 0.05, anchor: .top)
                    .opacity(phase >= 2 ? 0 : 1)
                    .offset(y: -geometry.size.height * 0.17)

                VStack(spacing: 0) {
                    topIdentity
                        .padding(.top, 18)

                    Spacer(minLength: 8)

                    emblem
                        .frame(height: min(232, geometry.size.height * 0.30))

                    VStack(spacing: 16) {
                        Text("منصة المرشد الوزاري")
                            .font(.system(size: 30, weight: .bold, design: .rounded))
                            .minimumScaleFactor(0.67)
                            .lineLimit(2)
                            .fixedSize(horizontal: false, vertical: true)
                            .multilineTextAlignment(.center)
                            .foregroundStyle(ivory)
                            .shadow(color: .black.opacity(0.33), radius: 9, y: 3)
                            .opacity(phase >= 2 ? 1 : 0)
                            .offset(y: phase >= 2 ? 0 : 25)
                            .scaleEffect(phase >= 2 ? 1 : 0.94)

                        HStack(spacing: 9) {
                            Rectangle().fill(paleGold.opacity(0.68)).frame(width: 36, height: 1)
                            Image(systemName: "diamond.fill")
                                .font(.system(size: 8))
                                .foregroundStyle(emerald)
                            Rectangle().fill(paleGold.opacity(0.68)).frame(width: 36, height: 1)
                        }
                        .opacity(phase >= 2 ? 1 : 0)

                        Text("تعلّم بثقة، واصنع مستقبلك")
                            .font(.subheadline.weight(.medium))
                            .multilineTextAlignment(.center)
                            .foregroundStyle(ivory.opacity(0.85))
                            .opacity(phase >= 2 ? 1 : 0)
                            .offset(y: phase >= 2 ? 0 : 12)

                        Text(welcomeText)
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(paleGold)
                            .lineLimit(2)
                            .minimumScaleFactor(0.85)
                            .fixedSize(horizontal: false, vertical: true)
                            .opacity(phase >= 3 ? 1 : 0)
                            .scaleEffect(phase >= 3 ? 1 : 0.82)
                            .padding(.top, 6)
                    }
                    .padding(.horizontal, 17)

                    Spacer(minLength: 14)

                    VStack(spacing: 13) {
                        HStack(spacing: 9) {
                            Image(systemName: "graduationcap.fill")
                                .foregroundStyle(paleGold)
                            Text("المنهج العراقي • طريقك نحو النجاح")
                                .foregroundStyle(ivory.opacity(0.9))
                        }
                        .font(.caption.weight(.medium))
                        .multilineTextAlignment(.center)

                        GeometryReader { proxy in
                            ZStack(alignment: .leading) {
                                Capsule()
                                    .fill(ivory.opacity(0.19))
                                Capsule()
                                    .fill(LinearGradient(
                                        colors: [emerald, Color(red: 0.40, green: 0.87, blue: 0.65), paleGold],
                                        startPoint: .leading, endPoint: .trailing
                                    ))
                                    .frame(width: proxy.size.width * animatedProgress)
                            }
                        }
                        .frame(height: 4)
                        .clipShape(Capsule())
                    }
                    .opacity(phase >= 2 ? 1 : 0)
                    .padding(.horizontal, 27)
                    .padding(.bottom, 18)
                }
                // Bound the *content* width before expanding the outer frame.
                // The previous unbounded HStack pushed the tricolour offscreen.
                .frame(width: min(max(geometry.size.width - 32, 1), 560))
                .frame(maxWidth: .infinity)
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
        }
        // Keep the artwork full-bleed but content inside iOS safe areas.
        .background(midnight.ignoresSafeArea())
        .environment(\.layoutDirection, .rightToLeft)
        .task { await playThreeSecondSequence() }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("منصة المرشد الوزاري. \(welcomeText)")
    }

    private var atmosphericBackground: some View {
        ZStack {
            LinearGradient(
                colors: [midnight, royalBlue, Color(red: 0.025, green: 0.10, blue: 0.18), midnight],
                startPoint: .topTrailing, endPoint: .bottomLeading
            )

            RadialGradient(
                colors: [emerald.opacity(phase >= 2 ? 0.38 : 0.12), .clear],
                center: .init(x: 0.78, y: 0.32),
                startRadius: 8,
                endRadius: 320
            )
            RadialGradient(
                colors: [paleGold.opacity(phase >= 1 ? 0.19 : 0.0), .clear],
                center: .init(x: 0.23, y: 0.60),
                startRadius: 9,
                endRadius: 280
            )

            // Subtle institutional geometry; not a government seal.
            Circle()
                .stroke(ivory.opacity(0.045), lineWidth: 1)
                .frame(width: 520, height: 520)
                .offset(x: 245, y: -245)
            Circle()
                .stroke(paleGold.opacity(0.08), lineWidth: 1)
                .frame(width: 430, height: 430)
                .offset(x: -205, y: 300)
        }
        .ignoresSafeArea()
    }

    private var skyline: some View {
        V46IraqiSkyline()
            .fill(
                LinearGradient(
                    colors: [emerald.opacity(0.02), Color(red: 0.14, green: 0.31, blue: 0.33), .black.opacity(0.85)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
            .accessibilityHidden(true)
    }

    private var topIdentity: some View {
        HStack(spacing: 10) {
            Circle().fill(emerald).frame(width: 7, height: 7)
            Text("منصة تعليمية عراقية")
                .font(.caption.weight(.semibold))
                .tracking(0.5)
                .lineLimit(1)
                .minimumScaleFactor(0.80)
                .foregroundStyle(ivory.opacity(0.86))
            Spacer()
            HStack(spacing: 2) {
                Capsule().fill(Color(red: 0.80, green: 0.23, blue: 0.27))
                Capsule().fill(ivory)
                Capsule().fill(emerald)
            }
            .frame(width: 40, height: 3)
        }
        .opacity(phase >= 1 ? 1 : 0)
        .padding(.horizontal, 4)
    }

    private var emblem: some View {
        ZStack {
            Circle()
                .stroke(ivory.opacity(0.075), lineWidth: 1)
                .frame(width: 216, height: 216)

            Circle()
                .trim(from: 0, to: phase >= 1 ? 0.93 : 0)
                .stroke(
                    AngularGradient(
                        colors: [emerald, paleGold, ivory.opacity(0.92), emerald],
                        center: .center
                    ),
                    style: StrokeStyle(lineWidth: 2.0, lineCap: .round)
                )
                .frame(width: 202, height: 202)
                .rotationEffect(.degrees(phase >= 2 ? 215 : -70))
                .shadow(color: paleGold.opacity(0.48), radius: 15)

            Circle()
                .trim(from: 0, to: phase >= 2 ? 0.78 : 0)
                .stroke(emerald.opacity(0.65), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
                .frame(width: 224, height: 224)
                .rotationEffect(.degrees(phase >= 2 ? -180 : 45))

            Circle()
                .fill(paleGold.opacity(phase >= 1 ? 0.15 : 0.0))
                .frame(width: 166, height: 166)
                .blur(radius: phase >= 2 ? 28 : 10)

            Image("WelcomeEmblem")
                .resizable()
                .scaledToFit()
                .frame(width: 160, height: 160)
                .shadow(color: paleGold.opacity(0.23), radius: 25, y: 6)
                .scaleEffect(phase == 0 ? 0.36 : (phase == 1 ? 1.12 : 1))
                .rotationEffect(.degrees(phase == 0 ? -16 : 0))
                .opacity(phase >= 1 ? 1 : 0)

            ForEach(0..<6, id: \.self) { index in
                Image(systemName: "sparkle")
                    .font(.system(size: index.isMultiple(of: 2) ? 8 : 5))
                    .foregroundStyle(index.isMultiple(of: 2) ? paleGold : ivory)
                    .offset(
                        x: CGFloat([87, -112, 119, -79, 0, 63][index]),
                        y: CGFloat([-83, -48, 56, 79, -114, 104][index])
                    )
                    .opacity(phase >= 1 ? (phase == 2 ? 0.88 : 0.42) : 0)
                    .scaleEffect(phase >= 2 ? 1 : 0.3)
                    .accessibilityHidden(true)
            }
        }
        .animation(reduceMotion ? .none : .easeInOut(duration: 0.40), value: phase)
    }

    @MainActor
    private func playThreeSecondSequence() async {
        // The app host ends the overlay at 3 seconds including its exit fade.
        phase = 0
        animatedProgress = 0

        withAnimation(reduceMotion ? .none : .easeOut(duration: 0.46)) {
            phase = 1
        }
        try? await Task.sleep(nanoseconds: 620_000_000)
        guard !Task.isCancelled else { return }

        withAnimation(reduceMotion ? .none : .spring(response: 0.75, dampingFraction: 0.75)) {
            phase = 2
        }
        withAnimation(reduceMotion ? .none : .linear(duration: 1.75)) {
            animatedProgress = 1
        }
        try? await Task.sleep(nanoseconds: 1_250_000_000)
        guard !Task.isCancelled else { return }

        withAnimation(reduceMotion ? .none : .spring(response: 0.44, dampingFraction: 0.83)) {
            phase = 3
        }
    }
}

// Baghdad-inspired silhouette composed of vector geometry.
// Avoids heavyweight image/video decoding on cold launch.
private struct V46IraqiSkyline: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width
        let h = rect.height
        var path = Path()
        path.move(to: CGPoint(x: 0, y: h * 0.67))
        path.addLine(to: CGPoint(x: w * 0.07, y: h * 0.67))
        path.addLine(to: CGPoint(x: w * 0.07, y: h * 0.50))
        path.addLine(to: CGPoint(x: w * 0.15, y: h * 0.50))
        path.addLine(to: CGPoint(x: w * 0.15, y: h * 0.62))
        path.addLine(to: CGPoint(x: w * 0.24, y: h * 0.62))
        path.addLine(to: CGPoint(x: w * 0.24, y: h * 0.38))
        path.addLine(to: CGPoint(x: w * 0.28, y: h * 0.38))
        path.addLine(to: CGPoint(x: w * 0.28, y: h * 0.28))
        path.addLine(to: CGPoint(x: w * 0.31, y: h * 0.28))
        path.addLine(to: CGPoint(x: w * 0.31, y: h * 0.62))
        path.addLine(to: CGPoint(x: w * 0.41, y: h * 0.62))
        path.addLine(to: CGPoint(x: w * 0.41, y: h * 0.53))
        path.addQuadCurve(to: CGPoint(x: w * 0.58, y: h * 0.53),
                          control: CGPoint(x: w * 0.495, y: h * 0.29))
        path.addLine(to: CGPoint(x: w * 0.58, y: h * 0.66))
        path.addLine(to: CGPoint(x: w * 0.69, y: h * 0.66))
        path.addLine(to: CGPoint(x: w * 0.69, y: h * 0.40))
        path.addLine(to: CGPoint(x: w * 0.74, y: h * 0.40))
        path.addLine(to: CGPoint(x: w * 0.74, y: h * 0.30))
        path.addLine(to: CGPoint(x: w * 0.77, y: h * 0.30))
        path.addLine(to: CGPoint(x: w * 0.77, y: h * 0.63))
        path.addLine(to: CGPoint(x: w * 0.88, y: h * 0.63))
        path.addLine(to: CGPoint(x: w * 0.88, y: h * 0.51))
        path.addLine(to: CGPoint(x: w, y: h * 0.51))
        path.addLine(to: CGPoint(x: w, y: h))
        path.addLine(to: CGPoint(x: 0, y: h))
        path.closeSubpath()
        return path
    }
}
