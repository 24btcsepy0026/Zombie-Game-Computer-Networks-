# Zombie Escape - Main Menu

## Overview
The game now features a flashy main menu with:
- **Zombies in all 4 corners** with animated glowing red eyes
- **"ZOMBIE ESCAPE" title** in red with dripping blood effect and pulsing glow
- **Menu Options:**
  - **Start Game** - Connect to a server and play
  - **Invite Members** - Get server IP info to share with friends
  - **Settings** - Configure sound and volume (placeholder)
  - **Exit** - Quit the game

## Features

### Visual Effects
- Dark vignette background
- Animated pulsing glow on title and selected menu items
- Zombie characters with glowing red eyes
- Blood drip effects on title
- Smooth animations

### Controls
- **Keyboard:**
  - ↑/↓ or W/S - Navigate menu
  - ENTER/SPACE - Select option
  - ESC - Jump to Exit option

- **Mouse:**
  - Hover over options to highlight
  - Click to select

## How to Compile and Run

### Option 1: Using Maven (if installed)
```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="com.zombiesurvival.client.GameClient"
```

### Option 2: Using javac directly
```bash
# Compile all files
javac -d out -sourcepath src/main/java src/main/java/com/zombiesurvival/client/GameClient.java

# Run the client
java -cp out com.zombiesurvival.client.GameClient
```

### Option 3: Using your IDE
1. Open the project in your Java IDE (IntelliJ IDEA, Eclipse, etc.)
2. Build the project
3. Run `GameClient.java` main method

## Menu Flow

1. **Main Menu** appears on startup
2. Select **"Start Game"**
   - Enter Server IP (default: localhost)
   - Enter Player Name
   - Connect to server
3. Select **"Invite Members"**
   - View server IP and port to share with friends
4. Select **"Settings"**
   - Configure game settings (placeholder for now)
5. Select **"Exit"**
   - Close the game

## File Structure

New files added:
- `src/main/java/com/zombiesurvival/client/ui/MainMenuScreen.java` - Main menu UI with zombies and animations

Modified files:
- `src/main/java/com/zombiesurvival/client/GameClient.java` - Updated to show menu first

## Customization

You can easily customize the menu by editing `MainMenuScreen.java`:
- Change colors (BLOOD_RED, ZOMBIE_GREEN, etc.)
- Modify animation speeds (pulseAngle increment)
- Add more menu options to the `menuOptions` array
- Adjust zombie appearance in `drawZombieInCorner()` method
- Change title text and effects in `drawTitle()` method

## Notes

- The menu uses Swing animations for smooth effects
- All rendering is done with Java2D for maximum compatibility
- The server must be running before connecting via "Start Game"
- Connection failures will show an error and return to the menu (won't exit the game)
