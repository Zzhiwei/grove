// Draws the 1024px app icon (progress ring + tree, forest palette). Run by build.sh: swift icon.swift out.png
import AppKit

func rgb(_ r: CGFloat, _ g: CGFloat, _ b: CGFloat) -> NSColor { NSColor(srgbRed: r, green: g, blue: b, alpha: 1) }
let forestNight = rgb(0.06, 0.14, 0.10), pine = rgb(0.11, 0.26, 0.17), moss = rgb(0.42, 0.62, 0.36)
let fern = rgb(0.64, 0.82, 0.48), bark = rgb(0.55, 0.40, 0.27) // keep in sync with GroveApp.swift

let size = 1024
let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: size, pixelsHigh: size, bitsPerSample: 8,
                           samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB,
                           bytesPerRow: 0, bitsPerPixel: 0)!
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)

// macOS icon grid: 824pt tile centered on the 1024 canvas, with a soft drop shadow.
let tile = NSRect(x: 100, y: 100, width: 824, height: 824)
let tilePath = NSBezierPath(roundedRect: tile, xRadius: 185, yRadius: 185)
NSGraphicsContext.saveGraphicsState()
let shadow = NSShadow()
shadow.shadowColor = NSColor.black.withAlphaComponent(0.35)
shadow.shadowOffset = NSSize(width: 0, height: -12)
shadow.shadowBlurRadius = 24
shadow.set()
pine.setFill()
tilePath.fill()
NSGraphicsContext.restoreGraphicsState()
NSGradient(starting: pine, ending: forestNight)!.draw(in: tilePath, angle: -90)

// Ring: bark track, fern arc ~3/4 full clockwise from 12 o'clock.
let center = NSPoint(x: 512, y: 512), radius: CGFloat = 270, width: CGFloat = 56
let track = NSBezierPath()
track.appendArc(withCenter: center, radius: radius, startAngle: 0, endAngle: 360)
track.lineWidth = width
bark.withAlphaComponent(0.35).setStroke()
track.stroke()
let arc = NSBezierPath()
arc.appendArc(withCenter: center, radius: radius, startAngle: 90, endAngle: 90 - 270, clockwise: true)
arc.lineWidth = width
arc.lineCapStyle = .round
fern.setStroke()
arc.stroke()

// Tree: SF Symbol tinted moss, centered in the ring.
let config = NSImage.SymbolConfiguration(pointSize: 300, weight: .regular)
    .applying(NSImage.SymbolConfiguration(paletteColors: [moss]))
let tree = NSImage(systemSymbolName: "tree.fill", accessibilityDescription: nil)!.withSymbolConfiguration(config)!
tree.draw(in: NSRect(x: center.x - tree.size.width / 2, y: center.y - tree.size.height / 2,
                     width: tree.size.width, height: tree.size.height))

NSGraphicsContext.current = nil
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
