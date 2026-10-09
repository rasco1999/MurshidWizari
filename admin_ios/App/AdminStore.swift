import SwiftUI
import Foundation

struct AdminStats: Decodable { var students:Int; var active_subs:Int; var questions:Int; var ads:Int }
struct AdminCode: Decodable, Identifiable { var id:Int; var months:Int; var status:String; var batch_label:String?; var valid_until:String?; var redeemed_by:Int? }
struct AdminAd: Decodable, Identifiable {
    var id:Int; var school_name:String; var ad_text:String
    var start_at:String?; var end_at:String?; var is_enabled:Int
    var impressions:Int; var max_impressions:Int; var total_price:Int; var days:Int
}
struct AdminStudent: Decodable, Identifiable {
    var id:Int; var full_name:String; var email:String?; var phone:String?
    var status:String; var grade_name:String?; var sub_status:String
    var expires_at:String?; var subscribed:Int
}
struct AdminQuestion: Decodable, Identifiable {
    var id:Int; var chapter_id:Int?; var type:String; var question_text:String
    var correct_answer:String; var explanation:String?; var status:Int
    var chapter_name:String?; var subject_name:String?; var grade_name:String?
}
struct AdminChapter: Decodable, Identifiable { var id:Int; var title:String }
struct AdminReply: Decodable {
    var ok:Bool; var message:String?; var csrf:String?; var pending_2fa:Bool?
    var name:String?; var capabilities:[String]?; var stats:AdminStats?
    var codes:[AdminCode]?; var ads:[AdminAd]?; var students:[AdminStudent]?
    var questions:[AdminQuestion]?; var chapters:[AdminChapter]?; var issued:[String]?
}
struct AdminError: LocalizedError {
    let message:String
    var errorDescription:String? { message }
}
@MainActor final class AdminStore: ObservableObject {
    enum State { case checking, login, totp, ready }
    @Published var state:State = .checking
    @Published var working = false
    @Published var error = ""
    @Published var notice = ""
    @Published var name = "المدير"
    @Published var permissions:[String] = []
    @Published var stats = AdminStats(students:0,active_subs:0,questions:0,ads:0)
    @Published var codes:[AdminCode] = []
    @Published var ads:[AdminAd] = []
    @Published var students:[AdminStudent] = []
    @Published var questions:[AdminQuestion] = []
    @Published var chapters:[AdminChapter] = []
    @Published var issued:[String] = []
    private var csrf = ""
    private let domain="https://www.mur-iq.com"
    private let session:URLSession
    init() {
        let c=URLSessionConfiguration.default
        c.httpCookieStorage = .shared
        c.httpShouldSetCookies = true
        c.requestCachePolicy = .reloadIgnoringLocalCacheData
        c.timeoutIntervalForRequest=25
        session=URLSession(configuration:c)
    }
    private func call(_ path:String, body:[String:Any]?=nil) async throws -> AdminReply {
        guard let url=URL(string:domain+path),url.scheme=="https" else { throw AdminError(message:"عنوان الخدمة غير صحيح.") }
        var req=URLRequest(url:url); req.cachePolicy = .reloadIgnoringLocalCacheData
        req.setValue("application/json",forHTTPHeaderField:"Accept")
        if let body {
            req.httpMethod="POST"
            req.httpBody=try JSONSerialization.data(withJSONObject:body)
            req.setValue("application/json",forHTTPHeaderField:"Content-Type")
        }
        let data:Data
        do { (data,_) = try await session.data(for:req) }
        catch { throw AdminError(message:"تعذّر الاتصال بالموقع. تحقق من الإنترنت.") }
        guard let response=try? JSONDecoder().decode(AdminReply.self,from:data) else {
            throw AdminError(message:"خدمة الإدارة الأصلية لم تُركّب على الاستضافة بعد.")
        }
        guard response.ok else { throw AdminError(message:response.message ?? "تعذّر تنفيذ العملية.") }
        return response
    }
    func check() async {
        do {
            let result=try await call("/admin/native-api.php?mode=status")
            csrf=result.csrf ?? csrf
            if result.pending_2fa == true { state = .totp }
            else {
                name=result.name ?? "المدير"
                permissions=result.capabilities ?? []
                state = .ready
                await load("dashboard")
            }
            error=""
        } catch {
            state = .login
            if !error.localizedDescription.contains("سجّل الدخول") { self.error=error.localizedDescription }
        }
    }
    func signIn(id:String,password:String) async {
        working=true;error=""
        defer { working=false }
        do {
            _ = try await call("/api/login.php",body:["identifier":id,"password":password])
            await check()
        } catch { self.error=error.localizedDescription }
    }
    func verify(_ code:String) async {
        working=true;error=""
        defer { working=false }
        do {
            _ = try await call("/admin/native-api.php",body:["mode":"verify_2fa","code":code,"csrf":csrf])
            await check()
        } catch { self.error=error.localizedDescription }
    }
    func signOut() async {
        do { _ = try await call("/admin/native-api.php",body:["mode":"logout","csrf":csrf]) }
        catch { error=error.localizedDescription;return }
        state = .login;permissions=[];codes=[];ads=[];students=[];questions=[]
    }
    func allowed(_ capability:String) -> Bool { permissions.contains(capability) }
    func load(_ section:String,query:String="") async {
        guard state == .ready else { return }
        working=true
        defer { working=false }
        var parts=URLComponents(string:domain+"/admin/native-api.php")!
        parts.queryItems=[URLQueryItem(name:"mode",value:section)]
        if !query.isEmpty { parts.queryItems?.append(URLQueryItem(name:"q",value:query)) }
        let path=parts.url!.absoluteString.replacingOccurrences(of:domain,with:"")
        do {
            let data=try await call(path)
            switch section {
            case "dashboard": stats=data.stats ?? stats
            case "codes": codes=data.codes ?? []
            case "ads": ads=data.ads ?? []
            case "students","subscriptions": students=data.students ?? []
            case "questions": questions=data.questions ?? []; chapters=data.chapters ?? []
            default: break
            }
            error=""
        } catch { error=error.localizedDescription }
    }
    @discardableResult func change(_ section:String,_ action:String, fields:[String:Any]=[:]) async -> Bool {
        working=true; error=""
        defer { working=false }
        var body=fields
        body["mode"]=section;body["action"]=action;body["csrf"]=csrf
        do {
            let data=try await call("/admin/native-api.php",body:body)
            notice=data.message ?? "تمت العملية."
            if let issued=data.issued { self.issued=issued }
            working=false
            await load(section)
            return true
        } catch { error=error.localizedDescription;return false }
    }
}
