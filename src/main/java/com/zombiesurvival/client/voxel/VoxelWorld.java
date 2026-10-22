package com.zombiesurvival.client.voxel;

import com.zombiesurvival.shared.GameState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Manages the 3D block grid for the 25×18 arena.
 *
 * Coordinate conventions
 * ──────────────────────
 *   bx  = MAP column  (0 .. WORLD_W-1 = 0..24)
 *   bz  = MAP row     (0 .. WORLD_D-1 = 0..17)
 *   by  = block layer (0 = ground, 1..2 = pillar/wall layers)
 *
 *   A block at (bx,by,bz) occupies world space
 *       X ∈ [bx, bx+1], Y ∈ [by, by+1], Z ∈ [bz, bz+1]
 *
 *   Floor block top face is at world Y = 1.
 *   Camera (eye) rest height = 1.0 + VoxelCamera.EYE_HEIGHT ≈ 2.62.
 *
 * Biome layout (interior only, biome boundaries may be tuned via constants)
 * ─────────────────────────────────────────────────────────────────────────
 *   Top-left     (bx < BIOME_X_MID, bz < BIOME_Z_MID) → Grass / Forest
 *   Top-right    (bx > BIOME_X_MID, bz < BIOME_Z_MID) → Desert / Sand
 *   Bottom-left  (bx < BIOME_X_MID, bz > BIOME_Z_MID) → Snow / Ice
 *   Bottom-right (bx > BIOME_X_MID, bz > BIOME_Z_MID) → Nether
 *   Centre band  (within BIOME_CENTER_* bounds)         → Stone plaza
 *   Transitions  (border bands)                         → 3-tile dither
 */
public class VoxelWorld {

    // ── Block type IDs ────────────────────────────────────────────────────────
    public static final int AIR         = 0;
    public static final int GRASS       = 1;
    public static final int SAND        = 2;
    public static final int SNOW        = 3;
    public static final int NETHER      = 4;
    public static final int STONE_PLAZA = 5;
    public static final int STONE_WALL  = 6;  // 3-block border wall
    public static final int PILLAR      = 7;  // 2-block interior obstacle

    // ── World dimensions ──────────────────────────────────────────────────────
    public static final int WORLD_W = 25;  // MAP_WIDTH  – X axis
    public static final int WORLD_H = 4;   // block layer count (0..3)
    public static final int WORLD_D = 18;  // MAP_HEIGHT – Z axis

    // ── Tunable biome split coordinates (interior block coords) ───────────────
    /** Columns strictly less than this go to the LEFT biome group. */
    private static final int BIOME_CTR_X0 = 9;
    /** Columns strictly greater than this go to the RIGHT biome group. */
    private static final int BIOME_CTR_X1 = 15;
    /** Rows strictly less than this go to the TOP biome group. */
    private static final int BIOME_CTR_Z0 = 7;
    /** Rows strictly greater than this go to the BOTTOM biome group. */
    private static final int BIOME_CTR_Z1 = 10;

    // ── Player collision AABB ─────────────────────────────────────────────────
    /** Player XZ half-width for wall push-out. */
    private static final float PLAYER_HALF_XZ = 0.3f;

    // ── Internal state ────────────────────────────────────────────────────────
    private final int[][][] blocks = new int[WORLD_W][WORLD_H][WORLD_D];
    private final VoxelChunk chunk;

    // ── Construction ─────────────────────────────────────────────────────────

    public VoxelWorld(TextureAtlas3D atlas) {
        buildBlocks();
        chunk = new VoxelChunk();
        chunk.buildMesh(this, atlas);
    }

    private void buildBlocks() {
        int[][] map = GameState.MAP;

        for (int bz = 0; bz < WORLD_D; bz++) {
            for (int bx = 0; bx < WORLD_W; bx++) {
                int  cell   = map[bz][bx];
                boolean border = (bx == 0 || bx == WORLD_W - 1 ||
                                  bz == 0 || bz == WORLD_D - 1);

                if (border) {
                    // 3-block-high tinted stone-brick border wall
                    blocks[bx][0][bz] = STONE_WALL;
                    blocks[bx][1][bz] = STONE_WALL;
                    blocks[bx][2][bz] = STONE_WALL;

                } else if (cell == 1) {
                    // Interior obstacle → 2-block pillar (high enough to block movement)
                    blocks[bx][0][bz] = PILLAR;
                    blocks[bx][1][bz] = PILLAR;

                } else {
                    // Walkable floor (MAP value 0 or 2) → single ground block
                    blocks[bx][0][bz] = getFloorBiome(bx, bz, cell);
                }
            }
        }
    }

