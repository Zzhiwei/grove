import Foundation
import GroveCore
import Security

/// Talks to Supabase Auth + Data API over plain HTTPS (no SDK).
@MainActor
final class Cloud {
    static let baseURL = URL(string: "https://ryaqrgzgnhbiybtsogln.supabase.co")!
    // Publishable key is meant to ship in apps; row-level security limits each user to their own rows.
    static let publishableKey = "sb_publishable_Vc0wZNzI9N2ZlL_8IlzoaA_yYvlFN2f"

    struct Failure: LocalizedError {
        let message: String
        let status: Int
        var errorDescription: String? { message }
    }

    private struct AuthResponse: Decodable {
        let access_token: String?
        let refresh_token: String?
        let expires_in: Double?
        let user: User?
        struct User: Decodable { let email: String? }
    }

    private(set) var email = UserDefaults.standard.string(forKey: "cloudEmail")
    private var accessToken: String?
    private var expiresAt = Date.distantPast
    private var refreshToken = Keychain.get() {
        didSet { Keychain.set(refreshToken) }
    }

    var signedIn: Bool { refreshToken != nil }

    /// Returns false when Supabase wants the email confirmed before signing in.
    func signUp(email: String, password: String) async throws -> Bool {
        let r = try await auth("signup", ["email": email, "password": password])
        if r.access_token != nil { adopt(r) }
        return r.access_token != nil
    }

    func signIn(email: String, password: String) async throws {
        adopt(try await auth("token?grant_type=password", ["email": email, "password": password]))
    }

    func signOut() {
        if let token = accessToken {
            var req = request("auth/v1/logout", method: "POST")
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            Task { _ = try? await URLSession.shared.data(for: req) } // best effort; local sign-out happens regardless
        }
        accessToken = nil
        refreshToken = nil
        email = nil
        UserDefaults.standard.removeObject(forKey: "cloudEmail")
    }

    func insert<T: Encodable>(_ table: String, _ rows: [T], onConflict: String, resolution: String) async throws {
        var req = request("rest/v1/\(table)?on_conflict=\(onConflict)", method: "POST")
        req.setValue("resolution=\(resolution),return=minimal", forHTTPHeaderField: "Prefer")
        req.httpBody = try supabaseEncoder.encode(rows)
        _ = try await authorized(req)
    }

    /// Fetches every row the user can see, a page at a time.
    // ponytail: pulls the full table each sync; switch to `created_at=gt.<last>` once histories get large.
    func selectAll<T: Decodable>(_ table: String, columns: String) async throws -> [T] {
        let pageSize = 1000 // Supabase's default max-rows; a lower project setting would end paging early
        var rows: [T] = []
        while true {
            let req = request("rest/v1/\(table)?select=\(columns)&order=\(table == "tags" ? "name" : "id")&limit=\(pageSize)&offset=\(rows.count)")
            let page = try supabaseDecoder.decode([T].self, from: try await authorized(req))
            rows += page
            if page.count < pageSize { return rows }
        }
    }

    // MARK: - Plumbing

    private func request(_ path: String, method: String = "GET") -> URLRequest {
        var req = URLRequest(url: URL(string: path, relativeTo: Self.baseURL)!)
        req.httpMethod = method
        req.timeoutInterval = 20
        req.setValue(Self.publishableKey, forHTTPHeaderField: "apikey")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        return req
    }

    private func auth(_ path: String, _ body: [String: String]) async throws -> AuthResponse {
        var req = request("auth/v1/\(path)", method: "POST")
        req.httpBody = try JSONEncoder().encode(body)
        return try JSONDecoder().decode(AuthResponse.self, from: try await send(req))
    }

    private func adopt(_ r: AuthResponse) {
        accessToken = r.access_token
        expiresAt = Date().addingTimeInterval((r.expires_in ?? 3600) - 60)
        if let refresh = r.refresh_token { refreshToken = refresh } // rotated on every refresh
        if let mail = r.user?.email {
            email = mail
            UserDefaults.standard.set(mail, forKey: "cloudEmail")
        }
    }

    private func authorized(_ req: URLRequest) async throws -> Data {
        if accessToken == nil || Date() >= expiresAt {
            guard let refresh = refreshToken else { throw Failure(message: "Not signed in", status: 401) }
            do {
                adopt(try await auth("token?grant_type=refresh_token", ["refresh_token": refresh]))
            } catch let f as Failure where (400..<500).contains(f.status) {
                signOut() // refresh token revoked or expired
                throw Failure(message: "Session expired, sign in again", status: f.status)
            }
        }
        var req = req
        req.setValue("Bearer \(accessToken ?? "")", forHTTPHeaderField: "Authorization")
        do {
            return try await send(req)
        } catch let f as Failure where f.status == 401 {
            accessToken = nil // force a refresh next sync
            throw f
        }
    }

    private func send(_ req: URLRequest) async throws -> Data {
        let (data, response) = try await URLSession.shared.data(for: req)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            let message = ["msg", "message", "error_description", "error"].lazy.compactMap { json?[$0] as? String }.first
            throw Failure(message: message ?? "HTTP \(status)", status: status)
        }
        return data
    }
}

/// Refresh token storage in the login Keychain.
private enum Keychain {
    private static let query: [CFString: Any] = [
        kSecClass: kSecClassGenericPassword,
        kSecAttrService: "local.grove.focus",
        kSecAttrAccount: "supabase-refresh-token",
    ]

    static func get() -> String? {
        var q = query
        q[kSecReturnData] = true
        var out: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let data = out as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func set(_ value: String?) {
        SecItemDelete(query as CFDictionary)
        guard let value else { return }
        var q = query
        q[kSecValueData] = Data(value.utf8)
        q[kSecAttrAccessible] = kSecAttrAccessibleAfterFirstUnlock
        let status = SecItemAdd(q as CFDictionary, nil)
        if status != errSecSuccess { NSLog("Grove: keychain save failed: \(status)") }
    }
}
