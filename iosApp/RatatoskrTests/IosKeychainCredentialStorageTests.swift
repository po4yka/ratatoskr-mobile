import Security
import Shared
import XCTest

final class IosKeychainCredentialStorageTests: XCTestCase {
  private let service = "com.ratatoskr.mobile.tests.device-identity"
  private let account = "xcode-simulator-round-trip"
  private lazy var accessGroup = try! discoverAccessGroup()

  override func setUpWithError() throws {
    try storage().clear()
  }

  override func tearDownWithError() throws {
    try storage().clear()
  }

  func testConfiguredGroupRoundTripReplaceAndDelete() throws {
    let first = credentials(accessToken: "access-first", refreshToken: "refresh-first")
    let replacement = credentials(accessToken: "access-next", refreshToken: "refresh-next")
    let storage = storage()

    try storage.save(credentials: first)
    assertCredentials(try storage.load(), equalTo: first)
    try assertDeviceOnlyNonSynchronizingPolicyIsPreserved()

    try storage.save(credentials: replacement)
    assertCredentials(try storage.load(), equalTo: replacement)

    try storage.clear()
    XCTAssertNil(try storage.load())
  }

  func testDeviceOnlyNonSynchronizingPolicyIsPreserved() throws {
    let storage = storage()
    try storage.save(
      credentials: credentials(accessToken: "policy", refreshToken: "policy-refresh"))

    try assertDeviceOnlyNonSynchronizingPolicyIsPreserved()
  }

  func testWrongGroupCannotReadItem() throws {
    let storage = storage()
    try storage.save(
      credentials: credentials(accessToken: "isolated", refreshToken: "isolated-refresh"))
    let wrong = IosKeychainCredentialStorage(
      service: service,
      account: account,
      accessGroup: "(accessGroup).wrong"
    )

    XCTAssertThrowsError(try wrong.load())
  }

  private func storage() -> IosKeychainCredentialStorage {
    IosKeychainCredentialStorage(service: service, account: account, accessGroup: accessGroup)
  }

  private func credentials(accessToken: String, refreshToken: String) -> DeviceCredentials {
    DeviceCredentials(
      origin: "https://platform.example",
      userId: "user-1",
      deviceId: "device-1",
      deviceSecret: "root-secret",
      accessToken: accessToken,
      accessExpiresAt: "2026-08-28T11:00:00Z",
      refreshToken: refreshToken,
      refreshExpiresAt: "2026-09-28T11:00:00Z",
      refreshTokenUsable: true
    )
  }

  private func assertCredentials(
    _ actual: DeviceCredentials?,
    equalTo expected: DeviceCredentials
  ) {
    XCTAssertEqual(actual?.origin, expected.origin)
    XCTAssertEqual(actual?.userId, expected.userId)
    XCTAssertEqual(actual?.deviceId, expected.deviceId)
    XCTAssertEqual(actual?.deviceSecret, expected.deviceSecret)
    XCTAssertEqual(actual?.accessToken, expected.accessToken)
    XCTAssertEqual(actual?.accessExpiresAt, expected.accessExpiresAt)
    XCTAssertEqual(actual?.refreshToken, expected.refreshToken)
    XCTAssertEqual(actual?.refreshExpiresAt, expected.refreshExpiresAt)
  }

  private func assertDeviceOnlyNonSynchronizingPolicyIsPreserved() throws {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: account,
      kSecAttrAccessGroup as String: accessGroup,
      kSecReturnAttributes as String: true,
      kSecMatchLimit as String: kSecMatchLimitOne as String,
    ]
    var item: CFTypeRef?
    XCTAssertEqual(SecItemCopyMatching(query as CFDictionary, &item), errSecSuccess)
    let attributes = try XCTUnwrap(item as? [String: Any])
    XCTAssertEqual(
      attributes[kSecAttrAccessible as String] as? String,
      kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly as String
    )
    XCTAssertEqual(attributes[kSecAttrSynchronizable as String] as? Bool ?? false, false)
  }

  private func discoverAccessGroup() throws -> String {
    let discoveryService = "\(service).access-group-discovery"
    let discoveryAccount = "access-group-discovery"
    let implicit = IosKeychainCredentialStorage(
      service: discoveryService,
      account: discoveryAccount,
      accessGroup: nil
    )
    try implicit.clear()
    defer { try? implicit.clear() }
    try implicit.save(credentials: credentials(accessToken: "discovery", refreshToken: "discovery"))
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: discoveryService,
      kSecAttrAccount as String: discoveryAccount,
      kSecReturnAttributes as String: true,
      kSecMatchLimit as String: kSecMatchLimitOne as String,
    ]
    var item: CFTypeRef?
    guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
      let attributes = item as? [String: Any],
      let group = attributes[kSecAttrAccessGroup as String] as? String
    else {
      throw NSError(domain: "RatatoskrKeychainTests", code: 1)
    }
    return group
  }
}
