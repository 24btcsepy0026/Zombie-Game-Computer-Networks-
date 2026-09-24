# 🎮 Single Player Mode - Final Setup

## ✅ Game Configuration:

### Player Requirements:
- **Minimum:** 1 player (you!)
- **Maximum:** Unlimited
- **Mode:** Single-player or multiplayer (your choice)

### Gameplay:
- ✅ **You vs AI Zombies** (solo mode)
- ✅ **You + Friends vs AI Zombies** (co-op mode)
- ✅ All players are survivors
- ✅ 3-7 random AI zombies spawn
- ✅ Survive 120 seconds to win!

---

## 🎮 How to Play Solo:

### Step 1: Start Server
```powershell
.\run-server.bat
```

### Step 2: Start Client (Just You!)
```powershell
.\compile.bat
.\run-client.bat
```

### Step 3: Navigate Menu
1. **Main Menu** → Click "Start Game"
2. **Enter Details:**
   - Server IP: `localhost`
   - Your Name: `abhinav`
3. **Waiting Screen** → Click "Go to Lobby"

### Step 4: Game Starts Immediately!
- No waiting for other players
- 3-7 AI zombies spawn
- You vs the zombie horde!
- Survive 120 seconds = Victory! 🏆

---

## 🎮 How to Play Co-op (Optional):

### Your Computer (Host):
```powershell
Terminal 1: .\run-server.bat
Terminal 2: .\run-client.bat
```

### Friend's Computer:
```powershell
.\run-client.bat
```
Connect to: **Your IP address**

### Both click "Go to Lobby" → Team up against zombies!

---

## 🧟 What Happens in Game:

### Solo Play:
```
=== GAME STARTED! Survive against 5 zombies! ===

You (blue survivor) vs 5 AI zombies (green)
⏱ Timer: 2:00
❤ Health: 100%
🎯 Collect medkits and keys
🏃 Run from zombie horde!
```

### Victory Condition:
- Survive until timer hits 0:00
- Don't let zombies kill you!

### Defeat Condition:
- Your health reaches 0
- Zombies win 😢

---

## 🎯 Game Flow:

```
Launch Client
    ↓
Main Menu
(Start Game, Settings, Exit)
    ↓
Connection Dialog
(localhost, Your Name)
    ↓
Waiting Screen
(Go to Lobby, Invite Members)
Shows: "Connected Players: 1"
       • abhinav (YOU)
    ↓
Click "Go to Lobby"
    ↓
Game Starts Immediately!
(No waiting for other players)
    ↓
3-7 AI Zombies Spawn
    ↓
Survive 120 Seconds!
    ↓
Victory or Defeat
    ↓
Auto-restart in 10 seconds
```

---

## ⚡ Quick Start Commands:

**One-Line Setup:**
```powershell
# Compile
.\compile.bat

# Terminal 1 - Server (leave running)
.\run-server.bat

# Terminal 2 - Play!
.\run-client.bat
```

**Then:**
1. Click "Start Game"
2. Enter your name
3. Click "Go to Lobby"
4. **PLAY!** 🎮

---

## ✅ Features:

- ✅ Single-player ready
- ✅ Multiplayer optional
- ✅ No waiting required
- ✅ Instant game start
- ✅ AI zombies chase you
- ✅ Cooperative gameplay
- ✅ Beautiful zombie menu
- ✅ Full game mechanics

---

## 🎮 Controls:

| Key | Action |
|-----|--------|
| **W** / **↑** | Move Up |
| **S** / **↓** | Move Down |
| **A** / **←** | Move Left |
| **D** / **→** | Move Right |
| **Chat** | Type and press ENTER |

---

**Ready to play solo? Compile and run!** 🚀🧟
