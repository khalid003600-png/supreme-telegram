import SwiftUI
import WebKit

@main struct RafedApp: App {
    var body: some Scene { WindowGroup { BrowserScreen() } }
}
final class Browser: NSObject, ObservableObject, WKNavigationDelegate, WKUIDelegate {
    let web: WKWebView
    @Published var errorMessage: String?
    override init() {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        web = WKWebView(frame: .zero, configuration: config)
        super.init()
        web.navigationDelegate = self
        web.uiDelegate = self
        web.allowsBackForwardNavigationGestures = true
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
        VStack(spacing: 0) {
            HStack {
                Button { browser.web.goBack() } label: { Image(systemName: "chevron.left") }.accessibilityLabel("رجوع")
                Spacer()
                Text("رافد").font(.headline)
                Spacer()
                Button { browser.home() } label: { Image(systemName: "house") }.accessibilityLabel("الرئيسية")
                Button { browser.web.reload() } label: { Image(systemName: "arrow.clockwise") }.accessibilityLabel("تحديث")
                Button { UIApplication.shared.open(URL(string: "https://www.ibtsm.org/rafed/")!) } label: { Image(systemName: "safari") }.accessibilityLabel("فتح في سفاري")
            }.padding()
            if let message = browser.errorMessage {
                VStack { Text(message).multilineTextAlignment(.center); Button("إعادة المحاولة") { browser.home() } }.padding()
            }
            WebSurface(browser: browser)
        }
    }
}
