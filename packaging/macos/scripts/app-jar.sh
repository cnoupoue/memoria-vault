#!/usr/bin/env bash

resolve_absolute_path() {
  target="$1"

  if [ -d "$target" ]; then
    (
      cd "$target"
      pwd -P
    )
  else
    (
      cd "$(dirname "$target")"
      printf '%s/%s\n' "$(pwd -P)" "$(basename "$target")"
    )
  fi
}

find_packaged_app_jar() {
  app_bundle="$1"
  app_dir="$app_bundle/Contents/app"
  candidates=()

  if [ ! -d "$app_dir" ]; then
    echo "Packaged application directory is missing." >&2
    return 1
  fi

  while IFS= read -r -d '' candidate; do
    if jar tf "$candidate" 2>/dev/null | awk '$0 == "BOOT-INF/classes/static/index.html" { found = 1 } END { exit found ? 0 : 1 }'; then
      candidates+=("$candidate")
    fi
  done < <(
    find "$app_dir" \
      -maxdepth 1 \
      -type f \
      -name 'memoria-vault*.jar' \
      -print0
  )

  if [ "${#candidates[@]}" -ne 1 ]; then
    echo "Unable to uniquely locate the packaged application JAR." >&2
    return 1
  fi

  resolve_absolute_path "${candidates[0]}"
}

sha256_file() {
  shasum -a 256 "$1" | awk '{print $1}'
}

metadata_value_from_codesign() {
  key="$1"
  file="$2"
  sed -n "s/^${key}=//p" "$file" | head -n 1
}

app_jar_version_from_filename() {
  jar_path="$1"
  artifact_id="$2"
  jar_name="$(basename "$jar_path")"
  version="${jar_name#"$artifact_id"-}"
  version="${version%.jar}"

  if [ "$version" = "$jar_name" ] || [ "$version" = "$artifact_id" ]; then
    return 1
  fi

  printf '%s\n' "$version"
}

assert_source_production_jar() {
  source_jar="$1"
  expected_version="$2"
  artifact_id="${3:-memoria-vault}"

  if [ ! -f "$source_jar" ]; then
    echo "Source production JAR is missing." >&2
    return 1
  fi

  jar_name="$(basename "$source_jar")"
  case "$jar_name" in
    *-sources.jar | *-javadoc.jar | *-tests.jar | *.jar.original)
      echo "Source production JAR is not a release application artifact." >&2
      return 1
      ;;
  esac

  jar_version="$(app_jar_version_from_filename "$source_jar" "$artifact_id")" || {
    echo "Source production JAR version could not be determined." >&2
    return 1
  }

  if [ "$jar_version" != "$expected_version" ]; then
    echo "Source production JAR version does not match the expected Maven version." >&2
    return 1
  fi

  if ! jar tf "$source_jar" 2>/dev/null | awk '$0 == "BOOT-INF/classes/static/index.html" { found = 1 } END { exit found ? 0 : 1 }'; then
    echo "Source production JAR is not the packaged Spring Boot application." >&2
    return 1
  fi
}

assert_packaged_app_jar_matches_build() {
  source_jar="$1"
  packaged_jar="$2"
  expected_version="$3"
  artifact_id="${4:-memoria-vault}"
  baseline_copy="${5:-}"

  assert_source_production_jar "$source_jar" "$expected_version" "$artifact_id" || return 1

  if [ ! -f "$packaged_jar" ]; then
    echo "Packaged application JAR is missing." >&2
    return 1
  fi

  packaged_version="$(app_jar_version_from_filename "$packaged_jar" "$artifact_id")" || {
    echo "Packaged application JAR version could not be determined." >&2
    return 1
  }

  if [ "$packaged_version" != "$expected_version" ]; then
    echo "Packaged application JAR version does not match the expected Maven version." >&2
    return 1
  fi

  source_sha="$(sha256_file "$source_jar")"
  packaged_sha="$(sha256_file "$packaged_jar")"

  if [ "$source_sha" != "$packaged_sha" ]; then
    echo "Packaged application JAR checksum does not match the source production JAR." >&2
    return 1
  fi

  echo "Expected Maven version: $expected_version"
  echo "Source production JAR: $(basename "$source_jar")"
  echo "Source production JAR SHA-256: $source_sha"
  echo "Packaged application JAR: $(basename "$packaged_jar")"
  echo "Packaged application JAR SHA-256: $packaged_sha"
  echo "Packaged application JAR version: $packaged_version"
  echo "Packaging freshness: verified"
  echo "Pristine packaged JAR checksum: matches source production JAR"

  if [ -n "$baseline_copy" ]; then
    mkdir -p "$(dirname "$baseline_copy")"
    cp "$packaged_jar" "$baseline_copy"
  fi
}

