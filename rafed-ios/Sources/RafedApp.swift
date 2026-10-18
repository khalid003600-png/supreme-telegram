import SwiftUI
import WebKit

@main struct RafedApp: App {
    var body: some Scene { WindowGroup { BrowserScreen().preferredColorScheme(.light) } }
}
final class Browser: NSObject, ObservableObject, WKNavigationDelegate, WKUIDelegate {
    let web: WKWebView
    @Published var errorMessage: String?
    @Published var pageBackground = UIColor.systemBackground
    private var backgroundObservation: NSKeyValueObservation?
    override init() {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        web = WKWebView(frame: .zero, configuration: config)
        super.init()
        web.overrideUserInterfaceStyle = .light
        web.navigationDelegate = self
        web.uiDelegate = self
        web.allowsBackForwardNavigationGestures = true
        backgroundObservation = web.observe(\.underPageBackgroundColor, options: [.initial, .new]) { [weak self] webView, _ in
            DispatchQueue.main.async { self?.pageBackground = webView.underPageBackgroundColor }
        }
        home()
    }
    func home() { web.load(URLRequest(url: URL(string: "https://www.ibtsm.org/rafed/")!)) }
    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) { errorMessage = nil }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        if (error as NSError).code != NSURLErrorCancelled { errorMessage = error.localizedDescription }
    }
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        if (error as NSError).code != NSURLErrorCancelled { errorMessage = error.localizedDescription }
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
            Color(uiColor: browser.pageBackground).ignoresSafeArea()
            WebSurface(browser: browser)
            if let message = browser.errorMessage {
                VStack(spacing: 16) {
                    Text(message).multilineTextAlignment(.center)
                    Button("إعادة المحاولة") { browser.home() }
                }
                .padding(24)
                .background(.regularMaterial)
                .cornerRadius(16)
                .padding()
            }
        }
    }
}
