@echo off
echo Compiling Java files...
javac -d out -sourcepath src/main/java src/main/java/com/zombiesurvival/client/GameClient.java src/main/java/com/zombiesurvival/client/ui/*.java src/main/java/com/zombiesurvival/shared/*.java
if %ERRORLEVEL% EQU 0 (
    echo Compilation successful!
) else (
    echo Compilation failed!
)
