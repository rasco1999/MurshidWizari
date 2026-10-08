import SwiftUI
import Foundation
import UIKit

// v4.3: the story index displays titles only; details open on user action.
struct V43StoriesView: View {
    @State private var stories: [JSON] = []
    @State private var loading = true
    @State private var error = ""

    var body: some View {
        Group {
            if loading && stories.isEmpty {
                LoadingView(text: "جاري تحميل القصص…")
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 13) {
                        V3IntroCard(
                            eyebrow: "استراحة خفيفة",
                            title: "غيّر جو",
                            text: "اختر عنوان قصة لقراءتها. قصص قصيرة تمنحك دفعة جديدة.",
                            icon: "sparkles"
                        )
                        if !error.isEmpty {
                            V3InlineMessage(text: error, icon: "wifi.exclamationmark", tone: .warning)
                            Button("إعادة المحاولة") { Task { await load() } }
                                .font(.headline).foregroundStyle(Color.murshidBlue)
                        }
                        if stories.isEmpty && error.isEmpty {
                            EmptyStateView(systemImage: "books.vertical.fill",
                                           title: "لا توجد قصص الآن",
                                           message: "ستضاف قصص جديدة قريباً.")
                        }
                        ForEach(Array(stories.enumerated()), id: \.offset) { index, item in
                            NavigationLink {
                                V43StoryDetailView(story: item)
                            } label: {
                                MurshidCard {
                                    HStack(spacing: 12) {
                                        Image(systemName: "book.closed.fill")
                                            .font(.title3)
                                            .foregroundStyle(Color.murshidBlue)
                                            .frame(width: 42, height: 42)
                                            .background(Color.murshidBlue.opacity(0.1), in: RoundedRectangle(cornerRadius: 12))
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(jString(item["title"], default: "قصة \(index+1)"))
                                                .font(.headline)
                                                .foregroundStyle(.primary)
                                                .multilineTextAlignment(.leading)
                                            Text("اضغط لقراءة القصة")
                                                .font(.caption)
                                                .foregroundStyle(.secondary)
                                        }
                                        Spacer()
                                        Image(systemName: "chevron.left")
                                            .font(.subheadline.bold())
                                            .foregroundStyle(Color.murshidBlue)
                                    }
                                    .contentShape(Rectangle())
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(16)
                }
                .background(Color.murshidBackground)
                .refreshable { await load() }
            }
        }
        .navigationTitle("غيّر جو")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    private func load() async {
        do {
            let response = try await APIClient.shared.request("mobile/stories.php")
            stories = jArray(response["stories"])
            error = ""
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }
}

struct V43StoryDetailView: View {
    let story: JSON
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Label("قصة قصيرة", systemImage: "book.pages.fill")
                    .font(.caption.bold())
                    .foregroundStyle(Color.murshidBlue)
                Text(jString(story["title"], default: "غيّر جو"))
                    .font(.system(.title2, design: .rounded, weight: .bold))
                    .foregroundStyle(.primary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Divider()
                Text(jString(story["body"], default: jString(story["story_text"], default: "لا يوجد نص لهذه القصة.")))
                    .font(.system(size: 18))
                    .foregroundStyle(.primary)
                    .lineSpacing(8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .textSelection(.enabled)
            }
            .padding(22)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.murshidSurface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .padding(16)
        }
        .environment(\.layoutDirection, .rightToLeft)
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("قراءة القصة")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// v4.3: on-device fallback to avoid a blank profile while CDN/network is delayed.
// Cache is per authenticated student ID; never show another student's avatar.
enum V43AvatarCache {
    static func save(_ bytes: Data, userID: Int) {
        guard userID > 0, bytes.count > 1024 else { return }
        let folder = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
        guard let root = folder else { return }
        let file = root.appendingPathComponent("murshid-avatar-\(userID).jpg")
        try? bytes.write(to: file, options: .atomic)
    }

    static func image(userID: Int) -> UIImage? {
        guard userID > 0,
              let root = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first else { return nil }
        return UIImage(contentsOfFile: root.appendingPathComponent("murshid-avatar-\(userID).jpg").path)
    }

    static func url(_ raw: String, revision: String) -> URL? {
        guard !raw.isEmpty, let base = URL(string: "https://www.mur-iq.com"),
              let candidate = URL(string: raw, relativeTo: base)?.absoluteURL,
              candidate.scheme?.lowercased() == "https",
              ["www.mur-iq.com", "mur-iq.com"].contains(candidate.host?.lowercased() ?? ""),
              var components = URLComponents(url: candidate, resolvingAgainstBaseURL: false) else { return nil }
        var query = components.queryItems ?? []
        query.removeAll { $0.name == "ios_rev" }
        query.append(URLQueryItem(name: "ios_rev", value: revision))
        components.queryItems = query
        return components.url
    }
}
