import Foundation

/// A short, happy bell arpeggio (C5–E5–G5–C6, then a C6+E6 chord) as 16-bit mono WAV data.
public func celebrationWAV(sampleRate: Int = 44_100) -> Data {
    let notes: [(freqs: [Double], length: Double)] = [
        ([523.25], 0.12), ([659.25], 0.12), ([783.99], 0.12), ([1046.50], 0.14), ([1046.50, 1318.51], 0.7),
    ]
    var samples: [Int16] = []
    for note in notes {
        let count = Int((note.length * Double(sampleRate)).rounded())
        for i in 0..<count {
            let t = Double(i) / Double(sampleRate)
            let envelope = min(1, t / 0.005) * exp(-t * 5) // quick attack, bell-like decay
            let tone = note.freqs.reduce(0.0) { sum, f in
                sum + sin(2 * .pi * f * t) + 0.3 * sin(4 * .pi * f * t) // fundamental + soft overtone
            } / Double(note.freqs.count)
            samples.append(Int16(max(-1, min(1, 0.45 * envelope * tone)) * Double(Int16.max)))
        }
    }

    var data = Data()
    func put<T: FixedWidthInteger>(_ v: T) { withUnsafeBytes(of: v.littleEndian) { data.append(contentsOf: $0) } }
    let byteCount = samples.count * 2
    data.append(contentsOf: Array("RIFF".utf8)); put(UInt32(36 + byteCount))
    data.append(contentsOf: Array("WAVEfmt ".utf8)); put(UInt32(16)); put(UInt16(1)); put(UInt16(1))
    put(UInt32(sampleRate)); put(UInt32(sampleRate * 2)); put(UInt16(2)); put(UInt16(16))
    data.append(contentsOf: Array("data".utf8)); put(UInt32(byteCount))
    samples.forEach(put)
    return data
}
