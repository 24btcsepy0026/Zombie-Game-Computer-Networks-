@echo off
echo ========================================
echo   ZOMBIE ESCAPE - Game Server
echo ========================================
echo.

REM Check if compiled classes exist
if not exist "out\com\zombiesurvival\server\GameServer.class" (
    echo Compiled classes not found. Compiling...
    echo.
    
    REM Create output directory
    if not exist "out" mkdir out
    
    REM Compile all Java files
    javac -d out -sourcepath src\main\java src\main\java\com\zombiesurvival\server\GameServer.java
    
    if %ERRORLEVEL% NEQ 0 (
        echo.
        echo ERROR: Compilation failed!
        echo Please check for syntax errors.
        pause
        exit /b 1
    )
    
    echo Compilation successful!
    echo.
)

echo Starting game server on port 8080...
echo.
echo Waiting for players to connect...
echo (Press Ctrl+C to stop the server)
echo.
java -cp out com.zombiesurvival.server.GameServer

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Failed to start game server!
    pause
)
