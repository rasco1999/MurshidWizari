import SwiftUI
import LocalAuthentication
import UIKit

typealias J = [String: Any]
// The palette is local to the administrator's iPhone and never changes server data.
private enum AdminPalette: String, CaseIterable, Identifiable {
    case aurora, orchid, royal
    var id: String { rawValue }
    static var current: AdminPalette {
        AdminPalette(rawValue: UserDefaults.standard.string(forKey: "admin_palette") ?? "aurora") ?? .aurora
    }
    var name: String {
        switch self {
        case .aurora: return "محيط"
        case .orchid: return "بنفسجي"
        case .royal: return "ملكي"
        }
    }
    var caption: String {
        switch self {
        case .aurora: return "كحلي وفيروزي"
        case .orchid: return "بنفسجي × وردي"
        case .royal: return "كحلي × ذهبي"
        }
    }
    var background: Color {
        switch self {
        case .aurora: return Color(red: 0.035, green: 0.055, blue: 0.090)
        case .orchid: return Color(red: 0.065, green: 0.042, blue: 0.125)
        case .royal: return Color(red: 0.025, green: 0.074, blue: 0.145)
        }
    }
    var surface: Color {
        switch self {
        case .aurora: return Color(red: 0.075, green: 0.100, blue: 0.145)
        case .orchid: return Color(red: 0.132, green: 0.092, blue: 0.219)
        case .royal: return Color(red: 0.075, green: 0.148, blue: 0.250)
        }
    }
    var edge: Color {
        switch self {
        case .aurora: return Color(red: 0.145, green: 0.185, blue: 0.240)
        case .orchid: return Color(red: 0.251, green: 0.180, blue: 0.365)
        case .royal: return Color(red: 0.115, green: 0.205, blue: 0.310)
        }
    }
    var accent: Color {
        switch self {
        case .aurora: return Color(red: 0.310, green: 0.880, blue: 0.775)
        case .orchid: return Color(red: 0.842, green: 0.682, blue: 1.000)
        case .royal: return Color(red: 0.972, green: 0.801, blue: 0.456)
        }
    }
    var second: Color {
        switch self {
        case .aurora: return Color(red: 0.375, green: 0.725, blue: 1.000)
        case .orchid: return Color(red: 1.000, green: 0.519, blue: 0.722)
        case .royal: return Color(red: 1.000, green: 0.657, blue: 0.439)
        }
    }
    var muted: Color {
        switch self {
        case .aurora: return Color(red: 0.650, green: 0.715, blue: 0.795)
        case .orchid: return Color(red: 0.801, green: 0.754, blue: 0.876)
        case .royal: return Color(red: 0.710, green: 0.785, blue: 0.870)
        }
    }
}
private enum Ink {
    static var navy: Color { AdminPalette.current.background }
    static var blue: Color { AdminPalette.current.surface }
    static var blue2: Color { AdminPalette.current.edge }
    static var gold: Color { AdminPalette.current.accent }
    static var secondary: Color { AdminPalette.current.second }
    static var faded: Color { AdminPalette.current.muted }
}
private func s(_ d:J,_ key:String,_ fallback:String="—")->String {
    guard let v=d[key], !(v is NSNull) else{return fallback}
    return String(describing:v)
}
private func num(_ d:J,_ key:String)->Int { (d[key] as? Int) ?? Int(s(d,key,"0")) ?? 0 }
private func list(_ d:J,_ key:String="items")->[J] {d[key] as? [J] ?? []}
private func safe(_ text:String)->String {text.trimmingCharacters(in: .whitespacesAndNewlines)}
private struct ServiceError: LocalizedError {
    let message:String
    var errorDescription:String? {message}
}
enum LoginStage {case loading, signin, otp, ready}
@MainActor final class AdminState: ObservableObject {
    @Published var stage:LoginStage = .loading
    @Published var name = "الإدارة"
    @Published var csrf = ""
    @Published var busy = false
    @Published var error = ""
    @Published var notice = ""
    @Published var dashboard:J = [:]
    @Published var campaign:J = [:]
    @Published var codes:[J] = []
    @Published var ads:[J] = []
    @Published var students:[J] = []
    @Published var subscriptions:[J] = []
    @Published var questions:[J] = []
    @Published var curriculum:J = [:]
    @Published var predictionSubjects:[J] = []
    @Published var predictionRows:[J] = []
    @Published var predictionMode = "automatic"
    #if DEBUG
    private var previewMode:Bool {ProcessInfo.processInfo.arguments.contains("--admin-preview") || ProcessInfo.processInfo.arguments.contains("--admin-login-preview")}
    init() {
        if ProcessInfo.processInfo.arguments.contains("--admin-preview") {
            name="مدير المنصة";stage = .ready
            dashboard=["stats":["students":1248,"subscriptions":386,"questions":18420,"codes":72,"ads":4,"teachers":18]]
            codes=[
                ["id":101,"months":1,"status":"available","batch_label":"دفعة أكتوبر","valid_until":"2026-12-31"],
                ["id":102,"months":3,"status":"redeemed","batch_label":"اشتراك الفصل الأول"],
                ["id":103,"months":1,"status":"revoked","batch_label":"دفعة سابقة"]]
            ads=[["id":1,"school_name":"مدرسة تجريبية","ad_text":"مساحة لإعلان المدرسة ومتابعة وقت العرض.","start_date":"2026-10-17","end_date":"2026-10-18","total_price":5000,"is_enabled":1]]
            students=[
                ["id":1,"full_name":"طالب تجريبي أول","grade_name":"السادس العلمي","phone":"07700000000","status":"active"],
                ["id":2,"full_name":"طالب تجريبي ثانٍ","grade_name":"الثالث المتوسط","phone":"07700000001","status":"inactive"]]
        } else if ProcessInfo.processInfo.arguments.contains("--admin-login-preview") {stage = .signin}
    }
    #endif
    private var appIsActive=false
    private var keepAliveTask:Task<Void,Never>?
    private let session:URLSession = {
        let c=URLSessionConfiguration.default
        c.httpCookieAcceptPolicy = .always
        c.httpShouldSetCookies = true
        // Permit essential HTTPS administration calls on cellular and Low Data Mode.
        // DNS resolution still belongs to iOS/the mobile provider.
        c.allowsCellularAccess = true
        c.allowsExpensiveNetworkAccess = true
        c.allowsConstrainedNetworkAccess = true
        c.timeoutIntervalForRequest = 18
        c.timeoutIntervalForResource = 38
        c.waitsForConnectivity = true
        return URLSession(configuration:c)
    }()
    private let endpoint=URL(string:"https://www.mur-iq.com/admin/native-api.php")!
    private func dataWithSafeRetry(_ request: URLRequest, retryRead: Bool) async throws -> (Data, URLResponse) {
        let retryCodes: Set<Int> = [
            URLError.Code.timedOut.rawValue,
            URLError.Code.networkConnectionLost.rawValue,
            URLError.Code.notConnectedToInternet.rawValue,
            URLError.Code.cannotConnectToHost.rawValue,
            URLError.Code.cannotFindHost.rawValue,
            URLError.Code.dnsLookupFailed.rawValue
        ]
        let attempts = retryRead ? 2 : 1
        for attempt in 0..<attempts {
            do {
                return try await session.data(for:request)
            } catch {
                let reason=error as NSError
                guard !Task.isCancelled,
                      attempt+1<attempts,
                      reason.domain==NSURLErrorDomain,
                      retryCodes.contains(reason.code) else { throw error }
                try await Task.sleep(nanoseconds:650_000_000)
            }
        }
        throw URLError(.unknown)
    }
    func call(_ action:String, params:J?=nil, query:String?=nil, extras:[URLQueryItem]=[]) async throws -> J {
        #if DEBUG
        if previewMode {
            guard params == nil else{throw ServiceError(message:"المعاينة تستخدم بيانات تجريبية فقط.")}
            switch action {
            case "dashboard": return dashboard
            case "codes": return ["items":codes]
            case "students": return ["items": query?.isEmpty == false ? students.filter{s($0,"full_name").contains(query!)} : students]
            case "ads": return ["items":ads]
            case "status": return ["ok":true,"name":name,"csrf":"preview"]
            default: return ["items":[J](),"ok":true]
            }
        }
        #endif
        var components=URLComponents(url:endpoint,resolvingAgainstBaseURL:false)!
        var qs=[URLQueryItem(name:"action",value:action)]
        if let query, !query.isEmpty {qs.append(URLQueryItem(name:"q",value:query))}
        qs.append(contentsOf:extras)
        components.queryItems=qs
        guard let url=components.url else {throw ServiceError(message:"رابط الخدمة غير صحيح.")}
        var request=URLRequest(url:url)
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.allowsCellularAccess = true
        request.allowsExpensiveNetworkAccess = true
        request.allowsConstrainedNetworkAccess = true
        request.setValue("application/json",forHTTPHeaderField:"Accept")
        if var params {
            params["action"]=action
            if action != "login" && action != "verify_2fa" {
                params["csrf"]=csrf
            }
            request.httpMethod="POST"
            request.setValue("application/json; charset=utf-8",forHTTPHeaderField:"Content-Type")
            request.httpBody=try JSONSerialization.data(withJSONObject:params,options:[])
        }
        let data:Data
        let response:URLResponse
        do {
            (data,response)=try await dataWithSafeRetry(request,retryRead:request.httpMethod=="GET")
        } catch let failure as URLError {
            let message:String
            switch failure.code {
            case .timedOut: message="انتهت مهلة الاتصال بالخادم. تحقق من بيانات الهاتف وأعد المحاولة."
            case .cannotFindHost, .dnsLookupFailed, .cannotConnectToHost:
                message="تعذّر الوصول إلى خادم المنصة عبر DNS لهذه الشبكة. جرّب إعادة المحاولة."
            default: message="تعذّر الاتصال بالمنصة. تحقق من اتصال الإنترنت."
            }
            throw ServiceError(message:message)
        }
        let result=(try? JSONSerialization.jsonObject(with:data)) as? J ?? [:]
        let http=(response as? HTTPURLResponse)?.statusCode ?? 0
        if http == 428 || (result["two_factor"] as? Bool == true && action == "status") {
            stage = .otp
            throw ServiceError(message:"أدخل رمز التحقق الثنائي.")
        }
        guard http>=200, http<300, result["ok"] as? Bool == true else {
            if http==401 && action != "login" && action != "verify_2fa" {stage = .signin}
            if http==404 && action=="subscription_sources" {
                throw ServiceError(message:"خدمة تصنيف الاشتراكات غير مثبّتة على الاستضافة. استبدل ملف public_html/admin/native-api.php بالتحديث الجديد، ثم اضغط إعادة المحاولة.")
            }
            throw ServiceError(message:s(result,"message",http==404 ? "تحتاج تركيب native-api.php على الاستضافة أولاً." : "تعذر الاتصال بالخدمة (\(http))."))
        }
        return result
    }
    // A foreground admin session stays refreshed while the app is in use.
    // The server continues to enforce admin status, authorization and 2FA.
    func setAppActive(_ active:Bool) {
        #if DEBUG
        if previewMode {return}
        #endif
        appIsActive=active
        UIApplication.shared.isIdleTimerDisabled=active
        if active {startKeepAlive()} else {stopKeepAlive()}
    }
    private func stopKeepAlive() {
        keepAliveTask?.cancel()
        keepAliveTask=nil
    }
    private func startKeepAlive() {
        stopKeepAlive()
        guard appIsActive && stage == .ready else{return}
        keepAliveTask=Task { [weak self] in
            // Revalidate on foreground return, then renew every 90 seconds.
            while !Task.isCancelled {
                guard let self, self.appIsActive, self.stage == .ready else {break}
                do {
                    let result=try await self.call("status")
                    self.csrf=s(result,"csrf",self.csrf)
                } catch {
                    // A temporary network error is not a reason to log out.
                    // Explicit server rejection still changes the login stage.
                    if self.stage != .ready {break}
                }
                do {try await Task.sleep(nanoseconds:90_000_000_000)}
                catch {break}
            }
        }
    }
    func bootstrap() async {
        stage = .loading
        do {let r=try await call("status"); applyAuth(r); error=""}
        catch {
            if stage != .otp {stage = .signin}
            self.error=error.localizedDescription
        }
    }
    private func applyAuth(_ r:J) {
        name=s(r,"name","مدير المنصة")
        csrf=s(r,"csrf","")
        stage = .ready
        if appIsActive {startKeepAlive()}
    }
    func login(_ identifier:String,_ password:String) async {
        busy=true; error=""
        defer{busy=false}
        do {
            let r=try await call("login",params:["identifier":identifier,"password":password])
            if r["two_factor"] as? Bool == true {stage = .otp}
            else {applyAuth(r)}
        } catch {self.error=error.localizedDescription}
    }
    func verify(_ code:String) async {
        busy=true;error=""
        defer{busy=false}
        do {applyAuth(try await call("verify_2fa",params:["code":code]))}
        catch {self.error=error.localizedDescription}
    }
    func logout() async {
        stopKeepAlive()
        _=try? await call("logout",params:[:])
        csrf="";name="الإدارة";stage = .signin
        dashboard=[:];campaign=[:];codes=[];ads=[];students=[];subscriptions=[];questions=[]
        curriculum=[:];predictionSubjects=[];predictionRows=[];updatedAt=[:]
        for key in Array(readVersions.keys){readVersions[key,default:0]+=1}
    }
    @Published private(set) var updatedAt:[String:Date]=[:]
    private var readVersions:[String:Int]=[:]
    private var activeReads:Set<String>=[]
    private var writeCount=0
    private func updateBusy(){busy = !activeReads.isEmpty || writeCount>0}
    func refreshIfStale(_ area:String) async {
        guard activeReads.contains(where:{$0.hasPrefix(area+"#")}) == false else{return}
        if let date=updatedAt[area],Date().timeIntervalSince(date)<30{return}
        await refresh(area)
    }
    func refresh(_ area:String,search:String?=nil) async {
        let version=(readVersions[area] ?? 0)+1
        readVersions[area]=version
        let token="\(area)#\(version)"
        activeReads.insert(token);updateBusy();error=""
        defer{activeReads.remove(token);updateBusy()}
        do {
            let result=try await call(area,query:search)
            guard readVersions[area]==version else{return}
            switch area {
            case "dashboard": dashboard=result
            case "promo_status": campaign=result["campaign"] as? J ?? [:]
            case "codes": codes=list(result)
            case "ads": ads=list(result)
            case "students": students=list(result)
            case "subscriptions": subscriptions=list(result)
            case "questions": questions=list(result)
            case "curriculum": curriculum=result
            case "predictions_subjects": predictionSubjects=list(result)
            default: break
            }
            if search == nil || search == "" {updatedAt[area]=Date()}
            else {updatedAt.removeValue(forKey:area)}
        } catch {
            if readVersions[area]==version && !Task.isCancelled {self.error=error.localizedDescription}
        }
    }
    func refreshPredictions(subjectID:Int) async {
        guard subjectID>0 else {predictionRows=[];return}
        busy=true;error=""
        defer{busy=false}
        do {
            let result=try await call("predictions",extras:[URLQueryItem(name:"subject_id",value:String(subjectID))])
            predictionRows=list(result)
            predictionMode=s(result,"mode","automatic")
        } catch {self.error=error.localizedDescription}
    }
    func mutation(_ action:String,_ payload:J,refresh area:String) async -> J? {
        writeCount+=1;updateBusy();error="";notice=""
        defer{writeCount-=1;updateBusy()}
        do {
            let response=try await call(action,params:payload)
            notice=s(response,"message","تم حفظ العملية بنجاح.")
            if !area.isEmpty {updatedAt.removeValue(forKey:area);await refresh(area)}
            updatedAt.removeValue(forKey:"dashboard")
            return response
        }catch {self.error=error.localizedDescription;return nil}
    }
}
@main struct MurshidAdministration:App {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var state=AdminState()
    @AppStorage("admin_palette") private var paletteID="aurora"
    var body:some Scene {
        WindowGroup {
            Group {
                switch state.stage {
                case .loading: Splash().task {await state.bootstrap()}
                case .signin: LoginScreen(state:state)
                case .otp: OTPScreen(state:state)
                case .ready: MainAdmin(state:state)
                }
            }
            .environment(\.layoutDirection,.rightToLeft)
            .preferredColorScheme(.dark)
            .tint(AdminPalette(rawValue:paletteID)?.accent ?? Ink.gold)
            .onAppear {state.setAppActive(scenePhase == .active)}
            .onChange(of:scenePhase) {phase in
                state.setAppActive(phase == .active)
            }
            .onDisappear {state.setAppActive(false)}
        }
    }
}
private struct AdminBackground:ViewModifier {
    func body(content:Content)->some View {
        content.background(Ink.navy.ignoresSafeArea())
            .scrollDismissesKeyboard(.interactively)
            .toolbarBackground(Ink.navy,for:.navigationBar)
            .toolbarBackground(.visible,for:.navigationBar)
            .toolbarColorScheme(.dark,for:.navigationBar)
    }
}
private extension View {func adminBG()->some View {modifier(AdminBackground())}}
private struct AdminPressStyle:ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    func makeBody(configuration:Configuration)->some View {
        configuration.label.opacity(configuration.isPressed ? 0.75 : 1)
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.98 : 1)
            .animation(reduceMotion ? nil : .easeOut(duration:0.14),value:configuration.isPressed)
    }
}
private struct AdminEmpty:View {
    let title:String
    let detail:String
    var body:some View {
        VStack(spacing:12) {
            Image(systemName:"tray").font(.system(size:30)).foregroundStyle(Ink.gold)
            Text(title).font(.headline)
            Text(detail).font(.footnote).foregroundStyle(Ink.faded).multilineTextAlignment(.center)
        }.padding(28).frame(maxWidth:.infinity).background(Ink.blue,in:RoundedRectangle(cornerRadius:18))
    }
}
private struct AdminSection:View {
    let title:String
    var detail:String=""
    var body:some View {
        HStack(alignment:.firstTextBaseline) {
            Text(title).font(.system(.headline,design:.rounded))
            Spacer(minLength:8)
            if !detail.isEmpty {Text(detail).font(.caption).foregroundStyle(Ink.faded)}
        }.padding(.top,4)
    }
}
private struct Splash:View {
    var body:some View {
        VStack(spacing:18) {
            Image(systemName:"shield.lefthalf.filled").font(.system(size:70))
                .foregroundStyle(LinearGradient(colors:[Ink.gold,Ink.secondary],startPoint:.topLeading,endPoint:.bottomTrailing))
            Text("المرشد | الإدارة").font(.largeTitle.bold())
            Text("كل المنصة بين يديك").font(.subheadline).foregroundStyle(Ink.faded)
            ProgressView().tint(Ink.gold)
        }.frame(maxWidth:.infinity,maxHeight:.infinity).adminBG()
    }
}
private struct GlassBox<Content:View>:View {
    let content:Content
    init(@ViewBuilder _ content:()->Content){self.content=content()}
    var body:some View {
        content.padding(16).frame(maxWidth:.infinity,alignment:.leading)
            .background(Ink.blue,in:RoundedRectangle(cornerRadius:18,style:.continuous))
            .overlay(RoundedRectangle(cornerRadius:18,style:.continuous).stroke(Ink.blue2.opacity(0.65),lineWidth:0.7))
    }
}
private struct Primary:View {
    let title:String
    var symbol:String="checkmark"
    var busy:Bool=false
    var action:()->Void
    var body:some View {
        Button(action:action) {
            HStack(spacing:10) {
                if busy {ProgressView().tint(Ink.navy)}
                else {Image(systemName:symbol)}
                Text(title).fontWeight(.bold)
            }
            .frame(maxWidth:.infinity).padding(14)
            .foregroundStyle(Ink.navy)
            .background(Ink.gold,in:RoundedRectangle(cornerRadius:13,style:.continuous))
        }.buttonStyle(AdminPressStyle()).disabled(busy)
    }
}
private struct LoginScreen:View {
    @ObservedObject var state:AdminState
    @State private var identifier=""
    @State private var password=""
    @State private var showPassword=false
    @FocusState private var focus:Field?
    private enum Field {case identifier,password}
    var body:some View {
        ScrollView {
            VStack(alignment:.leading,spacing:26) {
                HStack {
                    Label("المرشد الوزاري",systemImage:"building.columns.fill").font(.headline)
                    Spacer()
                    Text("الإدارة").font(.caption.bold()).padding(.horizontal,12).padding(.vertical,7)
                        .background(Ink.gold.opacity(0.12),in:Capsule()).foregroundStyle(Ink.gold)
                }.padding(.top,20)
                VStack(alignment:.leading,spacing:12) {
                    Image(systemName:"square.stack.3d.up.fill").font(.system(size:40)).foregroundStyle(Ink.gold).padding(.bottom,10)
                    Text("إدارة أوضح.\nإنجاز أسرع.").font(.system(size:38,weight:.bold,design:.rounded)).fixedSize(horizontal:false,vertical:true)
                    Text("أهلاً بك في مساحة إدارة المنصة").font(.subheadline).foregroundStyle(Ink.faded)
                }.padding(.vertical,14)
                GlassBox {
                    VStack(alignment:.leading,spacing:18) {
                        Text("تسجيل الدخول").font(.title3.bold())
                        VStack(alignment:.leading,spacing:8) {
                            Text("البريد الإلكتروني أو الهاتف").font(.caption).foregroundStyle(Ink.faded)
                            TextField("حساب المدير",text:$identifier).textContentType(.username)
                                .keyboardType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
                                .focused($focus,equals:.identifier).submitLabel(.next).onSubmit{focus = .password}
                                .padding(14).background(Ink.navy,in:RoundedRectangle(cornerRadius:12))
                        }
                        VStack(alignment:.leading,spacing:8) {
                            Text("كلمة المرور").font(.caption).foregroundStyle(Ink.faded)
                            HStack {
                                Group {
                                    if showPassword {TextField("كلمة المرور",text:$password)}
                                    else {SecureField("كلمة المرور",text:$password)}
                                }.textContentType(.password).focused($focus,equals:.password).submitLabel(.go)
                                    .onSubmit{signIn()}
                                Button {showPassword.toggle()} label:{Image(systemName:showPassword ? "eye.slash":"eye")}
                                    .foregroundStyle(Ink.faded).frame(width:36,height:32)
                                    .accessibilityLabel(showPassword ? "إخفاء كلمة المرور":"إظهار كلمة المرور")
                            }.padding(10).background(Ink.navy,in:RoundedRectangle(cornerRadius:12))
                        }
                        Note(state:state)
                        Primary(title:"دخول الإدارة",symbol:"arrow.left",busy:state.busy){signIn()}
                            .disabled(safe(identifier).isEmpty || password.isEmpty)
                    }
                }
                Label("اتصال مشفّر • حسابات المديرين فقط",systemImage:"lock.shield")
                    .font(.caption).foregroundStyle(Ink.faded).frame(maxWidth:.infinity)
            }.padding(22).frame(maxWidth:560).frame(maxWidth:.infinity)
        }.adminBG()
    }
    private func signIn() {
        guard !state.busy,!safe(identifier).isEmpty,!password.isEmpty else{return}
        focus=nil
        Task{await state.login(safe(identifier),password)}
    }
}
private struct OTPScreen:View {
    @ObservedObject var state:AdminState
    @State private var code=""
    var body:some View {
        VStack(spacing:22) {
            Image(systemName:"checkmark.shield.fill").font(.system(size:64)).foregroundStyle(Ink.gold)
            Text("التحقق الثنائي").font(.title.bold())
            Text("أدخل رمز المصادقة المكون من 6 أرقام").foregroundStyle(Ink.faded)
            TextField("000000",text:$code).keyboardType(.numberPad)
                .multilineTextAlignment(.center).font(.title.monospacedDigit())
                .padding(15).background(Ink.blue,in:RoundedRectangle(cornerRadius:15))
            Primary(title:"تأكيد هويتي",symbol:"lock.open",busy:state.busy) {Task{await state.verify(code)}}
            if !state.error.isEmpty {Text(state.error).foregroundStyle(.orange)}
        }.padding(28).frame(maxHeight:.infinity).adminBG()
    }
}
private enum AdminTab:Int,CaseIterable {
    case home,codes,ads,students,more
    var title:String {switch self {case .home:return "الرئيسية";case .codes:return "الأكواد";case .ads:return "الإعلانات";case .students:return "الطلاب";case .more:return "المزيد"}}
    var icon:String {switch self {case .home:return "square.grid.2x2.fill";case .codes:return "ticket.fill";case .ads:return "megaphone.fill";case .students:return "person.2.fill";case .more:return "line.3.horizontal.decrease.circle.fill"}}
}
private struct MainAdmin:View {
    @ObservedObject var state:AdminState
    @State private var tab:AdminTab = .home
    var body:some View {
        TabView(selection:$tab) {
            HomeScreen(state:state,tab:$tab).tabItem{Label(AdminTab.home.title,systemImage:AdminTab.home.icon)}.tag(AdminTab.home)
            CodesScreen(state:state).tabItem{Label(AdminTab.codes.title,systemImage:AdminTab.codes.icon)}.tag(AdminTab.codes)
            AdsScreen(state:state).tabItem{Label(AdminTab.ads.title,systemImage:AdminTab.ads.icon)}.tag(AdminTab.ads)
            StudentsScreen(state:state).tabItem{Label(AdminTab.students.title,systemImage:AdminTab.students.icon)}.tag(AdminTab.students)
            MoreScreen(state:state).tabItem{Label(AdminTab.more.title,systemImage:AdminTab.more.icon)}.tag(AdminTab.more)
        }
        .tint(Ink.gold)
        .toolbarBackground(Ink.blue,for:.tabBar).toolbarBackground(.visible,for:.tabBar)
        .toolbarColorScheme(.dark,for:.tabBar).adminBG()
        .onChange(of:tab){_ in UISelectionFeedbackGenerator().selectionChanged()}
        .onAppear {
            #if DEBUG
            if let value=ProcessInfo.processInfo.environment["ADMIN_PREVIEW_TAB"],let number=Int(value),let selected=AdminTab(rawValue:number){tab=selected}
            #endif
        }
    }
}
private struct ScreenTitle:View {
    let text:String;let detail:String
    var body:some View {
        VStack(alignment:.leading,spacing:10) {
            HStack(spacing:8) {
                RoundedRectangle(cornerRadius:2).fill(Ink.gold).frame(width:4,height:26)
                Text(text).font(.system(size:27,weight:.bold,design:.rounded))
            }
            Text(detail).font(.caption).foregroundStyle(Ink.faded).fixedSize(horizontal:false,vertical:true)
        }.frame(maxWidth:.infinity,alignment:.leading).padding(.vertical,10)
    }
}
private struct Note:View {
    @ObservedObject var state:AdminState
    var body:some View {
        Group {
            if state.busy {HStack(spacing:9){ProgressView().tint(Ink.gold);Text("جارٍ تحميل البيانات…").font(.caption).foregroundStyle(Ink.faded)}.padding(.vertical,6)}
            if !state.error.isEmpty {Label(state.error,systemImage:"exclamationmark.triangle").foregroundStyle(.orange).font(.footnote).padding(10).frame(maxWidth:.infinity).background(Ink.blue,in:RoundedRectangle(cornerRadius:12))}
            if !state.notice.isEmpty {Label(state.notice,systemImage:"checkmark.circle.fill").foregroundStyle(.green).font(.footnote).padding(10).frame(maxWidth:.infinity).background(Ink.blue,in:RoundedRectangle(cornerRadius:12))}
        }
    }
}
private struct MetricTile:View {
    let name:String;let count:String;let icon:String
    var body:some View {
        GlassBox {
            VStack(alignment:.leading,spacing:10) {
                Image(systemName:icon).font(.system(size:17,weight:.semibold)).foregroundStyle(Ink.gold)
                Text(count).font(.system(size:29,weight:.bold,design:.rounded)).monospacedDigit().lineLimit(1).minimumScaleFactor(0.65)
                Text(name).font(.caption).foregroundStyle(Ink.faded).fixedSize(horizontal:false,vertical:true)
            }.frame(maxWidth:.infinity,alignment:.leading)
        }
    }
}
private struct HomeScreen:View {
    @ObservedObject var state:AdminState
    @Binding var tab:AdminTab
    private let grids=[GridItem(.flexible()),GridItem(.flexible())]
    var body:some View {
        NavigationStack {
            ScrollView {
                VStack(alignment:.leading,spacing:18) {
                    HStack(alignment:.top) {
                        VStack(alignment:.leading,spacing:7) {
                            Text("أهلاً، \(state.name)").font(.subheadline).foregroundStyle(Ink.faded)
                            Text("نبض المنصة").font(.system(size:32,weight:.bold,design:.rounded))
                        }
                        Spacer(minLength:8)
                        Image(systemName:"building.columns.fill").font(.title2).foregroundStyle(Ink.gold)
                            .frame(width:50,height:50).background(Ink.gold.opacity(0.10),in:RoundedRectangle(cornerRadius:16))
                    }.padding(.top,12)
                    HStack(spacing:8) {
                        Image(systemName:state.busy ? "arrow.triangle.2.circlepath":"clock").foregroundStyle(Ink.gold)
                        Text(state.busy ? "جارٍ تحديث البيانات…" : updateLabel).font(.caption).foregroundStyle(Ink.faded)
                        Spacer()
                        Button{Task{await state.refresh("dashboard")}}label:{Image(systemName:"arrow.clockwise").frame(width:36,height:36)}
                            .disabled(state.busy).accessibilityLabel("تحديث الإحصائيات")
                    }
                    Note(state:state)
                    AdminSection(title:"نظرة عامة",detail:"بيانات المنصة")
                    LazyVGrid(columns:grids,spacing:10) {
                        MetricTile(name:"الطلاب",count:fmt("students"),icon:"person.2.fill")
                        MetricTile(name:"الاشتراكات الفعالة",count:fmt("subscriptions"),icon:"crown.fill")
                        MetricTile(name:"الأسئلة المنشورة",count:fmt("questions"),icon:"text.book.closed.fill")
                        MetricTile(name:"أكواد متاحة",count:fmt("codes"),icon:"ticket.fill")
                        MetricTile(name:"إعلانات نشطة",count:fmt("ads"),icon:"megaphone.fill")
                        MetricTile(name:"المعلمون",count:fmt("teachers"),icon:"person.crop.rectangle.stack")
                    }
                    AdminSection(title:"ابدأ إجراءً",detail:"وصول سريع")
                    LazyVGrid(columns:grids,spacing:10) {
                        quick("أكواد التفعيل","ticket",detail:"إنشاء وإدارة"){tab = .codes}
                        quick("إعلانات المدارس","megaphone",detail:"نشر ومتابعة"){tab = .ads}
                        quick("حسابات الطلاب","person.2",detail:"بحث وإدارة"){tab = .students}
                        quick("أدوات المنصة","slider.horizontal.3",detail:"الاشتراكات والمحتوى"){tab = .more}
                    }
                    NavigationLink{MillionScheduleAdminScreen(state:state).toolbar(.visible,for:.navigationBar)}label:{
                        HStack(spacing:12) {
                            Image(systemName:"trophy.fill").foregroundStyle(Ink.gold).font(.title3)
                            VStack(alignment:.leading,spacing:4){Text("تحدي المليون").font(.subheadline.bold());Text("إدارة مواعيد المنافسة").font(.caption).foregroundStyle(Ink.faded)}
                            Spacer();Image(systemName:"chevron.left").font(.caption).foregroundStyle(Ink.faded)
                        }.padding(16).background(Ink.blue,in:RoundedRectangle(cornerRadius:18))
                    }.buttonStyle(AdminPressStyle())
                }.padding(18).frame(maxWidth:700).frame(maxWidth:.infinity)
            }.adminBG().refreshable{await state.refresh("dashboard")}
                .task{await state.refreshIfStale("dashboard")}
                .toolbar(.hidden,for:.navigationBar)
        }
    }
    private var updateLabel:String {
        guard let date=state.updatedAt["dashboard"] else{return "اسحب للأسفل لتحديث الإحصائيات"}
        return "آخر تحديث \(date.formatted(date:.omitted,time:.shortened))"
    }
    private func fmt(_ key:String)->String {
        let stats=state.dashboard["stats"] as? J ?? [:]
        guard stats[key] != nil,!(stats[key] is NSNull) else{return "—"}
        let formatter=NumberFormatter();formatter.locale=Locale(identifier:"ar_IQ");formatter.numberStyle = .decimal
        return formatter.string(from:NSNumber(value:num(stats,key))) ?? s(stats,key)
    }
    private func quick(_ title:String,_ icon:String,detail:String,action:@escaping ()->Void)->some View {
        Button(action:action) {
            VStack(alignment:.leading,spacing:10) {
                Image(systemName:icon).font(.title3).foregroundStyle(Ink.gold)
                Text(title).font(.subheadline.bold())
                Text(detail).font(.caption).foregroundStyle(Ink.faded)
            }.frame(maxWidth:.infinity,alignment:.leading).padding(16)
                .background(Ink.gold.opacity(0.055),in:RoundedRectangle(cornerRadius:18))
                .overlay(RoundedRectangle(cornerRadius:18).stroke(Ink.blue2,lineWidth:0.7))
        }.buttonStyle(AdminPressStyle())
    }
}
private struct CodesScreen:View {
    @ObservedObject var state:AdminState
    @State private var codeFilter="all"
    private var visibleCodes:[J] {state.codes.filter{codeFilter=="all" || s($0,"status")==codeFilter}}
    @State private var showCreate=false
    @State private var editing:J?
    @State private var toRevoke:J?
    @State private var toDelete:J?
    @State private var issued:[String]=[]
    @State private var showIssued=false

