package com.zombiesurvival.client.voxel;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Builds and renders a face-culled, static triangle mesh from the VoxelWorld block grid.
 *
 * Vertex format (matches VoxelShader attribute layout):
 *   location 0 : vec3  aPos      – world-space XYZ
 *   location 1 : vec2  aTexCoord – atlas UV
 *   location 2 : float aLight    – per-face directional brightness
 *
 * Hidden-face culling: a face is omitted when its neighbour is a solid block.
 * Bottom faces are always omitted (never visible from above ground).
 * Face brightness: top=1.0, east/west=0.8, north/south=0.65 (Minecraft classic style).
 */
public class VoxelChunk {

    /** Floats per vertex: x,y,z,u,v,light */
    private static final int FPV = 6;

    private int vao;
    private int vbo;
    private int vertexCount;

    // ── Mesh Construction ────────────────────────────────────────────────────

    /**
     * Build the face-culled mesh for the entire world and upload it to the GPU.
     * Call once after GL is initialised, before the first render.
     */
    public void buildMesh(VoxelWorld world, TextureAtlas3D atlas) {
        // Upper bound: every block, every face (6 visible), 6 vertices each
        int maxV = VoxelWorld.WORLD_W * VoxelWorld.WORLD_H * VoxelWorld.WORLD_D * 6 * 6;
        FloatBuffer buf = BufferUtils.createFloatBuffer(maxV * FPV);
        int vCount = 0;

        for (int bz = 0; bz < VoxelWorld.WORLD_D; bz++) {
            for (int bx = 0; bx < VoxelWorld.WORLD_W; bx++) {
                for (int by = 0; by < VoxelWorld.WORLD_H; by++) {
                    int block = world.getBlock(bx, by, bz);
                    if (block == VoxelWorld.AIR) continue;

                    // Top (+Y) – brightest face
                    if (!world.isSolid(bx, by + 1, bz)) {
                        float[] uv = atlas.getUVs(world.getTopTile(block));
                        vCount += putTop(buf, bx, by, bz, uv, 1.0f);
                    }
                    // East (+X)
                    if (!world.isSolid(bx + 1, by, bz)) {
                        float[] uv = atlas.getUVs(world.getSideTile(block));
                        vCount += putEast(buf, bx, by, bz, uv, 0.8f);
                    }
                    // West (-X)
                    if (!world.isSolid(bx - 1, by, bz)) {
                        float[] uv = atlas.getUVs(world.getSideTile(block));
                        vCount += putWest(buf, bx, by, bz, uv, 0.8f);
                    }
                    // South (+Z)
                    if (!world.isSolid(bx, by, bz + 1)) {
                        float[] uv = atlas.getUVs(world.getSideTile(block));
                        vCount += putSouth(buf, bx, by, bz, uv, 0.65f);
                    }
                    // North (-Z)
                    if (!world.isSolid(bx, by, bz - 1)) {
                        float[] uv = atlas.getUVs(world.getSideTile(block));
                        vCount += putNorth(buf, bx, by, bz, uv, 0.65f);
                    }
                    // Bottom (-Y): only for elevated blocks with air underneath
                    if (by > 0 && !world.isSolid(bx, by - 1, bz)) {
                        float[] uv = atlas.getUVs(world.getBottomTile(block));
                        vCount += putBottom(buf, bx, by, bz, uv, 0.55f);
                    }
                }
            }
        }

        buf.flip();
        vertexCount = vCount;

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW);

        int stride = FPV * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5 * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    // ── Face emitters ────────────────────────────────────────────────────────
    // All faces use CCW winding when viewed from outside (verified via cross product).
    // Each method emits 6 vertices (2 CCW triangles) and returns 6.

    /** Top face: normal = +Y. Block occupies [bx,bx+1] x [by,by+1] x [bz,bz+1]. */
    private int putTop(FloatBuffer b, int bx, int by, int bz, float[] uv, float l) {
        float x0 = bx, x1 = bx + 1f, y = by + 1f, z0 = bz, z1 = bz + 1f;
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        v(b, x0, y, z0,  u0, v0,  l);
        v(b, x0, y, z1,  u0, v1,  l);
        v(b, x1, y, z1,  u1, v1,  l);
        v(b, x0, y, z0,  u0, v0,  l);
        v(b, x1, y, z1,  u1, v1,  l);
        v(b, x1, y, z0,  u1, v0,  l);
        return 6;
    }

