# 🎮 Unified Launcher - One-Click Play!

## ✅ What Changed:

### OLD System (Complex):
```
Terminal 1: run-server.bat  ← Start server
Terminal 2: run-client.bat  ← Start client
```
❌ Two terminals needed
❌ Manual server management
❌ Confusing for single player

### NEW System (Simple):
```
run-game.bat  ← Start EVERYTHING!
```
✅ ONE command
✅ Server runs automatically in background
✅ Client opens automatically
✅ Perfect for single player!

---

## 🚀 How to Play (Super Easy!):

### Step 1: Compile (First Time Only)
```powershell
.\compile.bat
```

### Step 2: Play!
```powershell
.\run-game.bat
```

**That's it!** 🎉

---

## 🎮 What Happens:

```
Double-click run-game.bat
        ↓
Server starts in background (automatic)
        ↓
Wait 1 second for server to initialize
        ↓
Client opens (main menu appears)
        ↓
Click "Start Game"
        ↓
Enter your name
        ↓
Click "Go to Lobby"
        ↓
PLAY! (vs AI zombies)
```

---

## 📁 New File Structure:

```
Zombie-Game-Computer-Networks-/
├── src/main/java/com/zombiesurvival/
│   ├── ZombieGame.java          ← NEW! Unified launcher
│   ├── client/
│   ├── server/
│   └── shared/
├── compile.bat                   ← Updated
├── run-game.bat                  ← NEW! One-click launcher
├── run-server.bat                ← Still available (manual mode)
└── run-client.bat                ← Still available (manual mode)
```

---

## 🎯 Use Cases:

### Single Player (Easy Mode):
```powershell
.\run-game.bat
```
✅ Server + Client in one!
✅ No terminal management
✅ Just play!

### Multiplayer (Manual Mode):
**Host Computer:**
```powershell
.\run-server.bat    (Terminal 1)
.\run-client.bat    (Terminal 2)
```

**Friend's Computer:**
```powershell
.\run-client.bat
```
Connect to host's IP

---

## 🔧 Technical Details:

### ZombieGame.java (Unified Launcher):
```java
public static void main(String[] args) {
    // 1. Start server in background thread
    Thread serverThread = new Thread(() -> {
        GameServer server = new GameServer();
        server.start();
    });
    serverThread.setDaemon(true); // Auto-stops when client exits
    serverThread.start();
    
    // 2. Wait for server to initialize
    Thread.sleep(1000);
    
    // 3. Start client (GUI)
    GameClient.main(new String[]{});
}
```

### Benefits:
- ✅ **Daemon thread**: Server stops automatically when you close the game
- ✅ **Background**: Server runs silently in background
- ✅ **Localhost**: Client auto-connects to localhost
- ✅ **Single process**: Everything in one application

---

## ⚡ Quick Commands:

### First Time Setup:
```powershell
.\compile.bat
.\run-game.bat
```

### Every Time After:
```powershell
.\run-game.bat
```

**That's literally it!** 🎮

---

## 🎮 Complete Single-Player Flow:

```
1. Double-click run-game.bat
2. Wait for menu to appear
3. Click "Start Game"
4. Enter your name (e.g., "Abhinav")
5. Click OK
6. Click "Go to Lobby"
7. Game starts with AI zombies!
8. Survive 120 seconds!
9. Victory or defeat
10. Auto-restart in 10 seconds
11. Play again!
```

**No terminal juggling. No server management. Just play!** 🎉

---

## 🆚 Comparison:

| Feature | OLD (Two Terminals) | NEW (Unified) |
|---------|---------------------|---------------|
| Commands to run | 2 | 1 |
| Terminals needed | 2 | 1 |
| Server management | Manual | Automatic |
| Single player | Awkward | Perfect |
| Multiplayer | Same | Still works |
| Complexity | High | Low |

---

## 🎯 Perfect For:

- ✅ Single-player gaming
- ✅ Quick testing
- ✅ Demo/presentation
- ✅ Learning/development
- ✅ First-time players
- ✅ Non-technical users

---

## 🔄 Multiplayer Still Works!

**If you want multiplayer:**
- Use the old way (run-server.bat + run-client.bat)
- Or use run-game.bat on your computer
- Friends connect with run-client.bat

**Both options still available!** 🎮

---

**Enjoy your simplified zombie game!** 🧟‍♂️🎉