    var body:some View {
        NavigationStack {
            content
                .adminBG().toolbar(.hidden,for:.navigationBar)
                .refreshable{await state.refresh("codes")}
                .task{await state.refreshIfStale("codes")}
                .sheet(isPresented:$showCreate,onDismiss:{if !issued.isEmpty {showIssued=true}}) {
                    CodeCreate(state:state,onIssued:{newCodes in issued=newCodes})
                }
                .sheet(item:$editing){row in CodeEdit(state:state,row:row)}
                .sheet(isPresented:$showIssued){IssuedCodes(codes:issued)}
                .confirmationDialog("إلغاء هذا الكود غير المستخدم؟",
                                    isPresented:Binding(get:{toRevoke != nil},set:{if !$0{toRevoke=nil}})) {
                    Button("إلغاء الكود",role:.destructive){if let row=toRevoke{Task{_ = await state.mutation("revoke_code",["id":num(row,"id")],refresh:"codes")}};toRevoke=nil}
                }
                .confirmationDialog("حذف السجل الملغى نهائياً؟",
                                    isPresented:Binding(get:{toDelete != nil},set:{if !$0{toDelete=nil}})) {
                    Button("حذف نهائياً",role:.destructive){if let row=toDelete{Task{_ = await state.mutation("delete_code",["id":num(row,"id")],refresh:"codes")}};toDelete=nil}
                }
        }
    }
    private var content:some View {
        ScrollView {
            LazyVStack(alignment:.leading,spacing:16) {
                ScreenTitle(text:"أكواد الاشتراك",detail:"إدارة التفعيل الحقيقي • شهر أو 3 أشهر")
                HStack {
                    Text("\(state.codes.count) سجل").foregroundStyle(Ink.faded)
                    Spacer()
                    Button {showCreate=true} label:{
                        Label("إنشاء أكواد",systemImage:"plus.circle.fill").fontWeight(.bold)
                    }.buttonStyle(.borderedProminent).tint(Ink.gold).foregroundStyle(Ink.navy)
                }
                Note(state:state)
                if state.codes.isEmpty {GlassBox{Text("لا توجد أكواد، أو لم تتصل الخدمة بعد.").foregroundStyle(Ink.faded)}}
                ScrollView(.horizontal,showsIndicators:false) {
                    HStack(spacing:8) {
                        filterChip("الكل","all")
                        filterChip("متاحة","available")
                        filterChip("مستخدمة","redeemed")
                        filterChip("ملغاة","revoked")
                    }.padding(.vertical,2)
                }
                if visibleCodes.isEmpty && !state.codes.isEmpty {AdminEmpty(title:"لا توجد أكواد بهذه الحالة",detail:"اختر تصنيفاً آخر من الأعلى.")}
                ForEach(visibleCodes){code in codeCard(code)}
            }.padding(16)
        }
    }
    private func filterChip(_ title:String,_ value:String)->some View {
        Button{codeFilter=value}label:{Text(title).font(.caption.bold()).padding(.horizontal,16).frame(minHeight:40)
            .foregroundStyle(codeFilter==value ? Ink.navy:Ink.faded)
            .background(codeFilter==value ? Ink.gold:Ink.blue,in:Capsule())}
            .buttonStyle(AdminPressStyle()).accessibilityAddTraits(codeFilter==value ? .isSelected:[])
    }
    private func codeCard(_ code:J)->some View {
        GlassBox {
            VStack(alignment:.leading,spacing:12) {
                HStack {
                    Label("كود #\(s(code,"id"))",systemImage:"ticket.fill")
                        .foregroundStyle(Ink.gold).fontWeight(.bold)
                    Spacer()
                    Text(status(s(code,"status")))
                        .font(.caption)
                        .foregroundStyle(s(code,"status")=="available" ? Color.green : Ink.faded)
                }
                Text(s(code,"months")=="3" ? "اشتراك 3 أشهر" : "اشتراك شهر واحد").font(.headline)
                Text("المجموعة: \(s(code,"batch_label"))").font(.caption).foregroundStyle(Ink.faded)
                if s(code,"status")=="available" {
                    HStack {
                        Button("تعديل"){editing=code}.buttonStyle(.bordered)
                        Button("إلغاء"){toRevoke=code}.buttonStyle(.bordered).tint(.orange)
                    }
                } else if s(code,"status")=="revoked" {
                    Button("حذف السجل"){toDelete=code}.buttonStyle(.bordered).tint(.red)
                }
            }
        }
    }
    private func status(_ value:String)->String {
        switch value {case "available":return "متاح";case "redeemed":return "مستخدم";default:return "ملغى"}
    }
}
extension Dictionary: @retroactive Identifiable where Key==String, Value==Any {
    public var id:String {
        if let value=self["id"] {return String(describing:value)}
        if JSONSerialization.isValidJSONObject(self),let data=try? JSONSerialization.data(withJSONObject:self,options:[.sortedKeys]),let text=String(data:data,encoding:.utf8){return text}
        return self.keys.sorted().map{"\($0)=\(String(describing:self[$0]!))"}.joined(separator:"|")
    }
}
private struct CodeCreate:View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var state:AdminState
    let onIssued:([String])->Void
    @State private var months=1
    @State private var count=1
    @State private var batch="ios_admin"
    @State private var expiry=""
    var body:some View {
        NavigationStack {
            Form {
                Section("مدة الاشتراك") {
                    Picker("المدة",selection:$months){Text("شهر واحد").tag(1);Text("3 أشهر").tag(3)}.pickerStyle(.segmented)
                    Stepper("عدد الأكواد: \(count)",value:$count,in:1...50)
                }
                Section("تفاصيل الإصدار") {
                    TextField("اسم المجموعة",text:$batch).textInputAutocapitalization(.never)
                    TextField("آخر تاريخ للتفعيل (اختياري YYYY-MM-DD)",text:$expiry).keyboardType(.numbersAndPunctuation)
                }
                Section {
                    Primary(title:"إصدار \(count) كود",symbol:"ticket.fill",busy:state.busy){
                        Task{
                            if let r=await state.mutation("issue_codes",["months":months,"count":count,"batch":batch,"valid_until":expiry],refresh:"codes"){
                                let codes=r["issued"] as? [String] ?? []
                                dismiss()
                                onIssued(codes)
                            }
                        }
                    }
                    Note(state:state)
                }
            }.scrollContentBackground(.hidden).background(Ink.navy)
            .navigationTitle("إنشاء أكواد").toolbar{ToolbarItem(placement:.topBarLeading){Button("إغلاق"){dismiss()}}}
        }
    }
}
private struct CodeEdit:View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var state:AdminState
    let row:J
    @State private var batch=""
    @State private var expiry=""
    var body:some View {
        NavigationStack {
            Form{
                TextField("المجموعة",text:$batch)
                TextField("تاريخ الصلاحية YYYY-MM-DD",text:$expiry)
                Text("مدة الكود ثابتة بعد الإصدار؛ غيّر المجموعة أو الصلاحية فقط.").font(.footnote).foregroundStyle(Ink.faded)
                Primary(title:"حفظ التعديلات",busy:state.busy){
                    Task{if await state.mutation("edit_code",["id":num(row,"id"),"batch":batch,"valid_until":expiry],refresh:"codes") != nil {dismiss()}}
                }
                Note(state:state)
            }.scrollContentBackground(.hidden).background(Ink.navy)
            .navigationTitle("تعديل الكود")
            .onAppear{batch=s(row,"batch_label","");expiry=String(s(row,"valid_until","").prefix(10))}
            .toolbar{ToolbarItem(placement:.topBarLeading){Button("إغلاق"){dismiss()}}}
        }
    }
}
private struct IssuedCodes:View {
    @Environment(\.dismiss) private var dismiss
    let codes:[String]
    var body:some View {
        NavigationStack {
            ScrollView{
                VStack(alignment:.leading,spacing:20){
                    Label("الأكواد الجديدة",systemImage:"checkmark.shield.fill").font(.title.bold()).foregroundStyle(Ink.gold)
                    Text("انسخ الأكواد واحفظها الآن. لن تُعرض مرة ثانية داخل التطبيق.").foregroundStyle(Ink.faded)
                    Text(codes.joined(separator:"\n")).font(.system(.body,design:.monospaced))
                        .textSelection(.enabled).padding(20).frame(maxWidth:.infinity,alignment:.leading)
                        .background(Ink.blue,in:RoundedRectangle(cornerRadius:16))
                    ShareLink(item:codes.joined(separator:"\n")) {Label("مشاركة أو نسخ الأكواد",systemImage:"square.and.arrow.up").frame(maxWidth:.infinity).padding(14).background(Ink.gold,in:RoundedRectangle(cornerRadius:14)).foregroundStyle(Ink.navy)}
                }.padding(18)
            }.adminBG().navigationTitle("نتيجة الإصدار")
                .toolbar{ToolbarItem(placement:.topBarLeading){Button("تم"){dismiss()}}}
        }
    }
}
private struct AdsScreen:View {
    @ObservedObject var state:AdminState
    @State private var editing:J?
    @State private var showNew=false
    @State private var deleting:J?
    var body:some View {
        NavigationStack{
            ScrollView{
                LazyVStack(alignment:.leading,spacing:16){
                    ScreenTitle(text:"إعلانات المدارس",detail:"إعلان 24 ساعة • 5,000 دينار عراقي")
                    HStack{
                        Text("\(state.ads.count) إعلان").foregroundStyle(Ink.faded)
                        Spacer()
                        Button{showNew=true}label:{Label("إعلان جديد",systemImage:"plus.circle.fill")}.buttonStyle(.borderedProminent).tint(Ink.gold).foregroundStyle(Ink.navy)
                    }
                    Note(state:state)
                    if state.ads.isEmpty && !state.busy {AdminEmpty(title:"لا توجد إعلانات",detail:"اضغط إعلان جديد لإضافة إعلان مدرسة.")}
                    ForEach(state.ads){ad in
                        GlassBox{
                            VStack(alignment:.leading,spacing:10){
                                HStack{Image(systemName:"megaphone.fill").foregroundStyle(Ink.gold);Text(s(ad,"school_name")).font(.headline);Spacer();Text(num(ad,"is_enabled")==1 ? "ظاهر":"متوقف").font(.caption).foregroundStyle(num(ad,"is_enabled")==1 ? .green:.orange)}
                                Text(s(ad,"ad_text")).font(.subheadline)
                                Text("\(s(ad,"start_date")) — \(s(ad,"end_date"))").foregroundStyle(Ink.faded).font(.caption)
                                Text("القيمة: \(s(ad,"total_price")) د.ع").foregroundStyle(Ink.gold)
                                HStack{
                                    Button("تعديل"){editing=ad}.buttonStyle(.bordered)
                                    Button(num(ad,"is_enabled")==1 ? "إيقاف":"تشغيل"){
                                        Task{_ = await state.mutation("toggle_ad",["id":num(ad,"id")],refresh:"ads")}
                                    }.buttonStyle(.bordered)
                                    Button("حذف"){deleting=ad}.buttonStyle(.bordered).tint(.red)
                                }
                            }
                        }
                    }
                }.padding(16)
            }.adminBG().toolbar(.hidden,for:.navigationBar).refreshable{await state.refresh("ads")}.task{await state.refreshIfStale("ads")}
            .sheet(isPresented:$showNew){AdEditor(state:state,row:nil)}
            .sheet(item:$editing){row in AdEditor(state:state,row:row)}
            .confirmationDialog("حذف الإعلان نهائياً؟",isPresented:Binding(get:{deleting != nil},set:{if !$0{deleting=nil}}),titleVisibility:.visible){
                Button("حذف",role:.destructive){if let row=deleting{Task{_ = await state.mutation("delete_ad",["id":num(row,"id")],refresh:"ads")}};deleting=nil}
            }
        }
    }
}
private struct AdEditor:View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var state:AdminState
    let row:J?
    @State private var school=""
    @State private var text=""
    @State private var start=Date()
    @State private var end=Date()
    @State private var imagePath=""
    private let formatter:DateFormatter={
        let f=DateFormatter();f.dateFormat="yyyy-MM-dd";f.locale=Locale(identifier:"en_US_POSIX");f.timeZone=TimeZone(identifier:"Asia/Baghdad");return f
    }()
    var body:some View {
        NavigationStack {
            Form{
                Section("المدرسة"){
                    TextField("اسم المدرسة",text:$school)
                    TextField("نص الإعلان",text:$text,axis:.vertical).lineLimit(3...6)
                }
                Section("مدة العرض"){
                    DatePicker("بداية الإعلان",selection:$start,displayedComponents:.date)
                    DatePicker("نهاية الإعلان",selection:$end,in:start...,displayedComponents:.date)
                    Text("السعر: 5,000 دينار لليوم الواحد").foregroundStyle(Ink.gold)
                }
                Section("اختياري"){TextField("مسار صورة الإعلان داخل الموقع",text:$imagePath)}
                Section{
                    Primary(title:row == nil ? "نشر إعلان المدرسة":"حفظ الإعلان",symbol:"megaphone.fill",busy:state.busy) {
                        Task {
                            let payload:J=["id":num(row ?? [:],"id"),"school_name":school,"ad_text":text,"start_date":formatter.string(from:start),"end_date":formatter.string(from:end),"image_path":imagePath]
                            if await state.mutation(row == nil ? "create_ad":"edit_ad",payload,refresh:"ads") != nil{dismiss()}
                        }
                    }
                    Note(state:state)
                }
            }.scrollContentBackground(.hidden).background(Ink.navy)
            .navigationTitle(row == nil ? "إعلان جديد":"تعديل إعلان")
            .onAppear{
                guard let row else{return}
                school=s(row,"school_name","");text=s(row,"ad_text","")
                imagePath=s(row,"image_path","")
                start=formatter.date(from:s(row,"start_date")) ?? Date()
                end=formatter.date(from:s(row,"end_date")) ?? Date()
            }
            .toolbar{ToolbarItem(placement:.topBarLeading){Button("إغلاق"){dismiss()}}}
        }
    }
}
private struct StudentsScreen:View {
    @ObservedObject var state:AdminState
    @State private var search=""
    @State private var confirm:J?
    var body:some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment:.leading,spacing:15){
                    ScreenTitle(text:"الطلاب",detail:"بحث مباشر في بيانات حسابات الموقع")
                    HStack {
                        Image(systemName:"magnifyingglass").foregroundStyle(Ink.gold)
                        TextField("الاسم، الهاتف أو البريد",text:$search).textInputAutocapitalization(.never).autocorrectionDisabled().submitLabel(.search).onSubmit{Task{await state.refresh("students",search:search)}}
                        Button("بحث"){Task{await state.refresh("students",search:search)}}.disabled(state.busy)
                    }.padding(13).background(Ink.blue,in:RoundedRectangle(cornerRadius:15))
                    Note(state:state)
                    if state.students.isEmpty && !state.busy {AdminEmpty(title:"لا توجد نتائج",detail:"ابحث بالاسم أو رقم الهاتف أو البريد الإلكتروني.")}
                    ForEach(state.students){row in
                        GlassBox{
                            HStack(spacing:13){
                                Image(systemName:"person.crop.circle.fill").font(.system(size:38)).foregroundStyle(Ink.gold)
                                VStack(alignment:.leading,spacing:5){
                                    Text(s(row,"full_name")).font(.headline)
                                    Text(s(row,"grade_name")).font(.footnote).foregroundStyle(Ink.faded)
                                    Text(s(row,"phone",s(row,"email"))).font(.caption).foregroundStyle(Ink.faded)
                                }
                                Spacer()
                                Button(s(row,"status")=="active" ? "حظر":"تفعيل"){confirm=row}
                                    .font(.caption.bold()).buttonStyle(.bordered).tint(s(row,"status")=="active" ? .orange : .green)
                            }
                        }
                    }
                }.padding(16)
            }.adminBG().toolbar(.hidden,for:.navigationBar).refreshable{await state.refresh("students",search:search)}
            .task{await state.refreshIfStale("students")}
            .confirmationDialog("تعديل حالة حساب الطالب؟",isPresented:Binding(get:{confirm != nil},set:{if !$0{confirm=nil}}),titleVisibility:.visible){
                Button("تأكيد تغيير الحالة"){if let row=confirm{Task{_ = await state.mutation("toggle_student",["id":num(row,"id")],refresh:"students")}};confirm=nil}
            }
        }
    }
}
private struct MoreScreen:View {
    @ObservedObject var state:AdminState
    @AppStorage("admin_palette") private var paletteID="aurora"
    @State private var search=""
    @State private var logoutConfirm=false
    private let tools:[AdminTool]=[
        .init(id:0,title:"اشتراكات الطلاب",detail:"متابعة الاشتراكات والطلبات",icon:"creditcard.fill",group:"الاشتراكات"),
        .init(id:1,title:"مصادر الاشتراكات",detail:"كود • عرض • دفع",icon:"chart.pie.fill",group:"الاشتراكات"),
        .init(id:2,title:"المقاعد المجانية",detail:"إعداد حملة الاشتراك المجاني",icon:"gift.fill",group:"الاشتراكات"),
        .init(id:3,title:"تحدي المليون",detail:"تحديد بداية المنافسة ونهايتها",icon:"trophy.fill",group:"المحتوى"),
        .init(id:4,title:"بنك الأسئلة",detail:"إضافة الأسئلة وتحريرها",icon:"text.book.closed.fill",group:"المحتوى"),
        .init(id:5,title:"إدارة المرشحات",detail:"المواد والأسئلة المختارة",icon:"scope",group:"المحتوى"),
        .init(id:6,title:"المواد والفصول",detail:"تنظيم المنهج الدراسي",icon:"books.vertical.fill",group:"المحتوى")
    ]
    private var filtered:[AdminTool] {tools.filter{safe(search).isEmpty || ($0.title+" "+$0.detail).localizedStandardContains(safe(search))}}
    var body:some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment:.leading,spacing:16) {
                    ScreenTitle(text:"مساحة الإدارة",detail:"كل أدواتك في مكان واحد")
                    HStack(spacing:10) {
                        Image(systemName:"magnifyingglass").foregroundStyle(Ink.gold)
                        TextField("ابحث عن أداة…",text:$search).autocorrectionDisabled()
                        if !search.isEmpty {Button{search=""}label:{Image(systemName:"xmark.circle.fill")}.accessibilityLabel("مسح البحث")}
                    }.padding(14).background(Ink.blue,in:RoundedRectangle(cornerRadius:13))
                    Note(state:state)
                    ForEach(["الاشتراكات","المحتوى"],id:\.self){group in
                        let rows=filtered.filter{$0.group==group}
                        if !rows.isEmpty {
                            AdminSection(title:group)
                            GlassBox {
                                VStack(spacing:0) {
                                    ForEach(rows){tool in
                                        NavigationLink{destination(tool.id)}label:{entry(tool)}.buttonStyle(AdminPressStyle())
                                        if tool.id != rows.last?.id {Divider().overlay(Ink.blue2)}
                                    }
                                }
                            }
                        }
                    }
                    if filtered.isEmpty {AdminEmpty(title:"لا توجد أداة بهذا الاسم",detail:"جرّب البحث عن الاشتراكات أو الأسئلة أو المرشحات.")}
                    if safe(search).isEmpty {
                        AdminSection(title:"مظهر الإدارة",detail:"يُحفظ على جهازك")
                        HStack(spacing:10) {
                            ForEach(AdminPalette.allCases){palette in
                                Button {
                                    paletteID=palette.rawValue
                                    UISelectionFeedbackGenerator().selectionChanged()
                                }label:{
                                    VStack(spacing:10) {
                                        HStack(spacing:4){Circle().fill(palette.accent).frame(width:18,height:18);Circle().fill(palette.second).frame(width:18,height:18)}
                                        Text(palette.name).font(.caption.bold())
                                        Image(systemName:paletteID==palette.rawValue ? "checkmark.circle.fill":"circle").foregroundStyle(paletteID==palette.rawValue ? palette.accent:Ink.faded)
                                    }.frame(maxWidth:.infinity).padding(.vertical,16)
                                        .background(palette.surface,in:RoundedRectangle(cornerRadius:16))
                                        .overlay(RoundedRectangle(cornerRadius:16).stroke(paletteID==palette.rawValue ? palette.accent:Ink.blue2,lineWidth:1))
                                }.buttonStyle(AdminPressStyle()).accessibilityLabel("مظهر \(palette.name)")
                                    .accessibilityAddTraits(paletteID==palette.rawValue ? .isSelected:[])
                            }
                        }
                        Button(role:.destructive){logoutConfirm=true}label:{Label("تسجيل الخروج",systemImage:"rectangle.portrait.and.arrow.right").frame(maxWidth:.infinity,minHeight:48)}
                            .buttonStyle(.bordered).padding(.top,8)
                        Text("المرشد الوزاري • الإدارة 5.1").font(.caption2).foregroundStyle(Ink.faded).frame(maxWidth:.infinity)
                    }
                }.padding(18).frame(maxWidth:700).frame(maxWidth:.infinity)
            }.adminBG().toolbar(.hidden,for:.navigationBar)
                .confirmationDialog("تسجيل الخروج من حساب الإدارة؟",isPresented:$logoutConfirm,titleVisibility:.visible){
                    Button("تسجيل الخروج",role:.destructive){Task{await state.logout()}}
                }
        }
    }
    private func entry(_ tool:AdminTool)->some View {
        HStack(spacing:12) {
            Image(systemName:tool.icon).foregroundStyle(Ink.gold).frame(width:40,height:40).background(Ink.gold.opacity(0.08),in:RoundedRectangle(cornerRadius:12))
            VStack(alignment:.leading,spacing:5){Text(tool.title).font(.subheadline.bold());Text(tool.detail).font(.caption).foregroundStyle(Ink.faded)}
            Spacer(minLength:4);Image(systemName:"chevron.left").font(.caption2.bold()).foregroundStyle(Ink.faded)
        }.padding(.vertical,12).contentShape(Rectangle())
    }
    @ViewBuilder private func destination(_ id:Int)->some View {
        Group {
        switch id {
        case 0: SubscriptionsScreen(state:state)
        case 1: SubscriptionSourcesHome(state:state)
        case 2: FreeMonthCampaignScreen(state:state)
        case 3: MillionScheduleAdminScreen(state:state)
        case 4: QuestionsScreen(state:state)
        case 5: PredictionsAdminScreen(state:state)
        default: CurriculumScreen(state:state)
        }
        }.toolbar(.visible,for:.navigationBar)
    }
}
private struct AdminTool:Identifiable {
    let id:Int;let title:String;let detail:String;let icon:String;let group:String
}


