#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# Generate changelog for a specific plugin using git log filtered by path.
#
# Mirrors the VS Code extension's generateChangelog.js for the JetBrains
# multi-plugin monorepo.
#
# Usage:
#   ./generateChangelog.sh --plugin checkmarx --version 2.3.4 --repo Checkmarx/ast-jetbrains-plugin
#   ./generateChangelog.sh --plugin devassist --version 1.0.0 --repo Checkmarx/ast-jetbrains-plugin --dev true
#
# Outputs:
#   Structured release body section on stdout between RELEASE_BODY_START / RELEASE_BODY_END
# ---------------------------------------------------------------------------

PLUGIN=""
VERSION=""
REPO=""
IS_DEV="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --plugin)   PLUGIN="$2";  shift 2 ;;
    --version)  VERSION="$2"; shift 2 ;;
    --repo)     REPO="$2";    shift 2 ;;
    --dev)      IS_DEV="$2";  shift 2 ;;
    *) echo "Unknown arg: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "$PLUGIN" || -z "$VERSION" || -z "$REPO" ]]; then
  echo "Usage: $0 --plugin checkmarx|devassist --version X.Y.Z --repo owner/repo [--dev true|false]" >&2
  exit 1
fi

case "$PLUGIN" in
  checkmarx)
    DISPLAY_NAME="Checkmarx (AST)"
    GIT_PATHS=("plugin-checkmarx-ast/" "common-lib/")
    ;;
  devassist)
    DISPLAY_NAME="DevAssist"
    GIT_PATHS=("plugin-checkmarx-devassist/" "devassist-lib/" "common-lib/")
    ;;
  *)
    echo "--plugin must be 'checkmarx' or 'devassist'" >&2
    exit 1
    ;;
esac

REPO_URL="https://github.com/${REPO}"

