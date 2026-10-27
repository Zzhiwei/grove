import Foundation
import Testing
@testable import GroveCore

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
