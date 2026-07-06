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
