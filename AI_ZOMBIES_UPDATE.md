# 🧟 AI Zombies Update

## 🎮 Major Game Change:

### OLD System:
- ❌ One random player becomes zombie
- ❌ Other players are survivors
- ❌ PvP: players vs players

### NEW System:
- ✅ **ALL players are survivors**
- ✅ **3-7 random AI zombies** spawn at game start
- ✅ **PvE: players vs AI enemies**

---

## 🤖 AI Zombie Behavior:

### Movement:
- **AI-controlled** - Move automatically
- **Chase nearest survivor** - Always pursuing
- **Speed:** 1.5 pixels per tick (slower than players)
- **Pathfinding:** Basic - move towards target, avoid walls
- **Spawning:** Random positions near spawn point

### Combat:
- **Damage:** Same as before (damage per tick on contact)
- **Kill survivors:** When health reaches 0
- **No infection:** Players don't become zombies, they die

---

## 🎯 Game Rules:

### Objective:
**Survivors:** Survive for 120 seconds
**Zombies (AI):** Kill all survivors

### Win Conditions:
- ✅ **Survivors win:** Any survivor alive when timer hits 0:00
- ✅ **Zombies win:** All survivors killed before timer ends

### Scoring:
- **+1 point per second** for surviving
- **+10 points** for collecting Medkits
- **+50 points** for collecting Keys

---

## 🧟 Zombie Spawning:

### Random Amount:
- **Minimum:** 3 zombies
- **Maximum:** 7 zombies
- **Randomized** each game

### Names:
- Zombie #1
- Zombie #2
- Zombie #3
- etc.

### Spawn Location:
- Near first spawn point
- Slight randomization for spread

---

## 👥 Player Experience:

### At Game Start:
```
=== GAME STARTED! Survive against 5 zombies! ===
```

### During Game:
- See AI zombies moving and chasing
- Work together as team
- Collect pickups
- Avoid zombie hordes

### When Killed:
```
** Player1 was killed by Zombie #3! **
```
- Player health = 0
- Can't move or play
- Watch other survivors

### Victory:
```
🏆 SURVIVORS WIN! Time ran out.
```

---

## 🎨 Visual Changes:

### In Game:
- **AI Zombies:** Green bodies, red eyes (same as before)
- **All Players:** Blue bodies, human skin (survivors)
- **Dead Players:** Gray out or show at 0 HP

### Zombie Names:
- Player-controlled zombies: Gone
- AI zombies: "Zombie #1", "Zombie #2", etc.

---

## 🔧 Technical Details:

### AI Implementation:
```java
private void moveAIZombies(List<Player> zombies, List<Player> survivors) {
    // For each AI zombie:
    // 1. Find nearest survivor
    // 2. Calculate direction
    // 3. Move towards target
    // 4. Check wall collisions
}
```

### Zombie Identification:
- **AI Zombies:** ID starts with "zombie_"
- **Player Survivors:** Regular player IDs
- **Easy filtering** for AI behavior

### Game Balance:
- **3-7 zombies** vs **2+ players**
- **Zombies slower** than players (1.5 vs 2 pixels/tick)
- **Numbers advantage** for challenge
- **Teamwork required** to survive

---

## 🚀 To Test:

**Terminal 1 (Server):**
```powershell
.\run-server.bat
```

**Terminal 2 (Client 1):**
```powershell
.\compile.bat
.\run-client.bat
```

**Terminal 3 (Client 2):**
```powershell
.\run-client.bat
```

**Flow:**
1. Both players connect
2. Click "Go to Lobby"
3. Game starts with 3-7 AI zombies
4. All players are survivors!
5. Watch zombies chase you!
6. Survive 120 seconds to win!

---

## ✅ Features:

- ✅ Cooperative gameplay
- ✅ Team vs AI
- ✅ Random zombie count each game
- ✅ Smart zombie AI (chase nearest)
- ✅ No friendly fire
- ✅ Everyone works together

**Much more fun and cooperative!** 🎮🧟‍♂️
