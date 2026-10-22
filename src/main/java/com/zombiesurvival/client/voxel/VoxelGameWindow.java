package com.zombiesurvival.client.voxel;

import com.zombiesurvival.client.GameClient;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Manages the dedicated GLFW OpenGL 3.3 Core Profile 3D window.
 * Runs its own event and render loop on a separate non-EDT thread.
 * Cleanly releases resources on window close and returns to Swing.
 */
public class VoxelGameWindow {

    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static boolean glfwInitialized = false;

    // Window state
    private static boolean isPaused = false;
    private static boolean firstMouse = true;
    private static double lastMouseX = 0;
    private static double lastMouseY = 0;
    private static float currentMouseX = 0;
    private static float currentMouseY = 0;

    /**
     * Launch the 3D voxel window from Swing.
     */
    public static void launch(GameClient client, Runnable onExit) {
        if (!isRunning.compareAndSet(false, true)) {
            System.out.println("Voxel 3D window is already running.");
            return;
        }

        // Hide the Swing menu frame on the EDT
        SwingUtilities.invokeLater(() -> {
            if (client.getMenuFrame() != null) {
                client.getMenuFrame().setVisible(false);
            }
        });

        Thread renderThread = new Thread(() -> runWindowLoop(client, onExit), "VoxelRenderThread");
        renderThread.start();
    }

    private static void runWindowLoop(GameClient client, Runnable onExit) {
        GLFWErrorCallback.createPrint(System.err).set();

        // 1. Initialize GLFW once
        synchronized (VoxelGameWindow.class) {
            if (!glfwInitialized) {
                if (!glfwInit()) {
                    showErrorAndRestore(client, "Failed to initialize GLFW library.");
                    isRunning.set(false);
                    return;
                }
                glfwInitialized = true;
            }
        }

        // 2. Request OpenGL 3.3 Core Profile explicitly
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        int initWidth = 1024;
        int initHeight = 768;
        long window = glfwCreateWindow(initWidth, initHeight, "Zombie Survival 3D - Minecraft Classic Edition", 0, 0);

        if (window == 0) {
            showErrorAndRestore(client,
                "Failed to create OpenGL 3.3 Core Profile context.\n" +
                "Please verify your graphics hardware and drivers support OpenGL 3.3+.");
            isRunning.set(false);
            return;
        }

        // Center on primary monitor
        GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vidMode != null) {
            glfwSetWindowPos(window, (vidMode.width() - initWidth) / 2, (vidMode.height() - initHeight) / 2);
        }

        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        glfwSwapInterval(1); // vsync enabled
        glfwShowWindow(window);

        // 3. Mouse and Window callbacks
        isPaused = false;
        firstMouse = true;
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        VoxelCamera camera = new VoxelCamera();

        // Cursor position callback
        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            currentMouseX = (float) xpos;
            currentMouseY = (float) ypos;

