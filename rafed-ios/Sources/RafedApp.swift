import SwiftUI
import WebKit

@main struct RafedApp: App {
    var body: some Scene { WindowGroup { BrowserScreen().preferredColorScheme(.light) } }
}
final class Browser: NSObject, ObservableObject, WKNavigationDelegate, WKUIDelegate {
    let web: WKWebView
    @Published var errorMessage: String?
    @Published var isLoading = true
    private var loadTimeout: DispatchWorkItem?
    override init() {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        web = WKWebView(frame: .zero, configuration: config)
        super.init()
        web.overrideUserInterfaceStyle = .light
        web.isOpaque = false
        web.backgroundColor = .white
        web.scrollView.backgroundColor = .white
        web.navigationDelegate = self
        web.uiDelegate = self
        web.allowsBackForwardNavigationGestures = true
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
    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) { beginLoading() }
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
    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()
            WebSurface(browser: browser)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            if browser.isLoading && browser.errorMessage == nil {
                VStack {
                    ProgressView("جارٍ فتح رافد…")
                        .padding()
                        .background(.regularMaterial)
                        .cornerRadius(12)
                    Spacer()
                }.padding().allowsHitTesting(false)
            }
            if let message = browser.errorMessage {
                VStack(spacing: 16) {
                    Image(systemName: "exclamationmark.triangle").font(.title)
                    Text(message).multilineTextAlignment(.center)
                    Button("إعادة المحاولة") { browser.home() }
                }
                .foregroundColor(.black)
                .padding(24)
                .background(Color.white)
                .cornerRadius(16)
                .shadow(radius: 8)
                .padding()
            }
        }
        .onAppear { browser.startIfNeeded() }
    }
}