// MARK: - Million contest schedule: single source of truth on production server.
private struct MillionScheduleAdminScreen: View {
    @ObservedObject var state: AdminState
    @State private var started = Date()
    @State private var ends = Date().addingTimeInterval(30 * 86400)
    @State private var loading = true
    @State private var saving = false
    @State private var errorMessage = ""
    @State private var successMessage = ""
    @State private var phase = ""
    private let baghdad = TimeZone(identifier: "Asia/Baghdad")!

    private func formatter() -> DateFormatter {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = baghdad
        f.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return f
    }
    private var phaseLabel: String {
        switch phase {
        case "scheduled": return "لم يبدأ التحدي بعد"
        case "active": return "التحدي جارٍ الآن"
        case "ended": return "انتهى التحدي"
        default: return "حالة التحدي غير متاحة"
        }
    }
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                ScreenTitle(text: "تحدي المليون", detail: "توقيت بداية المسابقة ونهايتها · بغداد")
                GlassBox {
                    VStack(alignment: .leading, spacing: 14) {
                        Label(phaseLabel, systemImage: "clock.badge.checkmark")
                            .font(.headline).foregroundStyle(Ink.gold)
                        Text("التحدي ظاهر للطلاب في كل الأوقات، لكن النقاط والترتيب يُحسبان خلال المدة التي تحددها هنا.")
                            .font(.footnote).foregroundStyle(Ink.faded)
                        DatePicker("بداية التحدي", selection: $started,
                                   displayedComponents: [.date, .hourAndMinute])
                        DatePicker("نهاية التحدي", selection: $ends,
                                   displayedComponents: [.date, .hourAndMinute])
                        Text("جميع المواعيد بتوقيت بغداد. لا يُحذف سجل النقاط السابق عند تعديل الفترة.")
                            .font(.caption).foregroundStyle(Ink.faded)
                        if !errorMessage.isEmpty {
                            Label(errorMessage, systemImage: "exclamationmark.triangle.fill")
                                .font(.footnote).foregroundStyle(.orange)
                        }
                        if !successMessage.isEmpty {
                            Label(successMessage, systemImage: "checkmark.circle.fill")
                                .font(.footnote).foregroundStyle(.green)
                        }
                        Primary(title: saving ? "جاري الحفظ…" : "حفظ الموعد على الموقع",
                                symbol: "checkmark.shield.fill") {
                            Task { await save() }
                        }.disabled(saving || loading || ends <= started)
                    }
                }
                if loading { ProgressView("جاري جلب المواعيد…") }
                Button {
                    Task { await load() }
                } label: { Label("تحديث المواعيد من الخادم", systemImage: "arrow.clockwise") }
                    .buttonStyle(.bordered)
            }.padding(16)
        }
        .environment(\.timeZone, baghdad)
        .adminBG()
        .navigationTitle("تحدي المليون")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
    }
    @MainActor private func load() async {
        loading = true; errorMessage = ""
        defer { loading = false }
        do {
            let response = try await state.call("contest_schedule")
            let schedule = response["schedule"] as? J ?? [:]
            let f = formatter()
            guard let start = f.date(from: s(schedule, "start_at", "")),
                  let end = f.date(from: s(schedule, "end_at", "")) else {
                errorMessage = "تعذر قراءة التواريخ. ثبّت تحديث الخدمة على الموقع."
                return
            }
            started = start
            ends = end
            phase = s(schedule, "phase", "")
        } catch { errorMessage = error.localizedDescription }
    }
    @MainActor private func save() async {
        guard ends > started else { errorMessage = "يجب أن تسبق البداية النهاية."; return }
        saving = true; errorMessage = ""; successMessage = ""
        defer { saving = false }
        let f = formatter()
        do {
            _ = try await state.call("contest_schedule_save", params: [
                "start_at": f.string(from: started),
                "end_at": f.string(from: ends)
            ])
            await load()
            successMessage = "تم اعتماد موعد التحدي على الخادم."
        } catch { errorMessage = error.localizedDescription }
    }
}

