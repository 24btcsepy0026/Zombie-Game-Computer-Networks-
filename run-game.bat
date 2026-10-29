@echo off
echo ========================================
echo   ZOMBIE ESCAPE - Unified Game
echo ========================================
echo.

REM Check if compiled classes exist or if stale voxel files exist
if not exist "out\com\zombiesurvival\ZombieGame.class" goto do_compile
if exist "out\com\zombiesurvival\client\voxel" goto do_compile
goto run

:do_compile
echo Compiled classes not found or stale. Compiling...
echo.
call compile.bat
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Compilation failed!
    echo Please check for syntax errors.
    pause
    exit /b 1
)
echo.

:run
echo Starting game (server + client)...
echo.
echo - Server runs in background
echo - Client window will open
echo - Just click "Start Game" and play!
echo.

java -cp out com.zombiesurvival.ZombieGame

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Failed to start game!
    pause
)
