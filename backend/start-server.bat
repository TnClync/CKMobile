@echo off
echo Starting Task Sync Server...
cd /d "%~dp0"
call venv\Scripts\activate.bat
python main.py
