@echo off
echo ========================================
echo   ZOMBIE ESCAPE - Game Client
echo ========================================
echo.

REM Check if compiled classes exist
if not exist "out\com\zombiesurvival\client\GameClient.class" (
    echo Compiled classes not found. Compiling...
    echo.
    
    REM Create output directory
    if not exist "out" mkdir out
    
    REM Compile all Java files
    javac -d out -sourcepath src\main\java src\main\java\com\zombiesurvival\client\GameClient.java
    
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

echo Starting game client...
echo.
java -cp out com.zombiesurvival.client.GameClient

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Failed to start game client!
    pause
)
