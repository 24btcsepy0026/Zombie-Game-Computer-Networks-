# 🎮 New Game Flow - Final Design

## ✅ Flow Overview:

### Screen 1: Main Menu (Zombie Background)
Shows:
- **Start Game** (red, highlighted)
- **Settings**
- **Exit**

Background: Dark with zombies in corners, red "ZOMBIE ESCAPE" title

### Screen 2: Connection Dialog
When you click "Start Game", a dialog appears asking:
- **Server IP:** (e.g., localhost)
- **Player Name:** (your name)

### Screen 3: Waiting Screen (Same Zombie Background)
After entering server details, returns to same background but now shows only:
- **Invite Members** button

You stay on this screen waiting for connection.

### Screen 4: Game Lobby
Once connected to server, automatically transitions to the game lobby showing:
- Connected players
- "Waiting for players... (minimum 2 to start)"
- Chat panel
- HUD

---

## 🎯 Step-by-Step User Experience:

### Step 1: Launch Game
```
┌──────────────────────────────────┐
│  🧟              🧟               │
│                                  │
│     ZOMBIE ESCAPE (red)          │
│                                  │
│   ▶ Start Game                   │
│     Settings                     │
│     Exit                         │
│                                  │
│  🧟              🧟               │
└──────────────────────────────────┘
```

### Step 2: Click "Start Game"
```
Dialog appears:
┌─────────────────────────┐
│ ☣ Connect to Server     │
├─────────────────────────┤
│ Server IP: [localhost]  │
│ Your Name: [Player]     │
│        [OK] [Cancel]    │
└─────────────────────────┘
```

### Step 3: After Clicking OK
```
Back to same background:
┌──────────────────────────────────┐
│  🧟              🧟               │
│                                  │
│     ZOMBIE ESCAPE (red)          │
│                                  │
│   ▶ Invite Members               │
│                                  │
│  (Connecting to server...)       │
│                                  │
│  🧟              🧟               │
└──────────────────────────────────┘
```

### Step 4: Click "Invite Members" (Optional)
```
Dialog shows:
┌─────────────────────────────────┐
│ Invite Friends                  │
├─────────────────────────────────┤
│ Server IP: 192.168.1.100        │
│ Port: 8080                      │
│                                 │
│ Share this with friends!        │
│           [OK]                  │
└─────────────────────────────────┘
```

### Step 5: Auto-Transition to Game
```
When server connection succeeds:
┌──────────────────────────────────┐
│ ZOMBIE SURVIVAL - Game Lobby    │
├──────────────────────────────────┤
│ Connected Players: (1)          │
│   • Player1 (YOU)               │
│                                 │
│ Waiting for players...          │
│ (minimum 2 to start)            │
│                                 │
│ [Game World Preview]            │
└──────────────────────────────────┘
```

---

## 🎨 Visual Design:

### Colors Used:
- **Background:** Dark black/gray (#141419)
- **Title:** Blood red (#B40000) with glow
- **Buttons (normal):** Light gray text
- **Buttons (selected):** Bright red (#DC1414) with pulse
- **Zombies:** Green body, red eyes

### Animations:
- ✅ Pulsing title glow
- ✅ Zombie eye fade
- ✅ Selected button pulse
- ✅ Blood drip animation

---

## 🚀 To Run:

### Terminal 1 - Start Server:
```powershell
.\run-server.bat
```

### Terminal 2 - Start Client:
```powershell
.\compile.bat
.\run-client.bat
```

---

## 📋 Menu Options Explained:

### Main Menu:

**Start Game**
- Opens connection dialog
- Enter server IP and name
- Switches to waiting screen

**Settings**
- Configure game options
- Sound, music, volume
- (Placeholder for now)

**Exit**
- Close the game

### Waiting Screen:

**Invite Members**
- Shows your server IP
- Shows port (8080)
- Share with friends to invite them

---

## ✅ Features:

- ✅ Single consistent background throughout
- ✅ Smooth transitions between screens
- ✅ No jarring window switches
- ✅ Clear progression from menu → waiting → game
- ✅ Beautiful zombie-themed visuals
- ✅ Intuitive flow

---

## 💡 Technical Details:

### MainMenuScreen.java
- Has two modes: normal and waiting
- `setWaitingMode(true)` switches to "Invite Members" only
- Same background, just different menu options

### GameClient.java
- Shows main menu on startup
- "Start Game" opens dialog
- After dialog, switches menu to waiting mode
- On successful connection, closes menu and shows game

---

Enjoy your zombie game! 🧟‍♂️🎮
