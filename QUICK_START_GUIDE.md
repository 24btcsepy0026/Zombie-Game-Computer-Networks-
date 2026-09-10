# 🧟 ZOMBIE ESCAPE - Quick Start Guide

## ⚡ Fastest Way to Play

### Step 1: Start the Server
Double-click: **`run-server.bat`**
```
You should see:
"Starting game server on port 8080..."
"Waiting for players to connect..."
```

### Step 2: Start the Client
Double-click: **`run-client.bat`**

The flashy menu appears! 🎮

### Step 3: Connect and Play
1. Press **↓** or **S** to highlight "Start Game"
2. Press **ENTER**
3. Leave "Server IP" as **localhost**
4. Enter your name (or keep default)
5. Click **OK**

### Step 4: Add More Players (Optional)
- Open another terminal/command prompt
- Run `run-client.bat` again
- Or double-click it again

**Game starts when 2+ players connect!**

---

## 🎮 Main Menu Navigation

### Keyboard Controls:
- **↑/W** - Move up
- **↓/S** - Move down  
- **ENTER** - Select
- **ESC** - Quick jump to Exit

### Mouse Controls:
- **Hover** - Highlight option
- **Click** - Select option

---

## 📋 Menu Options Explained

### 🎯 Start Game
Connects you to a game server:
- Enter **Server IP** (default: localhost for same computer)
- Enter **Your Name** (shows in-game)
- Click OK to connect

**For LAN play:** Get server IP from "Invite Members" menu

### 👥 Invite Members
Shows server connection information:
- **Server IP** - Share this with friends
- **Port** - Usually 8080
- Friends use this info when they click "Start Game"

### ⚙️ Settings
Configure game options:
- Enable/disable sound
- Enable/disable music
- Adjust volume
- *(Currently a placeholder)*

### 🚪 Exit
Closes the game

---

## 🌐 Playing Over Network (LAN)

### On the Host Computer (Server):
1. Run `run-server.bat`
2. Run `run-client.bat`
3. Select **"Invite Members"** from menu
4. **Share the IP address shown** with friends

### On Friend Computers (Clients):
1. Run `run-client.bat`
2. Select **"Start Game"**
3. Enter the **Host's IP address**
4. Enter your name
5. Click OK

---

## 🎮 In-Game Controls

| Key | Action |
|-----|--------|
| **W** or **↑** | Move Up |
| **S** or **↓** | Move Down |
| **A** or **←** | Move Left |
| **D** or **→** | Move Right |

**Chat:** Click in chat box, type message, press ENTER

---

## 🏆 Game Rules

### Roles:
- **🧟 Zombie** (1 player) - Chase and infect survivors
- **🧍 Survivors** (all others) - Stay alive for 120 seconds

### Objectives:
- **Zombies:** Infect all survivors by contact
- **Survivors:** Stay alive until timer reaches 0:00

### Items:
- **❤️ Medkit** - Restores 40 HP, +10 score
- **🔑 Key** - +50 score

### Special Zones:
- **💚 Safe Zone** (light green)
  - Heals survivors +2 HP/sec
  - Slows zombies by 50%

### Scoring:
- Collect items for points
- Survive to win
- High score wins!

---

## ❓ Troubleshooting

### "Connection Failed" error?
- ✅ Make sure server is running first
- ✅ Check Server IP is correct
- ✅ Try "localhost" if on same computer
- ✅ Check firewall isn't blocking port 8080

### Menu doesn't appear?
- ✅ Make sure Java 17+ is installed
- ✅ Try compiling manually (see below)
- ✅ Check for error messages in console

### Game won't start?
- ✅ Need **2 or more players** to start
- ✅ Wait for other players to connect
- ✅ Check server console for connection messages

### Compilation errors?
```bash
# Manual compile:
javac -d out -sourcepath src\main\java src\main\java\com\zombiesurvival\client\GameClient.java

# Manual run:
java -cp out com.zombiesurvival.client.GameClient
```

---

## 🎨 Menu Features

The new main menu includes:
- ✨ Animated zombies in all 4 corners
- 🩸 Blood-red glowing title
- 💫 Smooth pulsing animations
- 🎯 Intuitive navigation
- 🖱️ Mouse and keyboard support

**Enjoy the atmosphere!**

---

## 📚 Additional Documentation

- **MENU_INSTRUCTIONS.md** - Detailed menu system documentation
- **MENU_PREVIEW.md** - ASCII art preview of the menu
- **CHANGES_SUMMARY.md** - Technical implementation details
- **ZOMBIE_DESIGN.md** - Zombie character design specs
- **README.md** - Full project documentation

---

## 🎮 Ready to Play?

1. **Run server:** `run-server.bat`
2. **Run client:** `run-client.bat` (x2 for testing)
3. **Start Game** from menu
4. **Survive!** 🧟‍♂️

**Have fun and good luck surviving the zombie apocalypse!** 💀
