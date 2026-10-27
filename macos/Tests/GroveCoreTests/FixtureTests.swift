import Foundation
import Testing
@testable import GroveCore

// Cases in `shared/fixtures/` are also run by the Android tests.
private func fixture<T: Decodable>(_ name: String) throws -> T {
    let url = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        .appendingPathComponent("../../../shared/fixtures/\(name)")
    return try supabaseDecoder.decode(T.self, from: Data(contentsOf: url))
}

private struct TagCase: Decodable {
    let name: String, local: [String], server: [RemoteTag], pending: [String: Bool], expected: [String]
}

@Test func mergeTagsFixtures() throws {
    for c in try fixture("merge_tags.json") as [TagCase] {
        #expect(mergeTags(local: c.local, server: c.server, pending: c.pending) == c.expected, "\(c.name)")
    }
}

@Test func mergeSessionsFixture() throws {
    struct Case: Decodable { let local: [UUID], remote: [UUID], expected: [UUID] }
    let c: Case = try fixture("merge_sessions.json")
    func sessions(_ ids: [UUID]) -> [Session] { ids.map { Session(id: $0, tag: "T", start: Date(), seconds: 1) } }
    #expect(mergeSessions(local: sessions(c.local), remote: sessions(c.remote)).map(\.id) == c.expected)
}

@Test func supabaseDatesFixture() throws {
    struct Case: Decodable { let input: String, epoch: Double }
    for c in try fixture("supabase_dates.json") as [Case] {
        let date = try supabaseDecoder.decode([Date].self, from: Data("[\"\(c.input)\"]".utf8))[0]
        #expect(abs(date.timeIntervalSince1970 - c.epoch) < 0.001, "\(c.input)")
        let again = try supabaseDecoder.decode([Date].self, from: supabaseEncoder.encode([date]))[0]
        #expect(abs(again.timeIntervalSince(date)) < 0.001)
    }
}

@Test func totalsGroupsFiltersAndSorts() throws {
    struct Total: Decodable, Equatable { let tag: String, seconds: TimeInterval }
    struct Case: Decodable { let since: Date, sessions: [Session], expected: [Total] }
    let c: Case = try fixture("totals.json")
    #expect(totals(c.sessions, since: c.since).map { Total(tag: $0.tag, seconds: $0.seconds) } == c.expected)
}

@Test func formattingFixture() throws {
    struct Text: Decodable { let seconds: TimeInterval, text: String }
    struct Step: Decodable { let minutes: Int, up: Bool, expected: Int }
    struct Case: Decodable { let duration: [Text], total: [Text], step: [Step] }
    let c: Case = try fixture("formatting.json")
    for d in c.duration { #expect(formatDuration(d.seconds) == d.text) }
    for t in c.total { #expect(formatTotal(t.seconds) == t.text) }
    for s in c.step { #expect(stepMinutes(s.minutes, up: s.up) == s.expected, "\(s.minutes) up=\(s.up)") }
}