private struct SubscriptionsScreen:View{
    @ObservedObject var state:AdminState
    @State private var chosen:J?
    @State private var showAction=false
    var body:some View{
        ScrollView{
            VStack(spacing:14){
                ScreenTitle(text:"الاشتراكات",detail:"شهر أو 3 أشهر • من قاعدة بيانات الموقع")
                Note(state:state)
                NavigationLink { SubscriptionSourcesHome(state:state) } label: {
                    Label("عرض الاشتراكات حسب طريقة التفعيل",systemImage:"square.stack.3d.up.fill")
                        .font(.headline).foregroundStyle(Ink.navy)
                        .frame(maxWidth:.infinity).padding(15)
                        .background(Ink.gold,in:RoundedRectangle(cornerRadius:14))
                }
                ForEach(state.subscriptions){row in
                    GlassBox{
                        VStack(alignment:.leading,spacing:8){
                            Text(s(row,"full_name")).font(.headline)
                            Text(s(row,"grade_name")).foregroundStyle(Ink.faded).font(.caption)
                            Text("الحالة: \(s(row,"status","غير مشترك")) • الانتهاء: \(s(row,"expires_at"))").font(.footnote)
                            Button("إدارة الاشتراك"){chosen=row;showAction=true}.buttonStyle(.bordered)
                        }
                    }
                }
            }.padding(16)
        }.adminBG().navigationTitle("اشتراكات الطلاب").navigationBarTitleDisplayMode(.inline)
            .task{await state.refreshIfStale("subscriptions")}.refreshable{await state.refresh("subscriptions")}
            .confirmationDialog("اختر إجراء الاشتراك",isPresented:$showAction,titleVisibility:.visible){
                Button("تفعيل شهر واحد"){mutate(1)}
                Button("تفعيل 3 أشهر"){mutate(3)}
                Button("إلغاء الاشتراك",role:.destructive){mutate(0)}
            }
    }
    private func mutate(_ months:Int){
        guard let row=chosen else{return}
        Task{_ = await state.mutation("subscription_set",["id":num(row,"id"),"mode":months==0 ? "cancel":"activate","months":months],refresh:"subscriptions")}
    }
}

