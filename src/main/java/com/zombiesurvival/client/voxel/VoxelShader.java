package com.zombiesurvival.client.voxel;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL20.*;

/**
 * Minimal OpenGL 3.3 Core shader program for voxel rendering.
 * Features:
 *  - Texture atlas sampling with GL_NEAREST
 *  - Minecraft-Classic per-face directional brightness
 *  - Sky-colored linear distance fog
 */
public class VoxelShader {

    private static final String VERTEX_SOURCE =
        "#version 330 core\n" +
        "layout (location = 0) in vec3 aPos;\n" +
        "layout (location = 1) in vec2 aTexCoord;\n" +
        "layout (location = 2) in float aLight;\n" +
        "\n" +
        "uniform mat4 uProjection;\n" +
        "uniform mat4 uView;\n" +
        "uniform mat4 uModel;\n" +
        "\n" +
        "out vec2 vTexCoord;\n" +
        "out float vLight;\n" +
        "out float vFragDist;\n" +
        "\n" +
        "void main() {\n" +
        "    vec4 worldPos = uModel * vec4(aPos, 1.0);\n" +
        "    vec4 viewPos = uView * worldPos;\n" +
        "    gl_Position = uProjection * viewPos;\n" +
        "    vTexCoord = aTexCoord;\n" +
        "    vLight = aLight;\n" +
        "    vFragDist = length(viewPos.xyz);\n" +
        "}\n";

    private static final String FRAGMENT_SOURCE =
        "#version 330 core\n" +
        "in vec2 vTexCoord;\n" +
        "in float vLight;\n" +
        "in float vFragDist;\n" +
        "\n" +
        "uniform sampler2D uTexture;\n" +
        "uniform vec3 uSkyColor;\n" +
        "uniform float uFogStart;\n" +
        "uniform float uFogEnd;\n" +
        "uniform int uUseTexture;\n" +
        "uniform vec4 uColor;\n" +
        "\n" +
        "out vec4 FragColor;\n" +
        "\n" +
        "void main() {\n" +
        "    vec4 baseColor = (uUseTexture == 1) ? texture(uTexture, vTexCoord) * uColor : uColor;\n" +
        "    if (baseColor.a < 0.1) discard;\n" +
        "\n" +
        "    // Minecraft per-face brightness\n" +
        "    vec3 litColor = baseColor.rgb * vLight;\n" +
        "\n" +
        "    // Distance fog blending to sky color\n" +
        "    float fogFactor = clamp((uFogEnd - vFragDist) / (uFogEnd - uFogStart), 0.0, 1.0);\n" +
        "    vec3 finalColor = mix(uSkyColor, litColor, fogFactor);\n" +
        "\n" +
        "    FragColor = vec4(finalColor, baseColor.a);\n" +
        "}\n";

    private final int programId;
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    // Uniform locations
    private final int locProjection;
    private final int locView;
    private final int locModel;
    private final int locTexture;
    private final int locSkyColor;
    private final int locFogStart;
    private final int locFogEnd;
    private final int locUseTexture;
    private final int locColor;

    public VoxelShader() {
        int vShader = compileShader(GL_VERTEX_SHADER, VERTEX_SOURCE);
        int fShader = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SOURCE);

        programId = glCreateProgram();
        glAttachShader(programId, vShader);
        glAttachShader(programId, fShader);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(programId);
            throw new RuntimeException("Shader link error: " + log);
        }

        glDeleteShader(vShader);
        glDeleteShader(fShader);

        // Cache uniform locations
        locProjection = glGetUniformLocation(programId, "uProjection");
        locView       = glGetUniformLocation(programId, "uView");
        locModel      = glGetUniformLocation(programId, "uModel");
        locTexture    = glGetUniformLocation(programId, "uTexture");
        locSkyColor   = glGetUniformLocation(programId, "uSkyColor");
        locFogStart   = glGetUniformLocation(programId, "uFogStart");
        locFogEnd     = glGetUniformLocation(programId, "uFogEnd");
        locUseTexture = glGetUniformLocation(programId, "uUseTexture");
        locColor      = glGetUniformLocation(programId, "uColor");
    }

    private int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new RuntimeException("Shader compilation error: " + log);
        }
        return shader;
    }

    public void bind() {
        glUseProgram(programId);
    }

    public void unbind() {
        glUseProgram(0);
    }

    public void setProjection(Matrix4f matrix) {
        matrix.get(matrixBuffer);
        glUniformMatrix4fv(locProjection, false, matrixBuffer);
    }

    public void setView(Matrix4f matrix) {
        matrix.get(matrixBuffer);
        glUniformMatrix4fv(locView, false, matrixBuffer);
    }

    public void setModel(Matrix4f matrix) {
        matrix.get(matrixBuffer);
        glUniformMatrix4fv(locModel, false, matrixBuffer);
    }

    public void setSkyColor(float r, float g, float b) {
        glUniform3f(locSkyColor, r, g, b);
    }

    public void setFog(float start, float end) {
        glUniform1f(locFogStart, start);
        glUniform1f(locFogEnd, end);
    }

    public void setUseTexture(boolean useTexture) {
        glUniform1i(locUseTexture, useTexture ? 1 : 0);
    }

    public void setColor(float r, float g, float b, float a) {
        glUniform4f(locColor, r, g, b, a);
    }

    public void cleanup() {
        glDeleteProgram(programId);
    }
}
