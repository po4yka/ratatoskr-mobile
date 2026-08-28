import Security
import Shared
import XCTest

final class IosKeychainCredentialStorageTests: XCTestCase {
  private let service = "com.ratatoskr.mobile.tests.device-identity"
  private let account = "xcode-simulator-round-trip"

  override func setUpWithError() throws {
    try storage().clear()
  }

  override func tearDownWithError() throws {
    try storage().clear()
  }

  func testKeychainRoundTripReplaceAndDelete() throws {
    let first = credentials(accessToken: "access-first", refreshToken: "refresh-first")
    let replacement = credentials(accessToken: "access-next", refreshToken: "refresh-next")
    let storage = storage()

    try storage.save(credentials: first)
    assertCredentials(try storage.load(), equalTo: first)
    try assertDeviceOnlyNonSynchronizingPolicy()

    try storage.save(credentials: replacement)
    assertCredentials(try storage.load(), equalTo: replacement)

    try storage.clear()
    XCTAssertNil(try storage.load())
  }

  private func storage() -> IosKeychainCredentialStorage {
    IosKeychainCredentialStorage(service: service, account: account)
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

  private func assertDeviceOnlyNonSynchronizingPolicy() throws {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: account,
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
}
