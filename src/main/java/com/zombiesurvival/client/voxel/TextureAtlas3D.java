package com.zombiesurvival.client.voxel;

import org.lwjgl.BufferUtils;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * Procedural 16x16 Texture Atlas for 3D Voxels.
 * Configured with GL_NEAREST filtering for crisp Minecraft pixel aesthetic.
 */
public class TextureAtlas3D {

    public static final int ATLAS_SIZE = 256;
    public static final int TILE_SIZE  = 16;
    public static final int TILES_PER_ROW = ATLAS_SIZE / TILE_SIZE; // 16 tiles per row

    // Sub-texture slot IDs
    public static final int TILE_GRASS_TOP      = 0;
    public static final int TILE_GRASS_SIDE     = 1;
    public static final int TILE_DIRT           = 2;
    public static final int TILE_STONE_BEDROCK  = 3;
    public static final int TILE_OAK_WOOD       = 4;
    public static final int TILE_OAK_LEAF       = 5;
    public static final int TILE_SAND           = 6;
    public static final int TILE_SANDSTONE      = 7;
    public static final int TILE_SNOW_TOP       = 8;
    public static final int TILE_SNOW_SIDE      = 9;
    public static final int TILE_NETHERRACK     = 10;
    public static final int TILE_BLACKSTONE     = 11;
    public static final int TILE_COBBLESTONE    = 12;
    public static final int TILE_CLOUD          = 13;
    public static final int TILE_STONE_BRICK    = 14;
    public static final int TILE_CACTUS_TOP     = 15;
    public static final int TILE_CACTUS_SIDE    = 16;
    public static final int TILE_SPRUCE_WOOD    = 17;
    public static final int TILE_SPRUCE_LEAF    = 18;

    private final int textureId;

    public TextureAtlas3D() {
        BufferedImage atlas = new BufferedImage(ATLAS_SIZE, ATLAS_SIZE, BufferedImage.TYPE_INT_ARGB);

        // Generate procedural 16x16 sub-textures
        paintTile(atlas, TILE_GRASS_TOP,     generateGrassTop());
        paintTile(atlas, TILE_GRASS_SIDE,    generateGrassSide());
        paintTile(atlas, TILE_DIRT,          generateDirt());
        paintTile(atlas, TILE_STONE_BEDROCK, generateBedrock());
        paintTile(atlas, TILE_OAK_WOOD,      generateOakWood());
        paintTile(atlas, TILE_OAK_LEAF,      generateOakLeaf());
        paintTile(atlas, TILE_SAND,          generateSand());
        paintTile(atlas, TILE_SANDSTONE,     generateSandstone());
        paintTile(atlas, TILE_SNOW_TOP,      generateSnowTop());
        paintTile(atlas, TILE_SNOW_SIDE,     generateSnowSide());
        paintTile(atlas, TILE_NETHERRACK,    generateNetherrack());
        paintTile(atlas, TILE_BLACKSTONE,    generateBlackstone());
        paintTile(atlas, TILE_COBBLESTONE,   generateCobblestone());
        paintTile(atlas, TILE_CLOUD,         generateCloud());
        paintTile(atlas, TILE_STONE_BRICK,   generateStoneBrick());
        paintTile(atlas, TILE_CACTUS_TOP,    generateCactusTop());
        paintTile(atlas, TILE_CACTUS_SIDE,   generateCactusSide());
        paintTile(atlas, TILE_SPRUCE_WOOD,   generateSpruceWood());
        paintTile(atlas, TILE_SPRUCE_LEAF,   generateSpruceLeaf());

        // Upload to OpenGL
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        ByteBuffer buffer = BufferUtils.createByteBuffer(ATLAS_SIZE * ATLAS_SIZE * 4);
        for (int y = 0; y < ATLAS_SIZE; y++) {
            for (int x = 0; x < ATLAS_SIZE; x++) {
                int pixel = atlas.getRGB(x, y);
                buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
                buffer.put((byte) (pixel & 0xFF));         // B
                buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
            }
        }
        buffer.flip();

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, ATLAS_SIZE, ATLAS_SIZE, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
    }

