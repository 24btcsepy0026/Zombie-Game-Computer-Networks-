package com.zombiesurvival.client.voxel;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Renders the flat voxel grass floor for the arena.
 * In Milestone 1, generates a Minecraft Classic style flat grass terrain.
 */
public class VoxelFloorRenderer {

    private final int vao;
    private final int vbo;
    private final int vertexCount;

    public VoxelFloorRenderer(TextureAtlas3D atlas) {
        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        // Floor bounds: 25x18 arena + surrounding terrain (-15 to 40, -15 to 35)
        int minX = -20;
        int maxX = 45;
        int minZ = -20;
        int maxZ = 40;

        int totalBlocks = (maxX - minX) * (maxZ - minZ);
        FloatBuffer buffer = BufferUtils.createFloatBuffer(totalBlocks * 6 * 6); // 6 vertices per quad, 6 floats per vertex

        float[] grassUv = atlas.getUVs(TextureAtlas3D.TILE_GRASS_TOP);
        float u0 = grassUv[0], v0 = grassUv[1], u1 = grassUv[2], v1 = grassUv[3];

        int vCount = 0;
        float y = 1.0f; // top surface of floor blocks at Y = 1.0

        for (int z = minZ; z < maxZ; z++) {
            for (int x = minX; x < maxX; x++) {
                float x0 = x;
                float x1 = x + 1.0f;
                float z0 = z;
                float z1 = z + 1.0f;

                // Quad on XZ plane facing +Y (top face, brightness 1.0f)
                // Triangle 1
                putVertex(buffer, x0, y, z0, u0, v0, 1.0f);
                putVertex(buffer, x0, y, z1, u0, v1, 1.0f);
                putVertex(buffer, x1, y, z1, u1, v1, 1.0f);

                // Triangle 2
                putVertex(buffer, x0, y, z0, u0, v0, 1.0f);
                putVertex(buffer, x1, y, z1, u1, v1, 1.0f);
                putVertex(buffer, x1, y, z0, u1, v0, 1.0f);

                vCount += 6;
            }
        }

        buffer.flip();
        vertexCount = vCount;

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);

        int stride = 6 * 4;
        // location 0: vec3 aPos
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        // location 1: vec2 aTexCoord
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * 4);
        glEnableVertexAttribArray(1);
        // location 2: float aLight
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5 * 4);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    private void putVertex(FloatBuffer buf, float x, float y, float z, float u, float v, float light) {
        buf.put(x).put(y).put(z).put(u).put(v).put(light);
    }

    public void render(VoxelShader shader, TextureAtlas3D atlas, Matrix4f modelMatrix) {
        atlas.bind();
        shader.setUseTexture(true);
        shader.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        shader.setModel(modelMatrix);

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    public void cleanup() {
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
