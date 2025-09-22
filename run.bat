@echo off
chcp 65001 > nul
set ROOT=%~dp0
java -cp "%ROOT%out" com.example.dungeon.Main
