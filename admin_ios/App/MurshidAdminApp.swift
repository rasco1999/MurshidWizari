import SwiftUI
import LocalAuthentication

typealias J = [String: Any]
private enum Ink {
    static let navy = Color(red: 0.025, green: 0.074, blue: 0.145)
    static let blue = Color(red: 0.075, green: 0.148, blue: 0.250)
    static let blue2 = Color(red: 0.115, green: 0.205, blue: 0.310)
    static let gold = Color(red: 0.972, green: 0.801, blue: 0.456)
    static let faded = Color(red: 0.710, green: 0.785, blue: 0.870)
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
    @Published var codes:[J] = []
    @Published var ads:[J] = []
    @Published var students:[J] = []
    @Published var subscriptions:[J] = []
    @Published var questions:[J] = []
    @Published var curriculum:J = [:]
    @Published var contest:J = [:]
    @Published var contentHealth:J = [:]
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
    func call(_ action:String, params:J?=nil, query:String?=nil) async throws -> J {
        var components=URLComponents(url:endpoint,resolvingAgainstBaseURL:false)!
        var qs=[URLQueryItem(name:"action",value:action)]
        if let query, !query.isEmpty {qs.append(URLQueryItem(name:"q",value:query))}
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
            throw ServiceError(message:s(result,"message",http==404 ? "تحتاج تركيب native-api.php على الاستضافة أولاً." : "تعذر الاتصال بالخدمة (\(http))."))
        }
        return result
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
        _=try? await call("logout",params:[:])
        csrf="";name="الإدارة";stage = .signin
    }
    func refresh(_ area:String,search:String?=nil) async {
        busy=true;error=""
        defer{busy=false}
        do {
            let result=try await call(area,query:search)
            switch area {
            case "dashboard": dashboard=result
            case "codes": codes=list(result)
            case "ads": ads=list(result)
            case "students": students=list(result)
            case "subscriptions": subscriptions=list(result)
            case "questions": questions=list(result)
            case "curriculum": curriculum=result
            case "contest": contest=result
            case "content_health": contentHealth=result
            default: break
            }
        } catch { self.error=error.localizedDescription }
    }
    func mutation(_ action:String,_ payload:J,refresh area:String) async -> J? {
        busy=true;error="";notice=""
        defer{busy=false}
        do {
            let response=try await call(action,params:payload)
            notice=s(response,"message","تم حفظ العملية بنجاح.")
            if !area.isEmpty {await refresh(area)}
            return response
        }catch {self.error=error.localizedDescription;return nil}
    }
}
@main struct MurshidAdministration:App {
    @StateObject private var state=AdminState()
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
            .tint(Ink.gold)
        }
    }
}
private struct AdminBackground:ViewModifier {
    func body(content:Content)->some View {content.background(Ink.navy.ignoresSafeArea())}
}
private extension View {func adminBG()->some View {modifier(AdminBackground())}}
private struct Splash:View {
    var body:some View {
        VStack(spacing:18) {
            Image(systemName:"shield.lefthalf.filled").font(.system(size:70)).foregroundStyle(Ink.gold)
            Text("الإدارة").font(.largeTitle.bold())
            ProgressView().tint(Ink.gold)
        }.frame(maxWidth:.infinity,maxHeight:.infinity).adminBG()
    }
}
private struct GlassBox<Content:View>:View {
    let content:Content
    init(@ViewBuilder _ content:()->Content){self.content=content()}
    var body:some View {
        content.padding(16).frame(maxWidth:.infinity,alignment:.leading)
            .background(Ink.blue,in:RoundedRectangle(cornerRadius:20))
            .overlay(RoundedRectangle(cornerRadius:20).stroke(Ink.blue2,lineWidth:1))
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
            .foregroundStyle(Ink.navy).background(Ink.gold,in:RoundedRectangle(cornerRadius:14))
        }.buttonStyle(.plain).disabled(busy)
    }
}
private struct LoginScreen:View {
    @ObservedObject var state:AdminState
    @State private var identifier=""
    @State private var password=""
    var body:some View {
        ScrollView {
            VStack(spacing:22) {
                Image(systemName:"building.columns.circle.fill").font(.system(size:96)).foregroundStyle(Ink.gold).padding(.top,65)
                Text("الإدارة").font(.system(size:36,weight:.heavy))
                Text("المركز الخاص لإدارة منصة المرشد الوزاري").foregroundStyle(Ink.faded).multilineTextAlignment(.center)
                GlassBox {
                    VStack(alignment:.leading,spacing:16) {
                        Label("حساب المدير",systemImage:"person.crop.circle.fill").foregroundStyle(Ink.gold)
                        TextField("البريد الإلكتروني أو رقم الهاتف",text:$identifier)
                            .textContentType(.username).keyboardType(.emailAddress).textInputAutocapitalization(.never)
                            .padding(13).background(Ink.navy,in:RoundedRectangle(cornerRadius:12))
                        SecureField("كلمة المرور",text:$password)
                            .textContentType(.password)
                            .padding(13).background(Ink.navy,in:RoundedRectangle(cornerRadius:12))
                        Primary(title:"دخول الإدارة",symbol:"arrow.left.circle.fill",busy:state.busy) {
                            Task {await state.login(identifier,password)}
                        }
                    }
                }
                if !state.error.isEmpty {Text(state.error).foregroundStyle(.orange).font(.footnote)}
                Label("المدير فقط • اتصال مشفر • صلاحيات محمية من الخادم",systemImage:"lock.shield")
                    .font(.footnote).foregroundStyle(Ink.faded).multilineTextAlignment(.center)
            }.padding(20)
        }.adminBG()
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
        }.tint(Ink.gold).adminBG()
    }
}
private struct ScreenTitle:View {
    let text:String;let detail:String
    var body:some View {
        VStack(alignment:.leading,spacing:5){
            Text(text).font(.system(size:28,weight:.bold))
            Text(detail).font(.subheadline).foregroundStyle(Ink.faded)
        }.frame(maxWidth:.infinity,alignment:.leading).padding(.top,6)
    }
}
private struct Note:View {
    @ObservedObject var state:AdminState
    var body:some View {
        Group {
            if !state.error.isEmpty {Label(state.error,systemImage:"exclamationmark.triangle").foregroundStyle(.orange).font(.footnote).padding(10).frame(maxWidth:.infinity).background(Ink.blue,in:RoundedRectangle(cornerRadius:12))}
            if !state.notice.isEmpty {Label(state.notice,systemImage:"checkmark.circle.fill").foregroundStyle(.green).font(.footnote).padding(10).frame(maxWidth:.infinity).background(Ink.blue,in:RoundedRectangle(cornerRadius:12))}
        }
    }
}
private struct MetricTile:View {
    let name:String;let count:String;let icon:String
    var body:some View {
        GlassBox {
            VStack(alignment:.leading,spacing:14){
                Image(systemName:icon).font(.title2).foregroundStyle(Ink.gold)
                Text(count).font(.system(size:29,weight:.bold,design:.rounded)).minimumScaleFactor(0.7).lineLimit(1)
                Text(name).foregroundStyle(Ink.faded).font(.footnote)
            }
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
                VStack(alignment:.leading,spacing:18){
                    HStack {
                        VStack(alignment:.leading,spacing:5){
                            Text("مركز التحكم").font(.largeTitle.bold())
                            Text("أهلًا، \(state.name)").foregroundStyle(Ink.faded)
                        }
                        Spacer()
                        Image(systemName:"checkmark.shield.fill").font(.title).foregroundStyle(Ink.gold)
                    }.padding(.top,12)
                    GlassBox {
                        VStack(alignment:.leading,spacing:8){
                            Label("منصة المرشد الوزاري",systemImage:"building.columns.fill").foregroundStyle(Ink.gold).font(.headline)
                            Text("إحصائيات مباشرة من موقعك").foregroundStyle(Ink.faded).font(.subheadline)
                            HStack{
                                Image(systemName:"circle.fill").font(.system(size:8)).foregroundStyle(.green)
                                Text("الإدارة محمية بحساب المدير").font(.caption)
                            }
                        }
                    }
                    Note(state:state)
                    LazyVGrid(columns:grids,spacing:12) {
                        MetricTile(name:"الطلاب",count:fmt("students"),icon:"person.3.fill")
                        MetricTile(name:"الاشتراكات الفعالة",count:fmt("subscriptions"),icon:"crown.fill")
                        MetricTile(name:"الأسئلة المنشورة",count:fmt("questions"),icon:"checklist")
                        MetricTile(name:"أكواد متاحة",count:fmt("codes"),icon:"ticket.fill")
                        MetricTile(name:"إعلانات نشطة",count:fmt("ads"),icon:"megaphone.fill")
                        MetricTile(name:"المعلمون",count:fmt("teachers"),icon:"person.crop.rectangle.stack")
                    }
                    Text("الوصول السريع").font(.title3.bold())
                    GlassBox{
                        VStack(spacing:5){
                            quick("إنشاء أكواد الاشتراك","ticket.fill"){tab = .codes}
                            Divider().overlay(Ink.blue2)
                            quick("إدارة إعلانات المدارس","megaphone.fill"){tab = .ads}
                            Divider().overlay(Ink.blue2)
                            quick("البحث عن الطلاب","magnifyingglass"){tab = .students}
                        }
                    }
                }.padding(16)
            }.adminBG().refreshable{await state.refresh("dashboard")}
            .task {await state.refresh("dashboard")}
            .toolbar {ToolbarItem(placement:.topBarTrailing){Button{Task{await state.refresh("dashboard")}}label:{Image(systemName:"arrow.clockwise")}}}
        }
    }
    private func fmt(_ key:String)->String {
        let v=state.dashboard["stats"] as? J ?? [:]
        return v[key] == nil || v[key] is NSNull ? "—" : s(v,key)
    }
    private func quick(_ label:String,_ icon:String,action:@escaping ()->Void)->some View {
        Button(action:action){HStack{Image(systemName:icon).foregroundStyle(Ink.gold).frame(width:30);Text(label);Spacer();Image(systemName:"chevron.left").foregroundStyle(Ink.faded)}.padding(.vertical,8)}
            .buttonStyle(.plain)
    }
}
private struct CodesScreen:View {
    @ObservedObject var state:AdminState
    @State private var showCreate=false
    @State private var editing:J?
    @State private var toRevoke:J?
    @State private var toDelete:J?
    @State private var issued:[String]=[]
    @State private var showIssued=false