// Read-only subscription provenance, backed by authenticated admin-only SQL.
// A student may be present in more than one category after several activations.
private struct SubscriptionSourcesHome:View {
    @ObservedObject var state:AdminState
    var body:some View {
        ScrollView {
            VStack(alignment:.leading,spacing:17) {
                ScreenTitle(text:"مصادر اشتراكات الطلاب",detail:"ثلاث قوائم مستقلة • بيانات الحساب والاشتراك الفعّال")
                Text("اختر طريقة الاشتراك. يظهر الطالب في أكثر من قائمة إذا سبق له استخدام أكثر من طريقة؛ الدفع لا يُحتسب إلا بعملية مالية ناجحة.")
                    .font(.subheadline).foregroundStyle(Ink.faded)
                sourceButton("اشتراك بواسطة كود","ticket.fill","code","عرض الطلاب الذين فعّلوا أكواد الاشتراك")
                sourceButton("اشتراك بالعرض المجاني","gift.fill","offer","عرض المستفيدين من مقاعد الشهر المجاني")
                sourceButton("اشتراك بواسطة دفع","creditcard.fill","paid","عرض الطلاب أصحاب عمليات الدفع المؤكدة")
                GlassBox {
                    Label("هذه القوائم للاطلاع فقط. التفعيل اليدوي من الإدارة لا يُصنّف على أنه دفع مالي.",systemImage:"info.circle")
                        .font(.footnote).foregroundStyle(Ink.faded)
                }
            }.padding(16)
        }.adminBG().navigationTitle("مصادر الاشتراكات").navigationBarTitleDisplayMode(.inline)
    }
    private func sourceButton(_ title:String,_ symbol:String,_ source:String,_ detail:String)->some View {
        NavigationLink {
            SubscriptionSourceList(state:state,source:source,title:title)
        } label: {
            GlassBox {
                HStack(spacing:13) {
                    Image(systemName:symbol).font(.title2).foregroundStyle(Ink.gold)
                        .frame(width:46,height:46).background(Ink.navy,in:RoundedRectangle(cornerRadius:12))
                    VStack(alignment:.leading,spacing:6) {
                        Text(title).font(.headline).foregroundStyle(.white)
                        Text(detail).font(.caption).foregroundStyle(Ink.faded)
                    }
                    Spacer(minLength:4)
                    Image(systemName:"chevron.left").foregroundStyle(Ink.gold)
                }
            }
        }.buttonStyle(.plain)
    }
}

