@echo off
echo Cleaning build directories...
if exist "out" rmdir /s /q "out"
if exist "target" rmdir /s /q "target"
echo Clean complete.
