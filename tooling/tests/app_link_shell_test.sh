#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
manifest="$repo_root/androidApp/src/main/AndroidManifest.xml"
gradle="$repo_root/androidApp/build.gradle.kts"
activity="$repo_root/androidApp/src/main/kotlin/com/ratatoskr/mobile/MainActivity.kt"

fail() {
  printf 'not ok - android_app_link_shell: %s\n' "$1" >&2
  exit 1
}

grep -Fq 'android:autoVerify="true"' "$manifest" || fail 'verified HTTPS intent filter is absent'
# shellcheck disable=SC2016 # The placeholder must remain literal in the manifest.
grep -Fq 'android:host="${ratatoskrLinkHost}"' "$manifest" || fail 'manifest host is not build configured'
grep -Fq 'manifestPlaceholders["ratatoskrLinkHost"]' "$gradle" || fail 'Gradle host placeholder is absent'
grep -Fq 'RATATOSKR_LINK_HOST' "$gradle" || fail 'BuildConfig link host is absent'
grep -Fq 'BuildConfig.RATATOSKR_LINK_HOST' "$activity" || fail 'Activity does not pass the configured host to shared routing'
grep -Fq 'dataString' "$activity" || fail 'Activity does not forward the raw URL string'

printf 'ok - android_app_link_shell\n'
