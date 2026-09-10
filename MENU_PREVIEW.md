# Main Menu Visual Preview

```
┌──────────────────────────────────────────────────────────────┐
│  👾                                                      👾   │
│  Zombie                                              Zombie   │
│  (Green)                                             (Green)  │
│  Red Eyes                                          Red Eyes   │
│                                                                │
│                                                                │
│                  ═══════════════════════                       │
│                  ║ ZOMBIE ESCAPE ║                            │
│                  ═══════════════════════                       │
│                       (RED, GLOWING)                           │
│                  Can you survive the night?                    │
│                                                                │
│                                                                │
│                  ┌─────────────────────┐                      │
│                  │  ▶ Start Game       │  ← Selected          │
│                  └─────────────────────┘                      │
│                                                                │
│                     Invite Members                             │
│                                                                │
│                     Settings                                   │
│                                                                │
│                     Exit                                       │
│                                                                │
│                                                                │
│  👾                                                      👾   │
│  Zombie        Use ↑↓ or W/S • ENTER to select        Zombie │
│  (Green)                                               (Green) │
│  Red Eyes                                            Red Eyes  │
└──────────────────────────────────────────────────────────────┘
```

## Color Scheme:
- **Background**: Very dark gray/black with vignette effect
- **Title**: Bright red (#B40000) with glowing halo
- **Selected Option**: Highlighted in red with pulsing border
- **Unselected Options**: Light gray
- **Zombies**: Green body (#649650), darker head, glowing red eyes
- **Blood drips**: Dark red drops from title letters

## Animations:
1. **Pulsing glow** around the title (continuously)
2. **Glowing eyes** on zombies (fade in/out)
3. **Selected menu item** pulses gently
4. **Blood drips** have slight wave motion

## Menu Actions:

### "Start Game"
Opens a dialog box:
```
┌─────────────────────────────┐
│   ☣ Connect to Server       │
├─────────────────────────────┤
│  Server IP:  [localhost   ] │
│  Your Name:  [Player      ] │
│                             │
│         [OK]    [Cancel]    │
└─────────────────────────────┘
```

### "Invite Members"
Shows information dialog:
```
┌─────────────────────────────────────┐
│   Invite Friends                    │
├─────────────────────────────────────┤
│  Share this information:            │
│                                     │
│  Server IP: 192.168.1.100           │
│  Port: 8080                         │
│                                     │
│  They can connect using             │
│  'Start Game' option.               │
│                                     │
│              [OK]                   │
└─────────────────────────────────────┘
```

### "Settings"
Configuration dialog:
```
┌─────────────────────────────────────┐
│   Settings                          │
├─────────────────────────────────────┤
│  ☑ Enable Sound                     │
│  ☑ Enable Music                     │
│  Volume: ├───────●──┤ 70%           │
│                                     │
│              [OK]                   │
└─────────────────────────────────────┘
```

### "Exit"
Closes the application