            if (!isPaused) {
                if (firstMouse) {
                    lastMouseX = xpos;
                    lastMouseY = ypos;
                    firstMouse = false;
                }
                double dx = xpos - lastMouseX;
                double dy = ypos - lastMouseY;
                lastMouseX = xpos;
                lastMouseY = ypos;

                camera.rotate((float) dx, (float) dy);
            }
        });

        // Key callback (ESC toggles pause)
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS) {
                if (key == GLFW_KEY_ESCAPE) {
                    togglePause(win);
                }
            }
        });

        // Window focus callback
        glfwSetWindowFocusCallback(window, (win, focused) -> {
            if (!focused) {
                setPaused(win, true);
            }
        });

        // Mouse button callback for pause menu interaction
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                if (isPaused) {
                    int[] winW = new int[1], winH = new int[1];
                    glfwGetFramebufferSize(win, winW, winH);
                    int centerX = winW[0] / 2;
                    int centerY = winH[0] / 2;

                    int btnW = 260;
                    int btnH = 44;
                    int resumeY = centerY - 25;
                    int menuY   = centerY + 35;

                    // Clicked Resume
                    if (currentMouseX >= centerX - btnW / 2 && currentMouseX <= centerX + btnW / 2 &&
                        currentMouseY >= resumeY && currentMouseY <= resumeY + btnH) {
                        setPaused(win, false);
                        return;
                    }

                    // Clicked Back to Menu
                    if (currentMouseX >= centerX - btnW / 2 && currentMouseX <= centerX + btnW / 2 &&
                        currentMouseY >= menuY && currentMouseY <= menuY + btnH) {
                        glfwSetWindowShouldClose(win, true);
                        return;
                    }

                    // Click outside buttons resumes game
                    setPaused(win, false);
                }
            }
        });

        // 4. Initialize GL Rendering Systems
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        VoxelShader shader = new VoxelShader();
        TextureAtlas3D atlas = new TextureAtlas3D();
        VoxelFloorRenderer floor = new VoxelFloorRenderer(atlas);
        BlockyCloudRenderer clouds = new BlockyCloudRenderer();
        QuadRenderer2D quad2D = new QuadRenderer2D();
        BitmapFont3D font = new BitmapFont3D();

        Matrix4f projMatrix = new Matrix4f();
        Matrix4f viewMatrix = new Matrix4f();
        Matrix4f modelMatrix = new Matrix4f();
        Matrix4f orthoMatrix = new Matrix4f();

        double lastTime = glfwGetTime();

        // 5. Main Render Loop
        while (!glfwWindowShouldClose(window)) {
            double currentTime = glfwGetTime();
            float dt = (float) (currentTime - lastTime);
            lastTime = currentTime;
            if (dt > 0.1f) dt = 0.1f; // Clamped delta time

            // Handle gameplay input when active
            if (!isPaused) {
                // WASD: movement only
                boolean forward    = glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS;
                boolean back       = glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS;
                boolean strafeLeft = glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS;
                boolean strafeRight= glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS;
                boolean sprint     = glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS
                                  || glfwGetKey(window, GLFW_KEY_RIGHT_SHIFT) == GLFW_PRESS;
                boolean jump       = glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS;

                // Arrow keys: camera rotation only
                boolean turnLeft  = glfwGetKey(window, GLFW_KEY_LEFT)  == GLFW_PRESS;
                boolean turnRight = glfwGetKey(window, GLFW_KEY_RIGHT) == GLFW_PRESS;
                boolean lookUp    = glfwGetKey(window, GLFW_KEY_UP)    == GLFW_PRESS;
                boolean lookDown  = glfwGetKey(window, GLFW_KEY_DOWN)  == GLFW_PRESS;

                camera.update(dt,
                              forward, back, strafeLeft, strafeRight,
                              sprint, jump,
                              turnLeft, turnRight, lookUp, lookDown);
            }

            clouds.update(dt);

            // Framebuffer size
            int[] fWidth = new int[1], fHeight = new int[1];
            glfwGetFramebufferSize(window, fWidth, fHeight);
            int width = Math.max(1, fWidth[0]);
            int height = Math.max(1, fHeight[0]);

            glViewport(0, 0, width, height);

            // Minecraft Classic Sky Blue
            glClearColor(0.53f, 0.73f, 0.98f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // 3D Perspective Pass
            glEnable(GL_DEPTH_TEST);
            float aspect = (float) width / height;
            projMatrix.identity().perspective((float) Math.toRadians(VoxelCamera.FOV), aspect, 0.1f, 1000.0f);
            camera.getViewMatrix(viewMatrix);

            shader.bind();
            shader.setProjection(projMatrix);
            shader.setView(viewMatrix);
            shader.setSkyColor(0.53f, 0.73f, 0.98f);
            shader.setFog(40.0f, 95.0f);

            // Render floor
            floor.render(shader, atlas, modelMatrix.identity());

            // Render clouds
            clouds.render(shader, modelMatrix.identity());

            // 2D Orthographic Pass (HUD, crosshair, pause menu)
            glDisable(GL_DEPTH_TEST);
            orthoMatrix.identity().ortho(0, width, height, 0, -1, 1);
            shader.setProjection(orthoMatrix);
            shader.setView(viewMatrix.identity());
            shader.setModel(modelMatrix.identity());

            if (!isPaused) {
                // Crosshair in center
                int cx = width / 2;
                int cy = height / 2;
                // Horizontal bar
                quad2D.drawRect(shader, cx - 8, cy - 1, 16, 2, 1.0f, 1.0f, 1.0f, 0.85f);
                // Vertical bar
                quad2D.drawRect(shader, cx - 1, cy - 8, 2, 16, 1.0f, 1.0f, 1.0f, 0.85f);

                // Small HUD controls guide in top-left
                font.drawText(shader, "WASD: Move  ARROWS: Turn/Pitch  SHIFT: Sprint  SPACE: Jump  ESC: Pause", 14, 14, 0.8f, 1.0f, 1.0f, 1.0f, 0.9f);
                font.drawText(shader, "ZOMBIE SURVIVAL 3D [MILESTONE 1 - TURNING]", 14, 34, 0.8f, 0.4f, 1.0f, 0.4f, 0.9f);
            } else {
                // Pause Overlay: dark tint covering full screen
                quad2D.drawRect(shader, 0, 0, width, height, 0.0f, 0.0f, 0.0f, 0.65f);

                int centerX = width / 2;
                int centerY = height / 2;

                // Title
                String title = "GAME PAUSED";
                int titleW = font.getStringWidth(title, 1.6f);
                font.drawText(shader, title, centerX - titleW / 2, centerY - 100, 1.6f, 1.0f, 0.9f, 0.2f, 1.0f);

                int btnW = 260;
                int btnH = 44;
                int resumeY = centerY - 25;
                int menuY   = centerY + 35;

                // Resume button
                boolean hoverResume = currentMouseX >= centerX - btnW / 2 && currentMouseX <= centerX + btnW / 2 &&
                                     currentMouseY >= resumeY && currentMouseY <= resumeY + btnH;
                float rBg = hoverResume ? 0.35f : 0.20f;
                quad2D.drawRect(shader, centerX - btnW / 2, resumeY, btnW, btnH, rBg, rBg, rBg, 0.95f);
                quad2D.drawBorder(shader, centerX - btnW / 2, resumeY, btnW, btnH, 2, hoverResume ? 0.2f : 0.6f, 0.9f, 0.3f, 1.0f);
                String resumeText = "RESUME (ESC)";
                int resumeTextW = font.getStringWidth(resumeText, 1.0f);
                font.drawText(shader, resumeText, centerX - resumeTextW / 2, resumeY + 14, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f);

                // Back to Menu button
                boolean hoverMenu = currentMouseX >= centerX - btnW / 2 && currentMouseX <= centerX + btnW / 2 &&
                                   currentMouseY >= menuY && currentMouseY <= menuY + btnH;
                float mBg = hoverMenu ? 0.35f : 0.20f;
                quad2D.drawRect(shader, centerX - btnW / 2, menuY, btnW, btnH, mBg, mBg, mBg, 0.95f);
                quad2D.drawBorder(shader, centerX - btnW / 2, menuY, btnW, btnH, 2, hoverMenu ? 0.9f : 0.6f, 0.3f, 0.3f, 1.0f);
                String menuText = "BACK TO MENU";
                int menuTextW = font.getStringWidth(menuText, 1.0f);
                font.drawText(shader, menuText, centerX - menuTextW / 2, menuY + 14, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f);
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }

        // 6. Clean up GL resources (never terminate GLFW so relaunch works seamlessly)
        floor.cleanup();
        clouds.cleanup();
        quad2D.cleanup();
        atlas.cleanup();
        font.cleanup();
        shader.cleanup();

        glfwDestroyWindow(window);
        isRunning.set(false);

        // 7. Restore Swing Menu
        SwingUtilities.invokeLater(() -> {
            if (client.getMenuFrame() != null) {
                client.getMenuFrame().setVisible(true);
            }
            if (onExit != null) {
                onExit.run();
            }
        });
    }

    private static void togglePause(long window) {
        setPaused(window, !isPaused);
    }

    private static void setPaused(long window, boolean paused) {
        isPaused = paused;
        if (isPaused) {
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        } else {
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
            firstMouse = true;
        }
    }

    private static void showErrorAndRestore(GameClient client, String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(
                client.getMenuFrame(),
                message,
                "Graphics Initialization Error",
                JOptionPane.ERROR_MESSAGE
            );
            if (client.getMenuFrame() != null) {
                client.getMenuFrame().setVisible(true);
            }
        });
    }
}
