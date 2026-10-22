package com.zombiesurvival.client.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Procedural 16x16 pixel-art tile and sprite generator for the Minecraft aesthetic.
 * All tiles are generated in code without external PNGs, using fixed palettes from BiomeTheme,
 * and pre-scaled 2x (to 32x32) using nearest-neighbor interpolation.
 */
public final class TextureFactory {

    private static final int RAW_SIZE = 16;
    public static final int TILE_SIZE = 32;

    // Cache of scaled 32x32 tiles
    private static final Map<String, BufferedImage> CACHED_TILES = new HashMap<>();

    static {
        generateAllTiles();
    }

    private TextureFactory() {}

    /**
     * Deterministic variant index for (col, row).
     */
    public static int getVariantIndex(int col, int row, int numVariants) {
        int h = (col * 73856093 ^ row * 19349663);
        int val = (h ^ (h >>> 16)) & 0x7FFFFFFF;
        return val % numVariants;
    }

    public static BufferedImage getTile(String key) {
        return CACHED_TILES.getOrDefault(key, CACHED_TILES.get("plaza_floor_0"));
    }

    public static BufferedImage getFloorTile(BiomeTheme.Biome biome, int col, int row) {
        int var = getVariantIndex(col, row, 4);
        String prefix = switch (biome) {
            case FOREST -> "forest_floor_";
            case DESERT -> "desert_floor_";
            case SNOW   -> "snow_floor_";
            case NETHER -> "nether_floor_";
            case PLAZA  -> "plaza_floor_";
        };
        return getTile(prefix + var);
    }

    public static BufferedImage getObstacleTile(BiomeTheme.Biome biome, int col, int row) {
        return switch (biome) {
            case FOREST -> getTile("forest_block");
            case DESERT -> getTile("desert_block");
            case SNOW   -> getTile("snow_block");
            case NETHER -> getTile("nether_block");
            case PLAZA  -> getTile("plaza_block");
        };
    }

    public static BufferedImage getWallTile(BiomeTheme.Biome biome) {
        return switch (biome) {
            case FOREST -> getTile("wall_forest");
            case DESERT -> getTile("wall_desert");
            case SNOW   -> getTile("wall_snow");
            case NETHER -> getTile("wall_nether");
            case PLAZA  -> getTile("wall_plaza");
        };
    }

    public static BufferedImage getChestTile() {
        return getTile("chest_block");
    }

    public static BufferedImage getBushTile(BiomeTheme.Biome biome) {
        return switch (biome) {
            case FOREST -> getTile("leaf_oak");
            case DESERT -> getTile("cactus_block");
            case SNOW   -> getTile("leaf_spruce");
            case NETHER -> getTile("nether_block");
            case PLAZA  -> getTile("leaf_oak");
        };
    }

    public static BufferedImage getHealingTile() {
        return getTile("healing_plus");
    }

    public static BufferedImage getMedkitSprite() {
        return getTile("medkit_sprite");
    }

