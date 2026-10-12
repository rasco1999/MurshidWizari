import SwiftUI
import Combine
import UIKit

// The server owns the seat capacity, new-account eligibility, campaign window,
// and one-month grant. Dismissing the one-time sheet never removes this card.
struct V50FreeMonthOfferCard: View {
    @EnvironmentObject private var app: AppSession
    @State private var campaign: JSON = [:]
    @State private var claiming = false
    @State private var alertText = ""
    @State private var showAlert = false
    @State private var showWelcomeOffer = false
    @State private var currentClock = Date()
    @State private var lastRemoteRefresh = Date.distantPast
    private let ticker = Timer.publish(every: 15, on: .main, in: .common).autoconnect()

    private var endsAt: Date? {
        ISO8601DateFormatter().date(from: jString(campaign["ends_at_utc"]))
    }

    private var visible: Bool {
        jBool(campaign["enabled"]) &&
        jBool(campaign["started"]) &&
        !jBool(campaign["expired"]) &&
        jBool(campaign["eligible_new_student"]) &&
        jInt(campaign["remaining"]) > 0 &&
        (endsAt.map { currentClock < $0 } ?? false) &&
        !jBool(campaign["already_claimed"]) && !app.subscribed
    }

    var body: some View {
        Group {
            if visible {
                MurshidCard {
                    VStack(alignment: .leading, spacing: 13) {
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: "gift.fill")
                                .font(.title2)
                                .foregroundStyle(Color.murshidGold)
                                .frame(width: 45, height: 45)
                                .background(Color.murshidGold.opacity(0.14),
                                            in: RoundedRectangle(cornerRadius: 12))
                            VStack(alignment: .leading, spacing: 5) {
                                Text("شهر مجاني لأول \(jInt(campaign["capacity"])) طالب 🎁")
                                    .font(.headline).foregroundStyle(.primary)
                                Text("لأنك سجلت خلال فترة الحملة، تستطيع تفعيل شهر كامل دون أي دفع.")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer(minLength: 0)
                        }

                        HStack {
                            Label("المقاعد المتبقية: \(jInt(campaign["remaining"])) من \(jInt(campaign["capacity"]))",
                                  systemImage: "person.3.fill")
                                .font(.caption.weight(.semibold))
                            Spacer(minLength: 0)
                        }
                        .foregroundStyle(Color.murshidBlue)

                        Label("ينتهي العرض: \(endingTime)", systemImage: "clock.badge.exclamationmark")
                            .font(.caption).foregroundStyle(.secondary)

                        Button {
                            Task { await claimFreeMonth() }
                        } label: {
                            HStack(spacing: 8) {
                                if claiming { ProgressView().tint(.white) }
                                else { Image(systemName: "gift.fill") }
                                Text("احصل على شهرك المجاني")
                                    .fontWeight(.bold)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 13)
                            .foregroundStyle(.white)
                            .background(Color.murshidBlue, in: RoundedRectangle(cornerRadius: 12))
                        }
                        .disabled(claiming)
                        .opacity(claiming ? 0.55 : 1)

                        Text("إن أغلقت نافذة العرض بالخطأ، يمكنك الحصول عليه من هذا الزر حتى نهاية المهلة أو اكتمال المقاعد.")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
        .alert("عرض الشهر المجاني", isPresented: $showAlert) {
            Button("حسنًا", role: .cancel) {}
        } message: {
            Text(alertText)
        }
        .sheet(isPresented: $showWelcomeOffer) {
            VStack(spacing: 17) {
                Image(systemName: "gift.circle.fill")
                    .font(.system(size: 64))
                    .foregroundStyle(Color.murshidGold)
                Text("هدية لحسابك الجديد 🎁")
                    .font(.title2.bold())
                Text("احصل على اشتراك مجاني لمدة شهر كامل لأنك أنشأت حسابك خلال الحملة!")
                    .font(.body)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
                Text("المقاعد المتبقية: \(jInt(campaign["remaining"]))")
                    .font(.subheadline.weight(.semibold))
                Button {
                    showWelcomeOffer = false
                    Task { await claimFreeMonth() }
                } label: {
                    Text("تفعيل الشهر المجاني الآن")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(14)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.murshidBlue)
                Button("لاحقًا — يوجد زر في الرئيسية") {
                    showWelcomeOffer = false
                }
                .foregroundStyle(.secondary)
                Text("تنتهي فرصة الحجز في \(endingTime) أو عند اكتمال العدد.")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            .padding(24)
            .presentationDetents([.height(485)])
            .presentationDragIndicator(.visible)
        }
        .task { await refreshOffer() }
        .onReceive(ticker) { tick in
            currentClock = tick
            if !visible { showWelcomeOffer = false }
            if tick.timeIntervalSince(lastRemoteRefresh) >= 90 {
                Task { await refreshOffer() }
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
            Task { await refreshOffer() }
        }
    }

    private var endingTime: String {
        let source = jString(campaign["ends_at_utc"])
        guard let date = ISO8601DateFormatter().date(from: source) else { return "قريبًا" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ar_IQ")
        formatter.timeZone = TimeZone(identifier: "Asia/Baghdad")
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date) + " (بتوقيت بغداد)"
    }

    private func refreshOffer() async {
        guard app.v3Authenticated else { return }
        lastRemoteRefresh = Date()
        // The server update may not yet be deployed; keep the existing Home
        // screen unaffected by an unavailable optional campaign endpoint.
        guard let result = try? await APIClient.shared.request("mobile/free-month-offer.php") else { return }
        campaign = result["campaign"] as? JSON ?? [:]
        let key = "murshid_new_student_offer_\(app.user?.id ?? 0)_\(jString(campaign["starts_at_utc"]))"
        if visible && !UserDefaults.standard.bool(forKey: key) {
            // One prompt per campaign per account, including after relaunch.
            // The independent home card stays available after any dismissal.
            UserDefaults.standard.set(true, forKey: key)
            showWelcomeOffer = true
        }
    }

    private func claimFreeMonth() async {
        guard visible, !claiming, !app.csrf.isEmpty, app.v3Authenticated else { return }
        claiming = true
        defer { claiming = false }
        do {
            let result = try await APIClient.shared.request(
                "mobile/free-month-offer.php", method: "POST", body: ["csrf": app.csrf]
            )
            alertText = jString(result["message"], default: "تم تفعيل شهر مجاني لحسابك.")
            // Re-read subscription access from the server; never grant it on
            // the device solely because an optimistic button tap succeeded.
            if let fresh = try? await APIClient.shared.bootstrap() {
                app.applyBootstrap(fresh)
            } else {
                alertText += " افتح الصفحة مجددًا لتحديث صلاحية اشتراكك."
            }
            await refreshOffer()
        } catch {
            alertText = error.localizedDescription
            await refreshOffer()
        }
        showAlert = true
    }
}