    var body:some View {
        NavigationStack {
            content
                .adminBG()
                .refreshable{await state.refresh("codes")}
                .task{await state.refresh("codes")}
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
                ForEach(state.codes){code in codeCard(code)}
            }.padding(16)
        }
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
    public var id:String {String(describing:self["id"] ?? UUID().uuidString)}
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
                VStack(alignment:.leading,spacing:16){
                    ScreenTitle(text:"إعلانات المدارس",detail:"إعلان 24 ساعة • 5,000 دينار عراقي")
                    HStack{
                        Text("\(state.ads.count) إعلان").foregroundStyle(Ink.faded)
                        Spacer()
                        Button{showNew=true}label:{Label("إعلان جديد",systemImage:"plus.circle.fill")}.buttonStyle(.borderedProminent).tint(Ink.gold).foregroundStyle(Ink.navy)
                    }
                    Note(state:state)
                    if state.ads.isEmpty {GlassBox{Text("لا توجد إعلانات بعد.").foregroundStyle(Ink.faded)}}
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
            }.adminBG().refreshable{await state.refresh("ads")}.task{await state.refresh("ads")}
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
                VStack(alignment:.leading,spacing:15){
                    ScreenTitle(text:"الطلاب",detail:"بحث مباشر في بيانات حسابات الموقع")
                    HStack {
                        Image(systemName:"magnifyingglass").foregroundStyle(Ink.gold)
                        TextField("ابحث بالاسم أو الهاتف أو البريد",text:$search).textInputAutocapitalization(.never)
                        Button("بحث"){Task{await state.refresh("students",search:search)}}
                    }.padding(13).background(Ink.blue,in:RoundedRectangle(cornerRadius:15))
                    Note(state:state)
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
            }.adminBG().refreshable{await state.refresh("students",search:search)}
            .task{await state.refresh("students")}
            .confirmationDialog("تعديل حالة حساب الطالب؟",isPresented:Binding(get:{confirm != nil},set:{if !$0{confirm=nil}}),titleVisibility:.visible){
                Button("تأكيد تغيير الحالة"){if let row=confirm{Task{_ = await state.mutation("toggle_student",["id":num(row,"id")],refresh:"students")}};confirm=nil}
            }
        }
    }
}
private struct MoreScreen:View {
    @ObservedObject var state:AdminState
    var body:some View {
        NavigationStack{
            ScrollView {
                VStack(alignment:.leading,spacing:16) {
                    ScreenTitle(text:"إدارة المنصة",detail:"جميع الأدوات • تطبيق أصلي للآيفون")
                    Note(state:state)
                    GlassBox{
                        VStack(spacing:14){
                            NavigationLink{SubscriptionsScreen(state:state)}label:{entry("اشتراكات الطلاب","creditcard.fill")}
                            Divider().overlay(Ink.blue2)
                            NavigationLink{QuestionsScreen(state:state)}label:{entry("بنك الأسئلة","doc.text.fill")}
                            Divider().overlay(Ink.blue2)
                            NavigationLink{CurriculumScreen(state:state)}label:{entry("المواد والفصول","books.vertical.fill")}
                            Divider().overlay(Ink.blue2)
                            NavigationLink{ContestAdminScreen(state:state)}label:{entry("تحدي المليون والمواعيد","trophy.fill")}
                            Divider().overlay(Ink.blue2)
                            NavigationLink{ContentHealthScreen(state:state)}label:{entry("فحص ربط الأسئلة بالمنهج","checkmark.shield.fill")}
                        }
                    }
                    GlassBox{
                        VStack(alignment:.leading,spacing:12){
                            Label("الأمان",systemImage:"lock.shield.fill").foregroundStyle(Ink.gold).font(.headline)
                            Text("كل تغيير يتم داخل قاعدة بيانات الموقع بعد التحقق من حساب المدير وصلاحياته.").font(.footnote).foregroundStyle(Ink.faded)
                            Primary(title:"تسجيل الخروج",symbol:"rectangle.portrait.and.arrow.right"){
                                Task{await state.logout()}
                            }
                        }
                    }
                }.padding(16)
            }.adminBG()
        }
    }
    private func entry(_ title:String,_ icon:String)->some View{
        HStack{Image(systemName:icon).frame(width:32).foregroundStyle(Ink.gold);Text(title).fontWeight(.medium);Spacer();Image(systemName:"chevron.left").foregroundStyle(Ink.faded)}.padding(.vertical,9)
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
            .task{await state.refresh("subscriptions")}.refreshable{await state.refresh("subscriptions")}
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
private struct QuestionsScreen:View{
    @ObservedObject var state:AdminState
    @State private var search=""
    @State private var chosen:J?
    @State private var newQuestion=false
    @State private var deleting:J?
    var body:some View {
        ScrollView{
            VStack(alignment:.leading,spacing:14){
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
            .task{await state.refresh("questions")}.refreshable{await state.refresh("questions",search:search)}
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
            .task{await state.refresh("curriculum")}.refreshable{await state.refresh("curriculum")}
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

private struct ContestAdminScreen:View {
    @ObservedObject var state:AdminState
    @State private var startAt=Date()
    @State private var endAt=Date().addingTimeInterval(86400)
    @State private var loaded=false
    @State private var showConfirmation=false
    private let baghdad=TimeZone(identifier:"Asia/Baghdad")!
    private var dateFormat:DateFormatter {
        let format=DateFormatter()
        format.locale=Locale(identifier:"en_US_POSIX")
        format.calendar=Calendar(identifier:.gregorian)
        format.timeZone=baghdad
        format.dateFormat="yyyy-MM-dd HH:mm:ss"
        return format
    }
    private var validDates:Bool {startAt<endAt}
    var body:some View {
        ScrollView {
            VStack(alignment:.leading,spacing:16){
                ScreenTitle(text:"تحدي المليون",detail:"إدارة فترة التحدي وترتيب المتنافسين من الموقع")
                Note(state:state)
                GlassBox {
                    VStack(alignment:.leading,spacing:14) {
                        Label("الموعد بتوقيت بغداد",systemImage:"calendar.badge.clock")
                            .font(.headline).foregroundStyle(Ink.gold)
                        DatePicker("بداية التحدي",selection:$startAt,displayedComponents:[.date,.hourAndMinute])
                        DatePicker("نهاية التحدي",selection:$endAt,displayedComponents:[.date,.hourAndMinute])
                        if !validDates {
                            Text("يجب أن تكون نهاية التحدي بعد بدايته.")
                                .font(.footnote).foregroundStyle(.orange)
                        }
                        Text("تغيير الفترة يعيد احتساب الترتيب من سجلات النشاط داخل المدة الجديدة، دون حذف نقاط الطلاب القديمة.")
                            .font(.footnote).foregroundStyle(Ink.faded)
                        Primary(title:"حفظ موعد التحدي",symbol:"calendar.badge.checkmark",busy:state.busy){
                            showConfirmation=true
                        }
                        .disabled(!loaded || !validDates || state.busy)
                    }
                }
                GlassBox {
                    VStack(alignment:.leading,spacing:7) {
                        Text("الحالة الحالية").font(.headline).foregroundStyle(Ink.gold)
                        Text(state.contest["active"] as? Bool == true ? "التحدي جارٍ الآن" : "التحدي خارج الفترة الحالية")
                            .foregroundStyle(state.contest["active"] as? Bool == true ? .green:.orange)
                        Text("البداية: \(s(state.contest,"start_at"))")
                        Text("النهاية: \(s(state.contest,"end_at"))")
                    }.font(.footnote)
                }
                Text("ترتيب أفضل 50 طالبًا").font(.title3.bold())
                ForEach(list(state.contest,"leaderboard")){student in
                    GlassBox {
                        HStack(spacing:12){
                            Text("#\(num(student,"rank"))").foregroundStyle(Ink.gold)
                                .font(.headline.monospacedDigit()).frame(width:39)
                            AsyncImage(url:URL(string:s(student,"avatar_url",""))){image in
                                image.resizable().scaledToFill()
                            }placeholder:{
                                Image(systemName:"person.crop.circle.fill")
                                    .resizable().scaledToFit().foregroundStyle(Ink.gold)
                            }
                            .frame(width:46,height:46).clipShape(Circle())
                            VStack(alignment:.leading,spacing:4){
                                Text(s(student,"full_name")).font(.subheadline.bold())
                                Text(s(student,"grade_name","غير محدد"))
                                    .font(.caption).foregroundStyle(Ink.faded)
                                Text(student["verified"] as? Bool == true ? "موثّق" : "بانتظار التوثيق")
                                    .font(.caption2).foregroundStyle(Ink.faded)
                            }
                            Spacer(minLength:4)
                            VStack(alignment:.trailing,spacing:4){
                                Text("\(num(student,"contest_xp")) XP").font(.subheadline.bold())
                                Text("\(num(student,"correct")) صحيحة")
                                    .font(.caption).foregroundStyle(Ink.faded)
                            }
                        }
                    }
                }
                if loaded && list(state.contest,"leaderboard").isEmpty {
                    Text("لا توجد مشاركات ضمن الفترة الحالية.")
                        .font(.footnote).foregroundStyle(Ink.faded)
                }
            }.padding(16)
        }.adminBG().navigationTitle("تحدي المليون").navigationBarTitleDisplayMode(.inline)
            .task{await fetch()}.refreshable{await fetch()}
            .confirmationDialog("تأكيد تغيير فترة تحدي المليون؟",isPresented:$showConfirmation,titleVisibility:.visible){
                Button("حفظ المواعيد"){
                    guard validDates else{return}
                    Task{
                        _ = await state.mutation("contest_schedule_set",[
                            "start_at":dateFormat.string(from:startAt),
                            "end_at":dateFormat.string(from:endAt)
                        ],refresh:"contest")
                    }
                }
            }
            .environment(\.timeZone,baghdad)
    }
    private func fetch() async {
        await state.refresh("contest")
        guard state.contest["ok"] as? Bool == true,
              let start=dateFormat.date(from:s(state.contest,"start_at","")),
              let end=dateFormat.date(from:s(state.contest,"end_at","")) else {loaded=false;return}
        startAt=start;endAt=end;loaded=true
    }
}

private struct ContentHealthScreen:View {
    @ObservedObject var state:AdminState
    private var checks:[(String,String,String)] {[
        ("questions_total","جميع الأسئلة","doc.text.fill"),
        ("questions_orphaned","أسئلة مرتبطة بفصول أو مواد أو صفوف مفقودة","link.badge.plus"),
        ("questions_published_inactive","أسئلة منشورة في مادة أو فصل مخفي","exclamationmark.triangle.fill"),
        ("questions_without_answers","أسئلة اختيار من متعدد دون خيارات","list.bullet.rectangle"),
        ("chapters_orphaned","فصول لا تتبع مادة موجودة","books.vertical.fill"),
        ("french_subjects","مواد فرنسية باقية في قاعدة البيانات","text.book.closed.fill")
    ]}
    var body:some View {
        ScrollView{
            VStack(alignment:.leading,spacing:16){
                ScreenTitle(text:"سلامة المنهج والأسئلة",detail:"فحص مباشر للمواد والفصول والارتباطات")
                Note(state:state)
                GlassBox {
                    VStack(alignment:.leading,spacing:8){
                        Label("فحص للقراءة فقط",systemImage:"checkmark.shield.fill")
                            .foregroundStyle(Ink.gold).font(.headline)
                        Text("تظهر النتائج من قاعدة بيانات الموقع. لا يحذف هذا الفحص أي سؤال أو مادة ولا يصحح روابط تلقائيًا؛ راجع النسخة الاحتياطية قبل أي تغيير.")
                            .font(.footnote).foregroundStyle(Ink.faded)
                    }
                }
                let stats=state.contentHealth["stats"] as? J ?? [:]
                ForEach(checks.indices,id:\.self){index in
                    let item=checks[index]
                    GlassBox{
                        HStack(spacing:12){
                            Image(systemName:item.2).foregroundStyle(Ink.gold).frame(width:30)
                            Text(item.1).font(.subheadline)
                            Spacer()
                            Text(stats[item.0] is NSNull || stats[item.0] == nil ? "غير متاح" : "\(num(stats,item.0))")
                                .font(.headline.monospacedDigit())
                        }
                    }
                }
            }.padding(16)
        }.adminBG().navigationTitle("فحص المنهج").navigationBarTitleDisplayMode(.inline)
            .task{await state.refresh("content_health")}
            .refreshable{await state.refresh("content_health")}
    }
}
