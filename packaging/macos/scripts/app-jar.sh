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
}
