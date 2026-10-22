package com.zombiesurvival.client.ui;

import com.zombiesurvival.shared.GameState;
import java.awt.Color;

/**
 * BiomeTheme holds all Minecraft-style palettes for the 4 map corners and central plaza.
 * Modify the constants here to retune any biome's colors.
 */
public final class BiomeTheme {

    public enum Biome {
        FOREST,
        DESERT,
        SNOW,
        NETHER,
        PLAZA
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. TOP-LEFT: FOREST BIOME
    // ─────────────────────────────────────────────────────────────────────────
    public static final Color FOREST_FLOOR_BASE     = new Color(91, 135, 49);   // Classic Minecraft Grass
    public static final Color FOREST_FLOOR_VAR1     = new Color(76, 120, 40);   // Lush Grass Shadow
    public static final Color FOREST_FLOOR_VAR2     = new Color(102, 148, 55);  // Highlight Grass
    public static final Color FOREST_FLOOR_PATCH    = new Color(134, 96, 67);   // Dirt Path Patch
    public static final Color FOREST_BLOCK_MAIN     = new Color(115, 115, 115); // Mossy / Stone Brick
    public static final Color FOREST_BLOCK_EDGE     = new Color(75, 75, 75);    // Darker Bevel Edge
    public static final Color FOREST_BLOCK_OUTLINE  = new Color(38, 38, 38);    // Hard 1px Dark Outline
    public static final Color FOREST_LEAF_BASE      = new Color(56, 101, 35);   // Oak Leaves
    public static final Color FOREST_LEAF_VAR       = new Color(42, 82, 26);    // Dense Foliage
    public static final Color FOREST_ACCENT_RED     = new Color(224, 48, 48);   // Poppy Flower
    public static final Color FOREST_ACCENT_YEL     = new Color(255, 230, 48);  // Dandelion Flower
    public static final Color FOREST_WALL_TINT      = new Color(48, 62, 45);    // Mossy Bedrock Tint

    // ─────────────────────────────────────────────────────────────────────────
    // 2. TOP-RIGHT: DESERT BIOME
    // ─────────────────────────────────────────────────────────────────────────
    public static final Color DESERT_FLOOR_BASE     = new Color(217, 201, 138); // Sand Base
    public static final Color DESERT_FLOOR_VAR1     = new Color(203, 187, 122); // Shadow Sand
    public static final Color DESERT_FLOOR_VAR2     = new Color(228, 214, 153); // Sunlit Sand
    public static final Color DESERT_FLOOR_PATCH    = new Color(194, 175, 110); // Sandstone Floor Slab
    public static final Color DESERT_BLOCK_MAIN     = new Color(188, 167, 110); // Cut Sandstone Block
    public static final Color DESERT_BLOCK_EDGE     = new Color(152, 132, 82);  // Sandstone Bevel Edge
    public static final Color DESERT_BLOCK_OUTLINE  = new Color(92, 78, 46);    // Hard 1px Outline
    public static final Color DESERT_CACTUS_BASE    = new Color(82, 125, 36);   // Cactus Block
    public static final Color DESERT_CACTUS_SPINE   = new Color(48, 76, 20);    // Cactus Spines
    public static final Color DESERT_DEAD_BUSH      = new Color(122, 88, 51);   // Dead Bush Wood
    public static final Color DESERT_WALL_TINT      = new Color(72, 65, 50);    // Sandy Bedrock Tint

    // ─────────────────────────────────────────────────────────────────────────
    // 3. BOTTOM-LEFT: SNOW / TUNDRA BIOME
    // ─────────────────────────────────────────────────────────────────────────
    public static final Color SNOW_FLOOR_BASE       = new Color(240, 245, 245); // Fresh Snow
    public static final Color SNOW_FLOOR_VAR1       = new Color(219, 228, 230); // Packed Snow
    public static final Color SNOW_FLOOR_VAR2       = new Color(250, 255, 255); // Gleaming Snow
    public static final Color SNOW_FLOOR_PATCH      = new Color(144, 184, 204); // Packed Ice Patch
    public static final Color SNOW_BLOCK_MAIN       = new Color(210, 222, 228); // Snow Block / Stone
    public static final Color SNOW_BLOCK_EDGE       = new Color(165, 178, 188); // Ice Shadow Bevel Edge
    public static final Color SNOW_BLOCK_OUTLINE    = new Color(70, 82, 95);    // Dark 1px Outline
    public static final Color SNOW_SPRUCE_LEAF      = new Color(40, 66, 46);    // Spruce Needles
    public static final Color SNOW_ACCENT_ICE       = new Color(165, 210, 235); // Ice Crystals
    public static final Color SNOW_WALL_TINT        = new Color(50, 60, 72);    // Frosty Bedrock Tint

