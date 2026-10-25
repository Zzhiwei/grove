import Foundation

public struct Session: Codable, Equatable {
    public let id: UUID
    public let tag: String
    public let start: Date
    public let seconds: TimeInterval
    public var synced: Bool

    public init(id: UUID = UUID(), tag: String, start: Date, seconds: TimeInterval, synced: Bool = false) {
        self.id = id
        self.tag = tag
        self.start = start
        self.seconds = seconds
        self.synced = synced
    }

    // Files saved before cloud sync have no id/synced.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
        tag = try c.decode(String.self, forKey: .tag)
        start = try c.decode(Date.self, forKey: .start)
        seconds = try c.decode(TimeInterval.self, forKey: .seconds)
        synced = try c.decodeIfPresent(Bool.self, forKey: .synced) ?? false
    }
}

public enum StatRange: String, CaseIterable {
    case today = "Today", week = "7 Days", month = "Month"

    public func start(now: Date = Date(), calendar: Calendar = .current) -> Date {
        let today = calendar.startOfDay(for: now)
        switch self {
        case .today: return today
        case .week: return calendar.date(byAdding: .day, value: -6, to: today)!
        case .month: return calendar.date(byAdding: .month, value: -1, to: today)!
        }
    }
}

/// Total seconds per tag for sessions started on/after `since`, largest first.
// ponytail: a session is counted by its start time; one spanning midnight counts fully for the start day.
public func totals(_ sessions: [Session], since: Date) -> [(tag: String, seconds: TimeInterval)] {
    Dictionary(grouping: sessions.filter { $0.start >= since }, by: \.tag)
        .map { (tag: $0.key, seconds: $0.value.reduce(0) { $0 + $1.seconds }) }
        .sorted { $0.seconds > $1.seconds || ($0.seconds == $1.seconds && $0.tag < $1.tag) }
}

public func formatDuration(_ t: TimeInterval) -> String {
    let s = Int(t.rounded(.down))
    return s >= 3600 ? String(format: "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                     : String(format: "%02d:%02d", s / 60, s % 60)
}

public func formatTotal(_ t: TimeInterval) -> String {
    let m = Int(t) / 60
    return m >= 60 ? "\(m / 60)h \(m % 60)m" : "\(m)m"
}

/// Pomodoro length stepper: snaps to multiples of 5 within 1...180 (1 → 5 → 10, and back 10 → 5 → 1).
public func stepMinutes(_ m: Int, up: Bool) -> Int {
    up ? min(180, (m / 5 + 1) * 5) : max(1, (m - 1) / 5 * 5)
}