private struct SubscriptionSourceList:View {
    @ObservedObject var state:AdminState
    let source:String
    let title:String
    @State private var rows:[J]=[]
    @State private var search=""
    @State private var submittedSearch=""
    @State private var loading=false
    @State private var more=false
    @State private var total=0
    @State private var error=""

    var body:some View {
        ScrollView {
            LazyVStack(alignment:.leading,spacing:13) {
                ScreenTitle(text:title,detail:"الحسابات ذات الاشتراك الفعّال فقط")
                HStack(spacing:8) {
                    TextField("اسم الطالب أو الهاتف أو البريد",text:$search)
                        .textFieldStyle(.roundedBorder)
                        .submitLabel(.search)
                        .onSubmit {Task{await reload()}}
                    Button("بحث"){Task{await reload()}}.buttonStyle(.borderedProminent).tint(Ink.gold)
                        .foregroundStyle(Ink.navy)
                }
                Text("العدد: \(total) طالب • اضغط على الاسم لمعرفة تفاصيل الحساب")
                    .font(.subheadline).foregroundStyle(Ink.faded)
                if !error.isEmpty {
                    GlassBox {
                        Label(error,systemImage:"exclamationmark.triangle.fill")
                            .font(.footnote).foregroundStyle(.orange)
                        Button("إعادة المحاولة"){Task{await load(reset:rows.isEmpty)}}
                    }
                }
                ForEach(rows){row in
                    NavigationLink { SubscriptionSourceDetails(row:row) } label: {
                        GlassBox {
                            VStack(alignment:.leading,spacing:7) {
                                HStack {
                                    Text(s(row,"full_name")).font(.headline).foregroundStyle(.white)
                                    Spacer()
                                    Image(systemName:"chevron.left").foregroundStyle(Ink.gold)
                                }
                                Text("رقم الحساب: #\(s(row,"id")) • \(s(row,"grade_name","غير محدد"))")
                                    .font(.caption).foregroundStyle(Ink.faded)
                                Text("الهاتف: \(display(row,"phone")) • ينتهي: \(display(row,"expires_at"))")
                                    .font(.caption).foregroundStyle(Ink.faded)
                            }
                        }
                    }.buttonStyle(.plain)
                }
                if loading {ProgressView("جاري تحميل الطلاب…").frame(maxWidth:.infinity).padding()}
                if rows.isEmpty && !loading && error.isEmpty {
                    GlassBox {Text("لا توجد اشتراكات فعّالة مطابقة لهذه الفئة والبحث.").foregroundStyle(Ink.faded)}
                }
                if more && !loading {
                    Button("تحميل المزيد من الطلاب"){Task{await load(reset:false)}}
                        .frame(maxWidth:.infinity).buttonStyle(.borderedProminent).tint(Ink.gold)
                }
            }.padding(16)
        }.adminBG().navigationTitle(title).navigationBarTitleDisplayMode(.inline)
            .task{await reload()}.refreshable{await reload()}
    }
    private func reload() async {
        submittedSearch=safe(search)
        await load(reset:true)
    }
    private func load(reset:Bool) async {
        guard !loading else{return}
        loading=true;error=""
        defer{loading=false}
        do {
            let result=try await state.call("subscription_sources",extras:[
                URLQueryItem(name:"source",value:source),
                URLQueryItem(name:"offset",value:String(reset ? 0 : rows.count)),
                URLQueryItem(name:"q",value:submittedSearch)
            ])
            let newRows=list(result)
            if reset {rows=newRows} else {rows.append(contentsOf:newRows)}
            total=num(result,"total")
            more=result["has_more"] as? Bool ?? false
        } catch {self.error=error.localizedDescription}
    }
    private func display(_ row:J,_ key:String)->String {
        let value=s(row,key,"")
        return value.isEmpty ? "غير مضاف" : value
    }
}

private struct SubscriptionSourceDetails:View {
    let row:J
    var body:some View {
        ScrollView {
            VStack(alignment:.leading,spacing:15) {
                ScreenTitle(text:s(row,"full_name"),detail:"الملف الكامل المتاح للمدير • حساب #\(s(row,"id"))")
                GlassBox {
                    VStack(alignment:.leading,spacing:11) {
                        Label("بيانات حساب الطالب",systemImage:"person.text.rectangle.fill").foregroundStyle(Ink.gold).font(.headline)
                        field("الاسم", "full_name")
                        field("رقم الحساب", "id")
                        field("الصف الدراسي", "grade_name")
                        field("الهاتف", "phone")
                        field("البريد الإلكتروني", "email")
                        field("الجنس", "gender")
                        field("حالة الحساب", "account_status")
                        field("تاريخ إنشاء الحساب", "created_at")
                        field("تأكيد البريد", "email_verified_at")
                        field("تأكيد الهاتف", "phone_verified_at")
                    }
                }
                GlassBox {
                    VStack(alignment:.leading,spacing:11) {
                        Label("الاشتراك الحالي",systemImage:"checkmark.shield.fill").foregroundStyle(Ink.gold).font(.headline)
                        field("الحالة", "subscription_status")
                        field("البداية", "starts_at")
                        field("الانتهاء", "expires_at")
                        field("القيمة المسجلة (د.ع)", "subscription_amount")
                    }
                }
                if num(row,"code_id")>0 {
                    GlassBox {
                        VStack(alignment:.leading,spacing:11) {
                            Label("سجل تفعيل بكود",systemImage:"ticket.fill").foregroundStyle(Ink.gold).font(.headline)
                            field("مجموعة الكود", "code_batch")
                            field("مدة الكود بالأشهر", "code_months")
                            field("تاريخ استخدام الكود", "code_redeemed_at")
                        }
                    }
                }
                if num(row,"offer_seat")>0 {
                    GlassBox {
                        VStack(alignment:.leading,spacing:11) {
                            Label("سجل الشهر المجاني",systemImage:"gift.fill").foregroundStyle(Ink.gold).font(.headline)
                            field("رقم المقعد", "offer_seat")
                            field("تاريخ الحجز (UTC)", "offer_claimed_at_utc")
                        }
                    }
                }
                if num(row,"payment_id")>0 {
                    GlassBox {
                        VStack(alignment:.leading,spacing:11) {
                            Label("سجل دفع مؤكّد",systemImage:"creditcard.fill").foregroundStyle(Ink.gold).font(.headline)
                            field("رقم الطلب", "payment_id")
                            field("مزود الدفع", "payment_provider")
                            field("المبلغ المدفوع (د.ع)", "payment_amount")
                            field("تاريخ الدفع", "payment_paid_at")
                        }
                    }
                }
                Text("البيانات من سجلات موثّقة. لا تُعرض كلمات المرور أو بيانات البطاقات. إذا غُيّرت طريقة الاشتراك لاحقاً قد يظهر الحساب في أكثر من سجل.")
                    .font(.footnote).foregroundStyle(Ink.faded)
            }.padding(16)
        }.adminBG().navigationTitle("تفاصيل الطالب").navigationBarTitleDisplayMode(.inline)
    }
    private func field(_ title:String,_ key:String)->some View {
        let value=s(row,key,"")
        return VStack(alignment:.leading,spacing:3) {
            Text(title).font(.caption).foregroundStyle(Ink.faded)
            Text(value.isEmpty ? "غير متوفر" : value)
                .font(.subheadline).foregroundStyle(.white).textSelection(.enabled)
        }
    }
}

