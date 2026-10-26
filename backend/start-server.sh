#!/bin/bash
# Start Task Sync Server on Linux / macOS
cd "$(dirname "$0")"

if [ -f "../.venv/bin/activate" ]; then
    source ../.venv/bin/activate
elif [ -f "venv/bin/activate" ]; then
    source venv/bin/activate
fi

python3 main.py
