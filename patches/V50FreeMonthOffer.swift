import SwiftUI

// The server owns the 100-seat capacity, eligibility, scheduled launch, and
// one-month subscription grant. This card only displays the live offer state.
struct V50FreeMonthOfferCard: View {
    @EnvironmentObject private var app: AppSession
    @State private var campaign: JSON = [:]
    @State private var claiming = false
    @State private var alertText = ""
    @State private var showAlert = false

    private var visible: Bool {
        jBool(campaign["enabled"]) &&
        jInt(campaign["remaining"]) > 0 &&
        !jBool(campaign["already_claimed"]) && !app.subscribed
    }
    private var started: Bool { jBool(campaign["started"]) }

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
                                Text(started ? "العرض بدأ! احجز شهرًا كاملًا دون أي دفع." : "عرض قريبًا، بموعد تحدده إدارة المنصة.")
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

                        if !started {
                            Label("يبدأ: \(scheduledTime)", systemImage: "calendar.badge.clock")
                                .font(.caption).foregroundStyle(.secondary)
                        }

                        Button {
                            Task { await claimFreeMonth() }
                        } label: {
                            HStack(spacing: 8) {
                                if claiming { ProgressView().tint(.white) }
                                else { Image(systemName: started ? "gift.fill" : "clock.fill") }
                                Text(started ? "احصل على شهرك المجاني" : "بانتظار موعد الانطلاق")
                                    .fontWeight(.bold)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 13)
                            .foregroundStyle(.white)
                            .background(Color.murshidBlue, in: RoundedRectangle(cornerRadius: 12))
                        }
                        .disabled(!started || claiming)
                        .opacity(!started || claiming ? 0.55 : 1)

                        Text("تفعيل مرة واحدة للحساب المؤكد وغير المشترك؛ تنتهي المدة بعد شهر من لحظة الحصول عليها.")
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
        .task { await refreshOffer() }
    }

    private var scheduledTime: String {
        let source = jString(campaign["starts_at_utc"])
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
        // The server update may not yet be deployed; keep the existing Home
        // screen unaffected by an unavailable optional campaign endpoint.
        guard let result = try? await APIClient.shared.request("mobile/free-month-offer.php") else { return }
        campaign = result["campaign"] as? JSON ?? [:]
    }

    private func claimFreeMonth() async {
        guard started, !claiming, !app.csrf.isEmpty, app.v3Authenticated else { return }
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