    /**
     * Assign a floor block type for an interior walkable tile.
     * Centre gets stone plaza; quadrants get their biome; border bands use
     * a deterministic 3-tile dither so biomes blend rather than hard-cut.
     */
    private int getFloorBiome(int bx, int bz, int cell) {
        // MAP safe-zone tiles are always stone plaza
        if (cell == 2) return STONE_PLAZA;

        // Explicit centre plaza band
        if (bx >= BIOME_CTR_X0 && bx <= BIOME_CTR_X1 &&
            bz >= BIOME_CTR_Z0 && bz <= BIOME_CTR_Z1) {
            return STONE_PLAZA;
        }

        // Quadrant classification
        boolean left  = bx < BIOME_CTR_X0;
        boolean right = bx > BIOME_CTR_X1;
        boolean top   = bz < BIOME_CTR_Z0;
        boolean bot   = bz > BIOME_CTR_Z1;

        // Clean quadrant corners
        if (left  && top)  return GRASS;
        if (right && top)  return SAND;
        if (left  && bot)  return SNOW;
        if (right && bot)  return NETHER;

        // ── Transition strips: 3-tile dither ─────────────────────────────────
        // Hash mixes bx and bz so adjacent tiles scatter across all 3 types.
        int hash = Math.abs(bx * 7 + bz * 13) % 3;

        // Vertical centre strip (between left and right quadrants): blend with plaza
        if (!left && !right) {
            if (top)  { return hash == 0 ? GRASS  : (hash == 1 ? STONE_PLAZA : SAND);   }
            if (bot)  { return hash == 0 ? SNOW   : (hash == 1 ? STONE_PLAZA : NETHER); }
        }

        // Horizontal centre strip (between top and bottom quadrants): blend with plaza
        if (!top && !bot) {
            if (left)  { return hash == 0 ? GRASS  : (hash == 1 ? STONE_PLAZA : SNOW);   }
            if (right) { return hash == 0 ? SAND   : (hash == 1 ? STONE_PLAZA : NETHER); }
        }

        // Corner transition (diagonal overlap zone)
        return STONE_PLAZA;
    }

    // ── Block accessors ───────────────────────────────────────────────────────

    /** Returns AIR for out-of-bounds queries (used by face-cull in VoxelChunk). */
    public int getBlock(int bx, int by, int bz) {
        if (bx < 0 || bx >= WORLD_W || by < 0 || by >= WORLD_H ||
            bz < 0 || bz >= WORLD_D) return AIR;
        return blocks[bx][by][bz];
    }

    public boolean isSolid(int bx, int by, int bz) {
        return getBlock(bx, by, bz) != AIR;
    }

    // ── Texture queries ───────────────────────────────────────────────────────

    /** Atlas tile ID for the top (+Y) face of the given block type. */
    public int getTopTile(int block) {
        switch (block) {
            case GRASS:       return TextureAtlas3D.TILE_GRASS_TOP;
            case SAND:        return TextureAtlas3D.TILE_SAND;
            case SNOW:        return TextureAtlas3D.TILE_SNOW_TOP;
            case NETHER:      return TextureAtlas3D.TILE_NETHERRACK;
            case STONE_PLAZA: return TextureAtlas3D.TILE_COBBLESTONE;
            case STONE_WALL:  return TextureAtlas3D.TILE_COBBLESTONE;
            case PILLAR:      return TextureAtlas3D.TILE_COBBLESTONE;
            default:          return TextureAtlas3D.TILE_COBBLESTONE;
        }
    }

