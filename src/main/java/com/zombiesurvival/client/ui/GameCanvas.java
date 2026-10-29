package com.zombiesurvival.client.ui;

import com.zombiesurvival.client.InputController;
import com.zombiesurvival.shared.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Last-Day-on-Earth-style top-down game canvas.
 *
 * Render order:
 *  1.  Ground tiles  (LDOE palette: asphalt / dirt / grass variants / concrete)
 *  2.  Ground decals (blood pools, debris — world-space permanent)
 *  3.  Building interior floors
 *  4.  Wall bases + pseudo-3D shadow faces
 *  5.  Environmental props (trees, crates, rocks, crashed cars)
 *  6.  Pickups (glowing bob)
 *  7.  NPC zombies (shambling sprites)
 *  8.  Player characters (top-down with weapon arm)
 *  9.  Bullets / muzzle flash
 * 10.  Particles (blood splatter / sparkles / infection cloud)
 * 11.  Dynamic lighting overlay (dark ambient + warm player lantern)
 * 12.  Kill-feed (screen-space)
 * 13.  Minimap (LDOE dark style)
 * 14.  Crosshair (dot + ring)
 * 15.  Survivor / zombie vignette
 * 16.  Lobby / connecting / game-over overlays
 */
public class GameCanvas extends JPanel {

    // ── Viewport ──────────────────────────────────────────────────────────────
    public static final int VW = GameState.VIEWPORT_W;
    public static final int VH = GameState.VIEWPORT_H;
    private static final int TS = GameState.TILE_SIZE;
    private static final int MW = GameState.MAP_WIDTH;
    private static final int MH = GameState.MAP_HEIGHT;
    private static final int CW = GameState.CANVAS_W;
    private static final int CH = GameState.CANVAS_H;

    // ── LDOE Color Palette ────────────────────────────────────────────────────
    private static final Color C_GRASS_A   = new Color(42, 58, 28);   // brighter olive grass
    private static final Color C_GRASS_B   = new Color(36, 50, 22);   // slightly darker grass
    private static final Color C_CONCRETE  = new Color(62, 62, 68);   // lighter gray concrete
    private static final Color C_ASPHALT   = new Color(36, 36, 42);   // dark road
    private static final Color C_DIRT      = new Color(68, 52, 32);   // brown dirt
    private static final Color C_SAFE_FL   = new Color(30, 54, 30);   // safe zone floor
    private static final Color C_WOOD_FL   = new Color(60, 44, 26);   // interior wood
    private static final Color C_WALL_TOP  = new Color(78, 72, 64);   // brighter stone wall top
    private static final Color C_BLOOD_DK  = new Color(70,  5,  5);   // dark blood
    private static final Color C_SKIN      = new Color(210, 172, 118);
    private static final Color C_SKIN_Z    = new Color(118, 135, 65);

    // ── Tile textures ─────────────────────────────────────────────────────────
    private final BufferedImage tileGrassA, tileGrassB, tileWall, tileSafe;
    private final BufferedImage tileConcrete, tileAsphalt, tileDirt, tileWoodFloor;

    // ── Camera ────────────────────────────────────────────────────────────────
    private float camX, camY, tgtX, tgtY;

    // ── State ─────────────────────────────────────────────────────────────────
    private volatile GameState gameState;
    private volatile GameState prevState;
    private volatile String    myPlayerId;
    private int animTick;

    // Per-player animation
    private final Map<String, Double>  facingAngles  = new ConcurrentHashMap<>();
    private final Map<String, float[]> lastPositions = new ConcurrentHashMap<>();
    private final Map<String, Float>   walkPhases    = new ConcurrentHashMap<>();  // 0..2π

    // Mouse
    private volatile int mouseScreenX = VW / 2, mouseScreenY = VH / 2;

    // Particles  [x,y,vx,vy, life,maxLife, r,g,b, size, type]
    private final List<float[]>                   particles     = new ArrayList<>();
    private final ConcurrentLinkedQueue<float[]>  particleEvts  = new ConcurrentLinkedQueue<>();

    // Blood decals  [wx, wy, r, angle, opacity]
    private final List<float[]> bloodDecals = new ArrayList<>();

    // Screen-shake
    private final AtomicInteger shakeReq = new AtomicInteger(0);
    private int shakeFrames, shakeAmt = 3;

    // Lighting
    private BufferedImage lightMap;

    // Sound bookkeeping
    private int     lastTimeSec   = Integer.MAX_VALUE;
    private boolean warnedPlayed;

    // Input
    private InputController inputController;

    // Prop RNG (stable across frames)
    private final Random propRng = new Random(12345L);

    // Pre-computed prop positions (trees / crates) per visible area
    private final List<int[]> props = new ArrayList<>(); // [wx, wy, type, seed]

    // ═══════════════════════════════════════════════════════════════════════════

