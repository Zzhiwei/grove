import AppKit
import Combine
import GroveCore
import UserNotifications

/// Shows banners even though the menu-bar app counts as frontmost.
final class BannerDelegate: NSObject, UNUserNotificationCenterDelegate {
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .list])
    }
}

enum Mode: String, CaseIterable { case pomodoro = "Pomodoro", stopwatch = "Stopwatch" }

@MainActor
final class Model: ObservableObject {
    // Persisted data
    @Published var tags: [String] = ["Focus", "Study", "Reading"]
    @Published var sessions: [Session] = []

    // Timer state (time derived from dates, so sleep/wake stays accurate)
    @Published var mode: Mode = .pomodoro
    @Published var minutes = UserDefaults.standard.object(forKey: "minutes") as? Int ?? 25 {
        didSet { UserDefaults.standard.set(minutes, forKey: "minutes") }
    }
    @Published var tag = "Focus"
    @Published private(set) var runningSince: Date?
    @Published private(set) var accumulated: TimeInterval = 0
    @Published private(set) var now = Date()
    private var sessionStart: Date?
    private var ticker: AnyCancellable?
    private let celebration = NSSound(data: celebrationWAV())
    private let bannerDelegate = BannerDelegate()
    // UNUserNotificationCenter crashes without an app bundle (e.g. `swift run`).
    private let notifications = Bundle.main.bundleIdentifier != nil ? UNUserNotificationCenter.current() : nil

    private let url = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("Grove/data.json")

    private struct Saved: Codable { var tags: [String]; var sessions: [Session] }

    init() {
        load()
        notifications?.delegate = bannerDelegate
        notifications?.requestAuthorization(options: [.alert]) { _, _ in }
        ticker = Timer.publish(every: 1, on: .main, in: .common).autoconnect().sink { [weak self] _ in
            self?.tick()
        }
    }

    var isRunning: Bool { runningSince != nil }
    var isActive: Bool { sessionStart != nil }
    var elapsed: TimeInterval { accumulated + (runningSince.map { now.timeIntervalSince($0) } ?? 0) }
    var target: TimeInterval { TimeInterval(minutes * 60) }
    var display: TimeInterval { mode == .pomodoro ? max(0, target - elapsed) : elapsed }
    /// 0...1 ring fill: pomodoro progress, or progress through the current hour for stopwatch.
    var progress: Double {
        mode == .pomodoro ? min(1, elapsed / target) : elapsed.truncatingRemainder(dividingBy: 3600) / 3600
    }

    func start() {
        now = Date()
        runningSince = now
        if sessionStart == nil { sessionStart = now }
    }

    func pause() {
        accumulated = elapsed
        runningSince = nil
    }

    /// Ends the session and records the focused time (partial pomodoros count too).
    func stop() {
        now = Date()
        let seconds = mode == .pomodoro ? min(elapsed, target) : elapsed
        if let start = sessionStart, seconds >= 1 {
            sessions.append(Session(tag: tag, start: start, seconds: seconds))
            save()
        }
        runningSince = nil
        accumulated = 0
        sessionStart = nil
    }

    private func tick() {
        guard isRunning else { return }
        now = Date()
        if mode == .pomodoro && elapsed >= target {
            let finishedTag = tag
            stop()
            celebrate(tag: finishedTag)
        }
    }

    private func celebrate(tag: String) {
        celebration?.stop()
        celebration?.play()
        let content = UNMutableNotificationContent()
        content.title = "Pomodoro complete 🌳"
        content.body = "\(minutes) min of \(tag) focus. Time for a break."
        notifications?.add(UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil))
    }

    func addTag(_ name: String) {
        let name = name.trimmingCharacters(in: .whitespaces)
        guard !name.isEmpty else { return }
        if !tags.contains(name) { tags.append(name); save() }
        tag = name
    }

    func removeTag(_ name: String) {
        tags.removeAll { $0 == name }
        if tag == name { tag = tags.first ?? "Focus" }
        save()
    }

    func totals(_ range: StatRange) -> [(tag: String, seconds: TimeInterval)] {
        GroveCore.totals(sessions, since: range.start(now: now))
    }

    private func load() {
        guard let data = try? Data(contentsOf: url) else { return }
        do {
            let saved = try JSONDecoder().decode(Saved.self, from: data)
            tags = saved.tags
            sessions = saved.sessions
            tag = tags.first ?? "Focus"
        } catch {
            // Keep the unreadable file instead of overwriting it on next save.
            try? FileManager.default.moveItem(at: url, to: url.appendingPathExtension("corrupt-\(Int(Date().timeIntervalSince1970))"))
        }
    }

    private func save() {
        do {
            try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
            try JSONEncoder().encode(Saved(tags: tags, sessions: sessions)).write(to: url, options: .atomic)
        } catch {
            NSLog("Grove: save failed: \(error)")
        }
    }
}