    private void paintTile(BufferedImage atlas, int slot, BufferedImage tile) {
        int tileX = (slot % TILES_PER_ROW) * TILE_SIZE;
        int tileY = (slot / TILES_PER_ROW) * TILE_SIZE;
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                atlas.setRGB(tileX + x, tileY + y, tile.getRGB(x, y));
            }
        }
    }

    public float[] getUVs(int slot) {
        int tileX = slot % TILES_PER_ROW;
        int tileY = slot / TILES_PER_ROW;
        float u0 = (float) (tileX * TILE_SIZE) / ATLAS_SIZE;
        float v0 = (float) (tileY * TILE_SIZE) / ATLAS_SIZE;
        float u1 = (float) ((tileX + 1) * TILE_SIZE) / ATLAS_SIZE;
        float v1 = (float) ((tileY + 1) * TILE_SIZE) / ATLAS_SIZE;
        return new float[]{ u0, v0, u1, v1 };
    }

    public void bind() {
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    public void cleanup() {
        glDeleteTextures(textureId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PROCEDURAL 16x16 GENERATORS
    // ─────────────────────────────────────────────────────────────────────────

    private BufferedImage generateGrassTop() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(1111);
        Color base = new Color(91, 135, 49);
        Color var1 = new Color(76, 120, 40);
        Color var2 = new Color(102, 148, 55);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int n = r.nextInt(10);
                Color c = (n < 5) ? base : ((n < 8) ? var1 : var2);
                img.setRGB(x, y, c.getRGB());
            }
        }
        return img;
    }

    private BufferedImage generateGrassSide() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(2222);
        Color dirt1 = new Color(134, 96, 67);
        Color dirt2 = new Color(110, 75, 50);
        Color grass = new Color(91, 135, 49);

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                Color c = (r.nextInt(10) < 6) ? dirt1 : dirt2;
                img.setRGB(x, y, c.getRGB());
            }
        }
        // Grass fringe on top 3-4 pixels
        for (int x = 0; x < 16; x++) {
            img.setRGB(x, 0, grass.getRGB());
            img.setRGB(x, 1, grass.getRGB());
            int drop = r.nextInt(3);
            if (drop >= 1) img.setRGB(x, 2, grass.getRGB());
            if (drop >= 2) img.setRGB(x, 3, grass.getRGB());
        }
        return img;
    }

    private BufferedImage generateDirt() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(3333);
        Color c1 = new Color(134, 96, 67);
        Color c2 = new Color(110, 75, 50);
        Color c3 = new Color(150, 110, 80);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int n = r.nextInt(10);
                Color c = (n < 5) ? c1 : ((n < 8) ? c2 : c3);
                img.setRGB(x, y, c.getRGB());
            }
        }
        return img;
    }

    private BufferedImage generateBedrock() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(4444);
        Color c1 = new Color(38, 38, 38);
        Color c2 = new Color(60, 60, 60);
        Color c3 = new Color(85, 85, 85);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int n = r.nextInt(10);
                Color c = (n < 4) ? c1 : ((n < 8) ? c2 : c3);
                img.setRGB(x, y, c.getRGB());
            }
        }
        return img;
    }

    private BufferedImage generateOakWood() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Color barkDark = new Color(80, 56, 30);
        Color barkLight = new Color(112, 78, 42);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                Color c = (x % 3 == 0 || (y % 4 == 0 && x % 2 == 0)) ? barkDark : barkLight;
                img.setRGB(x, y, c.getRGB());
            }
        }
        return img;
    }

    private BufferedImage generateOakLeaf() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(5555);
        Color c1 = new Color(56, 101, 35);
        Color c2 = new Color(42, 82, 26);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                img.setRGB(x, y, (r.nextBoolean() ? c1 : c2).getRGB());
            }
        }
        return img;
    }

    private BufferedImage generateSand() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(6666);
        Color c1 = new Color(217, 201, 138);
        Color c2 = new Color(203, 187, 122);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                img.setRGB(x, y, (r.nextBoolean() ? c1 : c2).getRGB());
            }
        }
        return img;
    }

    private BufferedImage generateSandstone() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Color main = new Color(188, 167, 110);
        Color line = new Color(152, 132, 82);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                img.setRGB(x, y, (y == 4 || y == 11) ? line.getRGB() : main.getRGB());
            }
        }
        return img;
    }

    private BufferedImage generateSnowTop() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(7777);
        Color c1 = new Color(240, 245, 245);
        Color c2 = new Color(225, 235, 238);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                img.setRGB(x, y, (r.nextInt(10) < 7 ? c1 : c2).getRGB());
            }
        }
        return img;
    }

    private BufferedImage generateSnowSide() {
        BufferedImage img = generateDirt();
        Color snow = new Color(240, 245, 245);
        for (int x = 0; x < 16; x++) {
            img.setRGB(x, 0, snow.getRGB());
            img.setRGB(x, 1, snow.getRGB());
            img.setRGB(x, 2, snow.getRGB());
        }
        return img;
    }

    private BufferedImage generateNetherrack() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(8888);
        Color c1 = new Color(110, 36, 36);
        Color c2 = new Color(86, 28, 28);
        Color c3 = new Color(138, 48, 48);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int n = r.nextInt(10);
                img.setRGB(x, y, (n < 5 ? c1 : (n < 8 ? c2 : c3)).getRGB());
            }
        }
        return img;
    }

    private BufferedImage generateBlackstone() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Color main = new Color(39, 35, 43);
        Color edge = new Color(24, 21, 28);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean isEdge = (x == 0 || x == 15 || y == 0 || y == 15);
                img.setRGB(x, y, (isEdge ? edge : main).getRGB());
            }
        }
        return img;
    }

    private BufferedImage generateCobblestone() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(9999);
        Color c1 = new Color(115, 115, 115);
        Color c2 = new Color(85, 85, 85);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                img.setRGB(x, y, (r.nextBoolean() ? c1 : c2).getRGB());
            }
        }
        return img;
    }

    private BufferedImage generateCloud() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Color cloud = new Color(255, 255, 255, 220);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                img.setRGB(x, y, cloud.getRGB());
            }
        }
        return img;
    }

    private BufferedImage generateStoneBrick() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Color mortar = new Color(70, 70, 70);
        Color brickBase = new Color(130, 130, 130);
        Color brickLight = new Color(145, 145, 145);
        Color brickDark = new Color(112, 112, 112);
        Random r = new Random(1414);

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int n = r.nextInt(10);
                Color c = (n < 5) ? brickBase : ((n < 8) ? brickLight : brickDark);
                img.setRGB(x, y, c.getRGB());
            }
        }

        // Horizontal mortar lines
        for (int x = 0; x < 16; x++) {
            img.setRGB(x, 3, mortar.getRGB());
            img.setRGB(x, 7, mortar.getRGB());
            img.setRGB(x, 11, mortar.getRGB());
            img.setRGB(x, 15, mortar.getRGB());
        }

        // Vertical mortar lines: alternating running bond
        for (int y = 0; y < 3; y++) {
            img.setRGB(7, y, mortar.getRGB());
            img.setRGB(15, y, mortar.getRGB());
        }
        for (int y = 8; y < 11; y++) {
            img.setRGB(7, y, mortar.getRGB());
            img.setRGB(15, y, mortar.getRGB());
        }
        for (int y = 4; y < 7; y++) {
            img.setRGB(3, y, mortar.getRGB());
            img.setRGB(11, y, mortar.getRGB());
        }
        for (int y = 12; y < 15; y++) {
            img.setRGB(3, y, mortar.getRGB());
            img.setRGB(11, y, mortar.getRGB());
        }
        return img;
    }

    private BufferedImage generateCactusSide() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Color greenBase = new Color(88, 142, 54);
        Color greenLight = new Color(102, 160, 64);
        Color greenRib = new Color(54, 96, 32);
        Color thornDark = new Color(28, 48, 18);
        Color thornLight = new Color(220, 230, 205);
        Random r = new Random(1515);

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (x % 4 == 0) {
                    img.setRGB(x, y, greenRib.getRGB());
                } else {
                    img.setRGB(x, y, (r.nextBoolean() ? greenBase : greenLight).getRGB());
                }
            }
        }

        int[][] thorns = {
            {2, 3}, {6, 7}, {10, 2}, {14, 8},
            {2, 11}, {6, 15}, {10, 10}, {14, 14}
        };
        for (int[] t : thorns) {
            img.setRGB(t[0], t[1], thornDark.getRGB());
            if (t[0] + 1 < 16) img.setRGB(t[0] + 1, t[1], thornLight.getRGB());
        }
        return img;
    }

    private BufferedImage generateCactusTop() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Color outer = new Color(54, 96, 32);
        Color mid = new Color(88, 142, 54);
        Color center = new Color(115, 172, 70);

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int dx = Math.abs(x - 8);
                int dy = Math.abs(y - 8);
                int dist = Math.max(dx, dy);
                if (dist >= 7) {
                    img.setRGB(x, y, outer.getRGB());
                } else if (dist >= 3) {
                    img.setRGB(x, y, mid.getRGB());
                } else {
                    img.setRGB(x, y, center.getRGB());
                }
            }
        }
        return img;
    }

    private BufferedImage generateSpruceWood() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Color darkBark = new Color(42, 28, 16);
        Color midBark = new Color(60, 42, 24);
        Color lightBark = new Color(78, 54, 32);
        Random r = new Random(1717);

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int n = r.nextInt(10);
                Color c = (x % 3 == 0) ? darkBark : ((n < 6) ? midBark : lightBark);
                img.setRGB(x, y, c.getRGB());
            }
        }
        return img;
    }

    private BufferedImage generateSpruceLeaf() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Color base = new Color(34, 62, 40);
        Color dark = new Color(22, 44, 28);
        Color light = new Color(46, 80, 52);
        Random r = new Random(1818);

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int n = r.nextInt(10);
                Color c = (n < 4) ? base : ((n < 8) ? dark : light);
                img.setRGB(x, y, c.getRGB());
            }
        }
        return img;
    }
}
