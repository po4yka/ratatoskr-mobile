#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

fail() {
  printf 'not ok - %s: %s\n' "$1" "$2" >&2
  exit 1
}

assert_file() {
  local test_name="$1"
  local relative_path="$2"
  [[ -f "$repo_root/$relative_path" ]] || fail "$test_name" "missing required foundation file: $relative_path"
}

assert_contains() {
  local test_name="$1"
  local relative_path="$2"
  local literal="$3"
  grep -Fq "$literal" "$repo_root/$relative_path" || fail "$test_name" "$relative_path does not contain: $literal"
}

android_shell_hosts_shared_compose() {
  local test_name="android_shell_hosts_shared_compose"

  assert_file "$test_name" "settings.gradle.kts"
  assert_file "$test_name" "gradle/libs.versions.toml"
  assert_file "$test_name" "build-logic/settings.gradle.kts"
  assert_file "$test_name" "shared/src/commonMain/kotlin/com/ratatoskr/mobile/App.kt"
  assert_file "$test_name" "androidApp/src/main/kotlin/com/ratatoskr/mobile/MainActivity.kt"
  assert_contains "$test_name" "settings.gradle.kts" 'include(":shared", ":androidApp")'
  assert_contains "$test_name" "androidApp/src/main/kotlin/com/ratatoskr/mobile/MainActivity.kt" "RatatoskrApp()"

  printf 'ok - %s\n' "$test_name"
}

ios_shell_hosts_shared_compose() {
  local test_name="ios_shell_hosts_shared_compose"

  assert_file "$test_name" "shared/src/iosMain/kotlin/com/ratatoskr/mobile/MainViewController.kt"
  assert_file "$test_name" "iosApp/Ratatoskr/App/RatatoskrApp.swift"
  assert_file "$test_name" "iosApp/Ratatoskr/App/ComposeRootView.swift"
  assert_file "$test_name" "iosApp/Ratatoskr.xcodeproj/project.pbxproj"
  assert_file "$test_name" "iosApp/Ratatoskr.xcodeproj/xcshareddata/xcschemes/Ratatoskr.xcscheme"
  assert_contains "$test_name" "shared/src/iosMain/kotlin/com/ratatoskr/mobile/MainViewController.kt" "ComposeUIViewController"
  assert_contains "$test_name" "iosApp/Ratatoskr/App/ComposeRootView.swift" "MainViewControllerKt.MainViewController()"
  assert_contains "$test_name" "iosApp/Ratatoskr.xcodeproj/project.pbxproj" "embedAndSignAppleFrameworkForXcode"

  printf 'ok - %s\n' "$test_name"
}

android_shell_hosts_shared_compose
ios_shell_hosts_shared_compose