find_sqlite_jdbc_entry() {
  app_jar="$1"
  entries_file="${2:-}"

  if [ -z "$entries_file" ]; then
    entries_file="$(mktemp "${TMPDIR:-/tmp}/memoriavault-sqlite-entries.XXXXXX")"
  fi

  jar tf "$app_jar" >"$entries_file" || {
    echo "Packaged application JAR is malformed." >&2
    return 1
  }

  sqlite_count="$(grep -E '^BOOT-INF/lib/sqlite-jdbc-[^/]+\.jar$' "$entries_file" | wc -l | tr -d '[:space:]')"
  if [ "$sqlite_count" != "1" ]; then
    echo "Unable to uniquely locate the nested SQLite JDBC archive." >&2
    return 1
  fi

  grep -E '^BOOT-INF/lib/sqlite-jdbc-[^/]+\.jar$' "$entries_file"
}

jar_entry_sha256() {
  app_jar="$1"
  entry="$2"
  unzip -p "$app_jar" "$entry" | shasum -a 256 | awk '{print $1}'
}

verify_sqlite_native_signature() {
  dylib="$1"
  label="$2"
  expected_identity="$3"
  expected_team="$4"
  meta_file="$5"

  codesign --verify --strict --verbose=2 "$dylib" >/dev/null || {
    echo "SQLite native library signature verification failed." >&2
    return 1
  }
  codesign -dv --verbose=4 "$dylib" >"$meta_file" 2>&1 || {
    echo "Unable to inspect SQLite native library signature metadata." >&2
    return 1
  }

  authorities="$(sed -n 's/^Authority=//p' "$meta_file")"
  team="$(metadata_value_from_codesign TeamIdentifier "$meta_file")"
  timestamp="$(metadata_value_from_codesign Timestamp "$meta_file")"
  flags="$(grep -E '^CodeDirectory .* flags=' "$meta_file" | head -n 1 || true)"

  if ! printf '%s\n' "$authorities" | grep -Fq "$expected_identity"; then
    echo "SQLite native library Developer ID authority mismatch." >&2
    return 1
  fi

  if [ "$team" != "$expected_team" ]; then
    echo "SQLite native library Team Identifier mismatch." >&2
    return 1
  fi

  if [ -z "$timestamp" ]; then
    echo "SQLite native library is missing a secure timestamp." >&2
    return 1
  fi

  if grep -Eq '^Signature=adhoc$|flags=.*adhoc' "$meta_file"; then
    echo "SQLite native library has an ad hoc signature." >&2
    return 1
  fi

  if ! printf '%s\n' "$flags" | grep -Eq 'runtime|0x[0-9a-fA-F]*10000'; then
    echo "SQLite native library is missing Hardened Runtime." >&2
    return 1
  fi

  printf '%s\n' "$label" >/dev/null
}