    public static BufferedImage getKeySprite() {
        return getTile("key_sprite");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GENERATION PIPELINE
    // ─────────────────────────────────────────────────────────────────────────

    private static void generateAllTiles() {
        // 1. Forest Floor Variants (Grass, Dirt patch, Poppy, Dandelion)
        for (int v = 0; v < 4; v++) {
            CACHED_TILES.put("forest_floor_" + v, scale2x(createForestFloor(v)));
        }
        CACHED_TILES.put("forest_block", scale2x(createBlock(
            BiomeTheme.FOREST_BLOCK_MAIN, BiomeTheme.FOREST_BLOCK_EDGE, BiomeTheme.FOREST_BLOCK_OUTLINE, 1
        )));
        CACHED_TILES.put("leaf_oak", scale2x(createLeafBlock(BiomeTheme.FOREST_LEAF_BASE, BiomeTheme.FOREST_LEAF_VAR)));

        // 2. Desert Floor Variants (Sand, Sandstone grain, Bush stick, Ripples)
        for (int v = 0; v < 4; v++) {
            CACHED_TILES.put("desert_floor_" + v, scale2x(createDesertFloor(v)));
        }
        CACHED_TILES.put("desert_block", scale2x(createBlock(
            BiomeTheme.DESERT_BLOCK_MAIN, BiomeTheme.DESERT_BLOCK_EDGE, BiomeTheme.DESERT_BLOCK_OUTLINE, 2
        )));
        CACHED_TILES.put("cactus_block", scale2x(createCactusBlock()));

        // 3. Snow Floor Variants (Fresh snow, Packed snow, Ice patch, Ice slab)
        for (int v = 0; v < 4; v++) {
            CACHED_TILES.put("snow_floor_" + v, scale2x(createSnowFloor(v)));
        }
        CACHED_TILES.put("snow_block", scale2x(createSnowBlock()));
        CACHED_TILES.put("leaf_spruce", scale2x(createLeafBlock(BiomeTheme.SNOW_SPRUCE_LEAF, new Color(25, 45, 32))));

        // 4. Nether Floor Variants (Netherrack, Ember glow, Soul sand, Crust)
        for (int v = 0; v < 4; v++) {
            CACHED_TILES.put("nether_floor_" + v, scale2x(createNetherFloor(v)));
        }
        CACHED_TILES.put("nether_block", scale2x(createBlock(
            BiomeTheme.NETHER_BLOCK_MAIN, BiomeTheme.NETHER_BLOCK_EDGE, BiomeTheme.NETHER_BLOCK_OUTLINE, 3
        )));

        // 5. Plaza Floor Variants (Cobblestone, Mortar, Cracked, Stone)
        for (int v = 0; v < 4; v++) {
            CACHED_TILES.put("plaza_floor_" + v, scale2x(createPlazaFloor(v)));
        }
        CACHED_TILES.put("plaza_block", scale2x(createBlock(
            BiomeTheme.PLAZA_BLOCK_MAIN, BiomeTheme.PLAZA_BLOCK_EDGE, BiomeTheme.PLAZA_BLOCK_OUTLINE, 0
        )));

        // 6. Bedrock Walls tinted per biome
        CACHED_TILES.put("wall_forest", scale2x(createBedrockWall(BiomeTheme.FOREST_WALL_TINT)));
        CACHED_TILES.put("wall_desert", scale2x(createBedrockWall(BiomeTheme.DESERT_WALL_TINT)));
        CACHED_TILES.put("wall_snow",   scale2x(createBedrockWall(BiomeTheme.SNOW_WALL_TINT)));
        CACHED_TILES.put("wall_nether", scale2x(createBedrockWall(BiomeTheme.NETHER_WALL_TINT)));
        CACHED_TILES.put("wall_plaza",  scale2x(createBedrockWall(BiomeTheme.BEDROCK_MID)));

        // 7. Chest / Wood Plank block (replaces crates)
        CACHED_TILES.put("chest_block", scale2x(createChestBlock()));

        // 8. Safe Zone Healing Plus Tile
        CACHED_TILES.put("healing_plus", scale2x(createHealingTile()));

        // 9. Pickups: Medkit and Key
        CACHED_TILES.put("medkit_sprite", scale2x(createMedkitSprite()));
        CACHED_TILES.put("key_sprite", scale2x(createKeySprite()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 16x16 PROCEDURAL BUILDERS
    // ─────────────────────────────────────────────────────────────────────────

    private static BufferedImage createForestFloor(int variant) {
        BufferedImage img = new BufferedImage(RAW_SIZE, RAW_SIZE, BufferedImage.TYPE_INT_RGB);
        Random r = new Random(1001 + variant * 37);
        for (int y = 0; y < RAW_SIZE; y++) {
            for (int x = 0; x < RAW_SIZE; x++) {
                int noise = r.nextInt(10);
                Color c = (noise < 5) ? BiomeTheme.FOREST_FLOOR_BASE : ((noise < 8) ? BiomeTheme.FOREST_FLOOR_VAR1 : BiomeTheme.FOREST_FLOOR_VAR2);
                img.setRGB(x, y, c.getRGB());
            }
        }
        if (variant == 1) { // Dirt patch
            for (int dy = 5; dy <= 9; dy++) {
                for (int dx = 5; dx <= 10; dx++) {
                    if (r.nextBoolean()) img.setRGB(dx, dy, BiomeTheme.FOREST_FLOOR_PATCH.getRGB());
                }
            }
        } else if (variant == 2) { // Red poppy flower
            drawPixel(img, 7, 7, BiomeTheme.FOREST_ACCENT_RED);
            drawPixel(img, 8, 7, BiomeTheme.FOREST_ACCENT_RED);
            drawPixel(img, 7, 8, BiomeTheme.FOREST_ACCENT_RED);
            drawPixel(img, 8, 8, Color.BLACK);
        } else if (variant == 3) { // Yellow dandelion
            drawPixel(img, 5, 9, BiomeTheme.FOREST_ACCENT_YEL);
            drawPixel(img, 6, 9, BiomeTheme.FOREST_ACCENT_YEL);
            drawPixel(img, 5, 10, BiomeTheme.FOREST_ACCENT_YEL);
            drawPixel(img, 6, 10, new Color(180, 140, 20));
        }
        return img;
    }

    private static BufferedImage createDesertFloor(int variant) {
        BufferedImage img = new BufferedImage(RAW_SIZE, RAW_SIZE, BufferedImage.TYPE_INT_RGB);
        Random r = new Random(2002 + variant * 43);
        for (int y = 0; y < RAW_SIZE; y++) {
            for (int x = 0; x < RAW_SIZE; x++) {
                int noise = r.nextInt(10);
                Color c = (noise < 6) ? BiomeTheme.DESERT_FLOOR_BASE : ((noise < 8) ? BiomeTheme.DESERT_FLOOR_VAR1 : BiomeTheme.DESERT_FLOOR_VAR2);
                img.setRGB(x, y, c.getRGB());
            }
        }
        if (variant == 1) { // Sandstone slab grain
            for (int x = 3; x <= 12; x++) {
                img.setRGB(x, 8, BiomeTheme.DESERT_FLOOR_PATCH.getRGB());
            }
        } else if (variant == 2) { // Dead bush stick
            drawPixel(img, 7, 8, BiomeTheme.DESERT_DEAD_BUSH);
            drawPixel(img, 8, 7, BiomeTheme.DESERT_DEAD_BUSH);
            drawPixel(img, 9, 8, BiomeTheme.DESERT_DEAD_BUSH);
            drawPixel(img, 8, 9, BiomeTheme.DESERT_DEAD_BUSH);
        } else if (variant == 3) { // Ripple line
            for (int x = 1; x < 15; x += 2) {
                img.setRGB(x, 4, BiomeTheme.DESERT_FLOOR_VAR1.getRGB());
                img.setRGB(x + 1, 12, BiomeTheme.DESERT_FLOOR_VAR1.getRGB());
            }
        }
        return img;
    }

    private static BufferedImage createSnowFloor(int variant) {
        BufferedImage img = new BufferedImage(RAW_SIZE, RAW_SIZE, BufferedImage.TYPE_INT_RGB);
        Random r = new Random(3003 + variant * 53);
        for (int y = 0; y < RAW_SIZE; y++) {
            for (int x = 0; x < RAW_SIZE; x++) {
                int noise = r.nextInt(10);
                Color c = (noise < 6) ? BiomeTheme.SNOW_FLOOR_BASE : ((noise < 8) ? BiomeTheme.SNOW_FLOOR_VAR1 : BiomeTheme.SNOW_FLOOR_VAR2);
                img.setRGB(x, y, c.getRGB());
            }
        }
        if (variant == 1) { // Packed ice patch
            for (int dy = 6; dy <= 10; dy++) {
                for (int dx = 5; dx <= 11; dx++) {
                    if (r.nextBoolean()) img.setRGB(dx, dy, BiomeTheme.SNOW_FLOOR_PATCH.getRGB());
                }
            }
        } else if (variant == 2) { // Ice crystals
            drawPixel(img, 6, 6, BiomeTheme.SNOW_ACCENT_ICE);
            drawPixel(img, 10, 9, BiomeTheme.SNOW_ACCENT_ICE);
            drawPixel(img, 8, 12, BiomeTheme.SNOW_ACCENT_ICE);
        }
        return img;
    }

    private static BufferedImage createNetherFloor(int variant) {
        BufferedImage img = new BufferedImage(RAW_SIZE, RAW_SIZE, BufferedImage.TYPE_INT_RGB);
        Random r = new Random(4004 + variant * 61);
        for (int y = 0; y < RAW_SIZE; y++) {
            for (int x = 0; x < RAW_SIZE; x++) {
                int noise = r.nextInt(10);
                Color c = (noise < 5) ? BiomeTheme.NETHER_FLOOR_BASE : ((noise < 8) ? BiomeTheme.NETHER_FLOOR_VAR1 : BiomeTheme.NETHER_FLOOR_VAR2);
                img.setRGB(x, y, c.getRGB());
            }
        }
        if (variant == 1) { // Glowing fire embers
            drawPixel(img, 4, 7, BiomeTheme.NETHER_ACCENT_EMBER);
            drawPixel(img, 11, 4, BiomeTheme.NETHER_ACCENT_EMBER);
            drawPixel(img, 8, 11, new Color(255, 170, 40));
        } else if (variant == 2) { // Soul sand patch
            for (int dy = 4; dy <= 9; dy++) {
                for (int dx = 6; dx <= 12; dx++) {
                    if (r.nextBoolean()) img.setRGB(dx, dy, BiomeTheme.NETHER_FLOOR_PATCH.getRGB());
                }
            }
        }
        return img;
    }

    private static BufferedImage createPlazaFloor(int variant) {
        BufferedImage img = new BufferedImage(RAW_SIZE, RAW_SIZE, BufferedImage.TYPE_INT_RGB);
        Random r = new Random(5005 + variant * 71);
        for (int y = 0; y < RAW_SIZE; y++) {
            for (int x = 0; x < RAW_SIZE; x++) {
                int noise = r.nextInt(10);
                Color c = (noise < 6) ? BiomeTheme.PLAZA_FLOOR_BASE : ((noise < 8) ? BiomeTheme.PLAZA_FLOOR_VAR1 : BiomeTheme.PLAZA_FLOOR_VAR2);
                img.setRGB(x, y, c.getRGB());
            }
        }
        // Cobblestone stone grid lines
        for (int i = 0; i < RAW_SIZE; i++) {
            if (i % 4 == 0) {
                for (int x = 0; x < RAW_SIZE; x++) {
                    img.setRGB(x, i, BiomeTheme.PLAZA_FLOOR_VAR1.getRGB());
                    img.setRGB(i, x, BiomeTheme.PLAZA_FLOOR_VAR1.getRGB());
                }
            }
        }
        if (variant == 2) { // Cracked stone
            drawPixel(img, 6, 7, BiomeTheme.PLAZA_FLOOR_PATCH);
            drawPixel(img, 7, 8, BiomeTheme.PLAZA_FLOOR_PATCH);
            drawPixel(img, 8, 8, BiomeTheme.PLAZA_FLOOR_PATCH);
            drawPixel(img, 9, 9, BiomeTheme.PLAZA_FLOOR_PATCH);
        }
        return img;
    }

    private static BufferedImage createBlock(Color main, Color edge, Color outline, int patternType) {
        BufferedImage img = new BufferedImage(RAW_SIZE, RAW_SIZE, BufferedImage.TYPE_INT_RGB);
        // Fill main body
        for (int y = 0; y < RAW_SIZE; y++) {
            for (int x = 0; x < RAW_SIZE; x++) {
                img.setRGB(x, y, main.getRGB());
            }
        }
        // Pattern detail
        if (patternType == 1) { // Stone brick lines
            for (int x = 1; x < 15; x++) {
                img.setRGB(x, 7, edge.getRGB());
            }
            for (int y = 1; y < 7; y++) img.setRGB(7, y, edge.getRGB());
            for (int y = 8; y < 15; y++) img.setRGB(11, y, edge.getRGB());
        } else if (patternType == 2) { // Sandstone horizontal cuts
            for (int x = 1; x < 15; x++) {
                img.setRGB(x, 4, edge.getRGB());
                img.setRGB(x, 11, edge.getRGB());
            }
        } else if (patternType == 3) { // Blackstone chiseled square
            for (int i = 3; i <= 12; i++) {
                img.setRGB(i, 3, edge.getRGB());
                img.setRGB(i, 12, edge.getRGB());
                img.setRGB(3, i, edge.getRGB());
                img.setRGB(12, i, edge.getRGB());
            }
        }

        // Hard 1px Dark Outline
        for (int i = 0; i < RAW_SIZE; i++) {
            img.setRGB(i, 0, outline.getRGB());
            img.setRGB(i, RAW_SIZE - 1, outline.getRGB());
            img.setRGB(0, i, outline.getRGB());
            img.setRGB(RAW_SIZE - 1, i, outline.getRGB());
        }

        // Darker 1px Bottom & Right Bevel Edge (Blocky 3D feel)
        for (int i = 1; i < RAW_SIZE - 1; i++) {
            img.setRGB(i, RAW_SIZE - 2, edge.getRGB());
            img.setRGB(RAW_SIZE - 2, i, edge.getRGB());
        }
        return img;
    }

    private static BufferedImage createSnowBlock() {
        BufferedImage img = createBlock(
            BiomeTheme.SNOW_BLOCK_MAIN, BiomeTheme.SNOW_BLOCK_EDGE, BiomeTheme.SNOW_BLOCK_OUTLINE, 0
        );
        // Snow layer cap on top
        for (int x = 1; x < 15; x++) {
            img.setRGB(x, 1, Color.WHITE.getRGB());
            img.setRGB(x, 2, BiomeTheme.SNOW_FLOOR_BASE.getRGB());
        }
        return img;
    }

    private static BufferedImage createCactusBlock() {
        BufferedImage img = new BufferedImage(RAW_SIZE, RAW_SIZE, BufferedImage.TYPE_INT_RGB);
        // Fill cactus green
        for (int y = 0; y < RAW_SIZE; y++) {
            for (int x = 0; x < RAW_SIZE; x++) {
                img.setRGB(x, y, BiomeTheme.DESERT_CACTUS_BASE.getRGB());
            }
        }
        // Vertical ribs
        for (int y = 1; y < 15; y++) {
            img.setRGB(4, y, new Color(68, 108, 30).getRGB());
            img.setRGB(8, y, new Color(68, 108, 30).getRGB());
            img.setRGB(12, y, new Color(68, 108, 30).getRGB());
        }
        // Spines
        img.setRGB(2, 4, BiomeTheme.DESERT_CACTUS_SPINE.getRGB());
        img.setRGB(6, 8, BiomeTheme.DESERT_CACTUS_SPINE.getRGB());
        img.setRGB(10, 12, BiomeTheme.DESERT_CACTUS_SPINE.getRGB());
        img.setRGB(14, 5, BiomeTheme.DESERT_CACTUS_SPINE.getRGB());

        // Hard 1px dark outline
        for (int i = 0; i < RAW_SIZE; i++) {
            img.setRGB(i, 0, BiomeTheme.DESERT_BLOCK_OUTLINE.getRGB());
            img.setRGB(i, RAW_SIZE - 1, BiomeTheme.DESERT_BLOCK_OUTLINE.getRGB());
            img.setRGB(0, i, BiomeTheme.DESERT_BLOCK_OUTLINE.getRGB());
            img.setRGB(RAW_SIZE - 1, i, BiomeTheme.DESERT_BLOCK_OUTLINE.getRGB());
        }
        return img;
    }

    private static BufferedImage createLeafBlock(Color base, Color shadow) {
        BufferedImage img = new BufferedImage(RAW_SIZE, RAW_SIZE, BufferedImage.TYPE_INT_RGB);
        Random r = new Random(777);
        for (int y = 0; y < RAW_SIZE; y++) {
            for (int x = 0; x < RAW_SIZE; x++) {
                Color c = (r.nextInt(10) < 6) ? base : shadow;
                img.setRGB(x, y, c.getRGB());
            }
        }
        // Hard 1px dark outline
        Color outline = shadow.darker();
        for (int i = 0; i < RAW_SIZE; i++) {
            img.setRGB(i, 0, outline.getRGB());
            img.setRGB(i, RAW_SIZE - 1, outline.getRGB());
            img.setRGB(0, i, outline.getRGB());
            img.setRGB(RAW_SIZE - 1, i, outline.getRGB());
        }
        return img;
    }

    private static BufferedImage createBedrockWall(Color tint) {
        BufferedImage img = new BufferedImage(RAW_SIZE, RAW_SIZE, BufferedImage.TYPE_INT_RGB);
        Random r = new Random(9999);
        for (int y = 0; y < RAW_SIZE; y++) {
            for (int x = 0; x < RAW_SIZE; x++) {
                int n = r.nextInt(10);
                Color c = (n < 4) ? BiomeTheme.BEDROCK_DARK : ((n < 8) ? BiomeTheme.BEDROCK_MID : BiomeTheme.BEDROCK_LIGHT);
                // Blend with biome wall tint
                int red   = (c.getRed() * 2 + tint.getRed()) / 3;
                int green = (c.getGreen() * 2 + tint.getGreen()) / 3;
                int blue  = (c.getBlue() * 2 + tint.getBlue()) / 3;
                img.setRGB(x, y, new Color(red, green, blue).getRGB());
            }
        }
        // Hard 1px dark outline
        for (int i = 0; i < RAW_SIZE; i++) {
            img.setRGB(i, 0, BiomeTheme.BEDROCK_OUTLINE.getRGB());
            img.setRGB(i, RAW_SIZE - 1, BiomeTheme.BEDROCK_OUTLINE.getRGB());
            img.setRGB(0, i, BiomeTheme.BEDROCK_OUTLINE.getRGB());
            img.setRGB(RAW_SIZE - 1, i, BiomeTheme.BEDROCK_OUTLINE.getRGB());
        }
        return img;
    }

    private static BufferedImage createChestBlock() {
        BufferedImage img = new BufferedImage(RAW_SIZE, RAW_SIZE, BufferedImage.TYPE_INT_RGB);
        // Wood planks body
        for (int y = 0; y < RAW_SIZE; y++) {
            for (int x = 0; x < RAW_SIZE; x++) {
                img.setRGB(x, y, BiomeTheme.CHEST_WOOD_BASE.getRGB());
            }
        }
        // Plank separator lines
        for (int x = 1; x < 15; x++) {
            img.setRGB(x, 6, BiomeTheme.CHEST_WOOD_DARK.getRGB());
            img.setRGB(x, 11, BiomeTheme.CHEST_WOOD_DARK.getRGB());
        }
        // Center iron latch
        drawPixel(img, 7, 7, BiomeTheme.CHEST_LATCH);
        drawPixel(img, 8, 7, BiomeTheme.CHEST_LATCH);
        drawPixel(img, 7, 8, Color.BLACK);
        drawPixel(img, 8, 8, Color.BLACK);

        // Hard 1px outline
        for (int i = 0; i < RAW_SIZE; i++) {
            img.setRGB(i, 0, BiomeTheme.CHEST_OUTLINE.getRGB());
            img.setRGB(i, RAW_SIZE - 1, BiomeTheme.CHEST_OUTLINE.getRGB());
            img.setRGB(0, i, BiomeTheme.CHEST_OUTLINE.getRGB());
            img.setRGB(RAW_SIZE - 1, i, BiomeTheme.CHEST_OUTLINE.getRGB());
        }
        // Darker bottom/right edge
        for (int i = 1; i < RAW_SIZE - 1; i++) {
            img.setRGB(i, RAW_SIZE - 2, BiomeTheme.CHEST_WOOD_DARK.getRGB());
            img.setRGB(RAW_SIZE - 2, i, BiomeTheme.CHEST_WOOD_DARK.getRGB());
        }
        return img;
    }

    private static BufferedImage createHealingTile() {
        BufferedImage img = new BufferedImage(RAW_SIZE, RAW_SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < RAW_SIZE; y++) {
            for (int x = 0; x < RAW_SIZE; x++) {
                img.setRGB(x, y, BiomeTheme.HEALING_TILE_BG.getRGB());
            }
        }
        // Bold green cross "+"
        for (int y = 4; y <= 11; y++) {
            img.setRGB(7, y, BiomeTheme.HEALING_TILE_PLUS.getRGB());
            img.setRGB(8, y, BiomeTheme.HEALING_TILE_PLUS.getRGB());
        }
        for (int x = 4; x <= 11; x++) {
            img.setRGB(x, 7, BiomeTheme.HEALING_TILE_PLUS.getRGB());
            img.setRGB(x, 8, BiomeTheme.HEALING_TILE_PLUS.getRGB());
        }

        // Hard 1px dark border
        for (int i = 0; i < RAW_SIZE; i++) {
            img.setRGB(i, 0, BiomeTheme.HEALING_TILE_BORDER.getRGB());
            img.setRGB(i, RAW_SIZE - 1, BiomeTheme.HEALING_TILE_BORDER.getRGB());
            img.setRGB(0, i, BiomeTheme.HEALING_TILE_BORDER.getRGB());
            img.setRGB(RAW_SIZE - 1, i, BiomeTheme.HEALING_TILE_BORDER.getRGB());
        }
        return img;
    }

    private static BufferedImage createMedkitSprite() {
        BufferedImage img = new BufferedImage(RAW_SIZE, RAW_SIZE, BufferedImage.TYPE_INT_ARGB);
        // 12x12 white box in center
        for (int y = 2; y <= 13; y++) {
            for (int x = 2; x <= 13; x++) {
                img.setRGB(x, y, Color.WHITE.getRGB());
            }
        }
        // 1px black outline around medkit
        for (int i = 2; i <= 13; i++) {
            img.setRGB(i, 2, Color.BLACK.getRGB());
            img.setRGB(i, 13, Color.BLACK.getRGB());
            img.setRGB(2, i, Color.BLACK.getRGB());
            img.setRGB(13, i, Color.BLACK.getRGB());
        }
        // Red cross
        Color red = new Color(225, 35, 35);
        for (int y = 5; y <= 10; y++) {
            img.setRGB(7, y, red.getRGB());
            img.setRGB(8, y, red.getRGB());
        }
        for (int x = 5; x <= 10; x++) {
            img.setRGB(x, 7, red.getRGB());
            img.setRGB(x, 8, red.getRGB());
        }
        return img;
    }

    private static BufferedImage createKeySprite() {
        BufferedImage img = new BufferedImage(RAW_SIZE, RAW_SIZE, BufferedImage.TYPE_INT_ARGB);
        Color gold = new Color(255, 215, 0);
        Color goldDark = new Color(185, 140, 10);
        Color outline = Color.BLACK;

        // Key bow (head) 6x6 ring
        for (int y = 2; y <= 7; y++) {
            for (int x = 2; x <= 7; x++) {
                boolean isEdge = (x == 2 || x == 7 || y == 2 || y == 7);
                boolean isHole = (x >= 4 && x <= 5 && y >= 4 && y <= 5);
                if (isHole) continue;
                img.setRGB(x, y, isEdge ? outline.getRGB() : gold.getRGB());
            }
        }
        // Key shaft (stem)
        for (int x = 7; x <= 13; x++) {
            img.setRGB(x, 4, outline.getRGB());
            img.setRGB(x, 5, gold.getRGB());
            img.setRGB(x, 6, outline.getRGB());
        }
        // Key teeth (bits)
        img.setRGB(11, 7, gold.getRGB());
        img.setRGB(11, 8, outline.getRGB());
        img.setRGB(13, 7, gold.getRGB());
        img.setRGB(13, 8, outline.getRGB());
        img.setRGB(14, 5, outline.getRGB());
        return img;
    }

    private static void drawPixel(BufferedImage img, int x, int y, Color c) {
        if (x >= 0 && x < img.getWidth() && y >= 0 && y < img.getHeight()) {
            img.setRGB(x, y, c.getRGB());
        }
    }

    /**
     * Scales a 16x16 BufferedImage up to 32x32 using pure nearest-neighbor pixel duplication.
     */
    private static BufferedImage scale2x(BufferedImage src) {
        BufferedImage dst = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < RAW_SIZE; y++) {
            for (int x = 0; x < RAW_SIZE; x++) {
                int rgb = src.getRGB(x, y);
                dst.setRGB(x * 2,     y * 2,     rgb);
                dst.setRGB(x * 2 + 1, y * 2,     rgb);
                dst.setRGB(x * 2,     y * 2 + 1, rgb);
                dst.setRGB(x * 2 + 1, y * 2 + 1, rgb);
            }
        }
        return dst;
    }
}
