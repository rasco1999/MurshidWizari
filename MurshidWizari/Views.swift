import SwiftUI

struct RootView: View {
    @EnvironmentObject var session: SessionStore
    var body: some View {
        Group { if session.isLoggedIn { DashboardView() } else { LoginView() } }
            .tint(Color(red:0.10,green:0.35,blue:0.72))
    }
}

struct LoginView: View {
    @EnvironmentObject var session: SessionStore
    @State private var email=""; @State private var password=""
    var body: some View {
        NavigationStack {
            VStack(spacing:22) {
                Spacer()
                Image(systemName:"graduationcap.fill").font(.system(size:72)).foregroundStyle(.blue)
                Text("منصة المرشد الوزاري").font(.largeTitle.bold())
                Text("رفيقك الذكي نحو النجاح الوزاري").foregroundStyle(.secondary)
                VStack(spacing:14) {
                    TextField("البريد الإلكتروني", text:$email).textInputAutocapitalization(.never).keyboardType(.emailAddress)
                    SecureField("كلمة المرور", text:$password)
                }.textFieldStyle(.roundedBorder)
                if let e=session.errorMessage { Text(e).foregroundStyle(.red).font(.footnote) }
                Button {
                    Task { await session.login(email:email,password:password) }
                } label: {
                    HStack { if session.isBusy { ProgressView() }; Text("تسجيل الدخول").frame(maxWidth:.infinity) }
                }.buttonStyle(.borderedProminent).controlSize(.large).disabled(email.isEmpty || password.isEmpty || session.isBusy)
                Spacer()
                Text("mur-iq.com").font(.footnote).foregroundStyle(.secondary)
            }.padding(24)
        }
    }
}

struct DashboardView: View {
    @EnvironmentObject var session: SessionStore
    @State private var subjects:[Subject]=[]; @State private var loading=true
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment:.leading,spacing:18) {
                    VStack(alignment:.leading,spacing:6) {
                        Text("أهلاً، \(session.studentName)").font(.title.bold())
                        Text("اختر المادة وابدأ الاختبار").foregroundStyle(.secondary)
                    }.frame(maxWidth:.infinity,alignment:.leading)
                    if loading { ProgressView().frame(maxWidth:.infinity).padding(40) }
                    if subjects.isEmpty && !loading {
                        ContentUnavailableView("جاهز للاتصال بالمنصة", systemImage:"network", description:Text("سيتم جلب المواد من حسابك مباشرة من mur-iq.com"))
                    }
                    LazyVGrid(columns:[GridItem(.flexible()),GridItem(.flexible())],spacing:14) {
                        ForEach(subjects) { s in
                            NavigationLink { SubjectView(subject:s) } label: {
                                VStack(spacing:12) {
                                    Image(systemName:"book.closed.fill").font(.title)
                                    Text(s.name).font(.headline).multilineTextAlignment(.center)
                                }.frame(maxWidth:.infinity,minHeight:120).background(.thinMaterial,in:RoundedRectangle(cornerRadius:18))
                            }.buttonStyle(.plain)
                        }
                    }
                    NavigationLink("الاشتراك والدفع") { SubscriptionView() }.buttonStyle(.borderedProminent)
                }.padding()
            }
            .navigationTitle("المرشد الوزاري")
            .toolbar { ToolbarItem(placement:.topBarLeading){ Button("خروج"){session.logout()} } }
            .task {
                defer { loading=false }
                subjects = (try? await session.api.subjects()) ?? []
            }
        }
    }
}

struct SubjectView: View {
    let subject:Subject
    var body: some View {
        List {
            Section("المادة") { Label(subject.name,systemImage:"book.fill") }
            Section("الاختبارات") {
                NavigationLink("ابدأ اختباراً") { ExamPlaceholderView(subject:subject) }
                NavigationLink("مراجعة الأسئلة") { Text("المراجعة متصلة بحسابك على المنصة") }
            }
        }.navigationTitle(subject.name)
    }
}

struct ExamPlaceholderView: View {
    let subject:Subject
    var body: some View {
        VStack(spacing:18) {
            Image(systemName:"checkmark.seal.fill").font(.system(size:60)).foregroundStyle(.green)
            Text(subject.name).font(.title.bold())
            Text("واجهة الاختبار Native بالكامل، ومهيأة للاتصال بمسار mobile/exam.php وإرسال الإجابة فقط عند ضغط زر الإجابة.").multilineTextAlignment(.center)
        }.padding()
    }
}

struct SubscriptionView: View {
    var body: some View {
        List {
            Section("الباقات") {
                Label("شهر واحد — 8,000 د.ع",systemImage:"calendar")
                Label("3 أشهر — 15,000 د.ع",systemImage:"calendar.badge.plus")
            }
            Section("الدفع") {
                Text("زين كاش")
                Text("مصرف الرافدين")
                Text("مصرف الرشيد")
            }
        }.navigationTitle("الاشتراك")
    }
}
