import SwiftUI

// MARK: - v4.3 "غيّر جو": list headings first, open a separate reading screen.
struct V43StoriesView: View {
    @State private var stories: [JSON] = []
    @State private var loading = true
    @State private var error = ""

    var body: some View {
        Group {
            if loading {
                LoadingView()
            } else if !error.isEmpty {
                ErrorStateView(message: error, retry: { Task { await load() } })
            } else if stories.isEmpty {
                EmptyStateView(systemImage: "sparkles", title: "لا توجد قصص الآن", message: "ستظهر قصص خفيفة وتحفيزية هنا قريبًا.")
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(Array(stories.enumerated()), id: \.offset) { index, story in
                            NavigationLink(destination: V43StoryDetailView(story: story)) {
                                HStack(spacing: 14) {
                                    Image(systemName: "book.closed.fill")
                                        .font(.title3)
                                        .foregroundStyle(Color.murshidBlue)
                                        .frame(width: 44, height: 44)
                                        .background(Color.murshidBlue.opacity(0.10), in: RoundedRectangle(cornerRadius: 12))
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(jString(story["title"], default: "قصة \(index + 1)"))
                                            .font(.headline)
                                            .foregroundStyle(.primary)
                                            .multilineTextAlignment(.leading)
                                        Text("اضغط لقراءة القصة")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                    Spacer(minLength: 8)
                                    Image(systemName: "chevron.left").foregroundStyle(.secondary)
                                }
                                .padding(15)
                                .background(Color.murshidSurface, in: RoundedRectangle(cornerRadius: 17, style: .continuous))
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityHint("افتح صفحة قراءة القصة")
                        }
                    }.padding(16)
                }
                .background(Color.murshidBackground)
            }
        }
        .navigationTitle("غيّر جو")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        loading = true
        do {
            let json = try await APIClient.shared.request("mobile/stories.php")
            stories = jArray(json["stories"])
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
            VStack(alignment: .leading, spacing: 20) {
                Label("استراحة قصيرة", systemImage: "sparkles")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Color.murshidBlue)
                Text(jString(story["title"], default: "غيّر جو"))
                    .font(.title2.bold())
                    .foregroundStyle(.primary)
                Divider()
                Text(jString(story["body"], default: jString(story["story_text"], default: "المحتوى غير متاح حاليًا.")))
                    .font(.body)
                    .lineSpacing(9)
                    .foregroundStyle(.primary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .textSelection(.enabled)
            }
            .padding(22)
            .background(Color.murshidSurface, in: RoundedRectangle(cornerRadius: 22))
            .padding(16)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .environment(\.layoutDirection, .rightToLeft)
        .navigationTitle("قراءة القصة")
        .navigationBarTitleDisplayMode(.inline)
    }
}
