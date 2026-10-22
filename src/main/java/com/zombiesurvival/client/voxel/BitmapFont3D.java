package com.zombiesurvival.client.voxel;

import com.zombiesurvival.client.ui.UITheme;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Bitmap font renderer for OpenGL 3.3.
 * Renders the bundled PressStart2P (or Monospaced) glyphs once into an OpenGL texture atlas at startup.
 * Draws text as 2D textured quads with zero per-frame font allocations.
 */
public class BitmapFont3D {

    private static final int TEXTURE_W = 512;
    private static final int TEXTURE_H = 512;
    private static final int CHAR_START = 32;
    private static final int CHAR_COUNT = 96; // ASCII 32 to 127

    private final int textureId;
    private final int vao;
    private final int vbo;

    private final float[] charU0 = new float[CHAR_COUNT];
    private final float[] charV0 = new float[CHAR_COUNT];
    private final float[] charU1 = new float[CHAR_COUNT];
    private final float[] charV1 = new float[CHAR_COUNT];
    private final int[] charWidths = new int[CHAR_COUNT];
    private int cellHeight = 16;

    public BitmapFont3D() {
        BufferedImage img = new BufferedImage(TEXTURE_W, TEXTURE_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        Font font = (UITheme.FONT_PIXEL_TITLE != null) ? UITheme.FONT_PIXEL_TITLE.deriveFont(Font.PLAIN, 18f)
                                                        : new Font(Font.MONOSPACED, Font.BOLD, 18);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        cellHeight = fm.getHeight();

        g.setColor(Color.WHITE);
        int curX = 2;
        int curY = fm.getAscent() + 2;

        for (int i = 0; i < CHAR_COUNT; i++) {
            char c = (char) (CHAR_START + i);
            int w = fm.charWidth(c);
            charWidths[i] = Math.max(1, w);

            if (curX + w + 4 >= TEXTURE_W) {
                curX = 2;
                curY += cellHeight + 4;
            }

            g.drawString(String.valueOf(c), curX, curY);

            charU0[i] = (float) curX / TEXTURE_W;
            charV0[i] = (float) (curY - fm.getAscent()) / TEXTURE_H;
            charU1[i] = (float) (curX + w) / TEXTURE_W;
            charV1[i] = (float) (curY - fm.getAscent() + cellHeight) / TEXTURE_H;

            curX += w + 4;
        }
        g.dispose();

        // Upload to OpenGL
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        ByteBuffer buf = BufferUtils.createByteBuffer(TEXTURE_W * TEXTURE_H * 4);
        for (int y = 0; y < TEXTURE_H; y++) {
            for (int x = 0; x < TEXTURE_W; x++) {
                int p = img.getRGB(x, y);
                buf.put((byte) ((p >> 16) & 0xFF));
                buf.put((byte) ((p >> 8) & 0xFF));
                buf.put((byte) (p & 0xFF));
                buf.put((byte) ((p >> 24) & 0xFF));
            }
        }
        buf.flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, TEXTURE_W, TEXTURE_H, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);

        // Pre-allocate dynamic quad VAO/VBO
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        // Reserve buffer for up to 256 characters (6 vertices * 6 floats each = 36 floats per char)
        glBufferData(GL_ARRAY_BUFFER, 256 * 36 * 4, GL_DYNAMIC_DRAW);

        // Vertex format: x, y, z (3f), u, v (2f), light (1f)
        int stride = 6 * 4;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * 4);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5 * 4);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public void drawText(VoxelShader shader, String text, float startX, float startY, float scale,
                         float r, float g, float b, float a) {
        if (text == null || text.isEmpty()) return;

        glBindTexture(GL_TEXTURE_2D, textureId);
        shader.setUseTexture(true);
        shader.setColor(r, g, b, a);
        shader.setFog(1000f, 2000f); // Disable fog for 2D UI text

        FloatBuffer buffer = BufferUtils.createFloatBuffer(text.length() * 36);
        float penX = startX;
        int vertexCount = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int idx = c - CHAR_START;
            if (idx < 0 || idx >= CHAR_COUNT) {
                penX += 8 * scale;
                continue;
            }

            float w = charWidths[idx] * scale;
            float h = cellHeight * scale;
            float u0 = charU0[idx], v0 = charV0[idx], u1 = charU1[idx], v1 = charV1[idx];

            // 2 Triangles (6 vertices)
            putVertex(buffer, penX,     startY,     0, u0, v0, 1.0f);
            putVertex(buffer, penX,     startY + h, 0, u0, v1, 1.0f);
            putVertex(buffer, penX + w, startY + h, 0, u1, v1, 1.0f);

            putVertex(buffer, penX,     startY,     0, u0, v0, 1.0f);
            putVertex(buffer, penX + w, startY + h, 0, u1, v1, 1.0f);
            putVertex(buffer, penX + w, startY,     0, u1, v0, 1.0f);

            penX += w + (1 * scale);
            vertexCount += 6;
        }

        buffer.flip();

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    private void putVertex(FloatBuffer buf, float x, float y, float z, float u, float v, float light) {
        buf.put(x).put(y).put(z).put(u).put(v).put(light);
    }

    public int getStringWidth(String text, float scale) {
        if (text == null) return 0;
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int idx = c - CHAR_START;
            w += (idx >= 0 && idx < CHAR_COUNT ? charWidths[idx] : 8) * scale + (1 * scale);
        }
        return w;
    }

    public void cleanup() {
        glDeleteTextures(textureId);
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