private struct FreeMonthCampaignScreen:View {
    @ObservedObject var state:AdminState
    @State private var enabled=false
    @State private var capacityInput="100"
    @State private var startDate=Date().addingTimeInterval(3600)
    @State private var endDate=Date().addingTimeInterval(8*86400)
    @State private var needsImmediateConfirmation=false
    private let baghdad=TimeZone(identifier:"Asia/Baghdad") ?? .current

    private var claimed:Int {num(state.campaign,"claimed")}
    private var capacity:Int {num(state.campaign,"capacity")}
    private var remaining:Int {max(0,capacity-claimed)}
    private var requestedCapacity:Int? {
        guard let value=Int(capacityInput),value>=max(1,claimed),value<=10000 else {return nil}
        return value
    }

    var body:some View {
        ScrollView {
            VStack(alignment:.leading,spacing:17) {
                ScreenTitle(text:"مقاعد الاشتراك المجاني",detail:"شهر واحد لكل طالب • تحكم بالعدد والموعد من الإدارة")
                Note(state:state)

                GlassBox {
                    VStack(alignment:.leading,spacing:14) {
                        HStack {
                            Label("المقاعد المجانية",systemImage:"gift.fill")
                                .foregroundStyle(Ink.gold).font(.headline)
                            Spacer()
                            Text("\(claimed) / \(capacity)").font(.title2.bold()).monospacedDigit()
                        }
                        ProgressView(value:Double(claimed),total:Double(max(1,capacity))).tint(Ink.gold)
                        HStack {
                            Text("المتبقي: \(remaining) طالب").foregroundStyle(Ink.faded)
                            Spacer()
                            Text(campaignState).foregroundStyle(s(state.campaign,"state","")=="open" ? Color.green : Color.orange)
                        }.font(.subheadline)
                    }
                }

                GlassBox {
                    VStack(alignment:.leading,spacing:16) {
                        Label("إعدادات المقاعد والانطلاق",systemImage:"calendar.badge.clock")
                            .foregroundStyle(Ink.gold).font(.headline)
                        Toggle("تمكين الحملة",isOn:$enabled)
                            .tint(.green)
                        HStack {
                            Text("عدد المقاعد المجانية")
                            Spacer()
                            TextField("100",text:$capacityInput)
                                .keyboardType(.numberPad)
                                .multilineTextAlignment(.center)
                                .textFieldStyle(.roundedBorder)
                                .frame(width:100)
                        }
                        Text("يمكن تغييره بين 1 و10000. لا يمكن تقليل العدد عن \(claimed) اشتراكًا محجوزًا.")
                            .font(.footnote).foregroundStyle(requestedCapacity == nil ? Color.orange : Ink.faded)
                        DatePicker("موعد البداية",selection:$startDate,
                                   displayedComponents:[.date,.hourAndMinute])
                            .datePickerStyle(.compact)
                            .environment(\.timeZone,baghdad)
                            .disabled(claimed>0)
                        DatePicker("موعد انتهاء الحملة",selection:$endDate,
                                   displayedComponents:[.date,.hourAndMinute])
                            .datePickerStyle(.compact)
                            .environment(\.timeZone,baghdad)
                        Text("التوقيت المعروض: بغداد (UTC+3). يظهر العرض للحسابات التي تُنشأ خلال الفترة فقط، ويتوقف تلقائيًا عند النهاية.")
                            .font(.footnote).foregroundStyle(Ink.faded)
                            .fixedSize(horizontal:false,vertical:true)
                        if claimed>0 {
                            Label("حُجزت مقاعد بالفعل، لذا ثُبّت الموعد لحماية عدالة الحملة. يمكنك إيقاف العرض أو تشغيله.",systemImage:"lock.fill")
                                .font(.footnote).foregroundStyle(.orange)
                        }
                        Primary(title:"حفظ المقاعد والموعد والحالة",symbol:"checkmark.shield.fill",busy:state.busy) {
                            if requestedCapacity == nil {
                                state.error="اختر عدد مقاعد صحيحًا بين 1 و10000 ولا يقل عن المحجوز حاليًا."
                            } else if endDate <= startDate {
                                state.error="اختر موعد انتهاء بعد موعد البداية."
                            } else if enabled && startDate <= Date() && claimed==0 {
                                needsImmediateConfirmation=true
                            } else {
                                Task {await save()}
                            }
                        }
                    }
                }

                GlassBox {
                    VStack(alignment:.leading,spacing:9) {
                        Label("كيف يستفيد الطلاب؟",systemImage:"person.3.fill")
                            .foregroundStyle(Ink.gold).font(.headline)
                        Text("بعد البداية، يظهر تنبيه للشخص الذي أنشأ حسابه خلال مدة الحملة وأكّد بياناته، مع زر ثانٍ في الرئيسية إذا أغلق التنبيه. أول \(capacity) طلب ناجح يحصل على شهر كامل من لحظة التفعيل.")
                            .font(.subheadline).foregroundStyle(Ink.faded)
                        Text("بعد نهاية الحملة يختفي العرض تلقائيًا، والحسابات القديمة غير مؤهلة. الإيقاف لا يلغي الأشهر التي مُنحت سابقًا، ولا يُتجاوز عدد المقاعد.")
                            .font(.footnote).foregroundStyle(Ink.faded)
                    }
                }
            }.padding(16)
        }
        .adminBG()
        .navigationTitle("الحملة المجانية")
        .navigationBarTitleDisplayMode(.inline)
        .task {await load()}
        .refreshable {await load()}
        .confirmationDialog("التاريخ المختار مضى؛ هل تريد إطلاق العرض فورًا؟",
                            isPresented:$needsImmediateConfirmation,titleVisibility:.visible) {
            Button("تشغيل العرض الآن") {Task {await save()}}
            Button("العودة",role:.cancel) {}
        }
    }

    private var campaignState:String {
        switch s(state.campaign,"state","") {
        case "open":return "العرض جارٍ"
        case "scheduled":return "مجدول"
        case "full":return "المقاعد مكتملة"
        case "expired":return "انتهت مدة الحملة"
        case "paused":return "متوقف"
        default:return "غير مهيّأ"
        }
    }

    private func load() async {
        await state.refresh("promo_status")
        guard let value=state.campaign["starts_at_utc"] as? String,
              let date=ISO8601DateFormatter().date(from:value),
              let endValue=state.campaign["ends_at_utc"] as? String,
              let end=ISO8601DateFormatter().date(from:endValue) else {return}
        startDate=date
        endDate=end
        enabled=(state.campaign["enabled"] as? Bool) == true
        capacityInput=String(capacity)
    }

    private func save() async {
        let formatter=ISO8601DateFormatter()
        formatter.formatOptions=[.withInternetDateTime]
        guard let wanted=requestedCapacity else {
            state.error="عدد المقاعد غير صحيح؛ الحد 1 إلى 10000 ولا يقل عن المحجوز."
            return
        }
        guard endDate>startDate else {
            state.error="موعد النهاية يجب أن يكون بعد البداية."
            return
        }
        let params:J=["enabled":enabled,"capacity":wanted,
                      "starts_at_utc":formatter.string(from:startDate),
                      "ends_at_utc":formatter.string(from:endDate)]
        if await state.mutation("promo_save",params,refresh:"promo_status") != nil {
            await load()
        }
    }
}
private struct QuestionsScreen:View{
    @ObservedObject var state:AdminState
    @State private var search=""
    @State private var chosen:J?
    @State private var newQuestion=false
    @State private var deleting:J?
    var body:some View {
        ScrollView{
            LazyVStack(alignment:.leading,spacing:14){
                ScreenTitle(text:"بنك الأسئلة",detail:"إضافة أسئلة اختيارية وتعديل الإجابات وشرحها")
                HStack{
                    TextField("بحث عن سؤال",text:$search)
                    Button("بحث"){Task{await state.refresh("questions",search:search)}}
                }.padding(13).background(Ink.blue,in:RoundedRectangle(cornerRadius:12))
                Button{newQuestion=true}label:{Label("إضافة سؤال جديد",systemImage:"plus.circle.fill")}.buttonStyle(.borderedProminent)
                Note(state:state)
                ForEach(state.questions){row in
                    GlassBox{
                        VStack(alignment:.leading,spacing:9){
                            HStack{Text("#\(s(row,"id"))").foregroundStyle(Ink.gold);Text(s(row,"subject_name")).foregroundStyle(Ink.faded);Spacer();Text(num(row,"status")==1 ? "منشور":"مخفي").foregroundStyle(num(row,"status")==1 ? .green:.orange)}
                            Text(s(row,"question_text")).lineLimit(3)
                            Text("الإجابة: \(s(row,"correct_answer"))").font(.footnote).foregroundStyle(Ink.faded)
                            HStack{
                                Button("تعديل"){chosen=row}.buttonStyle(.bordered)
                                Button(num(row,"status")==1 ? "إخفاء":"نشر"){
                                    Task{_ = await state.mutation("question_toggle",["id":num(row,"id")],refresh:"questions")}
                                }.buttonStyle(.bordered)
                                Button("حذف"){deleting=row}.buttonStyle(.bordered).tint(.red)
                            }
                        }
                    }
                }
            }.padding(16)
        }.adminBG().navigationTitle("الأسئلة").navigationBarTitleDisplayMode(.inline)
            .task{await state.refreshIfStale("questions")}.refreshable{await state.refresh("questions",search:search)}
            .sheet(item:$chosen){row in QuestionEditor(state:state,row:row)}
            .sheet(isPresented:$newQuestion){QuestionEditor(state:state,row:nil)}
            .confirmationDialog("هل تريد حذف هذا السؤال نهائياً؟",isPresented:Binding(get:{deleting != nil},set:{if !$0{deleting=nil}}),titleVisibility:.visible){
                Button("حذف السؤال",role:.destructive){if let row=deleting{Task{_ = await state.mutation("question_delete",["id":num(row,"id")],refresh:"questions")}};deleting=nil}
            }
    }
}
private struct QuestionEditor:View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var state:AdminState
    let row:J?
    @State private var question=""
    @State private var answer=""
    @State private var explanation=""
    @State private var chapter=0
    @State private var options=["","","",""]
    var body:some View {
        NavigationStack {
            Form{
                Section("محتوى السؤال"){
                    TextField("نص السؤال",text:$question,axis:.vertical).lineLimit(3...8)
                    TextField("الإجابة الصحيحة",text:$answer,axis:.vertical)
                    TextField("شرح الإجابة",text:$explanation,axis:.vertical).lineLimit(3...8)
                }
                if row == nil {
                    Section("المادة والفصل"){
                        Picker("الفصل",selection:$chapter){
                            Text("اختر الفصل").tag(0)
                            ForEach(list(state.curriculum,"chapters")){c in Text(s(c,"name")).tag(num(c,"id"))}
                        }
                    }
                    Section("أربعة اختيارات"){
                        ForEach(options.indices,id:\.self){i in
                            TextField("الاختيار \(i+1)",text:$options[i])
                        }
                        Text("اكتب الإجابة الصحيحة حرفياً كما تظهر في أحد الاختيارات.").font(.footnote).foregroundStyle(Ink.faded)
                    }
                }
                Section{
                    Primary(title:"حفظ السؤال",symbol:"square.and.arrow.down",busy:state.busy){
                        Task{
                            var data:J=["id":num(row ?? [:],"id"),"question_text":question,"correct_answer":answer,"explanation":explanation]
                            if row == nil {data["chapter_id"]=chapter;data["options"]=options}
                            if await state.mutation("question_save",data,refresh:"questions") != nil{dismiss()}
                        }
                    }
                    Note(state:state)
                }
            }.scrollContentBackground(.hidden).background(Ink.navy)
            .navigationTitle(row == nil ? "سؤال جديد":"تعديل السؤال")
            .onAppear{
                if let row{question=s(row,"question_text","");answer=s(row,"correct_answer","");explanation=s(row,"explanation","")}
                if row == nil{Task{await state.refresh("curriculum")}}
            }
            .toolbar{ToolbarItem(placement:.topBarLeading){Button("إغلاق"){dismiss()}}}
        }
    }
}
private struct CurriculumScreen:View {
    @ObservedObject var state:AdminState
    @State private var selected:J?
    @State private var newKind:String?
    var body:some View{
        ScrollView{
            VStack(alignment:.leading,spacing:15){
                ScreenTitle(text:"المواد والفصول",detail:"مرتّبة حسب المنهج الحقيقي لكل صف")
                Note(state:state)
                HStack{
                    Button("إضافة مادة"){newKind="subject"}.buttonStyle(.borderedProminent)
                    Button("إضافة فصل"){newKind="chapter"}.buttonStyle(.bordered)
                }
                ForEach(list(state.curriculum,"grades")){grade in
                    Text(s(grade,"name")).font(.title3.bold()).foregroundStyle(Ink.gold).padding(.top,8)
                    let subjects=list(state.curriculum,"subjects").filter{num($0,"grade_id")==num(grade,"id")}
                    ForEach(subjects){subject in
                        GlassBox{
                            VStack(alignment:.leading,spacing:10){
                                HStack {
                                    Text(s(subject,"name")).font(.headline)
                                    Spacer()
                                    Button("تعديل"){var updated=subject;updated["_type"]="subject";selected=updated}.buttonStyle(.bordered)
                                }
                                ForEach(list(state.curriculum,"chapters").filter{num($0,"subject_id")==num(subject,"id")}){chapter in
                                    Button{var updated=chapter;updated["_type"]="chapter";selected=updated}label:{
                                        HStack{Image(systemName:"book.closed");Text(s(chapter,"name"));Spacer();Image(systemName:"pencil").foregroundStyle(Ink.faded)}
                                        .padding(.vertical,5)
                                    }.buttonStyle(.plain)
                                }
                            }
                        }
                    }
                }
            }.padding(16)
        }.adminBG().navigationTitle("المنهج").navigationBarTitleDisplayMode(.inline)
            .task{await state.refreshIfStale("curriculum")}.refreshable{await state.refresh("curriculum")}
            .sheet(item:$selected){row in CurriculumEditor(state:state,row:row,kind:s(row,"_type"))}
            .sheet(item:Binding(get:{newKind.map{KindItem(name:$0)}},set:{if $0 == nil{newKind=nil}})){kind in
                CurriculumEditor(state:state,row:nil,kind:kind.name)
            }
    }
}
private struct KindItem:Identifiable {let name:String;var id:String{name}}
private struct CurriculumEditor:View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var state:AdminState
    let row:J?
    let kind:String
    @State private var name=""
    @State private var parent=0
    @State private var visible=true
    private var pickerRows:[J] { kind == "subject" ? list(state.curriculum,"grades") : list(state.curriculum,"subjects") }
    var body:some View{
        NavigationStack {
            Form{
                TextField("اسم المادة أو الفصل",text:$name)
                Picker(kind=="subject" ? "الصف الدراسي":"المادة",selection:$parent){
                    Text("اختر").tag(0)
                    ForEach(pickerRows){item in Text(s(item,"name")).tag(num(item,"id"))}
                }
                Toggle("متاح للطلاب",isOn:$visible)
                Primary(title:"حفظ",busy:state.busy){
                    Task{let d:J=["type":kind,"id":num(row ?? [:],"id"),"name":name,"parent_id":parent,"status":visible ? 1:0]
                        if await state.mutation("curriculum_save",d,refresh:"curriculum") != nil{dismiss()}
                    }
                }
                Note(state:state)
            }.scrollContentBackground(.hidden).background(Ink.navy)
            .navigationTitle(row == nil ? "إضافة":"تعديل")
            .onAppear{
                if let row{name=s(row,"name","");parent=num(row,kind=="subject" ? "grade_id":"subject_id");visible=num(row,"status")==1}
            }
            .toolbar{ToolbarItem(placement:.topBarLeading){Button("إغلاق"){dismiss()}}}
        }
    }
}


