import SwiftUI
import CoreLocation

@main struct HudoorApp: App {
    var body: some Scene { WindowGroup { Home().environment(\.layoutDirection, .rightToLeft) } }
}
@MainActor final class Location: NSObject, ObservableObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    @Published var fix: CLLocation?
    @Published var error = ""
    override init() { super.init(); manager.delegate = self; manager.desiredAccuracy = kCLLocationAccuracyBest }
    func request() {
        if manager.authorizationStatus == .notDetermined { manager.requestWhenInUseAuthorization() }
        else if manager.authorizationStatus == .denied || manager.authorizationStatus == .restricted { error = "اسمح بالوصول للموقع من الإعدادات" }
        else { manager.requestLocation() }
    }
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager.authorizationStatus == .authorizedWhenInUse { manager.requestLocation() }
    }
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) { fix = locations.last; error = "" }
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) { self.error = error.localizedDescription }
}
struct Home: View {
    @AppStorage("server") private var server = ""
    @State private var email = ""
    @State private var password = ""
    @State private var token = ""
    @State private var role = ""
    @State private var output = ""
    @State private var busy = false
    @StateObject private var location = Location()
    var body: some View {
        NavigationStack {
            Form {
                if token.isEmpty {
                    Section("اتصال آمن بالخادم") { TextField("https://your-domain", text: $server).keyboardType(.URL).textInputAutocapitalization(.never) }
                    Section("تسجيل الدخول") {
                        TextField("البريد الإلكتروني", text: $email).textInputAutocapitalization(.never).keyboardType(.emailAddress)
                        SecureField("كلمة المرور", text: $password)
                        Button("دخول") { perform("/login", ["email":email,"password":password]) }
                    }
                } else {
                    if role == "employee" {
                        Section("الحضور والانصراف") {
                            Button("تحديث موقعي") { location.request() }
                            if let fix = location.fix { Text("دقة الموقع: \(Int(max(0,fix.horizontalAccuracy))) متر") }
                            Text(location.error).foregroundStyle(.red)
                            Button("تسجيل حضور") { attend("in") }
                            Button("تسجيل انصراف") { attend("out") }
                            Button("سجلي") { perform("/attendance") }
                            Button("موقع العمل") { perform("/worksite") }
                        }
                    }
                    Button("تسجيل الخروج",role:.destructive) { perform("/logout",[:]); token=""; role=""; output="" }
                }
                if busy { ProgressView() }
                if !output.isEmpty { Section("النتيجة") { Text(output).textSelection(.enabled) } }
            }.navigationTitle("حضوري").disabled(busy).tint(.teal)
        }
    }
    func attend(_ kind:String) {
        guard let fix=location.fix, abs(fix.timestamp.timeIntervalSinceNow)<60, fix.horizontalAccuracy>0, fix.horizontalAccuracy<=50 else { output="حدّث الموقع وانتظر دقة أفضل من 50 مترًا"; return }
        perform("/attendance",["kind":kind,"lat":fix.coordinate.latitude,"lon":fix.coordinate.longitude,"accuracy":fix.horizontalAccuracy,"timestamp":fix.timestamp.timeIntervalSince1970])
    }
    func perform(_ path:String,_ data:[String:Any]?=nil) {
        let endpoint = server.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard let url=URL(string:endpoint+path), url.scheme == "https", url.host != nil else { output="أدخل عنوان خادم HTTPS صالحًا"; return }
        let bearer=token
        busy=true
        Task { @MainActor in
            defer { busy=false }
            do {
                var req=URLRequest(url:url); req.timeoutInterval=20
                req.httpMethod=data == nil ? "GET":"POST"
                req.setValue("application/json",forHTTPHeaderField:"Content-Type")
                req.setValue("Bearer "+bearer,forHTTPHeaderField:"Authorization")
                if let data { req.httpBody=try JSONSerialization.data(withJSONObject:data) }
                let (body,response)=try await URLSession.shared.data(for:req)
                let object=try JSONSerialization.jsonObject(with:body)
                guard let http=response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                    output=(object as? [String:Any])?["error"] as? String ?? "تعذر تنفيذ الطلب"; return
                }
                if path == "/login",let v=object as? [String:String] {
                    guard v["role"] == "employee" else {
                        var logout=URLRequest(url:URL(string:endpoint+"/logout")!)
                        logout.httpMethod="POST"; logout.httpBody=Data("{}".utf8)
                        logout.setValue("Bearer "+(v["token"] ?? ""),forHTTPHeaderField:"Authorization")
                        _ = try? await URLSession.shared.data(for:logout)
                        password=""; output="تطبيق حضوري للموظفين. افتح لوحة الشركة من المتصفح عبر مسار /company على خادمك."; return
                    }
                    token=v["token"] ?? ""; role="employee"; password=""; output="مرحبًا \(v["name"] ?? "")"
                }
                else { let pretty=try JSONSerialization.data(withJSONObject:object,options:[.prettyPrinted,.sortedKeys,.withoutEscapingSlashes]); output=String(decoding:pretty,as:UTF8.self) }
            } catch { output=error.localizedDescription }
        }
    }
}