    /** Atlas tile ID for the four side faces of the given block type. */
    public int getSideTile(int block) {
        switch (block) {
            case GRASS:       return TextureAtlas3D.TILE_GRASS_SIDE;
            case SAND:        return TextureAtlas3D.TILE_SANDSTONE;
            case SNOW:        return TextureAtlas3D.TILE_SNOW_SIDE;
            case NETHER:      return TextureAtlas3D.TILE_NETHERRACK;
            case STONE_PLAZA: return TextureAtlas3D.TILE_COBBLESTONE;
            case STONE_WALL:  return TextureAtlas3D.TILE_COBBLESTONE;
            case PILLAR:      return TextureAtlas3D.TILE_COBBLESTONE;
            default:          return TextureAtlas3D.TILE_COBBLESTONE;
        }
    }

    // ── AABB Collision ────────────────────────────────────────────────────────

    /**
     * Push the player out of any solid wall blocks via XZ AABB resolution.
     * Vertical collision (gravity / floor) is already handled by VoxelCamera.update().
     *
     * The player body is modelled as a column:
     *   XZ footprint : [pos.x ± PLAYER_HALF_XZ,  pos.z ± PLAYER_HALF_XZ]
     *   Y range      : [pos.y - EYE_HEIGHT,  pos.y + 0.1]
     *
     * Only block layers by ≥ 1 are tested for XZ collision: the ground-floor
     * blocks (by=0) support the player vertically but must not interfere with
     * lateral movement (they are below feet level at rest).
     *
     * Three iterations handle corner-clip scenarios.
     */
    public void resolveCollision(VoxelCamera camera) {
        Vector3f pos   = camera.getPosition();
        float    headY = pos.y + 0.1f;

        // Block layers to test: skip by=0 (floor blocks) to avoid lateral pushes
        // when standing on them.  Check by=1 upward through head height.
        int byLow  = 1;  // wall/pillar layers start at by=1
        int byHigh = Math.min(WORLD_H - 1, (int) Math.floor(headY));

        for (int iter = 0; iter < 3; iter++) {
            float minX = pos.x - PLAYER_HALF_XZ;
            float maxX = pos.x + PLAYER_HALF_XZ;
            float minZ = pos.z - PLAYER_HALF_XZ;
            float maxZ = pos.z + PLAYER_HALF_XZ;

            int bxMin = (int) Math.floor(minX);
            int bxMax = (int) Math.floor(maxX);
            int bzMin = (int) Math.floor(minZ);
            int bzMax = (int) Math.floor(maxZ);

            for (int bx = bxMin; bx <= bxMax; bx++) {
                for (int bz = bzMin; bz <= bzMax; bz++) {

                    // Is there ANY solid block in the player's vertical slice?
                    boolean solid = false;
                    for (int by = byLow; by <= byHigh; by++) {
                        if (isSolid(bx, by, bz)) { solid = true; break; }
                    }
                    if (!solid) continue;

                    // XZ overlap check
                    float blkMinX = bx, blkMaxX = bx + 1f;
                    float blkMinZ = bz, blkMaxZ = bz + 1f;
                    if (maxX <= blkMinX || minX >= blkMaxX) continue;
                    if (maxZ <= blkMinZ || minZ >= blkMaxZ) continue;

                    // Penetration depths along each axis
                    float oPX = maxX - blkMinX;  // overlap pushing player -X
                    float oNX = blkMaxX - minX;  // overlap pushing player +X
                    float oPZ = maxZ - blkMinZ;  // overlap pushing player -Z
                    float oNZ = blkMaxZ - minZ;  // overlap pushing player +Z

                    // Resolve on the axis with smallest penetration (smooth sliding)
                    float minOv = Math.min(Math.min(oPX, oNX), Math.min(oPZ, oNZ));
                    if      (minOv == oPX) pos.x -= oPX;
                    else if (minOv == oNX) pos.x += oNX;
                    else if (minOv == oPZ) pos.z -= oPZ;
                    else                   pos.z += oNZ;

                    // Recompute footprint for next block in this iteration
                    minX = pos.x - PLAYER_HALF_XZ;  maxX = pos.x + PLAYER_HALF_XZ;
                    minZ = pos.z - PLAYER_HALF_XZ;  maxZ = pos.z + PLAYER_HALF_XZ;
                }
            }
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    public void render(VoxelShader shader, TextureAtlas3D atlas, Matrix4f model) {
        chunk.render(shader, atlas, model);
    }

    public void cleanup() {
        chunk.cleanup();
    }
}
