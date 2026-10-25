import Foundation
import Testing
@testable import GroveCore

@Test func totalsGroupsFiltersAndSorts() {
    let now = Date()
    let old = now.addingTimeInterval(-40 * 86400)
    let sessions = [
        Session(tag: "Work", start: now, seconds: 1500),
        Session(tag: "Read", start: now, seconds: 600),
        Session(tag: "Work", start: now, seconds: 300),
        Session(tag: "Work", start: old, seconds: 9999),
    ]
    let result = totals(sessions, since: StatRange.month.start(now: now))
    #expect(result.map(\.tag) == ["Work", "Read"])
    #expect(result.map(\.seconds) == [1800, 600])
}

@Test func rangeStarts() {
    var cal = Calendar(identifier: .gregorian)
    cal.timeZone = TimeZone(identifier: "UTC")!
    let now = Date(timeIntervalSince1970: 1_700_000_000) // 2023-11-14 22:13 UTC
    #expect(StatRange.today.start(now: now, calendar: cal) == Date(timeIntervalSince1970: 1_699_920_000))
    #expect(StatRange.week.start(now: now, calendar: cal) == Date(timeIntervalSince1970: 1_699_920_000 - 6 * 86400))
}

@Test func celebrationIsValidWAV() {
    let wav = celebrationWAV(sampleRate: 8000)
    #expect(wav.prefix(4) == Data("RIFF".utf8))
    #expect(wav.count == 44 + 2 * (960 * 3 + 1120 + 5600)) // header + 16-bit samples of each note
}

@Test func formatting() {
    #expect(formatDuration(1500) == "25:00")
    #expect(formatDuration(3725) == "1:02:05")
    #expect(formatTotal(5400) == "1h 30m")
    #expect(formatTotal(59) == "0m")
}
