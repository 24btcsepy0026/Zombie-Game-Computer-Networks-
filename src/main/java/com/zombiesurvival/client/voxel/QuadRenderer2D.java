package com.zombiesurvival.client.voxel;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Utility for drawing solid color 2D rectangles and borders in orthographic projection.
 */
public class QuadRenderer2D {

    private final int vao;
    private final int vbo;
    private final FloatBuffer buffer = BufferUtils.createFloatBuffer(36); // 6 vertices * 6 floats

    public QuadRenderer2D() {
        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, 36 * 4, GL_DYNAMIC_DRAW);

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

    public void drawRect(VoxelShader shader, float x, float y, float w, float h,
                         float r, float g, float b, float a) {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        shader.setUseTexture(false);
        shader.setColor(r, g, b, a);
        shader.setFog(1000f, 2000f);

        buffer.clear();
        // Triangle 1
        putVertex(x,     y,     0, 0, 0, 1.0f);
        putVertex(x,     y + h, 0, 0, 1, 1.0f);
        putVertex(x + w, y + h, 0, 1, 1, 1.0f);

        // Triangle 2
        putVertex(x,     y,     0, 0, 0, 1.0f);
        putVertex(x + w, y + h, 0, 1, 1, 1.0f);
        putVertex(x + w, y,     0, 1, 0, 1.0f);

        buffer.flip();

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        glDisable(GL_BLEND);
    }

    public void drawBorder(VoxelShader shader, float x, float y, float w, float h, float thickness,
                           float r, float g, float b, float a) {
        // Top
        drawRect(shader, x, y, w, thickness, r, g, b, a);
        // Bottom
        drawRect(shader, x, y + h - thickness, w, thickness, r, g, b, a);
        // Left
        drawRect(shader, x, y, thickness, h, r, g, b, a);
        // Right
        drawRect(shader, x + w - thickness, y, thickness, h, r, g, b, a);
    }

    private void putVertex(float x, float y, float z, float u, float v, float light) {
        buffer.put(x).put(y).put(z).put(u).put(v).put(light);
    }

    public void cleanup() {
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
