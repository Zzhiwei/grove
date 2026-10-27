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
    private var pendingTags: [String: Bool] = [:] // tag changes not yet uploaded: name → deleted

    // Cloud sync
    let cloud = Cloud()
    @Published private(set) var cloudStatus = ""
    @Published private(set) var syncing = false
    private var syncTicker: AnyCancellable?

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

    private struct Saved: Codable { var tags: [String]; var sessions: [Session]; var pendingTags: [String: Bool]? }

    init() {
        load()
        notifications?.delegate = bannerDelegate
        notifications?.requestAuthorization(options: [.alert]) { _, _ in }
        ticker = Timer.publish(every: 1, on: .main, in: .common).autoconnect().sink { [weak self] _ in
            self?.tick()
        }
        syncTicker = Timer.publish(every: 300, on: .main, in: .common).autoconnect().sink { [weak self] _ in
            self?.scheduleSync()
        }
        scheduleSync()
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

    /// Ends the session and records the focused time if at least 30s (partial pomodoros count too).
    func stop() {
        now = Date()
        let seconds = mode == .pomodoro ? min(elapsed, target) : elapsed
        if let start = sessionStart, seconds >= 30 { // shorter runs are treated as accidental starts
            sessions.append(Session(tag: tag, start: start, seconds: seconds))
            save()
            scheduleSync()
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
        if !tags.contains(name) {
            tags.append(name)
            pendingTags[name] = false
            save()
            scheduleSync()
        }
        tag = name
    }

    func removeTag(_ name: String) {
        tags.removeAll { $0 == name }
        pendingTags[name] = true
        if tag == name { tag = tags.first ?? "Focus" }
        save()
        scheduleSync()
    }

    // MARK: - Cloud sync

    func signIn(email: String, password: String, create: Bool) async {
        cloudStatus = create ? "Creating account…" : "Signing in…"
        do {
            if create, !(try await cloud.signUp(email: email, password: password)) {
                cloudStatus = "Confirm the email we sent, then sign in."
                return
            }
            if !create { try await cloud.signIn(email: email, password: password) }
            for t in tags where pendingTags[t] == nil { pendingTags[t] = false } // upload this Mac's tags
            save()
            await sync()
        } catch {
            cloudStatus = error.localizedDescription
        }
        objectWillChange.send() // cloud.signedIn changed
    }

    func signOut() {
        cloud.signOut()
        // Local data stays; mark it unsent so a later sign-in uploads it again (re-uploads are ignored).
        for i in sessions.indices { sessions[i].synced = false }
        pendingTags = [:]
        save()
        cloudStatus = ""
        objectWillChange.send()
    }

    func scheduleSync() {
        Task { await sync() }
    }

    /// Push unsent sessions and tag changes, then pull everything from other devices.
    func sync() async {
        guard cloud.signedIn, !syncing else { return }
        syncing = true
        defer { syncing = false; objectWillChange.send() }
        do {
            let unsent = sessions.filter { !$0.synced }
            if !unsent.isEmpty {
                try await cloud.insert("sessions", unsent.map(RemoteSession.init), onConflict: "id", resolution: "ignore-duplicates")
                let ids = Set(unsent.map(\.id))
                for i in sessions.indices where ids.contains(sessions[i].id) { sessions[i].synced = true }
                save()
            }
            let tagChanges = pendingTags
            if !tagChanges.isEmpty {
                try await cloud.insert("tags", tagChanges.map { RemoteTag(name: $0.key, deleted: $0.value) },
                                       onConflict: "user_id,name", resolution: "merge-duplicates")
                for (name, deleted) in tagChanges where pendingTags[name] == deleted { pendingTags[name] = nil }
                save()
            }
            let remoteSessions: [RemoteSession] = try await cloud.selectAll("sessions", columns: "id,tag,started_at,seconds")
            let remoteTags: [RemoteTag] = try await cloud.selectAll("tags", columns: "name,deleted")
            sessions = mergeSessions(local: sessions, remote: remoteSessions.map(\.session))
            tags = mergeTags(local: tags, server: remoteTags, pending: pendingTags)
            if !isActive, !tags.contains(tag), let first = tags.first { tag = first }
            save()
            cloudStatus = "Synced \(Date().formatted(date: .omitted, time: .shortened))"
        } catch {
            cloudStatus = "Sync failed: \(error.localizedDescription)"
        }
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
            pendingTags = saved.pendingTags ?? [:]
            tag = tags.first ?? "Focus"
            save() // persists ids given to sessions saved before sync existed
        } catch {
            // Keep the unreadable file instead of overwriting it on next save.
            try? FileManager.default.moveItem(at: url, to: url.appendingPathExtension("corrupt-\(Int(Date().timeIntervalSince1970))"))
        }
    }

    private func save() {
        do {
            try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
            try JSONEncoder().encode(Saved(tags: tags, sessions: sessions, pendingTags: pendingTags)).write(to: url, options: .atomic)
        } catch {
            NSLog("Grove: save failed: \(error)")
        }
    }
}
