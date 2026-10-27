import Foundation

/// Row shape of `public.sessions` in Supabase.
public struct RemoteSession: Codable, Equatable {
    public let id: UUID
    public let tag: String
    public let started_at: Date
    public let seconds: TimeInterval

    public init(_ s: Session) { id = s.id; tag = s.tag; started_at = s.start; seconds = s.seconds }
    public var session: Session { Session(id: id, tag: tag, start: started_at, seconds: seconds, synced: true) }
}

/// Row shape of `public.tags` in Supabase (`deleted` is a tombstone).
public struct RemoteTag: Codable, Equatable {
    public let name: String
    public let deleted: Bool

    public init(name: String, deleted: Bool) { self.name = name; self.deleted = deleted }
}

/// Adds remote sessions missing locally (matched by id). Sessions are append-only, so this never conflicts.
public func mergeSessions(local: [Session], remote: [Session]) -> [Session] {
    let known = Set(local.map(\.id))
    return local + remote.filter { !known.contains($0.id) }
}

/// Server tag state wins, except local changes not uploaded yet (`pending`: name → deleted). Keeps local order.
public func mergeTags(local: [String], server: [RemoteTag], pending: [String: Bool]) -> [String] {
    let deletedOnServer = Set(server.filter(\.deleted).map(\.name))
    var result = local.filter { pending[$0].map { !$0 } ?? !deletedOnServer.contains($0) }
    for t in server where !t.deleted && pending[t.name] != true && !result.contains(t.name) {
        result.append(t.name)
    }
    return result
}

/// PostgREST timestamps look like `2026-10-25T10:00:00.123456+00:00`; the fraction is omitted when zero.
public let supabaseDecoder: JSONDecoder = {
    let d = JSONDecoder()
    d.dateDecodingStrategy = .custom { decoder in
        let s = try decoder.singleValueContainer().decode(String.self)
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = f.date(from: s) { return date }
        f.formatOptions = [.withInternetDateTime]
        if let date = f.date(from: s) { return date }
        throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "Bad date \(s)"))
    }
    return d
}()

public let supabaseEncoder: JSONEncoder = {
    let e = JSONEncoder()
    e.dateEncodingStrategy = .custom { date, encoder in
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        var c = encoder.singleValueContainer()
        try c.encode(f.string(from: date))
    }
    return e
}()
