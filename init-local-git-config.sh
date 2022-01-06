#!/bin/zsh

cd "$(dirname "$0")" || exit 1
for f in git-hooks/*; do
  echo "Add git hook: .git/hooks/$(basename "$f")"
  dst=".git/hooks/$(basename "$f")"
  cp "$f" "$dst"
done
