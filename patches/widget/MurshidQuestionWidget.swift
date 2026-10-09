import WidgetKit
import SwiftUI

struct MurshidQuickEntry: TimelineEntry {
    let date: Date
}

struct MurshidQuickProvider: TimelineProvider {
    func placeholder(in context: Context) -> MurshidQuickEntry { MurshidQuickEntry(date: .now) }
    func getSnapshot(in context: Context, completion: @escaping (MurshidQuickEntry) -> Void) {
        completion(MurshidQuickEntry(date: .now))
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<MurshidQuickEntry>) -> Void) {
        completion(Timeline(entries: [MurshidQuickEntry(date: .now)], policy: .never))
    }
}

struct MurshidQuickWidgetView: View {
    let entry: MurshidQuickEntry

    var body: some View {
        ZStack {
            LinearGradient(colors: [Color(red: 0.07, green: 0.12, blue: 0.27), Color(red: 0.12, green: 0.29, blue: 0.52)], startPoint: .topTrailing, endPoint: .bottomLeading)
            VStack(alignment: .leading, spacing: 9) {
                Label("المرشد الوزاري", systemImage: "graduationcap.fill")
                    .font(.caption.bold()).foregroundStyle(Color(red: 0.98, green: 0.80, blue: 0.38))
                Spacer(minLength: 0)
                Text("سؤال اليوم")
                    .font(.title3.bold()).foregroundStyle(.white)
                Text("افتح التحدّي وحل السؤال")
                    .font(.caption).foregroundStyle(.white.opacity(0.84))
                Label("ابدأ الآن", systemImage: "arrow.left.circle.fill")
                    .font(.caption.bold()).foregroundStyle(Color(red: 0.98, green: 0.80, blue: 0.38))
            }
            .padding(16)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        }
        .environment(\.layoutDirection, .rightToLeft)
        .widgetURL(URL(string: "murshid://daily"))
    }
}

@main
struct MurshidQuestionWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "MurshidQuickQuestion", provider: MurshidQuickProvider()) { entry in
            MurshidQuickWidgetView(entry: entry)
        }
        .configurationDisplayName("سؤال سريع من المرشد الوزاري")
        .description("انتقل من شاشة الآيفون مباشرة إلى تحدي اليوم.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
