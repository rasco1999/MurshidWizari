import SwiftUI
import UIKit

extension Color {
    static let murshidNavy = Color(red: 11/255, green: 31/255, blue: 51/255)
    static let murshidBlue = Color(red: 45/255, green: 108/255, blue: 223/255)
    static let murshidGold = Color(red: 245/255, green: 184/255, blue: 61/255)
    static let murshidSurface = Color(uiColor: .secondarySystemGroupedBackground)
    static let murshidBackground = Color(uiColor: .systemGroupedBackground)
}

struct MurshidCard<Content: View>: View {
    @ViewBuilder let content: Content
    init(@ViewBuilder content: () -> Content) { self.content = content() }

    var body: some View {
        content
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.murshidSurface)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .stroke(Color.primary.opacity(0.055), lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.035), radius: 10, y: 4)
    }
}

struct PrimaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .frame(maxWidth: .infinity, minHeight: 50)
            .padding(.horizontal, 16)
            .foregroundStyle(.white)
            .background(
                LinearGradient(
                    colors: [.murshidBlue, Color(red: 0.09, green: 0.28, blue: 0.68)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .opacity(configuration.isPressed ? 0.82 : (isEnabled ? 1 : 0.48))
            )
            .clipShape(RoundedRectangle(cornerRadius: 17, style: .continuous))
            .scaleEffect(configuration.isPressed && isEnabled ? 0.985 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
            .animation(.easeOut(duration: 0.12), value: isEnabled)
    }
}

struct SecondaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .frame(maxWidth: .infinity, minHeight: 48)
            .padding(.horizontal, 14)
            .foregroundStyle(Color.murshidBlue)
            .background(Color.murshidBlue.opacity(configuration.isPressed ? 0.14 : 0.08))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Color.murshidBlue.opacity(0.22), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .opacity(isEnabled ? 1 : 0.48)
            .scaleEffect(configuration.isPressed && isEnabled ? 0.985 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

struct LoadingView: View {
    var text: String = "جاري التحميل…"

    var body: some View {
        VStack(spacing: 14) {
            ProgressView().controlSize(.large)
            Text(text).font(.subheadline).foregroundStyle(.secondary)
        }
        .padding(28)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .combine)
    }
}

struct ErrorStateView: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "wifi.exclamationmark")
                .font(.system(size: 40, weight: .semibold))
                .foregroundStyle(.orange)
            Text("تعذر إكمال الطلب")
                .font(.headline)
            Text(message)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .lineSpacing(3)
            Button("إعادة المحاولة", action: retry)
                .buttonStyle(PrimaryButtonStyle())
                .frame(maxWidth: 270)
        }
        .padding(28)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct EmptyStateView: View {
    let systemImage: String
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: systemImage)
                .font(.system(size: 42, weight: .medium))
                .foregroundStyle(Color.murshidBlue.opacity(0.72))
            Text(title).font(.headline)
            Text(message)
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .lineSpacing(3)
        }
        .padding(28)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct AcademicWelcomeMark: View {
    @State private var scale = 0.78
    @State private var opacity = 0.0
    @State private var glow = false

    var body: some View {
        ZStack {
            Circle()
                .fill(Color.murshidBlue.opacity(glow ? 0.20 : 0.08))
                .frame(width: 220, height: 220)
                .blur(radius: 2)
            Image("WelcomeEmblem")
                .resizable()
                .scaledToFit()
                .frame(width: 190, height: 190)
                .shadow(color: .murshidBlue.opacity(0.20), radius: 24, y: 12)
        }
        .scaleEffect(scale)
        .opacity(opacity)
        .onAppear {
            withAnimation(.spring(response: 0.65, dampingFraction: 0.72)) {
                scale = 1
                opacity = 1
            }
            withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) {
                glow = true
            }
        }
        .accessibilityHidden(true)
    }
}

extension View {
    func murshidNavigation() -> some View {
        self
            .navigationBarTitleDisplayMode(.inline)
            .environment(\.layoutDirection, .rightToLeft)
    }
}

@MainActor
func haptic(_ type: UINotificationFeedbackGenerator.FeedbackType = .success) {
    let generator = UINotificationFeedbackGenerator()
    generator.prepare()
    generator.notificationOccurred(type)
}

@MainActor
func selectionHaptic() {
    let generator = UISelectionFeedbackGenerator()
    generator.prepare()
    generator.selectionChanged()
}
