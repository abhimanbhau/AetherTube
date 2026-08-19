#!/usr/bin/env bash
# Fetches pinned upstream commits of SharedModules/MediaServiceCore and applies this repo's own
# patches on top (see patches/README.md for why). Replaces the personal-fork-and-merge approach:
# no separate fork repos, no scheduled sync workflow, no recurring merge conflicts on files that
# permanently diverge from upstream.
#
# Idempotent: skips re-fetching a dependency already at its pinned commit with patches applied,
# so repeated local builds don't re-clone every time. Run this before ./gradlew - it's not a
# Gradle task itself because the fetched directories need to exist before Gradle's settings.gradle
# evaluates the module list.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=../patches/pins.env
source "$REPO_ROOT/patches/pins.env"

fetch_and_patch() {
  local name="$1" url="$2" sha="$3"
  local target="$REPO_ROOT/$name"
  local marker="$target/.patched-$sha"

  if [ -f "$marker" ]; then
    echo "$name already at $sha with patches applied, skipping."
    return
  fi

  echo "Fetching $name @ $sha..."
  rm -rf "$target"
  mkdir -p "$target"
  git -C "$target" init --quiet
  git -C "$target" remote add origin "$url"
  git -C "$target" fetch --quiet --depth 1 origin "$sha"
  git -C "$target" checkout --quiet FETCH_HEAD

  shopt -s nullglob
  for patch in "$REPO_ROOT/patches/$name"/*.patch; do
    echo "Applying $(basename "$patch")..."
    if ! git -C "$target" apply --whitespace=nowarn "$patch"; then
      echo "::error::$(basename "$patch") failed to apply against $name @ $sha - upstream" \
           "likely changed a line this patch touches. See patches/README.md to resolve." >&2
      exit 1
    fi
  done
  shopt -u nullglob

  touch "$marker"
  echo "$name ready."
}

fetch_and_patch SharedModules "$SHARED_MODULES_URL" "$SHARED_MODULES_SHA"
fetch_and_patch MediaServiceCore "$MEDIA_SERVICE_CORE_URL" "$MEDIA_SERVICE_CORE_SHA"
