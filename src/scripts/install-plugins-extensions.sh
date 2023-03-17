#!/usr/bin/env zsh

echo "Installation of plugins and extensions"

pip install --user -r requirements.txt | grep -v "already satisfied" || true