    public GameCanvas() {
        setPreferredSize(new Dimension(VW, VH));
        setMinimumSize(new Dimension(640, 480));
        setBackground(new Color(8, 10, 6));

        tileGrassA   = makeTileGrass(0, C_GRASS_A);
        tileGrassB   = makeTileGrass(1, C_GRASS_B);
        tileWall     = makeTileWall();
        tileSafe     = makeTileSafe();
        tileConcrete = makeTileConcrete();
        tileAsphalt  = makeTileAsphalt();
        tileDirt     = makeTileDirt();
        tileWoodFloor= makeTileWoodFloor();

        buildProps();

        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override public void mouseMoved  (java.awt.event.MouseEvent e) { mouseScreenX=e.getX(); mouseScreenY=e.getY(); }
            @Override public void mouseDragged(java.awt.event.MouseEvent e) { mouseScreenX=e.getX(); mouseScreenY=e.getY(); }
        });

        new javax.swing.Timer(33, e -> { animTick++; drainEvents(); updateParticles(); repaint(); }).start();
    }

    // ── API ───────────────────────────────────────────────────────────────────
    public void setInputController(InputController ic) { inputController = ic; }
    public void setMyPlayerId(String id)               { myPlayerId = id; }

    public void setGameState(GameState next) {
        if (next != null) { detectEvents(gameState, next); trackFacing(next); }
        prevState = gameState;
        gameState = next;
        updateCamera();
    }

    // ── Camera ────────────────────────────────────────────────────────────────
    private void updateCamera() {
        GameState gs = gameState; if (gs == null) return;
        int vw = getWidth(), vh = getHeight(); if (vw <= 0 || vh <= 0) return;
        Player me = gs.getPlayer(myPlayerId);
        if (me != null) {
            tgtX = me.getXf() + Player.SIZE / 2f - vw / 2f;
            tgtY = me.getYf() + Player.SIZE / 2f - vh / 2f;
        }
        tgtX = Math.max(0, Math.min(tgtX, CW - vw));
        tgtY = Math.max(0, Math.min(tgtY, CH - vh));
        camX += (tgtX - camX) * 0.10f;
        camY += (tgtY - camY) * 0.10f;
        if (inputController != null) inputController.updateCameraOffset(camX, camY);
    }

    // ── Event Detection ───────────────────────────────────────────────────────
    private void detectEvents(GameState prev, GameState next) {
        if (prev == null) return;
        for (Player np : next.getPlayers().values()) {
            Player pp = prev.getPlayer(np.getId()); if (pp == null) continue;
            float cx = np.getXf() + Player.SIZE/2f, cy = np.getYf() + Player.SIZE/2f;
            if (np.getHealth() < pp.getHealth()) {
                particleEvts.offer(new float[]{cx, cy, 0});
                bloodDecals.add(new float[]{cx + (float)(Math.random()*12-6), cy + (float)(Math.random()*12-6),
                    6+rndF()*10, rndF()*6.28f, 0.7f + rndF()*0.3f});
                if (bloodDecals.size() > 120) bloodDecals.remove(0);
                if (np.getId().equals(myPlayerId))
                    shakeReq.set(Math.min(18, (pp.getHealth()-np.getHealth())*2+4));
            }
            if (!pp.isInfected() && np.isInfected()) {
                particleEvts.offer(new float[]{cx, cy, 3});
                if (np.getId().equals(myPlayerId)) shakeReq.set(24);
            }
        }
        for (Pickup nPk : new ArrayList<>(next.getPickups())) {
            if (!nPk.isCollected()) continue;
            for (Pickup pPk : new ArrayList<>(prev.getPickups())) {
                if (!pPk.getId().equals(nPk.getId()) || pPk.isCollected()) continue;
                float cx = nPk.getX() + Pickup.SIZE/2f, cy = nPk.getY() + Pickup.SIZE/2f;
                particleEvts.offer(new float[]{cx, cy, nPk.getType()==Pickup.Type.MEDKIT ? 1 : 2});
                break;
            }
        }
    }

    private void drainEvents() {
        float[] ev; Random r2 = new Random();
        while ((ev = particleEvts.poll()) != null) switch ((int)ev[2]) {
            case 0 -> spawnBlood (ev[0], ev[1], r2);
            case 1 -> spawnSpark (ev[0], ev[1], new Color(220,40,40),  r2);
            case 2 -> spawnSpark (ev[0], ev[1], new Color(200,180,20), r2);
            case 3 -> spawnInfect(ev[0], ev[1], r2);
        }
    }

    private void spawnBlood(float cx, float cy, Random r) {
        for (int i = 0; i < 12; i++) {
            float a = r.nextFloat()*6.28f, s = 1.5f+r.nextFloat()*3f;
            particles.add(new float[]{cx,cy, (float)Math.cos(a)*s, (float)Math.sin(a)*s,
                16+r.nextInt(20), 36, 185,18,18, 3+r.nextInt(4), 0});
        }
    }
    private void spawnSpark(float cx, float cy, Color col, Random r) {
        for (int i = 0; i < 10; i++) {
            float a = r.nextFloat()*6.28f, s = 1.8f+r.nextFloat()*3.5f;
            particles.add(new float[]{cx,cy, (float)Math.cos(a)*s, (float)Math.sin(a)*s-1.5f,
                18+r.nextInt(22), 40, col.getRed(),col.getGreen(),col.getBlue(), 4+r.nextInt(5), 1});
        }
    }
    private void spawnInfect(float cx, float cy, Random r) {
        for (int i = 0; i < 16; i++) {
            float a = r.nextFloat()*6.28f, s = 2.5f+r.nextFloat()*4f;
            particles.add(new float[]{cx,cy, (float)Math.cos(a)*s, (float)Math.sin(a)*s-1f,
                24+r.nextInt(28), 52, 80+r.nextInt(60),190+r.nextInt(60),40, 3+r.nextInt(6), 1});
        }
    }

    private void updateParticles() {
        particles.removeIf(p -> { p[0]+=p[2]; p[1]+=p[3]; p[3]+=0.07f; p[2]*=0.92f; p[4]--; return p[4]<=0; });
    }

    private void trackFacing(GameState s) {
        for (Player p : s.getPlayers().values()) {
            float[] prev = lastPositions.get(p.getId());
            float dx=0, dy=0;
            if (prev!=null){ dx=p.getXf()-prev[0]; dy=p.getYf()-prev[1]; }
            if (dx*dx+dy*dy>0.25) {
                facingAngles.put(p.getId(), Math.atan2(dy,dx));
                float wp = walkPhases.getOrDefault(p.getId(), 0f);
                walkPhases.put(p.getId(), (wp + 0.28f) % ((float)Math.PI*2));
            }
            lastPositions.put(p.getId(), new float[]{p.getXf(), p.getYf()});
        }
    }

    // ── Paint dispatch ────────────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int vw=getWidth(), vh=getHeight();
        Graphics2D g2=(Graphics2D)g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        int req=shakeReq.getAndSet(0);
        if (req>0){shakeFrames=req; shakeAmt=req>15?8:4;}
        AffineTransform orig=g2.getTransform();
        if (shakeFrames>0){ g2.translate((int)((Math.random()*2-1)*shakeAmt),(int)((Math.random()*2-1)*shakeAmt)); shakeFrames--; }

        GameState gs=gameState;
        if (gs==null){ renderConnecting(g2,vw,vh); g2.dispose(); return; }
        switch (gs.getCurrentPhase()) {
            case WAITING   -> renderLobby(g2,gs,vw,vh);
            case PLAYING   -> renderGame (g2,gs,vw,vh);
            case GAME_OVER -> { renderGame(g2,gs,vw,vh); renderGameOver(g2,gs,vw,vh); }
        }
        g2.setTransform(orig);
        g2.dispose();
    }

    // ── Connecting screen ─────────────────────────────────────────────────────
    private void renderConnecting(Graphics2D g, int vw, int vh) {
        g.setColor(new Color(8,8,6)); g.fillRect(0,0,vw,vh);
        float p=0.5f+0.5f*(float)Math.sin(animTick*0.08f);
        g.setFont(new Font("Impact", Font.PLAIN, 18));
        g.setColor(new Color(180,120,40,(int)(120+135*p)));
        drawCentered(g,"Connecting to server...",vw/2,vh/2);
    }

    // ── Lobby screen (LDOE-style) ─────────────────────────────────────────────
    private void renderLobby(Graphics2D g, GameState gs, int vw, int vh) {
        // Dark background with vignette
        g.setColor(new Color(8,8,6)); g.fillRect(0,0,vw,vh);
        g.setPaint(new RadialGradientPaint(vw*.5f,vh*.45f,vw*.65f,
            new float[]{0f,1f}, new Color[]{new Color(30,22,12,0),new Color(0,0,0,210)}));
        g.fillRect(0,0,vw,vh);

        // Horizontal grime lines (LDOE texture feel)
        for (int y=0;y<vh;y+=18){
            g.setColor(new Color(255,255,255,(int)(3+3*Math.random())));
            g.drawLine(0,y,vw,y);
        }

        int cx=vw/2;
        // Skull / bio icon
        float pulse=0.5f+0.5f*(float)Math.sin(animTick*0.05);
        g.setFont(new Font("Segoe UI Symbol",Font.PLAIN,(int)(70+8*pulse)));
        g.setColor(new Color(180,30,20,(int)(60+80*pulse)));
        drawCentered(g,"☣",cx,vh/2-215);

        // Game title – LDOE military stencil feel
        g.setFont(new Font("Impact",Font.PLAIN,88));
        g.setColor(new Color(0,0,0,180)); drawCentered(g,"ZOMBIE",cx+4,vh/2-132+4);
        g.setPaint(new GradientPaint(cx-250,vh/2-200,new Color(200,140,30),cx+250,vh/2-130,new Color(170,80,20)));
        drawCentered(g,"ZOMBIE",cx,vh/2-132);

        g.setFont(new Font("Impact",Font.PLAIN,42));
        g.setColor(new Color(180,180,180));
        drawCentered(g,"SURVIVAL",cx,vh/2-85);

        g.setFont(new Font("Segoe UI",Font.PLAIN,12));
        g.setColor(new Color(100,90,60));
        drawCentered(g,"Multiplayer  •  Weapons  •  NPC Zombies  •  80×60 Tile Map  •  TCP/IP Networking",cx,vh/2-62);

        // Horizontal divider
        g.setPaint(new GradientPaint(cx-200,0,new Color(0,0,0,0),cx,0,new Color(180,140,40,200)));
        g.fillRect(cx-200,vh/2-50,200,1);
        g.setPaint(new GradientPaint(cx,0,new Color(180,140,40,200),cx+200,0,new Color(0,0,0,0)));
        g.fillRect(cx,vh/2-50,200,1);

        // Player slots
        java.util.Collection<Player> players=gs.getPlayers().values();
        int listY=vh/2-30;
        g.setFont(new Font("Segoe UI",Font.BOLD,12));
        g.setColor(new Color(140,120,60));
        drawCentered(g,"CONNECTED  (" +players.size()+")",cx,listY);
        for (Player p:players){
            listY+=42;
            boolean me=p.getId().equals(myPlayerId);
            // Avatar circle
            int ax=cx-130,ay=listY-20;
            g.setPaint(new RadialGradientPaint(ax+14,ay+12,14,
                new float[]{0f,1f},
                new Color[]{me?new Color(200,160,40):new Color(80,80,80),new Color(20,15,8)}));
            g.fillOval(ax,ay,28,28);
            g.setColor(me?new Color(220,180,60):new Color(100,100,80));
            g.setStroke(new BasicStroke(1.5f)); g.drawOval(ax,ay,28,28); g.setStroke(new BasicStroke(1f));
            g.setFont(new Font("Segoe UI",Font.BOLD,13)); g.setColor(Color.WHITE);
            drawCentered(g,p.getName().substring(0,1).toUpperCase(),ax+14,ay+19);
            g.setFont(new Font("Segoe UI",me?Font.BOLD:Font.PLAIN,13));
            g.setColor(me?new Color(220,190,80):new Color(160,150,110));
            g.drawString(p.getName()+(me?"   ▶ YOU":""),cx-90,listY);
        }

        // Waiting pulse
        float wp=0.5f+0.5f*(float)Math.sin(animTick*0.07);
        g.setFont(new Font("Segoe UI",Font.PLAIN,13));
        g.setColor(new Color(160,120,40,(int)(80+175*wp)));
        drawCentered(g,"⚠  Waiting — game starts when 2+ players connect",cx,vh-128);

        // Controls bar
        int bx=cx-330,by=vh-112;
        g.setColor(new Color(14,12,8,230)); g.fillRoundRect(bx,by,660,90,10,10);
        g.setColor(new Color(80,60,30,200)); g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(bx,by,660,90,10,10); g.setStroke(new BasicStroke(1f));
        g.setFont(new Font("Segoe UI",Font.BOLD,11)); g.setColor(new Color(160,130,60));
        drawCentered(g,"CONTROLS  •  F11 = Full Screen  •  ESC = Exit Full Screen",cx,by+16);
        g.setFont(new Font("Segoe UI",Font.PLAIN,11)); g.setColor(new Color(130,115,80));
        drawCentered(g,"Move: WASD  •  SHIFT: Sprint  •  Left Click: Shoot (toward cursor)  •  F: Melee",cx,by+36);
        drawCentered(g,"1-5: Switch Weapon  •  Q: Zombie Lunge  •  E: Zombie Howl  •  Pick up items by walking over them",cx,by+54);
        drawCentered(g,"Survivors: survive 3 minutes  •  Zombies: infect everyone  •  NPC Zombies: 15 on the map",cx,by+72);
    }

    // ── Game world ────────────────────────────────────────────────────────────
    private void renderGame(Graphics2D g, GameState gs, int vw, int vh) {
        int cx=(int)camX, cy=(int)camY;
        g.translate(-cx,-cy);

        int sc=Math.max(0,cx/TS-1), ec=Math.min(MW-1,(cx+vw)/TS+2);
        int sr=Math.max(0,cy/TS-1), er=Math.min(MH-1,(cy+vh)/TS+2);

        drawTiles       (g,sc,ec,sr,er);
        drawGroundDecals(g,cx,cy,vw,vh);
        drawBuildingFloors(g,sc,ec,sr,er);
        drawWallFaces   (g,sc,ec,sr,er);
        drawProps       (g,cx,cy,vw,vh);
        drawPickups     (g,gs);
        drawNpcs        (g,gs);
        drawPlayers     (g,gs);
        drawBullets     (g,gs);
        drawParticles   (g);
        drawDynLighting (g,gs,cx,cy,vw,vh);
        drawKillFeed    (g,gs,cx,cy,vw);

        g.translate(cx,cy);

        drawMinimap  (g,gs,vw,vh);
        drawCrosshair(g,gs,vw,vh);
        drawVignette (g,gs,vw,vh);
    }

    // ── 1. Tiles ──────────────────────────────────────────────────────────────
    private void drawTiles(Graphics2D g, int sc, int ec, int sr, int er) {
        for (int row=sr;row<=er;row++) for (int col=sc;col<=ec;col++) {
            int tile=GameState.MAP[row][col];
            int px=col*TS, py=row*TS;
            switch (tile) {
                case 0 -> g.drawImage((row+col)%2==0?tileGrassA:tileGrassB, px, py, null);
                case 1 -> g.drawImage(tileWall, px, py, null);
                case 2 -> {
                    g.drawImage(tileSafe, px, py, null);
                    float gl=0.3f+0.22f*(float)Math.sin(animTick*0.055+col*0.3+row*0.3);
                    g.setColor(new Color(28,180,80,(int)(50*gl)));
                    g.fillRect(px,py,TS,TS);
                }
            }
        }
    }

    // ── 2. Blood decals (world-space persistent) ──────────────────────────────
    private void drawGroundDecals(Graphics2D g, int cx, int cy, int vw, int vh) {
        for (float[] d : bloodDecals) {
            float wx=d[0],wy=d[1],r=d[2]; float a=d[4];
            if (wx<cx-r||wx>cx+vw+r||wy<cy-r||wy>cy+vh+r) continue;
            g.setColor(new Color(C_BLOOD_DK.getRed(),C_BLOOD_DK.getGreen(),C_BLOOD_DK.getBlue(),(int)(a*180)));
            AffineTransform old=g.getTransform();
            g.translate(wx,wy); g.rotate(d[3]);
            g.fillOval(-(int)r,-(int)(r*0.6f),(int)(r*2),(int)(r*1.2f));
            g.setTransform(old);
        }
    }

    // ── 3. Building interior floors ───────────────────────────────────────────
    private void drawBuildingFloors(Graphics2D g, int sc, int ec, int sr, int er) {
        // Draw wood floor inside wall-enclosed areas (tiles surrounded by walls)
        for (int row=sr+1;row<=er-1;row++) for (int col=sc+1;col<=ec-1;col++) {
            if (GameState.MAP[row][col]==1) {
                // Check if surrounded by walls — if so, render interior floor instead
                boolean top=GameState.MAP[row-1][col]==1;
                boolean bot=GameState.MAP[row+1][col]==1;
                boolean lft=GameState.MAP[row][col-1]==1;
                boolean rgt=GameState.MAP[row][col+1]==1;
                if (!top&&!bot&&!lft&&!rgt) {
                    g.drawImage(tileWoodFloor,col*TS,row*TS,null);
                }
            }
        }
    }

    // ── 4. Wall faces (pseudo-3D shadow) ─────────────────────────────────────
    private void drawWallFaces(Graphics2D g, int sc, int ec, int sr, int er) {
        for (int row=sr;row<=er;row++) for (int col=sc;col<=ec;col++) {
            if (GameState.MAP[row][col]!=1) continue;
            int px=col*TS, py=row*TS;
            if (row+1<MH && GameState.MAP[row+1][col]!=1) {
                // Bottom face of wall (visible in top-down)
                g.setPaint(new GradientPaint(0,py+TS,new Color(22,20,18),0,py+TS+13,new Color(12,10,8)));
                g.fillRect(px,py+TS,TS,13);
                g.setColor(new Color(255,255,255,8)); g.drawLine(px,py+TS,px+TS-1,py+TS);
                // Shadow
                Paint sv=g.getPaint();
                g.setPaint(new GradientPaint(0,py+TS+13,new Color(0,0,0,60),0,py+TS+20,new Color(0,0,0,0)));
                g.fillRect(px,py+TS+13,TS,7); g.setPaint(sv);
            }
            if (col+1<MW && GameState.MAP[row][col+1]!=1) {
                g.setPaint(new GradientPaint(px+TS,0,new Color(18,16,14),px+TS+6,0,new Color(0,0,0,0)));
                g.fillRect(px+TS,py,6,TS);
            }
        }
    }

    // ── 5. Environmental props ────────────────────────────────────────────────
    private void buildProps() {
        // Trees placed on grass tiles — low density so paths are visible
        Random r = new Random(99887L);
        for (int row=1;row<MH-1;row++) for (int col=1;col<MW-1;col++) {
            if (GameState.MAP[row][col]!=0) continue;
            boolean inForest=(row>=31&&row<=58&&col>=1&&col<=38);
            boolean inUrban =(row>=1 &&row<=28&&col>=1&&col<=38);
            // Reduced density so routes/paths are clearly visible
            float chance = inForest ? 0.04f : inUrban ? 0.008f : 0.012f;
            if (r.nextFloat()<chance) props.add(new int[]{col*TS+TS/2, row*TS+TS/2, 0, r.nextInt(1000)});
        }
        // Rocks & debris
        for (int i=0;i<250;i++) {
            int col=r.nextInt(MW-2)+1, row=r.nextInt(MH-2)+1;
            if (GameState.MAP[row][col]==0) props.add(new int[]{col*TS+r.nextInt(TS), row*TS+r.nextInt(TS), 1, r.nextInt(1000)});
        }
        // Crashed cars
        for (int i=0;i<18;i++){
            int col=r.nextInt(MW-4)+2, row=r.nextInt(MH-4)+2;
            if (GameState.MAP[row][col]==0) props.add(new int[]{col*TS, row*TS, 2, r.nextInt(1000)});
        }
    }

    private void drawProps(Graphics2D g, int cx, int cy, int vw, int vh) {
        for (int[] prop : props) {
            int wx=prop[0],wy=prop[1],type=prop[2];
            if (wx<cx-80||wx>cx+vw+80||wy<cy-80||wy>cy+vh+80) continue;
            switch(type) {
                case 0 -> drawTree (g, wx, wy, prop[3]);
                case 1 -> drawRock (g, wx, wy, prop[3]);
                case 2 -> drawCar  (g, wx, wy, prop[3]);
            }
        }
    }

    private void drawTree(Graphics2D g, int wx, int wy, int seed) {
        Random r=new Random(seed);
        int tr = 9 + r.nextInt(5);   // smaller: 9-13px radius (was 14-22)
        // Shadow
        g.setColor(new Color(0,0,0,45));
        g.fillOval(wx-tr+3, wy-tr/2+5, tr*2, (int)(tr*1.1));
        // Trunk
        g.setColor(new Color(45,32,18));
        g.fillOval(wx-4,wy-4,8,8);
        // Canopy — dark green LDOE style
        g.setPaint(new RadialGradientPaint(wx,wy-1,tr,new float[]{0f,0.55f,1f},
            new Color[]{new Color(50,78,28),new Color(38,60,20),new Color(24,42,12)}));
        g.fillOval(wx-tr,wy-tr-1,tr*2,tr*2);
        // Highlight
        g.setColor(new Color(70,100,36,70));
        g.fillOval(wx-tr/2,wy-tr,tr,tr/2);
    }

    private void drawRock(Graphics2D g, int wx, int wy, int seed) {
        Random r=new Random(seed); int rs=4+r.nextInt(5);
        g.setColor(new Color(0,0,0,40)); g.fillOval(wx-rs+2,wy-rs+3,rs*2,rs);
        g.setColor(new Color(62,62,66)); g.fillOval(wx-rs,wy-rs,rs*2,(int)(rs*1.5));
        g.setColor(new Color(80,80,84)); g.fillOval(wx-rs,wy-rs,rs,rs/2);
    }

    private void drawCar(Graphics2D g, int wx, int wy, int seed) {
        Random r2=new Random(seed);
        double ang=(r2.nextInt(4))*Math.PI/2;
        AffineTransform old=g.getTransform();
        g.translate(wx+16,wy+12); g.rotate(ang);
        // Shadow
        g.setColor(new Color(0,0,0,70)); g.fillRoundRect(-18,-12,40,28,6,6);
        // Body
        Color body=new Color[]{new Color(65,25,18),new Color(30,45,30),new Color(35,30,50)}[r2.nextInt(3)];
        g.setColor(body); g.fillRoundRect(-16,-10,36,24,5,5);
        // Windshield
        g.setColor(new Color(80,90,100,140)); g.fillRect(-6,-8,16,5);
        // Rust
        g.setColor(new Color(120,50,20,80)); g.fillRect(-14,-8,8,3);
        // Wheels
        g.setColor(new Color(20,20,20));
        g.fillRect(-18,-10,5,7); g.fillRect(-18,7,5,7);
        g.fillRect(14,-10,5,7);  g.fillRect(14,7,5,7);
        g.setTransform(old);
    }

    // ── 6. Pickups ────────────────────────────────────────────────────────────
    private void drawPickups(Graphics2D g, GameState gs) {
        for (Pickup pk : new ArrayList<>(gs.getPickups())) {
            if (pk.isCollected()) continue;
            float px=pk.getX()+Pickup.SIZE/2f, py=pk.getY()+Pickup.SIZE/2f;
            float bob=(float)Math.sin(animTick*0.1+px*0.01)*3f;
            float glow=0.5f+0.5f*(float)Math.sin(animTick*0.12+py*0.01);
            Color col=pickupColor(pk.getType());
            // Glow ring
            g.setColor(new Color(col.getRed(),col.getGreen(),col.getBlue(),(int)(40*glow)));
            int gr=12; g.fillOval((int)(px-gr),(int)(py-gr+bob),gr*2,gr*2);
            // Item box (dark background)
            g.setColor(new Color(20,18,14,210)); g.fillRoundRect((int)(px-8),(int)(py-8+bob),16,16,4,4);
            g.setColor(col); g.setStroke(new BasicStroke(1.2f));
            g.drawRoundRect((int)(px-8),(int)(py-8+bob),16,16,4,4); g.setStroke(new BasicStroke(1f));
            // Icon letter
            g.setFont(new Font("Segoe UI",Font.BOLD,9));
            String ic=pickupIcon(pk.getType());
            g.setColor(col);
            FontMetrics fm=g.getFontMetrics(); g.drawString(ic,(int)(px-fm.stringWidth(ic)/2f),(int)(py+3+bob));
        }
    }

    private Color pickupColor(Pickup.Type t) {
        return switch(t) {
            case MEDKIT,BANDAGE       -> new Color(220,60,60);
            case KEY                  -> new Color(220,190,30);
            case ARMOR,VEST,HELMET,BOOTS -> new Color(80,140,220);
            case SPEED_BOOST          -> new Color(220,180,30);
            case PISTOL,SHOTGUN,RIFLE,BAT -> new Color(200,120,40);
            default                   -> new Color(160,160,160);
        };
    }
    private String pickupIcon(Pickup.Type t) {
        return switch(t) {
            case MEDKIT   -> "MK"; case BANDAGE -> "BD"; case KEY -> "K";
            case ARMOR    -> "AR"; case VEST -> "VS";  case HELMET -> "HM"; case BOOTS -> "BT";
            case SPEED_BOOST -> "SP";
            case PISTOL   -> "PI"; case SHOTGUN -> "SH"; case RIFLE -> "RI"; case BAT -> "BA";
            case PISTOL_AMMO -> "9m"; case RIFLE_AMMO -> "5.5"; case SHOTGUN_AMMO -> "12g";
            default        -> "?";
        };
    }

    // ── 7. NPC Zombies ────────────────────────────────────────────────────────
    private void drawNpcs(Graphics2D g, GameState gs) {
        for (NpcZombie npc : gs.getNpcZombies().values()) {
            if (!npc.isAlive()) continue;
            float wx=npc.getX(), wy=npc.getY(), s=NpcZombie.SIZE;
            float cx=wx+s/2f, cy=wy+s/2f;
            double angle=npc.getFacingAngle();
            float stagger=(float)Math.sin(animTick*0.35+wx*0.01)*3f; // stumble

            // Shadow
            g.setColor(new Color(0,0,0,70));
            g.fillOval((int)(cx-11),(int)(cy-5+4),22,10);

            AffineTransform old=g.getTransform();
            g.translate(cx,cy+stagger);
            g.rotate(angle+Math.PI/2);

            // Legs (animated stagger)
            float legOff=(float)Math.sin(animTick*0.35f+wx)*4f;
            g.setColor(new Color(50,50,35));
            g.fillOval(-4,(int)(3+legOff),7,9);
            g.fillOval(-1,(int)(3-legOff),7,9);

            // Body (torn clothes)
            g.setPaint(new GradientPaint(0,-8,new Color(55,62,32),0,10,new Color(35,42,18)));
            g.fillOval(-9,-8,18,18);
            // Wound detail
            g.setColor(new Color(120,15,15,150)); g.fillOval(-2,0,5,3);

            // Head (rotting skin)
            g.setPaint(new GradientPaint(-3,-18,C_SKIN_Z.brighter(),3,-13,C_SKIN_Z));
            g.fillOval(-7,-18,14,14);
            // Eye sockets
            g.setColor(new Color(30,8,8)); g.fillOval(-5,-15,4,4); g.fillOval(1,-15,4,4);
            g.setColor(new Color(180,20,20,200)); g.fillOval(-4,-14,3,3); g.fillOval(2,-14,3,3);
            // Mouth (open)
            g.setColor(new Color(30,5,5)); g.fillRect(-4,-10,8,3);
            g.setColor(new Color(180,20,20,120)); g.fillRect(-3,-10,6,2);

            // HP indicator (only if damaged)
            if (npc.getHealth() < 100) {
                g.setColor(new Color(0,0,0,150)); g.fillRect(-9,-26,18,4);
                g.setColor(new Color(220,40,40));
                g.fillRect(-9,-26,(int)(18*npc.getHealth()/100f),4);
            }

            g.setTransform(old);
        }
    }

    // ── 8. Players (top-down LDOE-style) ─────────────────────────────────────
    private void drawPlayers(Graphics2D g, GameState gs) {
        for (Player p : gs.getPlayers().values()) {
            float wx=p.getXf(), wy=p.getYf();
            float cx=wx+Player.SIZE/2f, cy=wy+Player.SIZE/2f;
            boolean isMe=p.getId().equals(myPlayerId);
            boolean zombie=p.isInfected();
            double angle=facingAngles.getOrDefault(p.getId(), 0.0);

            // Aim direction for local player
            if (isMe) {
                float mwx=camX+mouseScreenX, mwy=camY+mouseScreenY;
                angle=Math.atan2(mwy-cy, mwx-cx);
            }

            float wp=walkPhases.getOrDefault(p.getId(),0f);
            float legOff=(float)Math.sin(wp)*4.5f;
            boolean moving=Math.abs(legOff)>0.5f;

            // Shadow
            g.setColor(new Color(0,0,0,zombie?80:65));
            g.fillOval((int)(cx-12),(int)(cy-5+5),24,10);

            AffineTransform old=g.getTransform();
            g.translate(cx,cy);
            g.rotate(angle+Math.PI/2);

            if (zombie) {
                // ── Zombie player ──────────────────────────────────────────────
                // Legs
                g.setColor(new Color(40,55,30));
                g.fillOval(-4,(int)(4+legOff),7,9); g.fillOval(-1,(int)(4-legOff),7,9);
                // Body (rotten green)
                g.setPaint(new GradientPaint(0,-9,new Color(60,72,35),0,10,new Color(38,50,22)));
                g.fillOval(-10,-9,20,20);
                // Wound marks
                g.setColor(new Color(140,20,20,140)); g.fillOval(-3,1,5,3); g.fillOval(3,-3,4,3);
                // Head
                g.setPaint(new RadialGradientPaint(0,-15,8,new float[]{0f,1f},
                    new Color[]{C_SKIN_Z.brighter(),C_SKIN_Z.darker()}));
                g.fillOval(-8,-21,16,16);
                // Zombie eyes glow
                g.setColor(new Color(255,30,30,200)); g.fillOval(-5,-18,4,4); g.fillOval(1,-18,4,4);
                g.setColor(new Color(255,120,0,180)); g.fillOval(-4,-17,3,3); g.fillOval(2,-17,3,3);
                // Mouth
                g.setColor(new Color(30,5,5)); g.fillRect(-4,-13,8,3);
                // Arms outward
                g.setColor(new Color(95,110,55));
                g.fillOval(-15,-5,8,6); g.fillOval(8,-5,8,6);
            } else {
                // ── Survivor player ────────────────────────────────────────────
                // Boots
                g.setColor(new Color(35,28,20));
                g.fillOval(-5,(int)(5+legOff),8,9); g.fillOval(-2,(int)(5-legOff),8,9);
                // Pants (dark cargo)
                g.setColor(new Color(50,50,40));
                g.fillOval(-9,0,18,12);
                // Jacket / shirt
                Color shirt=isMe?new Color(55,80,120):new Color(45,65,35); // blue for me, green otherwise
                g.setPaint(new GradientPaint(0,-9,shirt.brighter(),0,8,shirt.darker()));
                g.fillOval(-10,-9,20,18);
                // Head (skin)
                g.setPaint(new RadialGradientPaint(0,-16,8,new float[]{0f,1f},
                    new Color[]{C_SKIN.brighter(),C_SKIN}));
                g.fillOval(-8,-22,16,16);
                // Hair
                g.setColor(new Color(50,30,15)); g.fillRect(-6,-22,12,5);
                // Eyes
                g.setColor(new Color(30,30,50)); g.fillOval(-4,-17,4,3); g.fillOval(1,-17,4,3);

                // Weapon arm + weapon (points toward mouse/facing)
                g.rotate(0); // arms always point toward aim angle, handled by parent rotation
                drawWeapon(g, p.getCurrentWeapon(), p.currentAmmo());
            }

            g.setTransform(old);

            // Name tag
            g.setFont(new Font("Segoe UI",Font.BOLD,10));
            FontMetrics fm=g.getFontMetrics();
            String tag=(isMe?"▶ ":"")+p.getName();
            int tw=fm.stringWidth(tag);
            g.setColor(new Color(0,0,0,140)); g.fillRoundRect((int)(cx-tw/2-3),(int)(wy-20),tw+6,12,4,4);
            g.setColor(isMe?new Color(220,190,60):zombie?new Color(100,200,80):new Color(180,190,220));
            g.drawString(tag,(int)(cx-tw/2f),(int)(wy-11));

            // HP bar above player
            int bw=28;
            g.setColor(new Color(20,10,10,180)); g.fillRect((int)(cx-bw/2),(int)(wy-32),bw,4);
            Color hpCol=p.getHealth()>60?new Color(40,200,60):p.getHealth()>30?new Color(220,170,20):new Color(220,40,40);
            g.setColor(hpCol); g.fillRect((int)(cx-bw/2),(int)(wy-32),(int)(bw*p.getHealth()/100f),4);

            // "ME" highlight circle
            if (isMe) {
                g.setColor(new Color(220,190,60,50));
                g.setStroke(new BasicStroke(1.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,1f,new float[]{4,4},0));
                g.drawOval((int)(cx-16),(int)(cy-16),32,32);
                g.setStroke(new BasicStroke(1f));
            }

            // Stun stars
            if (p.isStunned()) {
                g.setFont(new Font("Segoe UI Symbol",Font.PLAIN,10));
                g.setColor(new Color(255,230,50));
                for (int s=0;s<3;s++) {
                    double sa=animTick*0.15+s*2.09;
                    g.drawString("★",(int)(cx-5+Math.cos(sa)*10),(int)(wy-24+Math.sin(sa)*4));
                }
            }
        }
    }

    private void drawWeapon(Graphics2D g, Weapon w, int ammo) {
        int col=(ammo==0&&ammo>=0)?0xCC2020:0xCCCCAA;
        g.setColor(new Color(col));
        switch(w) {
            case FIST -> {
                g.setColor(C_SKIN); g.fillOval(6,-3,8,8);
            }
            case PISTOL -> {
                g.setColor(new Color(55,55,60)); g.fillRoundRect(8,-3,14,6,2,2);
                g.setColor(new Color(40,40,45)); g.fillRect(8,-1,18,4);
                g.setColor(new Color(80,80,85)); g.fillRect(22,-3,4,2);
            }
            case SHOTGUN -> {
                g.setColor(new Color(50,38,25)); g.fillRoundRect(4,-3,24,8,3,3);
                g.setColor(new Color(65,50,30)); g.fillRect(8,-4,16,4);
                g.setColor(new Color(45,40,35)); g.fillRect(24,-2,5,3);
            }
            case RIFLE -> {
                g.setColor(new Color(40,48,40)); g.fillRoundRect(4,-2,30,6,2,2);
                g.setColor(new Color(55,65,55)); g.fillRect(8,-3,22,4);
                g.setColor(new Color(35,42,35)); g.fillRect(30,-3,8,3); // barrel
                g.setColor(new Color(80,90,80)); g.fillRect(14,-1,6,2); // scope
            }
            case BAT -> {
                g.setColor(new Color(100,65,30)); g.setStroke(new BasicStroke(3f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.drawLine(6,-2,28,0); g.setStroke(new BasicStroke(1f));
                g.setColor(new Color(140,90,40)); g.drawLine(6,-2,10,-1);
            }
        }
    }

    // ── 9. Bullets ────────────────────────────────────────────────────────────
    private void drawBullets(Graphics2D g, GameState gs) {
        for (Bullet b : new ArrayList<>(gs.getBullets())) {
            float bx=b.getX(), by=b.getY();
            double ba=Math.atan2(b.getDy(), b.getDx());
            boolean isShotgun = b.getWeapon() == Weapon.SHOTGUN;
            int tl=isShotgun?6:10;
            // Trail
            g.setColor(new Color(255,220,80,80));
            g.setStroke(new BasicStroke(isShotgun?1.5f:2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            g.drawLine((int)(bx-(float)Math.cos(ba)*tl),(int)(by-(float)Math.sin(ba)*tl),(int)bx,(int)by);
            // Head
            g.setColor(new Color(255,240,120));
            g.fillOval((int)(bx-2),(int)(by-2),4,4);
            g.setStroke(new BasicStroke(1f));
        }
    }

    // ── 10. Particles ─────────────────────────────────────────────────────────
    private void drawParticles(Graphics2D g) {
        for (float[] p : particles) {
            float a=Math.max(0,p[4]/p[5]);
            int al=(int)(a*220);
            if (al<=0) continue;
            g.setColor(new Color((int)p[6],(int)p[7],(int)p[8],al));
            float s=p[9]*a;
            g.fillOval((int)(p[0]-s/2),(int)(p[1]-s/2),(int)s,(int)s);
        }
    }

    // ── 11. Dynamic lighting (LDOE dark lantern) ──────────────────────────────
    private void drawDynLighting(Graphics2D g, GameState gs, int cx, int cy, int vw, int vh) {
        if (lightMap==null||lightMap.getWidth()!=vw||lightMap.getHeight()!=vh)
            lightMap=new BufferedImage(vw,vh,BufferedImage.TYPE_INT_ARGB);
        Graphics2D lg=lightMap.createGraphics();
        lg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        // Much less dark — alpha 65 instead of 185 so the map is clearly visible
        lg.setComposite(AlphaComposite.Clear); lg.fillRect(0,0,vw,vh);
        lg.setComposite(AlphaComposite.Src);
        lg.setColor(new Color(0,0,0,65)); lg.fillRect(0,0,vw,vh);
        lg.setComposite(AlphaComposite.DstOut);

        // Player warm lantern light — larger radius so surroundings are visible
        for (Player p : gs.getPlayers().values()) {
            int wx=p.getX()+Player.SIZE/2, wy=p.getY()+Player.SIZE/2;
            int sx=wx-cx, sy=wy-cy;
            boolean isMe=p.getId().equals(myPlayerId);
            float rad=isMe?(p.isInfected()?160:300):200;  // was 180/120
            if (p.hasSpeedBoost()) rad*=1.15f;
            if (sx+rad<0||sx-rad>vw||sy+rad<0||sy-rad>vh) continue;
            try {
                lg.setPaint(new RadialGradientPaint(sx,sy,rad,
                    new float[]{0f,0.5f,1f},
                    new Color[]{new Color(255,240,190,255),new Color(255,220,140,160),new Color(255,180,80,0)}));
                lg.fillOval((int)(sx-rad),(int)(sy-rad),(int)(rad*2),(int)(rad*2));
            } catch (Exception ignored){}
        }

        // NPC zombie faint red glow
        for (NpcZombie npc : gs.getNpcZombies().values()) {
            if (!npc.isAlive()) continue;
            int sx=(int)(npc.getX()+NpcZombie.SIZE/2-cx);
            int sy=(int)(npc.getY()+NpcZombie.SIZE/2-cy);
            if (sx<-60||sx>vw+60||sy<-60||sy>vh+60) continue;
            try {
                lg.setPaint(new RadialGradientPaint(sx,sy,45,
                    new float[]{0f,1f},new Color[]{new Color(200,30,0,90),new Color(0,0,0,0)}));
                lg.fillOval(sx-45,sy-45,90,90);
            } catch(Exception ignored){}
        }
        lg.dispose();

        AffineTransform old=g.getTransform();
        g.setTransform(new AffineTransform());
        g.setComposite(AlphaComposite.SrcOver);
        g.drawImage(lightMap,0,0,null);
        g.setTransform(old);
    }

    // ── 12. Kill feed ─────────────────────────────────────────────────────────
    private void drawKillFeed(Graphics2D g, GameState gs, int cx, int cy, int vw) {
        List<KillFeedEntry> feed=new ArrayList<>(gs.getKillFeed()); if (feed.isEmpty()) return;
        int kx=cx+vw-280, ky=cy+16;
        for (int i=feed.size()-1;i>=0;i--) {
            KillFeedEntry e=feed.get(i);
            long age=System.currentTimeMillis()-e.getTimestamp();
            float al=Math.max(0f,1f-age/6000f);
            int a=(int)(al*200); if(a<=0) continue;
            String text=e.getKillerName()+" "+e.icon()+" "+e.getVictimName();
            g.setFont(new Font("Segoe UI",Font.BOLD,11));
            FontMetrics fm=g.getFontMetrics(); int tw=fm.stringWidth(text);
            g.setColor(new Color(12,10,6,(int)(a*0.75f))); g.fillRoundRect(kx-tw-10,ky-13,tw+14,16,4,4);
            g.setColor(new Color(200,185,140,a)); g.drawString(text,kx-tw,ky);
            ky+=22;
        }
    }

    // ── 13. Minimap (LDOE dark style) ────────────────────────────────────────
    private void drawMinimap(Graphics2D g, GameState gs, int vw, int vh) {
        int mmW=180,mmH=134,mmX=vw-mmW-12,mmY=vh-mmH-12;
        float sx=mmW/(float)CW, sy=mmH/(float)CH;

        // Dark background with border
        g.setColor(new Color(8,8,6,210)); g.fillRoundRect(mmX-3,mmY-3,mmW+6,mmH+6,6,6);
        g.setColor(new Color(14,14,10,240)); g.fillRect(mmX,mmY,mmW,mmH);

        // Tile sampling
        for (int row=0;row<MH;row+=2) for (int col=0;col<MW;col+=2) {
            int tile=GameState.MAP[row][col];
            Color tc=tile==1?new Color(55,52,46):tile==2?new Color(25,50,25):new Color(28,38,18);
            g.setColor(tc);
            g.fillRect(mmX+(int)(col*TS*sx),mmY+(int)(row*TS*sy),Math.max(1,(int)(2*TS*sx)),Math.max(1,(int)(2*TS*sy)));
        }

        // Viewport rect
        g.setColor(new Color(200,180,80,35));
        int vx2=mmX+(int)(camX*sx),vy2=mmY+(int)(camY*sy);
        g.fillRect(vx2,vy2,(int)(getWidth()*sx),(int)(getHeight()*sy));
        g.setColor(new Color(200,180,80,80));
        g.drawRect(vx2,vy2,(int)(getWidth()*sx),(int)(getHeight()*sy));

        // NPCs (red dots)
        for (NpcZombie npc : gs.getNpcZombies().values()) {
            if (!npc.isAlive()) continue;
            g.setColor(new Color(180,35,35,180));
            g.fillOval(mmX+(int)(npc.getX()*sx)-2,mmY+(int)(npc.getY()*sy)-2,4,4);
        }

        // Players
        for (Player p : gs.getPlayers().values()) {
            boolean isMe=p.getId().equals(myPlayerId);
            g.setColor(p.isInfected()?new Color(80,200,80):isMe?new Color(220,190,60):new Color(100,155,220));
            int mx=mmX+(int)(p.getXf()*sx),my=mmY+(int)(p.getYf()*sy);
            g.fillOval(mx-3,my-3,6,6);
            if (isMe){g.setColor(new Color(220,190,60,80));g.drawOval(mx-5,my-5,10,10);}
        }

        // Border glow
        g.setColor(new Color(80,65,30,150)); g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(mmX-3,mmY-3,mmW+6,mmH+6,6,6); g.setStroke(new BasicStroke(1f));
        // Label
        g.setFont(new Font("Segoe UI",Font.BOLD,8)); g.setColor(new Color(100,85,45));
        g.drawString("TACTICAL MAP",mmX+4,mmY-5);
    }

    // ── 14. Crosshair ─────────────────────────────────────────────────────────
    private void drawCrosshair(Graphics2D g, GameState gs, int vw, int vh) {
        Player me=gs.getPlayer(myPlayerId);
        if (me==null||me.isInfected()) return;
        int mx=mouseScreenX,my=mouseScreenY;
        // Outer ring
        g.setColor(new Color(220,200,100,130));
        g.setStroke(new BasicStroke(1.2f));
        g.drawOval(mx-12,my-12,24,24);
        // Cross
        g.setColor(new Color(255,240,150,200));
        g.drawLine(mx-18,my,mx-5,my); g.drawLine(mx+5,my,mx+18,my);
        g.drawLine(mx,my-18,mx,my-5); g.drawLine(mx,my+5,mx,my+18);
        // Center dot
        g.setColor(new Color(255,240,150,240));
        g.fillOval(mx-2,my-2,4,4);
        g.setStroke(new BasicStroke(1f));
    }

    // ── 15. Vignette ─────────────────────────────────────────────────────────
    private void drawVignette(Graphics2D g, GameState gs, int vw, int vh) {
        Player me=gs.getPlayer(myPlayerId);
        boolean zomb=(me!=null&&me.isInfected());
        float pulse=0.3f+0.25f*(float)Math.sin(animTick*0.07);
        Color vc=zomb?new Color(80,0,0,(int)(160*pulse)):new Color(0,0,0,100);
        g.setPaint(new RadialGradientPaint(vw/2f,vh/2f,Math.max(vw,vh)*0.62f,
            new float[]{0.4f,1f},new Color[]{new Color(0,0,0,0),vc}));
        g.fillRect(0,0,vw,vh);
    }

    // ── Game-over overlay ─────────────────────────────────────────────────────
    private void renderGameOver(Graphics2D g, GameState gs, int vw, int vh) {
        g.setColor(new Color(0,0,0,175)); g.fillRect(0,0,vw,vh);
        String winner=gs.getWinnerMessage();
        boolean survWin=winner.contains("SURVIVOR");

        // Flare
        Color flare=survWin?new Color(60,200,80,50):new Color(200,40,40,50);
        g.setPaint(new RadialGradientPaint(vw/2f,vh/2f-60,220,new float[]{0f,1f},new Color[]{flare,new Color(0,0,0,0)}));
        g.fillRect(0,0,vw,vh);

        g.setFont(new Font("Impact",Font.PLAIN,70));
        g.setColor(new Color(0,0,0,200)); drawCentered(g,winner,vw/2+4,vh/2-54+4);
        g.setPaint(survWin?new GradientPaint(vw/2-200,vh/2-120,new Color(80,200,80),vw/2+200,vh/2-50,new Color(40,160,40))
                          :new GradientPaint(vw/2-200,vh/2-120,new Color(220,50,50),vw/2+200,vh/2-50,new Color(160,20,20)));
        drawCentered(g,winner,vw/2,vh/2-58);

        g.setFont(new Font("Segoe UI",Font.PLAIN,16));
        g.setColor(new Color(180,175,150));
        drawCentered(g,"Resetting in "+gs.getGameOverCountdown()+" seconds...",vw/2,vh/2-10);

        // Scoreboard
        List<Player> ranked=new ArrayList<>(gs.getPlayers().values());
        ranked.sort((a,b)->b.getScore()-a.getScore());
        int sy=vh/2+28;
        g.setFont(new Font("Segoe UI",Font.BOLD,12)); g.setColor(new Color(150,140,100));
        drawCentered(g,"── SCOREBOARD ──",vw/2,sy); sy+=26;
        for (int i=0;i<Math.min(ranked.size(),5);i++) {
            Player p=ranked.get(i);
            String line=(i+1)+".  "+p.getName()+"   "+p.getScore()+" pts   "+p.getKills()+" kills";
            g.setColor(i==0?new Color(215,180,25):i==1?new Color(185,190,200):i==2?new Color(180,140,80):new Color(120,120,110));
            g.setFont(new Font("Segoe UI",i<3?Font.BOLD:Font.PLAIN,12));
            drawCentered(g,line,vw/2,sy); sy+=22;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void drawCentered(Graphics2D g, String text, int cx, int y) {
        FontMetrics fm=g.getFontMetrics(); g.drawString(text,cx-fm.stringWidth(text)/2,y);
    }
    private float rndF() { return (float)Math.random(); }

    // ── Tile texture builders ─────────────────────────────────────────────────

    private BufferedImage makeTileGrass(int variant, Color base) {
        BufferedImage img=new BufferedImage(TS,TS,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=img.createGraphics();
        g.setColor(base); g.fillRect(0,0,TS,TS);
        Random r=new Random(variant*1337L+77);
        for (int i=0;i<35;i++) {
            int bx=r.nextInt(TS), by=r.nextInt(TS), bs=r.nextInt(3)+1;
            int dr=r.nextInt(16)-8, dg=r.nextInt(18)-9;
            Color bc=new Color(clamp(base.getRed()+dr),clamp(base.getGreen()+dg),clamp(base.getBlue()+dr/2));
            g.setColor(bc); g.fillRect(bx,by,bs,bs);
        }
        // Occasional small pebble
        for (int i=0;i<4;i++){
            int bx=r.nextInt(TS),by=r.nextInt(TS);
            g.setColor(new Color(48,46,42)); g.fillRect(bx,by,2,2);
        }
        g.dispose(); return img;
    }

    private BufferedImage makeTileWall() {
        BufferedImage img=new BufferedImage(TS,TS,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=img.createGraphics();
        // Brighter stone base so walls are clearly visible
        g.setColor(C_WALL_TOP); g.fillRect(0,0,TS,TS);
        g.setColor(new Color(55,50,44));
        // Brick rows
        for (int row=0;row<4;row++){
            int y=row*8;
            g.drawLine(0,y,TS,y);
            int off=(row%2==0)?0:TS/2;
            g.drawLine(off,y,off,y+8);
            g.drawLine(off+TS/2,y,off+TS/2,y+8);
        }
        // Top highlight for 3D look
        g.setColor(new Color(100,95,88)); g.fillRect(0,0,TS,3);
        // Bottom shadow
        g.setColor(new Color(30,27,24)); g.fillRect(0,TS-4,TS,4);
        g.dispose(); return img;
    }

    private BufferedImage makeTileSafe() {
        BufferedImage img=new BufferedImage(TS,TS,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=img.createGraphics();
        g.setColor(C_SAFE_FL); g.fillRect(0,0,TS,TS);
        g.setColor(new Color(18,36,18));
        for (int i=0;i<TS;i+=8){ g.drawLine(i,0,i,TS); g.drawLine(0,i,TS,i); }
        g.setColor(new Color(28,70,28,70)); g.fillRect(2,2,TS-4,TS-4);
        g.dispose(); return img;
    }

    private BufferedImage makeTileConcrete() {
        BufferedImage img=new BufferedImage(TS,TS,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=img.createGraphics();
        g.setColor(C_CONCRETE); g.fillRect(0,0,TS,TS);
        // Crack lines
        g.setColor(new Color(35,35,40)); g.drawLine(4,8,14,22); g.drawLine(18,2,28,18);
        g.setColor(new Color(65,65,70)); g.fillRect(0,0,TS,2); g.fillRect(0,0,2,TS);
        g.dispose(); return img;
    }

    private BufferedImage makeTileAsphalt() {
        BufferedImage img=new BufferedImage(TS,TS,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=img.createGraphics();
        g.setColor(C_ASPHALT); g.fillRect(0,0,TS,TS);
        // Noise
        Random r=new Random(42L);
        for (int i=0;i<20;i++){
            int bx=r.nextInt(TS),by=r.nextInt(TS);
            g.setColor(new Color(38,38,42)); g.fillRect(bx,by,2,1);
        }
        g.dispose(); return img;
    }

    private BufferedImage makeTileDirt() {
        BufferedImage img=new BufferedImage(TS,TS,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=img.createGraphics();
        g.setColor(C_DIRT); g.fillRect(0,0,TS,TS);
        Random r=new Random(77L);
        for (int i=0;i<30;i++){
            int bx=r.nextInt(TS),by=r.nextInt(TS);
            g.setColor(new Color(clamp(C_DIRT.getRed()+r.nextInt(14)-7),
                                 clamp(C_DIRT.getGreen()+r.nextInt(10)-5),
                                 clamp(C_DIRT.getBlue()+r.nextInt(8)-4)));
            g.fillRect(bx,by,2,2);
        }
        g.dispose(); return img;
    }

    private BufferedImage makeTileWoodFloor() {
        BufferedImage img=new BufferedImage(TS,TS,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=img.createGraphics();
        g.setColor(C_WOOD_FL); g.fillRect(0,0,TS,TS);
        // Plank lines
        g.setColor(new Color(38,28,16));
        for (int x=0;x<TS;x+=8) g.drawLine(x,0,x,TS);
        g.setColor(new Color(60,44,26,80)); g.fillRect(2,1,4,TS-2);
        g.dispose(); return img;
    }

    private static int clamp(int v){ return Math.max(0,Math.min(255,v)); }
}
