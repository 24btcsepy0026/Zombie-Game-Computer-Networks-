# ☣ Zombie Survival — Multiplayer Network Game

A real-time multiplayer survival game built with **Java** to demonstrate **Computer Networks** concepts including TCP/IP, Socket Programming, Client-Server Architecture, and Multithreading.

---

## 🎮 Gameplay

- One player is randomly assigned as the **Zombie 🧟** — the rest are **Survivors 🧍**
- Zombies chase and infect survivors by contact (drains HP → 0 = infected)
- Survivors collect **Medkits ❤** (+40 HP, +10 score) and **Keys 🔑** (+50 score)
- **Safe Zones** heal survivors +2 HP/sec and slow zombies by 50%
- Timer: **120 seconds** — if any survivor stays alive, survivors win!
- After Game Over, the server auto-resets in 10 seconds

---

## 🖥️ Screenshot

```
┌─────────────────────────────────────────────────────────────┐
│  [Lobby]  ZOMBIE SURVIVAL  — Waiting for players...         │
│                                                             │
│  [Game]   Tile map (25×18) with walls, safe zones, pickups  │
│           Direction-aware player sprites, minimap corner    │
│                                                             │
│  [HUD]    Role | Health bar | ⏱ Timer | Score              │
└─────────────────────────────────────────────────────────────┘
```

---

## 🌐 Computer Networks Concepts Demonstrated

| Concept | Implementation |
|---|---|
| **TCP/IP Sockets** | `java.net.Socket` / `ServerSocket` on port 8080 |
| **Client-Server Architecture** | Authoritative `GameServer` + multiple `GameClient`s |
| **Multithreading** | Dedicated thread per client (`ClientHandler`) + `GameEngine` thread |
| **Object Serialization** | Java `ObjectOutputStream/InputStream` over TCP |
| **LAN / Internet Play** | Clients connect by IP address (localhost or LAN IP) |
| **Real-time Synchronization** | `GameStateUpdate` broadcast at ~30 FPS |
| **Concurrency Safety** | `ConcurrentHashMap`, `CopyOnWriteArrayList`, `synchronized` |
| **In-game Chat** | `ChatMessage` → server → `ChatBroadcast` to all clients |

---

## 🏗️ Architecture

```
GameServer (port 8080)
│
├── ClientHandler (Thread per player) ─── receives: JoinRequest, MoveCommand, ChatMessage
│                                  ─── sends:    AssignIdMessage, GameStateUpdate, ChatBroadcast
│
└── GameEngine (Thread @ 30 TPS)
        - Role assignment, collision, health/damage
        - Pickup spawning & collection
        - Timer, win conditions, auto-reset
        - Broadcasts GameStateUpdate to all clients
```

---

## 📁 Project Structure

```
src/main/java/com/zombiesurvival/
├── shared/
│   ├── Player.java           — Player model (position, health, role, score)
│   ├── Pickup.java           — Medkit / Key pickup model
│   ├── GameState.java        — Full game state (tile map, players, pickups, timer)
│   ├── NetworkMessage.java   — Marker interface for all messages
│   ├── JoinRequest.java      — Client → Server: join with name
│   ├── MoveCommand.java      — Client → Server: movement delta
│   ├── ChatMessage.java      — Client → Server: chat text
│   ├── GameStateUpdate.java  — Server → Client: full state snapshot
│   ├── AssignIdMessage.java  — Server → Client: "your player ID is X"
│   └── ChatBroadcast.java    — Server → All: formatted chat line
│
├── server/
│   ├── GameServer.java       — Accepts connections, manages client list
│   ├── ClientHandler.java    — Per-client thread: reads messages, applies movement
│   └── GameEngine.java       — Core game loop: 30 TPS, physics, pickups, scoring
│
└── client/
    ├── GameClient.java       — Connect dialog, receive loop, sends messages
    ├── InputController.java  — WASD/Arrow → MoveCommand @ 30 Hz
    └── ui/
        ├── GameScreen.java   — Main JFrame (canvas + HUD + chat)
        ├── GameCanvas.java   — Renders: tiles, players, pickups, minimap, lobby, scoreboard
        ├── HudPanel.java     — Bottom HUD: role, health bar, timer, score
        └── ChatPanel.java    — Right-side live chat panel
```

---

## 🚀 How to Run

### Requirements
- Java 17 or higher
- No external libraries (pure Java SE)

### 1. Compile
```bash
cd "Computer Network Zombie Game"
javac -d out -sourcepath src/main/java src/main/java/com/zombiesurvival/shared/*.java src/main/java/com/zombiesurvival/server/*.java src/main/java/com/zombiesurvival/client/InputController.java src/main/java/com/zombiesurvival/client/GameClient.java src/main/java/com/zombiesurvival/client/ui/*.java
```

### 2. Start Server
```bash
java -cp out com.zombiesurvival.server.GameServer
```

### 3. Start Client(s)
```bash
java -cp out com.zombiesurvival.client.GameClient
```
A dialog will prompt for:
- **Server IP** — `localhost` for same machine, or LAN IP (e.g. `192.168.1.5`) for network play
- **Your Name** — display name in-game

> 🟢 Game starts automatically when **2 or more players** connect!

### 4. For Two Players on the Same PC
Open **two separate terminals** and run the client command in each.

---

## 🎮 Controls

| Key | Action |
|---|---|
| `W` / `↑` | Move Up |
| `S` / `↓` | Move Down |
| `A` / `←` | Move Left |
| `D` / `→` | Move Right |
| Click chat → type → `Enter` | Send chat message |

---

## 👥 Authors

Computer Networks Project — Java Multiplayer Game
