import SwiftUI
import GroveCore

extension Color {
    static let forestNight = Color(red: 0.06, green: 0.14, blue: 0.10)
    static let pine = Color(red: 0.11, green: 0.26, blue: 0.17)
    static let moss = Color(red: 0.42, green: 0.62, blue: 0.36)
    static let fern = Color(red: 0.64, green: 0.82, blue: 0.48)
    static let bark = Color(red: 0.55, green: 0.40, blue: 0.27)
    static let mist = Color(red: 0.93, green: 0.92, blue: 0.84)
}

@main
struct GroveApp: App {
    @StateObject private var model = Model()

    var body: some Scene {
        MenuBarExtra {
            ContentView().environmentObject(model)
        } label: {
            Image(systemName: model.isActive ? "tree.fill" : "leaf")
            if model.isActive { Text(formatDuration(model.display)) }
        }
        .menuBarExtraStyle(.window)
    }
}

struct ContentView: View {
    @EnvironmentObject var model: Model
    @State private var range: StatRange = .today
    @State private var newTag = ""
    @State private var showAccount = false
    @State private var email = ""
    @State private var password = ""

    var body: some View {
        VStack(spacing: 14) {
            HStack {
                Label("Grove", systemImage: "leaf.fill").font(.headline).foregroundStyle(Color.fern)
                Spacer()
                Picker("", selection: $model.mode) {
                    ForEach(Mode.allCases, id: \.self) { Text($0.rawValue) }
                }
                .pickerStyle(.segmented).labelsHidden().frame(width: 170)
                .disabled(model.isActive)
            }

            ring

            if model.mode == .pomodoro && !model.isActive {
                Stepper("\(model.minutes) min",
                        onIncrement: { model.minutes = stepMinutes(model.minutes, up: true) },
                        onDecrement: { model.minutes = stepMinutes(model.minutes, up: false) })
                    .foregroundStyle(Color.mist)
            }

            tagRow
            controls

            Divider().overlay(Color.moss.opacity(0.4))
            stats

            Divider().overlay(Color.moss.opacity(0.4))
            account

            HStack {
                Spacer()
                Button("Quit") { NSApp.terminate(nil) }.buttonStyle(.plain).foregroundStyle(Color.mist.opacity(0.6))
            }
        }
        .padding(16)
        .frame(width: 320)
        .background(LinearGradient(colors: [.forestNight, .pine], startPoint: .top, endPoint: .bottom))
        .tint(.moss)
        .environment(\.colorScheme, .dark)
    }

