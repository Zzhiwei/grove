import Foundation
import Testing
@testable import GroveCore

@Test func decodesSessionsSavedBeforeSync() throws {
    let legacy = #"[{"tag":"Focus","start":700000000,"seconds":1500}]"#
    let s = try JSONDecoder().decode([Session].self, from: Data(legacy.utf8))
    #expect(s[0].tag == "Focus" && s[0].seconds == 1500 && !s[0].synced)
}

@Test func mergeSessionsAddsOnlyUnknownIds() {
    let a = Session(tag: "A", start: Date(), seconds: 1)
    let b = Session(tag: "B", start: Date(), seconds: 2, synced: true)
    let merged = mergeSessions(local: [a], remote: [a, b])
    #expect(merged.map(\.id) == [a.id, b.id])
}

@Test func mergeTagsRespectsServerAndPending() {
    let server = [RemoteTag(name: "Gone", deleted: true), RemoteTag(name: "New", deleted: false),
                  RemoteTag(name: "Undo", deleted: false)]
    let merged = mergeTags(local: ["Focus", "Gone", "Mine"], server: server, pending: ["Mine": false, "Undo": true])
    #expect(merged == ["Focus", "Mine", "New"])
}

@Test func supabaseDatesRoundTrip() throws {
    let json = #"[{"id":"6F9619FF-8B86-D011-B42D-00C04FC964FF","tag":"T","started_at":"2026-10-25T10:00:00.123456+00:00","seconds":60},"#
        + #"{"id":"7F9619FF-8B86-D011-B42D-00C04FC964FF","tag":"T","started_at":"2026-10-25T10:00:00+00:00","seconds":60}]"#
    let rows = try supabaseDecoder.decode([RemoteSession].self, from: Data(json.utf8))
    #expect(abs(rows[0].started_at.timeIntervalSince(rows[1].started_at) - 0.123) < 0.001)
    let again = try supabaseDecoder.decode([RemoteSession].self, from: supabaseEncoder.encode(rows))
    #expect(abs(again[0].started_at.timeIntervalSince(rows[0].started_at)) < 0.001)
}
