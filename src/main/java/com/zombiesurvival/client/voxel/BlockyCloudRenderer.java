package com.zombiesurvival.client.voxel;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Drifting Minecraft-style blocky clouds high in the sky.
 */
public class BlockyCloudRenderer {

    private static final float CLOUD_Y = 28.0f;
    private static final float CLOUD_ALTITUDE = 1.2f;

    private final int vao;
    private final int vbo;
    private final int vertexCount;
    private float cloudOffset = 0.0f;

    public BlockyCloudRenderer() {
        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        // Build a grid of blocky cloud patches
        int cloudGrid = 8;
        float patchSize = 8.0f;
        FloatBuffer buffer = BufferUtils.createFloatBuffer(cloudGrid * cloudGrid * 36 * 6); // Up to 6 faces per cloud block
        int vCount = 0;

        for (int cz = -cloudGrid / 2; cz < cloudGrid / 2; cz++) {
            for (int cx = -cloudGrid / 2; cx < cloudGrid / 2; cx++) {
                // Deterministic cloud presence pattern
                if ((cx * 3 + cz * 5) % 3 == 0) {
                    float x0 = cx * patchSize;
                    float z0 = cz * patchSize;
                    float x1 = x0 + patchSize;
                    float z1 = z0 + patchSize;
                    float y0 = CLOUD_Y;
                    float y1 = CLOUD_Y + CLOUD_ALTITUDE;

                    // Bottom face
                    putQuad(buffer,
                        x0, y0, z0,
                        x1, y0, z0,
                        x1, y0, z1,
                        x0, y0, z1,
                        0.9f
                    );
                    vCount += 6;

                    // Top face
                    putQuad(buffer,
                        x0, y1, z1,
                        x1, y1, z1,
                        x1, y1, z0,
                        x0, y1, z0,
                        1.0f
                    );
                    vCount += 6;
                }
            }
        }

        buffer.flip();
        vertexCount = vCount;

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);

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

    private void putQuad(FloatBuffer buf,
                         float x0, float y0, float z0,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         float x3, float y3, float z3,
                         float light) {
        // Vertex 1
        buf.put(x0).put(y0).put(z0).put(0).put(0).put(light);
        // Vertex 2
        buf.put(x1).put(y1).put(z1).put(1).put(0).put(light);
        // Vertex 3
        buf.put(x2).put(y2).put(z2).put(1).put(1).put(light);

        // Vertex 1
        buf.put(x0).put(y0).put(z0).put(0).put(0).put(light);
        // Vertex 3
        buf.put(x2).put(y2).put(z2).put(1).put(1).put(light);
        // Vertex 4
        buf.put(x3).put(y3).put(z3).put(0).put(1).put(light);
    }

    public void update(float dt) {
        cloudOffset += 0.8f * dt; // slow horizontal drift
        if (cloudOffset > 1000f) cloudOffset = 0f;
    }

    public void render(VoxelShader shader, Matrix4f modelMatrix) {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        shader.setUseTexture(false);
        shader.setColor(1.0f, 1.0f, 1.0f, 0.75f);
        shader.setFog(60f, 180f);

        modelMatrix.identity().translate(cloudOffset, 0, cloudOffset * 0.3f);
        shader.setModel(modelMatrix);

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);

        glDisable(GL_BLEND);
    }

    public void cleanup() {
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