    private var ring: some View {
        ZStack {
            Circle().stroke(Color.bark.opacity(0.35), lineWidth: 10)
            Circle().trim(from: 0, to: model.progress)
                .stroke(Color.fern, style: StrokeStyle(lineWidth: 10, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.linear(duration: 1), value: model.progress)
            VStack(spacing: 4) {
                // The tree grows as the session progresses.
                Image(systemName: model.progress < 0.34 ? "leaf.fill" : model.progress < 0.67 ? "camera.macro" : "tree.fill")
                    .font(.system(size: 26 + 18 * model.progress))
                    .foregroundStyle(Color.moss)
                Text(formatDuration(model.display))
                    .font(.system(size: 32, weight: .light, design: .rounded).monospacedDigit())
                    .foregroundStyle(Color.mist)
                Text(model.tag).font(.caption).foregroundStyle(Color.fern.opacity(0.8))
            }
        }
        .frame(width: 170, height: 170)
        .accessibilityElement(children: .combine)
    }

    private var tagRow: some View {
        HStack {
            Menu {
                ForEach(model.tags, id: \.self) { t in
                    Button(t) { model.tag = t }
                }
                if model.tags.count > 1 {
                    Divider()
                    Menu("Remove Tag") {
                        ForEach(model.tags, id: \.self) { t in Button(t) { model.removeTag(t) } }
                    }
                }
            } label: {
                Label(model.tag, systemImage: "tag")
            }
            .frame(width: 120)
            .disabled(model.isActive)

            TextField("New tag", text: $newTag)
                .textFieldStyle(.roundedBorder)
                .onSubmit { model.addTag(newTag); newTag = "" }
                .disabled(model.isActive)
        }
    }

    private var controls: some View {
        HStack(spacing: 10) {
            Button {
                model.isRunning ? model.pause() : model.start()
            } label: {
                Label(model.isRunning ? "Pause" : model.isActive ? "Resume" : "Start",
                      systemImage: model.isRunning ? "pause.fill" : "play.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)

            Button {
                model.stop()
            } label: {
                Label("Stop & Save", systemImage: "stop.fill").frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(!model.isActive)
        }
        .controlSize(.large)
    }

    private var account: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button {
                showAccount.toggle()
            } label: {
                HStack {
                    Image(systemName: model.cloud.signedIn ? "icloud.fill" : "icloud.slash")
                    Text(model.cloud.signedIn ? (model.cloud.email ?? "Signed in") : "Sync to cloud")
                    Spacer()
                    if model.syncing { ProgressView().controlSize(.mini) }
                    Image(systemName: showAccount ? "chevron.up" : "chevron.down")
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .foregroundStyle(Color.mist)

            if !model.cloudStatus.isEmpty {
                Text(model.cloudStatus).font(.caption2).foregroundStyle(Color.fern.opacity(0.8))
                    .fixedSize(horizontal: false, vertical: true) // wrap long errors instead of truncating
            }

            if showAccount {
                if model.cloud.signedIn {
                    HStack {
                        Button("Sync Now") { model.scheduleSync() }.disabled(model.syncing)
                        Spacer()
                        Button("Sign Out") { model.signOut() }
                    }
                } else {
                    TextField("Email", text: $email).textFieldStyle(.roundedBorder).textContentType(.username)
                    SecureField("Password", text: $password).textFieldStyle(.roundedBorder).textContentType(.password)
                    HStack {
                        Button("Sign In") { submit(create: false) }.buttonStyle(.borderedProminent)
                        Button("Create Account") { submit(create: true) }
                    }
                    .disabled(email.isEmpty || password.count < 6 || model.syncing)
                }
            }
        }
        .font(.caption)
    }

    private func submit(create: Bool) {
        Task {
            await model.signIn(email: email.trimmingCharacters(in: .whitespaces), password: password, create: create)
            if model.cloud.signedIn { password = "" }
        }
    }

    private var stats: some View {
        let rows = model.totals(range)
        let maxSeconds = rows.first?.seconds ?? 1
        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Focus by tag").font(.subheadline.bold()).foregroundStyle(Color.mist)
                Spacer()
                Picker("", selection: $range) {
                    ForEach(StatRange.allCases, id: \.self) { Text($0.rawValue) }
                }
                .pickerStyle(.segmented).labelsHidden().frame(width: 170)
            }
            if rows.isEmpty {
                Text("No focus time yet. Plant a seed.")
                    .font(.caption).foregroundStyle(Color.mist.opacity(0.6))
            }
            ForEach(rows, id: \.tag) { row in
                HStack {
                    Text(row.tag).frame(width: 80, alignment: .leading).lineLimit(1)
                    GeometryReader { g in
                        Capsule().fill(Color.moss)
                            .frame(width: max(4, g.size.width * row.seconds / maxSeconds))
                    }
                    .frame(height: 8)
                    Text(formatTotal(row.seconds)).monospacedDigit().frame(width: 60, alignment: .trailing)
                }
                .font(.caption)
                .foregroundStyle(Color.mist)
            }
            Text("Total \(formatTotal(rows.reduce(0) { $0 + $1.seconds }))")
                .font(.caption2).foregroundStyle(Color.fern.opacity(0.8))
        }
    }
}
