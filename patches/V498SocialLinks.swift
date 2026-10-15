import SwiftUI

struct V498SocialLinksFooter: View {
    // Small logos, full 44pt touch targets. No oversized tiles, faux "f" text,
    // generic camera/paper-plane symbols, shadows or forced RTL logo mirroring.
    private let accounts: [(asset: String, name: String, url: String)] = [
        ("SocialInstagram", "Instagram", "https://www.instagram.com/murshid.iq/"),
        ("SocialFacebook", "Facebook", "https://www.facebook.com/Murshidiq/"),
        ("SocialTelegram", "Telegram", "https://t.me/El_Murshed_iq")
    ]
    var body: some View {
        VStack(spacing: 8) {
            Divider()
            HStack(spacing: 6) {
                Text("تابعنا").font(.footnote.weight(.medium)).foregroundStyle(.secondary)
                Spacer(minLength: 16)
                ForEach(accounts, id: \.asset) { account in
                    if let url = URL(string: account.url) {
                        Link(destination: url) {
                            Image(account.asset)
                                .resizable()
                                .scaledToFit()
                                .foregroundStyle(.primary)
                                .frame(width: 22, height: 22)
                                .frame(width: 44, height: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("حساب المرشد الوزاري على \(account.name)")
                        .accessibilityHint("يفتح حسابنا خارج التطبيق")
                        .accessibilityIdentifier("social-\(account.name.lowercased())")
                    }
                }
            }
        }
        .padding(.top, 8)
        .accessibilityElement(children: .contain)
    }
}