    // ─────────────────────────────────────────────────────────────────────────
    // 4. BOTTOM-RIGHT: NETHER / RUINS BIOME
    // ─────────────────────────────────────────────────────────────────────────
    public static final Color NETHER_FLOOR_BASE     = new Color(110, 36, 36);   // Netherrack Red
    public static final Color NETHER_FLOOR_VAR1     = new Color(86, 28, 28);    // Dark Nether Crust
    public static final Color NETHER_FLOOR_VAR2     = new Color(128, 44, 44);   // Hot Nether Surface
    public static final Color NETHER_FLOOR_PATCH    = new Color(74, 55, 45);    // Soul Sand Patch
    public static final Color NETHER_BLOCK_MAIN     = new Color(39, 35, 43);    // Polished Blackstone
    public static final Color NETHER_BLOCK_EDGE     = new Color(24, 21, 28);    // Deep Shadow Bevel
    public static final Color NETHER_BLOCK_OUTLINE  = new Color(15, 12, 18);    // Void Outline
    public static final Color NETHER_ACCENT_EMBER   = new Color(255, 110, 32);  // Fiery Ember
    public static final Color NETHER_ACCENT_SOUL    = new Color(55, 160, 180);  // Soul Fire Glimmer
    public static final Color NETHER_WALL_TINT      = new Color(65, 30, 32);    // Nether Bedrock Tint

    // ─────────────────────────────────────────────────────────────────────────
    // 5. CENTER: NEUTRAL STONE PLAZA
    // ─────────────────────────────────────────────────────────────────────────
    public static final Color PLAZA_FLOOR_BASE      = new Color(115, 115, 115); // Cobblestone Base
    public static final Color PLAZA_FLOOR_VAR1      = new Color(92, 92, 92);    // Cobblestone Mortar
    public static final Color PLAZA_FLOOR_VAR2      = new Color(138, 138, 138); // Smooth Stone Tile
    public static final Color PLAZA_FLOOR_PATCH     = new Color(78, 78, 78);    // Cracked Stone
    public static final Color PLAZA_BLOCK_MAIN      = new Color(130, 130, 130); // Chiseled Stone
    public static final Color PLAZA_BLOCK_EDGE      = new Color(85, 85, 85);    // Bevel Edge
    public static final Color PLAZA_BLOCK_OUTLINE   = new Color(35, 35, 35);    // Dark 1px Outline

    // ─────────────────────────────────────────────────────────────────────────
    // BEDROCK & COMMON BLOCKS
    // ─────────────────────────────────────────────────────────────────────────
    public static final Color BEDROCK_DARK          = new Color(28, 28, 28);
    public static final Color BEDROCK_MID           = new Color(52, 52, 52);
    public static final Color BEDROCK_LIGHT         = new Color(76, 76, 76);
    public static final Color BEDROCK_OUTLINE       = new Color(14, 14, 14);

    public static final Color CHEST_WOOD_BASE       = new Color(168, 115, 52);  // Oak Planks / Chest
    public static final Color CHEST_WOOD_DARK       = new Color(124, 82, 34);   // Chest Corners
    public static final Color CHEST_LATCH           = new Color(220, 220, 220); // Iron Latch
    public static final Color CHEST_OUTLINE         = new Color(56, 36, 14);

    public static final Color HEALING_TILE_BG       = new Color(24, 48, 32);    // Safe Zone Base
    public static final Color HEALING_TILE_PLUS     = new Color(65, 215, 105);  // Flat Green Plus
    public static final Color HEALING_TILE_BORDER   = new Color(15, 32, 20);

    private BiomeTheme() {}

    /**
     * Computes the biome for any (col, row) coordinate on the map.
     * Uses the actual map dimensions, placing Forest in Top-Left, Desert in Top-Right,
     * Snow in Bottom-Left, Nether in Bottom-Right, with neutral Plaza in the center.
     * Blends smoothly over ~3 tiles using a deterministic hash so there are no hard seams and zero flicker.
     */
    public static Biome getBiomeAt(int col, int row) {
        double centerX = GameState.MAP_WIDTH / 2.0;
        double centerY = GameState.MAP_HEIGHT / 2.0;

        double dx = col - centerX;
        double dy = row - centerY;
        double distFromCenter = Math.hypot(dx, dy);

        // Deterministic pseudorandom dither based on integer tile coordinates
        int h = (col * 73856093 ^ row * 19349663 ^ 83492791);
        double dither = ((h & 0xFFFF) / 65535.0 - 0.5) * 2.2; // range [-1.1, 1.1]

        // 1. Central Plaza (~3.2 tiles radius with dithered edge)
        if (distFromCenter + dither * 0.75 < 3.2) {
            return Biome.PLAZA;
        }

        // 2. Corner Biomes with 3-tile dithered boundary
        double ditheredDx = dx + dither * 1.1;
        double ditheredDy = dy + dither * 1.1;

        if (ditheredDx < 0 && ditheredDy < 0) {
            return Biome.FOREST;
        } else if (ditheredDx >= 0 && ditheredDy < 0) {
            return Biome.DESERT;
        } else if (ditheredDx < 0 && ditheredDy >= 0) {
            return Biome.SNOW;
        } else {
            return Biome.NETHER;
        }
    }

    /**
     * Returns the wall tint color matching the biome corner for outer walls.
     */
    public static Color getWallTint(int col, int row) {
        Biome b = getBiomeAt(col, row);
        return switch (b) {
            case FOREST -> FOREST_WALL_TINT;
            case DESERT -> DESERT_WALL_TINT;
            case SNOW   -> SNOW_WALL_TINT;
            case NETHER -> NETHER_WALL_TINT;
            case PLAZA  -> BEDROCK_MID;
        };
    }
}
