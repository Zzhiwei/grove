import Foundation
import Testing
@testable import GroveCore

@Test func decodesSessionsSavedBeforeSync() throws {
    let legacy = #"[{"tag":"Focus","start":700000000,"seconds":1500}]"#
    let s = try JSONDecoder().decode([Session].self, from: Data(legacy.utf8))
    #expect(s[0].tag == "Focus" && s[0].seconds == 1500 && !s[0].synced)
}
