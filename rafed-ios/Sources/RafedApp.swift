import SwiftUI
import WebKit
import CoreLocation

@main struct RafedApp: App {
    var body: some Scene { WindowGroup { BrowserScreen().preferredColorScheme(.light) } }
}
final class Browser: NSObject, ObservableObject, WKNavigationDelegate, WKUIDelegate, CLLocationManagerDelegate {
    let web: WKWebView
    @Published var errorMessage: String?
    @Published var isLoading = true
    @Published var progress = 0.0
    @Published var canGoBack = false
    @Published var canGoForward = false
    private let locationManager = CLLocationManager()
    @Published var locationCheckInProgress = false
    private var locationCheckID = UUID()
    @Published var locationStatus: CLAuthorizationStatus = .notDetermined
    @Published var locationMessage = "إذن الموقع لم يُطلب بعد"
    private var observations: [NSKeyValueObservation] = []
    private var loadTimeout: DispatchWorkItem?
    override init() {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        web = WKWebView(frame: .zero, configuration: config)
        super.init()
        locationManager.delegate = self
        updateLocationStatus()
        web.overrideUserInterfaceStyle = .light
        web.isOpaque = false
        web.backgroundColor = .white
        web.scrollView.backgroundColor = .white
        web.navigationDelegate = self
        web.uiDelegate = self
        web.allowsBackForwardNavigationGestures = true
        observations = [
            web.observe(\.estimatedProgress, options: [.initial, .new]) { [weak self] view, _ in
                let value = view.estimatedProgress
                DispatchQueue.main.async { self?.progress = value }
            },
            web.observe(\.canGoBack, options: [.initial, .new]) { [weak self] view, _ in
                let value = view.canGoBack
                DispatchQueue.main.async { self?.canGoBack = value }
            },
            web.observe(\.canGoForward, options: [.initial, .new]) { [weak self] view, _ in
                let value = view.canGoForward
                DispatchQueue.main.async { self?.canGoForward = value }
            }
        ]
    }
    func updateLocationStatus() {
        locationStatus = locationManager.authorizationStatus
        switch locationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            locationMessage = locationManager.accuracyAuthorization == .fullAccuracy
                ? "الموقع مسموح — دقة كاملة" : "الموقع مسموح — دقة تقريبية"
        case .denied: locationMessage = "إذن الموقع مرفوض — فعّله من الإعدادات"
        case .restricted: locationMessage = "إذن الموقع مقيد بإعدادات الجهاز"
        case .notDetermined: locationMessage = "السماح بالموقع أثناء استخدام التطبيق"
        @unknown default: locationMessage = "تحقق من إعدادات الموقع"
        }
    }
    private func checkLocationServices(_ completion: @escaping (Bool) -> Void) {
        DispatchQueue.global(qos: .utility).async { [weak self] in
            let enabled = CLLocationManager.locationServicesEnabled()
            DispatchQueue.main.async {
                guard let self = self else { return }
                if !enabled {
                    self.locationMessage = "خدمات الموقع معطلة على الجهاز — فعّلها من الخصوصية والأمان"
                }
                completion(enabled)
            }
        }
    }
    func requestLocationIfNeeded() {
        updateLocationStatus()
        checkLocationServices { [weak self] enabled in
            guard let self = self, enabled else { return }
            if self.locationManager.authorizationStatus == .notDetermined {
                self.locationManager.requestWhenInUseAuthorization()
            }
        }
    }
    func locationButtonTapped() {
        guard !locationCheckInProgress else { return }
        updateLocationStatus()
        checkLocationServices { [weak self] enabled in
            guard let self = self, enabled else { return }
            switch self.locationManager.authorizationStatus {
            case .denied:
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            case .notDetermined:
                self.locationManager.requestWhenInUseAuthorization()
            case .restricted:
                self.locationMessage = "إذن الموقع مقيد بإعدادات الجهاز"
            case .authorizedAlways, .authorizedWhenInUse:
                self.testPageLocation()
            @unknown default: break
            }
        }
    }
    private func testPageLocation() {
        guard let url = web.url, url.scheme == "https", url.host == "www.ibtsm.org",
              !web.isLoading else {
            locationMessage = "انتظر اكتمال فتح صفحة رافد ثم اضغط الموقع للفحص"
            return
        }
        locationCheckInProgress = true
        locationMessage = "جارٍ فحص تحديد الموقع داخل الصفحة…"
        let checkID = UUID()
        locationCheckID = checkID
        let script = """
        return await new Promise(resolve => {
            let settled = false;
            const finish = result => { if (!settled) { settled = true; clearTimeout(timer); resolve(result); } };
            const timer = setTimeout(() => finish({ok:false, code:3}), 25000);
            if (!window.isSecureContext) { finish({ok:false, code:4}); return; }
            if (!navigator.geolocation) { finish({ok:false, code:5}); return; }
            const policy = document.permissionsPolicy || document.featurePolicy;
            if (policy && typeof policy.allowsFeature === 'function' && !policy.allowsFeature('geolocation')) {
                finish({ok:false, code:6}); return;
            }
            navigator.geolocation.getCurrentPosition(
                position => finish({ok:true, accuracy:position.coords.accuracy}),
                error => finish({ok:false, code:error.code}),
                {enableHighAccuracy:true, timeout:15000, maximumAge:0}
            );
        });
        """
        web.callAsyncJavaScript(script, arguments: [:], in: nil, in: .page) { [weak self] result in
            guard let self = self, self.locationCheckID == checkID else { return }
            self.locationCheckInProgress = false
            switch result {
            case .failure:
                self.locationMessage = "تعذر إكمال فحص الصفحة — حدّث الصفحة وأعد المحاولة"
            case .success(let value):
                guard let data = value as? [String: Any] else {
                    self.locationMessage = "لم تُرجع الصفحة نتيجة صالحة للموقع"
                    return
                }
                if data["ok"] as? Bool == true {
                    let accuracy = (data["accuracy"] as? NSNumber)?.doubleValue ?? -1
                    self.locationMessage = accuracy.isFinite && accuracy >= 0
                        ? "نجح تحديد الموقع داخل الصفحة — الدقة نحو " + String(Int(min(accuracy, 1000000).rounded())) + " متر"
                        : "نجح تحديد الموقع داخل الصفحة"
                } else {
                    switch (data["code"] as? NSNumber)?.intValue {
                    case 1: self.locationMessage = "الصفحة رفضت إذن الموقع — تحقق من موافقة الموقع"
                    case 2: self.locationMessage = "الموقع غير متاح حاليًا — تحقق من خدمات الموقع والإشارة"
                    case 3: self.locationMessage = "انتهت مهلة تحديد الموقع — أعد المحاولة"
                    case 4: self.locationMessage = "تحديد الموقع يتطلب صفحة HTTPS آمنة"
                    case 5: self.locationMessage = "تحديد الموقع غير متاح في هذه الصفحة"
                    case 6: self.locationMessage = "سياسة الصفحة تمنع تحديد الموقع"
                    default: self.locationMessage = "تعذر تحديد الموقع داخل الصفحة"
                    }
                }
            }
        }
    }
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        updateLocationStatus()
    }
    func back() { errorMessage = nil; web.goBack() }
    func forward() { errorMessage = nil; web.goForward() }
    func refresh() {
        errorMessage = nil
        if web.url == nil { home() } else { web.reload() }
    }
    func startIfNeeded() { if web.url == nil && !web.isLoading { home() } }
    func home() {
        errorMessage = nil
        web.load(URLRequest(url: URL(string: "https://www.ibtsm.org/rafed/")!, timeoutInterval: 45))
    }
    private func beginLoading() {
        loadTimeout?.cancel()
        errorMessage = nil
        isLoading = true
        let timeout = DispatchWorkItem { [weak self] in
            guard let self = self, self.isLoading else { return }
            self.isLoading = false
            self.errorMessage = "استغرق فتح الموقع وقتًا طويلًا. تحقق من الاتصال ثم أعد المحاولة."
        }
        loadTimeout = timeout
        DispatchQueue.main.asyncAfter(deadline: .now() + 45, execute: timeout)
    }
    private func fail(_ message: String) {
        loadTimeout?.cancel()
        isLoading = false
        errorMessage = message
    }
    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        locationCheckID = UUID()
        locationCheckInProgress = false
        updateLocationStatus()
        beginLoading()
    }
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        loadTimeout?.cancel()
        isLoading = false
        errorMessage = nil
    }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        if (error as NSError).code != NSURLErrorCancelled { fail(error.localizedDescription) }
    }
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        if (error as NSError).code != NSURLErrorCancelled { fail(error.localizedDescription) }
    }
    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        fail("توقف عرض الصفحة. اضغط إعادة المحاولة لفتح الموقع من جديد.")
    }
    func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse, decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
        if navigationResponse.isForMainFrame,
           let response = navigationResponse.response as? HTTPURLResponse,
           response.statusCode >= 500 {
            fail("تعذر فتح الموقع (HTTP \(response.statusCode)). قد تكون هناك حماية وصول أو مشكلة في الخادم.")
            decisionHandler(.cancel)
            return
        }
        decisionHandler(.allow)
    }
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = navigationAction.request.url else { decisionHandler(.cancel); return }
        if ["https", "http", "about"].contains(url.scheme ?? "") { decisionHandler(.allow) }
        else { decisionHandler(.cancel); UIApplication.shared.open(url) }
    }
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if navigationAction.targetFrame == nil { webView.load(navigationAction.request) }
        return nil
    }
    private func presentDialog(_ alert: UIAlertController, fallback: () -> Void) {
        guard var presenter = web.window?.rootViewController else { fallback(); return }
        while let presented = presenter.presentedViewController { presenter = presented }
        guard presenter.viewIfLoaded?.window != nil else { fallback(); return }
        presenter.present(alert, animated: true)
    }
    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
        let alert = UIAlertController(title: "رافد", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "موافق", style: .default) { _ in completionHandler() })
        presentDialog(alert, fallback: completionHandler)
    }
    func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (Bool) -> Void) {
        let alert = UIAlertController(title: "رافد", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "إلغاء", style: .cancel) { _ in completionHandler(false) })
        alert.addAction(UIAlertAction(title: "موافق", style: .default) { _ in completionHandler(true) })
        presentDialog(alert) { completionHandler(false) }
    }
    func webView(_ webView: WKWebView, runJavaScriptTextInputPanelWithPrompt prompt: String, defaultText: String?, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (String?) -> Void) {
        let alert = UIAlertController(title: "رافد", message: prompt, preferredStyle: .alert)
        alert.addTextField { $0.text = defaultText }
        alert.addAction(UIAlertAction(title: "إلغاء", style: .cancel) { _ in completionHandler(nil) })
        alert.addAction(UIAlertAction(title: "موافق", style: .default) { [weak alert] _ in completionHandler(alert?.textFields?.first?.text ?? "") })
        presentDialog(alert) { completionHandler(nil) }
    }

}
struct WebSurface: UIViewRepresentable {
    let browser: Browser
    func makeUIView(context: Context) -> WKWebView { browser.web }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
struct BrowserScreen: View {
    @StateObject private var browser = Browser()
    @Environment(\.scenePhase) private var scenePhase
    private let accent = Color(red: 0.08, green: 0.29, blue: 0.39)
    var body: some View {
        VStack(spacing: 0) {
            ProgressView(value: browser.progress, total: 1)
                .progressViewStyle(.linear)
                .tint(accent)
                .opacity(browser.isLoading ? 1 : 0)
                .frame(height: 3)
                .accessibilityLabel("تقدم تحميل الصفحة")
            ZStack {
                Color.white
                WebSurface(browser: browser)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                if let message = browser.errorMessage {
                    VStack(spacing: 16) {
                        Image(systemName: "exclamationmark.triangle").font(.title)
                        Text(message).multilineTextAlignment(.center)
                        Button("إعادة المحاولة") { browser.refresh() }
                    }
                    .foregroundColor(.black)
                    .padding(24)
                    .background(Color.white)
                    .cornerRadius(16)
                    .shadow(radius: 8)
                    .padding()
                }
            }
            Divider()
            HStack(spacing: 12) {
                Button { browser.locationButtonTapped() } label: {
                    Label(browser.locationCheckInProgress ? "فحص…" : "الموقع", systemImage: "location")
                        .frame(minHeight: 44)
                }
                Text(browser.locationMessage)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
            .disabled(browser.locationCheckInProgress)
            .tint(accent)
            .padding(.horizontal, 16)
            .background(Color.white)
            Divider()
            HStack {
                Button { browser.back() } label: {
                    Label("رجوع", systemImage: "chevron.left")
                        .frame(minWidth: 72, minHeight: 44)
                }.disabled(!browser.canGoBack)
                Spacer(minLength: 8)
                Button { browser.forward() } label: {
                    Label("تقدم", systemImage: "chevron.right")
                        .frame(minWidth: 72, minHeight: 44)
                }.disabled(!browser.canGoForward)
                Spacer(minLength: 8)
                Button { browser.refresh() } label: {
                    Label("تحديث", systemImage: "arrow.clockwise")
                        .frame(minWidth: 72, minHeight: 44)
                }
            }
            .font(.subheadline)
            .tint(accent)
            .padding(.horizontal, 16)
            .padding(.vertical, 4)
            .background(Color.white)
        }
        .background(Color.white.ignoresSafeArea())
        .onAppear {
            browser.startIfNeeded()
            browser.requestLocationIfNeeded()
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active { browser.requestLocationIfNeeded() }
        }
    }
}
