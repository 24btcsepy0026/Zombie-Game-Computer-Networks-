# Zombie Character Design

## Visual Representation

```
     ┌─────────┐
     │  O   O  │  ← Glowing red eyes (animated)
     │    ▬    │  ← Darker green head
     └────┬────┘
      ╔═══╩═══╗
      ║ ╱   ╲ ║  ← Torn clothes (dark gray lines)
      ║ Blood ║  ← Blood drips (red)
      ╚═══╤═══╝
          │      ← Green body
      ┌───┴───┐
      │       │
    ┌─┘       └─┐  ← Arms reaching forward
    │           │
```

## Color Scheme

### Zombie Body Parts:
1. **Body**: `Color(100, 150, 80)` - Sickly green
2. **Head**: `Color(90, 130, 70)` - Darker green shade
3. **Eyes**: `Color(180, 0, 0)` - Blood red (pulsing)
4. **Clothes**: `Color(60, 60, 80)` - Dark tattered fabric
5. **Blood**: `Color(180, 0, 0)` - Red drips

## Dimensions

For a 150x150 pixel zombie:
- **Head**: Oval, 50x50px (1/3 width, 1/3 height)
- **Body**: Oval, 50x75px (1/3 width, 1/2 height)
- **Arms**: Rectangles, 15px wide, 50px tall (1/3 height)
- **Eyes**: Small ovals, 10px each
- **Blood drips**: Lines, 2-3px wide, 10px long

## Animation Details

### Glowing Eyes Effect:
```
Time (seconds)  |  Alpha Value  |  Visual Effect
----------------|---------------|------------------
0.0             |  0.7          |  Dim red glow
0.5             |  0.85         |  Medium glow
1.0             |  1.0          |  Full bright
1.5             |  0.85         |  Medium glow
2.0             |  0.7          |  Dim red glow
```

Formula: `alpha = sin(pulseAngle) * 0.3 + 0.7`
- Range: 0.4 to 1.0 (never fully invisible)
- Period: ~2 seconds per cycle

### Positioning in Menu:

```
Screen: 1024 x 768

Top-Left Zombie:
  Position: (50, 50)
  Size: 150x150px

Top-Right Zombie:
  Position: (1024 - 200, 50) = (824, 50)
  Size: 150x150px
  Flipped: true (faces inward)

Bottom-Left Zombie:
  Position: (50, 768 - 200) = (50, 568)
  Size: 150x150px

Bottom-Right Zombie:
  Position: (824, 568)
  Size: 150x150px
  Flipped: true (faces inward)
```

## Drawing Order (Layers):

1. **Background** (vignette)
2. **Zombie Body** (main oval)
3. **Zombie Head** (head oval)
4. **Arms** (rectangles)
5. **Torn Clothes** (decorative lines)
6. **Blood Drips** (red lines from body)
7. **Eyes** (glowing with alpha composite)

## Code Structure:

```java
private void drawZombieInCorner(Graphics2D g2, int x, int y, int size, boolean flipX) {
    // 1. Draw body (green oval)
    // 2. Draw head (darker green oval)
    // 3. Draw eyes with pulsing effect
    // 4. Draw arms (left and right)
    // 5. Draw torn clothes details
    // 6. Draw blood drips
}
```

## Alternative Design Ideas (Not Implemented):

### More Detailed Version:
- Add individual fingers on hands
- Add hair/torn scalp details
- Add mouth with visible teeth
- Add texture/scars on skin
- Add shambling animation (slight sway)

### Simplified Version:
- Just head with eyes
- Single body shape (no separate head)
- No arms or details
- Static (no animation)

### Pixel Art Version:
- 8x8 or 16x16 pixel sprite
- Retro gaming aesthetic
- Frame-by-frame animation

## Performance Notes:

- Each zombie redraws ~20 times per second (50ms timer)
- Simple shape drawing is very efficient
- Alpha compositing (for glow) has minimal performance impact
- 4 zombies + title + menu = stable 60 FPS on modern hardware

## Customization Guide:

To change zombie appearance, modify these values in `MainMenuScreen.java`:

```java
// Make zombies bigger:
drawZombieInCorner(g2, 50, 50, 200, false);  // Change 150 → 200

// Change color to blue zombie:
ZOMBIE_GREEN = new Color(80, 100, 150);  // Blue-ish tint

// Faster eye pulse:
pulseAngle += 0.2f;  // Change 0.1f → 0.2f

// Brighter eyes:
alpha = sin(pulseAngle) * 0.5 + 0.5;  // Range 0.0 to 1.0
```

## Accessibility:

- Red eyes contrast well with green body (colorblind-friendly)
- Eyes pulse slowly (no seizure risk)
- Dark background ensures zombies stand out
- No flashing or strobing effects
