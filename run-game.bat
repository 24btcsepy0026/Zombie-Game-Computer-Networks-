@echo off
echo ========================================
echo   ZOMBIE ESCAPE - Unified Game
echo ========================================
echo.

REM Check if compiled classes exist
if not exist "out\com\zombiesurvival\ZombieGame.class" (
    echo Compiled classes not found. Compiling...
    echo.
    
    REM Create output directory
    if not exist "out" mkdir out
    
    REM Compile all Java files
    javac -cp "lib/*" -d out -sourcepath src\main\java src\main\java\com\zombiesurvival\ZombieGame.java src\main\java\com\zombiesurvival\client\GameClient.java src\main\java\com\zombiesurvival\server\GameServer.java src\main\java\com\zombiesurvival\client\ui\*.java src\main\java\com\zombiesurvival\client\voxel\*.java src\main\java\com\zombiesurvival\shared\*.java src\main\java\com\zombiesurvival\server\*.java src\main\java\com\zombiesurvival\client\*.java
    
    if %ERRORLEVEL% NEQ 0 (
        echo.
        echo ERROR: Compilation failed!
        echo Please check for syntax errors.
        pause
        exit /b 1
    )
    
    echo Compilation successful!
    if not exist "out\fonts" mkdir out\fonts
    copy /Y "resources\fonts\*" "out\fonts\" >nul 2>&1
    echo.
)

echo Starting game (server + client)...
echo.
echo - Server runs in background
echo - Client window will open
echo - Just click "Start Game" and play!
echo.

java -cp "out;lib/*" com.zombiesurvival.ZombieGame

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Failed to start game!
    pause
)