private struct PredictionsAdminScreen: View {
    @ObservedObject var state: AdminState
    @State private var selectedSubject = 0
    @State private var manual = false
    @State private var showEditor = false
    @State private var editingRow: J?
    @State private var deletion: Int?
    var body: some View {
        ScrollView {
            VStack(alignment:.leading,spacing:15) {
                ScreenTitle(text:"إدارة المرشحات",detail:"إضافة وتعديل وحذف وإيقاف المرشحات لكل مادة")
                Note(state:state)
                subjectControls
                Button {
                    editingRow=nil
                    showEditor=true
                } label: { Label("إضافة مرشح",systemImage:"plus.circle.fill") }
                .buttonStyle(.borderedProminent)
                .disabled(selectedSubject==0)
                predictionsList
            }.padding(16)
        }
        .adminBG()
        .navigationTitle("المرشحات")
        .task {
            await state.refresh("predictions_subjects")
            if selectedSubject==0 {selectedSubject=num(state.predictionSubjects.first ?? [:],"id")}
            await state.refreshPredictions(subjectID:selectedSubject)
            manual=state.predictionMode=="manual"
        }
        .refreshable {
            await state.refreshPredictions(subjectID:selectedSubject)
            manual=state.predictionMode=="manual"
        }
        .sheet(isPresented:$showEditor) {
            PredictionEditor(state:state,subjectID:selectedSubject,row:editingRow)
        }
        .alert("حذف المرشح؟",isPresented:Binding(get:{deletion != nil},set:{if !$0{deletion=nil}})) {
            Button("حذف",role:.destructive) {
                guard let id=deletion else {return}
                deletion=nil
                Task {
                    if await state.mutation("prediction_delete",["subject_id":selectedSubject,"id":id],refresh:"") != nil {
                        await state.refreshPredictions(subjectID:selectedSubject)
                    }
                }
            }
            Button("إلغاء",role:.cancel){deletion=nil}
        } message: {Text("سيُحذف المرشح المحدد من هذه المادة.")}
    }
    private var subjectControls: some View {
        GlassBox {
            VStack(alignment:.leading,spacing:12) {
                Picker("الصف والمادة",selection:$selectedSubject) {
                    Text("اختر مادة").tag(0)
                    ForEach(state.predictionSubjects) { row in
                        Text(s(row,"grade_name")+" — "+s(row,"name")).tag(num(row,"id"))
                    }
                }
                .onChange(of:selectedSubject){ id in
                    Task {
                        await state.refreshPredictions(subjectID:id)
                        manual=state.predictionMode=="manual"
                    }
                }
                Toggle("عرض المرشحات التي تحددها الإدارة فقط",isOn:$manual)
                Button("حفظ طريقة العرض") {
                    Task {
                        let payload:J = ["subject_id":selectedSubject,"mode":manual ? "manual" : "automatic"]
                        if await state.mutation("prediction_mode",payload,refresh:"") != nil {
                            await state.refreshPredictions(subjectID:selectedSubject)
                        }
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(selectedSubject == 0 || state.busy)
            }
        }
    }
    private var predictionsList: some View {
        VStack(alignment:.leading,spacing:12) {
            ForEach(state.predictionRows) { row in
                PredictionAdminRow(row:row,edit:{
                    editingRow=row
                    showEditor=true
                },toggle:{
                    Task {
                        let payload:J=["subject_id":selectedSubject,"id":num(row,"id")]
                        if await state.mutation("prediction_toggle",payload,refresh:"") != nil {
                            await state.refreshPredictions(subjectID:selectedSubject)
                        }
                    }
                },delete:{ deletion=num(row,"id") })
            }
            if state.predictionRows.isEmpty {
                Text("لا توجد مرشحات مضافة من الإدارة لهذه المادة.")
                    .foregroundStyle(Ink.faded).font(.footnote)
            }
        }
    }
}
private struct PredictionAdminRow:View {
    let row:J
    let edit:()->Void
    let toggle:()->Void
    let delete:()->Void
    private var statusText:String {
        "الترتيب: "+s(row,"sort_order")+" · الدرجة: "+s(row,"prediction_score")+" · "+(num(row,"is_enabled")==1 ? "ظاهر" : "موقوف")
    }
    var body:some View {
        GlassBox {
            VStack(alignment:.leading,spacing:10) {
                Text(s(row,"question_text")).font(.headline)
                    .fixedSize(horizontal:false,vertical:true)
                Text(s(row,"correct_answer")).font(.subheadline).foregroundStyle(Ink.faded)
                Text(statusText).font(.caption).foregroundStyle(Ink.faded)
                HStack {
                    Button("تعديل",action:edit).buttonStyle(.bordered)
                    Button(num(row,"is_enabled")==1 ? "إيقاف" : "تفعيل",action:toggle).buttonStyle(.bordered)
                    Button("حذف",role:.destructive,action:delete).buttonStyle(.bordered)
                }
            }
        }
    }
}
private struct PredictionEditor: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var state:AdminState
    let subjectID:Int
    let row:J?
    @State private var question=""
    @State private var answer=""
    @State private var explanation=""
    @State private var topic=""
    @State private var reason=""
    @State private var questionID=0
    @State private var score=70
    @State private var order=0
    @State private var enabled=true
    var body:some View {
        NavigationStack {
            Form {
                Section("نص المرشح") {
                    TextField("نص السؤال",text:$question,axis:.vertical).lineLimit(3...8)
                    TextField("الإجابة الصحيحة",text:$answer,axis:.vertical).lineLimit(2...8)
                    TextField("شرح الإجابة",text:$explanation,axis:.vertical).lineLimit(2...8)
                    TextField("اسم الموضوع",text:$topic)
                    TextField("سبب الترشيح",text:$reason,axis:.vertical)
                }
                Section("الإعدادات") {
                    TextField("معرّف السؤال في البنك، اختياري",value:$questionID,format:.number).keyboardType(.numberPad)
                    TextField("درجة الترشيح (0–100)",value:$score,format:.number).keyboardType(.numberPad)
                    TextField("الترتيب",value:$order,format:.number).keyboardType(.numbersAndPunctuation)
                    Toggle("المرشح مفعّل",isOn:$enabled)
                }
                Section {
                    Primary(title:"حفظ المرشح",symbol:"square.and.arrow.down",busy:state.busy) {
                        Task {
                            let data:J=["subject_id":subjectID,"id":num(row ?? [:],"id"),
                              "question_id":questionID,"question_text":question,
                              "correct_answer":answer,"explanation":explanation,
                              "chapter_name":topic,"reason":reason,
                              "prediction_score":score,"sort_order":order,"is_enabled":enabled]
                            if await state.mutation("prediction_save",data,refresh:"") != nil {
                                await state.refreshPredictions(subjectID:subjectID)
                                dismiss()
                            }
                        }
                    }
                    Note(state:state)
                }
            }
            .scrollContentBackground(.hidden).background(Ink.navy)
            .navigationTitle(row == nil ? "مرشح جديد" : "تعديل المرشح")
            .onAppear {
                if let row {
                    question=s(row,"question_text","")
                    answer=s(row,"correct_answer","")
                    explanation=s(row,"explanation","")
                    topic=s(row,"chapter_name","")
                    reason=s(row,"reason","")
                    questionID=num(row,"question_id")
                    score=num(row,"prediction_score")
                    order=num(row,"sort_order")
                    enabled=num(row,"is_enabled")==1
                }
            }
            .toolbar{ToolbarItem(placement:.topBarLeading){Button("إغلاق"){dismiss()}}}
        }
    }
}