    /** East face: normal = +X, at x = bx+1. */
    private int putEast(FloatBuffer b, int bx, int by, int bz, float[] uv, float l) {
        float x = bx + 1f, y0 = by, y1 = by + 1f, z0 = bz, z1 = bz + 1f;
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        v(b, x, y0, z1,  u0, v1,  l);
        v(b, x, y0, z0,  u1, v1,  l);
        v(b, x, y1, z0,  u1, v0,  l);
        v(b, x, y0, z1,  u0, v1,  l);
        v(b, x, y1, z0,  u1, v0,  l);
        v(b, x, y1, z1,  u0, v0,  l);
        return 6;
    }

    /** West face: normal = -X, at x = bx. */
    private int putWest(FloatBuffer b, int bx, int by, int bz, float[] uv, float l) {
        float x = bx, y0 = by, y1 = by + 1f, z0 = bz, z1 = bz + 1f;
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        v(b, x, y0, z0,  u0, v1,  l);
        v(b, x, y0, z1,  u1, v1,  l);
        v(b, x, y1, z1,  u1, v0,  l);
        v(b, x, y0, z0,  u0, v1,  l);
        v(b, x, y1, z1,  u1, v0,  l);
        v(b, x, y1, z0,  u0, v0,  l);
        return 6;
    }

    /** South face: normal = +Z, at z = bz+1. */
    private int putSouth(FloatBuffer b, int bx, int by, int bz, float[] uv, float l) {
        float x0 = bx, x1 = bx + 1f, y0 = by, y1 = by + 1f, z = bz + 1f;
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        v(b, x0, y0, z,  u0, v1,  l);
        v(b, x1, y0, z,  u1, v1,  l);
        v(b, x1, y1, z,  u1, v0,  l);
        v(b, x0, y0, z,  u0, v1,  l);
        v(b, x1, y1, z,  u1, v0,  l);
        v(b, x0, y1, z,  u0, v0,  l);
        return 6;
    }

    /** North face: normal = -Z, at z = bz. */
    private int putNorth(FloatBuffer b, int bx, int by, int bz, float[] uv, float l) {
        float x0 = bx, x1 = bx + 1f, y0 = by, y1 = by + 1f, z = bz;
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        v(b, x1, y0, z,  u0, v1,  l);
        v(b, x0, y0, z,  u1, v1,  l);
        v(b, x0, y1, z,  u1, v0,  l);
        v(b, x1, y0, z,  u0, v1,  l);
        v(b, x0, y1, z,  u1, v0,  l);
        v(b, x1, y1, z,  u0, v0,  l);
        return 6;
    }

    /** Bottom face: normal = -Y, at y = by. CCW winding when viewed from below. */
    private int putBottom(FloatBuffer b, int bx, int by, int bz, float[] uv, float l) {
        float x0 = bx, x1 = bx + 1f, y = by, z0 = bz, z1 = bz + 1f;
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        v(b, x0, y, z1,  u0, v1,  l);
        v(b, x0, y, z0,  u0, v0,  l);
        v(b, x1, y, z0,  u1, v0,  l);
        v(b, x0, y, z1,  u0, v1,  l);
        v(b, x1, y, z0,  u1, v0,  l);
        v(b, x1, y, z1,  u1, v1,  l);
        return 6;
    }

    private void v(FloatBuffer b, float x, float y, float z, float u, float vv, float l) {
        b.put(x).put(y).put(z).put(u).put(vv).put(l);
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    public void render(VoxelShader shader, TextureAtlas3D atlas, Matrix4f model) {
        if (vertexCount == 0) return;
        atlas.bind();
        shader.setUseTexture(true);
        shader.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        shader.setModel(model);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    public void cleanup() {
        if (vbo != 0) { glDeleteBuffers(vbo);      vbo = 0; }
        if (vao != 0) { glDeleteVertexArrays(vao); vao = 0; }
    }
}
