import SwiftUI
import UIKit

// MARK: - v4.3 reliable profile photos. No account photo ever leaks to another user.
@MainActor
enum V43AvatarCache {
    private static func location(for userID: Int) -> URL? {
        guard userID > 0,
              let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first else { return nil }
        return base.appendingPathComponent("murshid-avatar-\(userID).jpg", isDirectory: false)
    }

    static func image(for userID: Int) -> UIImage? {
        guard let url = location(for: userID),
              let bytes = try? Data(contentsOf: url) else { return nil }
        return UIImage(data: bytes)
    }

    static func save(_ jpeg: Data, for userID: Int) {
        guard let url = location(for: userID), UIImage(data: jpeg) != nil else { return }
        try? jpeg.write(to: url, options: [.atomic, .completeFileProtectionUnlessOpen])
    }

    static func clear(for userID: Int) {
        guard let url = location(for: userID) else { return }
        try? FileManager.default.removeItem(at: url)
    }

    static func resolvedURL(_ raw: String, revision: String) -> URL? {
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, !value.lowercased().contains("default-user.svg"),
              !value.lowercased().hasSuffix(".svg"),
              let resolved = URL(string: value, relativeTo: APIClient.shared.baseURL)?.absoluteURL,
              resolved.scheme?.lowercased() == "https",
              let host = resolved.host?.lowercased(),
              host == "www.mur-iq.com" || host == "mur-iq.com",
              var components = URLComponents(url: resolved, resolvingAgainstBaseURL: false) else { return nil }
        var params = components.queryItems ?? []
        params.removeAll(where: { $0.name == "ios_rev" })
        params.append(URLQueryItem(name: "ios_rev", value: revision.isEmpty ? "0" : revision))
        components.queryItems = params
        return components.url
    }
}

// Retain the last *successfully uploaded* JPEG while revalidating the remote photo.
// This fixes temporary blank/profile-placeholder transitions after an upload.
struct V43AvatarImage: View {
    let rawURL: String
    let revision: String
    let userID: Int
    let preview: UIImage?
    @State private var remoteImage: UIImage?
    @State private var fallbackImage: UIImage?

    private var requestIdentity: String { "\(userID)|\(rawURL)|\(revision)" }
    var body: some View {
        Group {
            if let preview {
                Image(uiImage: preview).resizable().scaledToFill()
            } else if let image = remoteImage ?? fallbackImage {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                Image(systemName: "person.crop.circle.fill")
                    .resizable()
                    .scaledToFill()
                    .foregroundStyle(.white.opacity(0.90))
            }
        }
        .frame(width: 78, height: 78)
        .clipShape(Circle())
        .overlay(Circle().stroke(.white.opacity(0.34), lineWidth: 2))
        .task(id: requestIdentity) {
            remoteImage = nil
            fallbackImage = V43AvatarCache.image(for: userID)
            guard let url = V43AvatarCache.resolvedURL(rawURL, revision: revision) else { return }
            var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 18)
            request.setValue("image/jpeg, image/png, image/webp", forHTTPHeaderField: "Accept")
            do {
                let (bytes, response) = try await URLSession.shared.data(for: request)
                guard !Task.isCancelled,
                      let result = response as? HTTPURLResponse, result.statusCode == 200,
                      result.value(forHTTPHeaderField: "Content-Type")?.lowercased().hasPrefix("image/") == true,
                      let image = UIImage(data: bytes) else { return }
                remoteImage = image
            } catch {
                // Keep the successfully uploaded local copy if the connection is interrupted.
            }
        }
        .accessibilityLabel("صورة الحساب")
    }
}
