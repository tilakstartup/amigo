#!/usr/bin/env swift
import Foundation
import AppKit
import WebKit

let base = "/Users/tilakputta/projects/apps/healthamigoai"
let imgDir = "\(base)/mobile/ios/Amigo/Images"

struct RenderJob {
    let svgPath: String
    let size: Int
    let outputPath: String
}

let jobs: [RenderJob] = [
    RenderJob(svgPath: "\(base)/mobile/shared/assets/svg/amigo.svg", size: 40, outputPath: "\(imgDir)/amigo_profile.png"),
    RenderJob(svgPath: "\(base)/mobile/shared/assets/svg/amigo.svg", size: 80, outputPath: "\(imgDir)/amigo_profile@2x.png"),
    RenderJob(svgPath: "\(base)/mobile/shared/assets/svg/amigo.svg", size: 120, outputPath: "\(imgDir)/amigo_profile@3x.png"),
    RenderJob(svgPath: "\(base)/mobile/shared/assets/svg/amigo_speech_balloon.svg", size: 25, outputPath: "\(imgDir)/amigo_chat.png"),
    RenderJob(svgPath: "\(base)/mobile/shared/assets/svg/amigo_speech_balloon.svg", size: 50, outputPath: "\(imgDir)/amigo_chat@2x.png"),
    RenderJob(svgPath: "\(base)/mobile/shared/assets/svg/amigo_speech_balloon.svg", size: 75, outputPath: "\(imgDir)/amigo_chat@3x.png"),
]

class Renderer: NSObject, WKNavigationDelegate {
    var jobs: [RenderJob]
    var currentIndex = 0
    var webView: WKWebView!
    let semaphore = DispatchSemaphore(value: 0)

    init(jobs: [RenderJob]) {
        self.jobs = jobs
        super.init()
    }

    func run() {
        processNext()
        semaphore.wait()
    }

    func processNext() {
        guard currentIndex < jobs.count else {
            semaphore.signal()
            return
        }
        let job = jobs[currentIndex]
        let size = CGFloat(job.size)

        // Recreate WKWebView for each job to avoid stale state
        let config = WKWebViewConfiguration()
        webView = WKWebView(frame: NSRect(x: 0, y: 0, width: size, height: size), configuration: config)
        webView.navigationDelegate = self

        // Add to a real window so it renders properly
        let window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: size, height: size),
            styleMask: [.borderless],
            backing: .buffered,
            defer: false
        )
        window.contentView = webView
        window.orderFront(nil)

        let svgData = try! Data(contentsOf: URL(fileURLWithPath: job.svgPath))
        let html = """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=\(Int(size)), initial-scale=1">
        <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        html, body { width: \(Int(size))px; height: \(Int(size))px; overflow: hidden; background: transparent; }
        img { width: \(Int(size))px; height: \(Int(size))px; display: block; }
        </style>
        </head>
        <body>
        <img src="data:image/svg+xml;base64,\(svgData.base64EncodedString())">
        </body>
        </html>
        """
        webView.loadHTMLString(html, baseURL: nil)
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        // Wait a bit for rendering to complete
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            let job = self.jobs[self.currentIndex]
            let size = CGFloat(job.size)

            let config = WKSnapshotConfiguration()
            config.rect = CGRect(x: 0, y: 0, width: size, height: size)

            webView.takeSnapshot(with: config) { image, error in
                if let error = error {
                    print("Snapshot error for \(job.outputPath): \(error)")
                } else if let image = image {
                    // Convert NSImage to PNG
                    let rep = NSBitmapImageRep(data: image.tiffRepresentation!)!
                    if let png = rep.representation(using: .png, properties: [:]) {
                        try! png.write(to: URL(fileURLWithPath: job.outputPath))
                        print("Saved \(Int(size))x\(Int(size)) -> \(job.outputPath)")
                    }
                }
                self.currentIndex += 1
                self.processNext()
            }
        }
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        print("Navigation failed: \(error)")
        currentIndex += 1
        processNext()
    }
}

let app = NSApplication.shared
app.setActivationPolicy(.regular)

let renderer = Renderer(jobs: jobs)
DispatchQueue.main.async {
    renderer.run()
    app.terminate(nil)
}
app.run()