assert_sqlite_only_postprocessed_app_jar() {
  baseline_jar="$1"
  packaged_jar="$2"
  expected_version="$3"
  artifact_id="${4:-memoria-vault}"
  expected_identity="${5:-}"
  expected_team="${6:-}"

  if [ ! -f "$baseline_jar" ]; then
    echo "Pristine packaged application JAR baseline is missing." >&2
    return 1
  fi

  if [ ! -f "$packaged_jar" ]; then
    echo "Packaged application JAR is missing." >&2
    return 1
  fi

  packaged_version="$(app_jar_version_from_filename "$packaged_jar" "$artifact_id")" || {
    echo "Packaged application JAR version could not be determined." >&2
    return 1
  }

  if [ "$packaged_version" != "$expected_version" ]; then
    echo "Packaged application JAR version does not match the expected Maven version." >&2
    return 1
  fi

  if [ -z "$expected_identity" ]; then
    echo "APPLE_DEVELOPER_ID_APPLICATION is required for post-processed SQLite validation." >&2
    return 2
  fi

  if [ -z "$expected_team" ]; then
    expected_team="$(printf '%s\n' "$expected_identity" | sed -n 's/.*(\([^()]*\)).*/\1/p')"
  fi

  if [ -z "$expected_team" ]; then
    echo "APPLE_TEAM_ID is required, or APPLE_DEVELOPER_ID_APPLICATION must include a Team ID." >&2
    return 2
  fi

  work_dir="$(mktemp -d "${TMPDIR:-/tmp}/memoriavault-postprocessed-jar.XXXXXX")"

  before_entries="$work_dir/before-entries.txt"
  after_entries="$work_dir/after-entries.txt"
  sorted_before="$work_dir/before-sorted.txt"
  sorted_after="$work_dir/after-sorted.txt"

  jar tf "$baseline_jar" >"$before_entries" || {
    echo "Pristine packaged application JAR baseline is malformed." >&2
    return 1
  }
  jar tf "$packaged_jar" >"$after_entries" || {
    echo "Packaged application JAR is malformed." >&2
    return 1
  }

  grep -Fx 'BOOT-INF/classes/static/index.html' "$after_entries" >/dev/null || {
    echo "Packaged application JAR is missing the Spring Boot application marker." >&2
    return 1
  }

  sqlite_entry="$(find_sqlite_jdbc_entry "$packaged_jar" "$after_entries")" || return 1
  before_sqlite_entry="$(find_sqlite_jdbc_entry "$baseline_jar" "$before_entries")" || return 1
  if [ "$sqlite_entry" != "$before_sqlite_entry" ]; then
    echo "Nested SQLite JDBC archive entry changed unexpectedly." >&2
    return 1
  fi

  sort "$before_entries" >"$sorted_before"
  sort "$after_entries" >"$sorted_after"
  if ! cmp -s "$sorted_before" "$sorted_after"; then
    echo "Post-processed application JAR entry list changed unexpectedly." >&2
    return 1
  fi

  unexpected_changes=0
  while IFS= read -r entry; do
    [ -n "$entry" ] || continue
    [ "$entry" != "$sqlite_entry" ] || continue
    before_sha="$(jar_entry_sha256 "$baseline_jar" "$entry")"
    after_sha="$(jar_entry_sha256 "$packaged_jar" "$entry")"
    if [ "$before_sha" != "$after_sha" ]; then
      unexpected_changes=$((unexpected_changes + 1))
    fi
  done <"$before_entries"

  if [ "$unexpected_changes" -ne 0 ]; then
    echo "Post-processed application JAR changed entries outside SQLite JDBC." >&2
    return 1
  fi

  before_sqlite_sha="$(jar_entry_sha256 "$baseline_jar" "$sqlite_entry")"
  after_sqlite_sha="$(jar_entry_sha256 "$packaged_jar" "$sqlite_entry")"
  if [ "$before_sqlite_sha" = "$after_sqlite_sha" ]; then
    echo "Nested SQLite JDBC archive was not modified by post-processing." >&2
    return 1
  fi

  sqlite_outer="$work_dir/sqlite-outer"
  sqlite_extract="$work_dir/sqlite-extract"
  mkdir -p "$sqlite_outer" "$sqlite_extract"
  (
    cd "$sqlite_outer"
    jar xf "$packaged_jar" "$sqlite_entry"
  )
  (
    cd "$sqlite_extract"
    jar xf "$sqlite_outer/$sqlite_entry"
  )

  for arch in aarch64 x86_64; do
    dylib="$sqlite_extract/org/sqlite/native/Mac/$arch/libsqlitejdbc.dylib"
    if [ ! -f "$dylib" ]; then
      echo "Post-processed SQLite native library is missing." >&2
      return 1
    fi
    verify_sqlite_native_signature "$dylib" "$sqlite_entry/org/sqlite/native/Mac/$arch/libsqlitejdbc.dylib" "$expected_identity" "$expected_team" "$work_dir/sqlite-$arch-meta.txt" || return 1
  done

  echo "SQLite post-processing integrity: verified"
  echo "Allowed modified archive entry: $sqlite_entry"
  echo "Unexpected modified entries: 0"
  echo "Post-processed SQLite native libraries: Developer ID verified"
  rm -rf "$work_dir"
}
