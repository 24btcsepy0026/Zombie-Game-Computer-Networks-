@echo off
echo Compiling Java files (2D)...
if exist "out\com\zombiesurvival\client\voxel" rmdir /s /q "out"
if not exist "out" mkdir out
javac -d out -sourcepath src/main/java src/main/java/com/zombiesurvival/ZombieGame.java src/main/java/com/zombiesurvival/client/GameClient.java src/main/java/com/zombiesurvival/client/ui/*.java src/main/java/com/zombiesurvival/shared/*.java src/main/java/com/zombiesurvival/server/*.java
if %ERRORLEVEL% EQU 0 (
    if not exist "out\fonts" mkdir out\fonts
    copy /Y "resources\fonts\*" "out\fonts\" >nul 2>&1
    echo Compilation successful!
) else (
    echo Compilation failed!
)
