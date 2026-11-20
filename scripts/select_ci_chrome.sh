#!/usr/bin/env bash
set -euo pipefail

# GitHub-hosted images install these browsers with their normal OS integration.
case "${RUNNER_OS:?RUNNER_OS is required}" in
  Linux) chrome_binary=/usr/bin/google-chrome ;;
  macOS) chrome_binary='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' ;;
  *) echo "Unsupported CI browser platform: $RUNNER_OS" >&2; exit 1 ;;
esac

test -x "$chrome_binary"
"$chrome_binary" --version
chrome_profile=$(mktemp -d "${RUNNER_TEMP:?RUNNER_TEMP is required}/shaderx-chrome.XXXXXX")
trap 'rm -rf "$chrome_profile"' EXIT
"$chrome_binary" --headless --disable-gpu --no-first-run --no-default-browser-check \
  --user-data-dir="$chrome_profile" --dump-dom about:blank > "$chrome_profile/smoke.html"
grep -q '<html' "$chrome_profile/smoke.html"
printf 'chrome-path=%s\n' "$chrome_binary" >> "${GITHUB_OUTPUT:?GITHUB_OUTPUT is required}"