# ---------------------------------------------------------------------------
# Find last stable tag (plain version tags like 2.3.3, no suffix)
# Tags follow the JetBrains convention: plain numbers, no v prefix
# ---------------------------------------------------------------------------
find_last_stable_tag() {
  local all_tags
  all_tags=$(git tag --sort=-creatordate 2>/dev/null || true)

  if [[ -z "$all_tags" ]]; then
    echo ""
    return
  fi

  while IFS= read -r tag; do
    # Match plain version tags (e.g. 2.3.3) -- no suffix like -nightly
    if [[ "$tag" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
      echo "$tag"
      return
    fi
  done <<< "$all_tags"

  # Fallback: check v-prefixed tags (legacy)
  while IFS= read -r tag; do
    if [[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
      echo "$tag"
      return
    fi
  done <<< "$all_tags"

  echo ""
}

LAST_TAG=$(find_last_stable_tag)
if [[ -n "$LAST_TAG" ]]; then
  echo "Found last stable tag: $LAST_TAG" >&2
  RANGE="${LAST_TAG}..HEAD"
else
  echo "No stable tag found, using full history" >&2
  RANGE="HEAD"
fi

# ---------------------------------------------------------------------------
# Patterns to exclude (automation / version bump commits)
# ---------------------------------------------------------------------------
EXCLUDE_REGEX="(update.*version.*automated|bump version|\[create-pull-request\] automated|Merge pull request.*dependabot|^Merge branch)"

# ---------------------------------------------------------------------------
# Collect commits filtered by subproject paths
# ---------------------------------------------------------------------------
PATH_ARGS=""
for p in "${GIT_PATHS[@]}"; do
  PATH_ARGS="${PATH_ARGS} -- ${p}"
done

RAW_LOG=$(git log ${RANGE} --pretty=format:"%H|||%s|||%an" ${PATH_ARGS} 2>/dev/null || true)

# ---------------------------------------------------------------------------
# Categorize commits by conventional commit patterns
# ---------------------------------------------------------------------------
declare -a FEATURES=()
declare -a FIXES=()
declare -a DOCS=()
declare -a REFACTORS=()
declare -a PERFS=()
declare -a OTHERS=()

if [[ -n "$RAW_LOG" ]]; then
  while IFS= read -r line; do
    HASH=$(echo "$line" | awk -F'\\|\\|\\|' '{print $1}')
    MSG=$(echo "$line" | awk -F'\\|\\|\\|' '{print $2}')
    AUTHOR_NAME=$(echo "$line" | awk -F'\\|\\|\\|' '{print $3}')

    [[ -z "$MSG" ]] && continue

    if echo "$MSG" | grep -qiE "$EXCLUDE_REGEX"; then
      continue
    fi

    GH_USER="$AUTHOR_NAME"
    if command -v gh &>/dev/null && [[ -n "$HASH" ]]; then
      RESOLVED=$(gh api "repos/${REPO}/commits/${HASH}" --template '{{.author.login}}' 2>/dev/null || true)
      if [[ -n "$RESOLVED" && "$RESOLVED" != " " ]]; then
        GH_USER="$RESOLVED"
      else
        GH_USER=$(echo "$AUTHOR_NAME" | tr -d ' ')
      fi
    fi

    ENTRY="- ${MSG} by @${GH_USER}"
    MSG_LOWER=$(echo "$MSG" | tr '[:upper:]' '[:lower:]')

    if echo "$MSG" | grep -qE "^feat(\(.+\))?[:\!]" || echo "$MSG_LOWER" | grep -qiE "\b(add|added|adding|new feature|feature|implement|implemented)\b"; then
      FEATURES+=("$ENTRY")
    elif echo "$MSG" | grep -qE "^fix(\(.+\))?[:\!]" || echo "$MSG_LOWER" | grep -qiE "\b(fix|fixed|fixing|fixes|resolve|resolved|bug)\b"; then
      FIXES+=("$ENTRY")
    elif echo "$MSG" | grep -qE "^docs(\(.+\))?[:\!]" || echo "$MSG_LOWER" | grep -qiE "\b(doc|docs|documentation|readme)\b"; then
      DOCS+=("$ENTRY")
    elif echo "$MSG" | grep -qE "^refactor(\(.+\))?[:\!]" || echo "$MSG_LOWER" | grep -qiE "\b(refactor|refactoring|restructure|reorganize)\b"; then
      REFACTORS+=("$ENTRY")
    elif echo "$MSG" | grep -qE "^perf(\(.+\))?[:\!]" || echo "$MSG_LOWER" | grep -qiE "\b(perf|performance|optimize|optimized|optimization)\b"; then
      PERFS+=("$ENTRY")
    else
      OTHERS+=("$ENTRY")
    fi
  done <<< "$RAW_LOG"
fi

# ---------------------------------------------------------------------------
# Build the release body section (write line-by-line for clean markdown)
# ---------------------------------------------------------------------------
emit() { printf '%s\n' "$1"; }

echo "RELEASE_BODY_START"

emit "## ${DISPLAY_NAME}: ${VERSION}"
emit ""
emit "### What's Changed"
emit ""

section_added=false

if [[ ${#FEATURES[@]} -gt 0 ]]; then
  emit "#### New Features"
  emit ""
  for e in "${FEATURES[@]}"; do emit "$e"; done
  emit ""
  section_added=true
fi

if [[ ${#FIXES[@]} -gt 0 ]]; then
  emit "#### Bug Fixes"
  emit ""
  for e in "${FIXES[@]}"; do emit "$e"; done
  emit ""
  section_added=true
fi

if [[ ${#DOCS[@]} -gt 0 ]]; then
  emit "#### Documentation"
  emit ""
  for e in "${DOCS[@]}"; do emit "$e"; done
  emit ""
  section_added=true
fi

if [[ ${#REFACTORS[@]} -gt 0 ]]; then
  emit "#### Refactor"
  emit ""
  for e in "${REFACTORS[@]}"; do emit "$e"; done
  emit ""
  section_added=true
fi

if [[ ${#PERFS[@]} -gt 0 ]]; then
  emit "#### Performance"
  emit ""
  for e in "${PERFS[@]}"; do emit "$e"; done
  emit ""
  section_added=true
fi

if [[ ${#OTHERS[@]} -gt 0 ]]; then
  emit "#### Other Changes"
  emit ""
  for e in "${OTHERS[@]}"; do emit "$e"; done
  emit ""
  section_added=true
fi

if [[ "$section_added" == "false" ]]; then
  emit "_No changes_"
  emit ""
fi

if [[ -n "$LAST_TAG" ]]; then
  emit "**Full Changelog**: ${REPO_URL}/compare/${LAST_TAG}...${VERSION}"
else
  emit "**Full Changelog**: ${REPO_URL}/releases/tag/${VERSION}"
fi

echo "RELEASE_BODY_END"
