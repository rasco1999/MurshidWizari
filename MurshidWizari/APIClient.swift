import Foundation

struct LoginResult: Decodable { var name: String = ""; var success: Bool? }
struct Subject: Identifiable, Decodable { let id: Int; let name: String }
struct Topic: Identifiable, Decodable { let id: Int; let name: String }

final class APIClient {
    private let base = URL(string: "https://mur-iq.com")!
    private let session: URLSession
    init() {
        let c = URLSessionConfiguration.default
        c.httpCookieStorage = .shared
        c.httpShouldSetCookies = true
        c.timeoutIntervalForRequest = 25
        session = URLSession(configuration: c)
    }
    private func request(_ path: String, method: String = "GET", form: [String:String] = [:]) async throws -> Data {
        var r = URLRequest(url: base.appendingPathComponent(path))
        r.httpMethod = method
        r.setValue("application/json", forHTTPHeaderField: "Accept")
        if method != "GET" {
            r.setValue("application/x-www-form-urlencoded; charset=utf-8", forHTTPHeaderField: "Content-Type")
            r.httpBody = form.map { "\($0.key.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!)=\($0.value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!)" }.joined(separator:"&").data(using:.utf8)
        }
        let (d,res) = try await session.data(for:r)
        guard let h=res as? HTTPURLResponse, (200..<400).contains(h.statusCode) else { throw URLError(.badServerResponse) }
        return d
    }
    func login(email:String,password:String) async throws -> LoginResult {
        let d = try await request("mobile/login.php", method:"POST", form:["email":email,"password":password])
        if let x = try? JSONDecoder().decode(LoginResult.self, from:d) { return x }
        return LoginResult(name:"الطالب",success:true)
    }
    func subjects() async throws -> [Subject] {
        let d = try await request("mobile/bootstrap.php")
        if let x = try? JSONDecoder().decode([Subject].self, from:d) { return x }
        return []
    }
}
