package com.zombiesurvival.client.voxel;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * First-person 3D camera with mouse-look, horizontal WASD movement,
 * sprint, jump, and gravity.
 */
public class VoxelCamera {

    // Configurable constants
    public static float FOV = 70.0f;
    public static float SPEED_SCALE = 1.0f;

    public static final float WALK_SPEED  = 4.3f;
    public static final float SPRINT_MULT = 1.4f;
    public static final float JUMP_SPEED  = 5.2f;
    public static final float GRAVITY     = -18.0f;
    public static final float EYE_HEIGHT  = 1.62f;

    private final Vector3f position = new Vector3f(12.5f, EYE_HEIGHT, 9.0f);
    private float yaw = -90.0f; // Facing -Z by default
    private float pitch = 0.0f;

    private final Vector3f front = new Vector3f(0, 0, -1);
    private final Vector3f right = new Vector3f(1, 0, 0);
    private final Vector3f up    = new Vector3f(0, 1, 0);

    private float velocityY = 0.0f;
    private boolean onGround = true;
    private float mouseSensitivity = 0.12f;

    public VoxelCamera() {
        updateVectors();
    }

    public void update(float dt, boolean forward, boolean back, boolean left, boolean rightMove,
                       boolean sprint, boolean jump) {
        float speed = WALK_SPEED * (sprint ? SPRINT_MULT : 1.0f) * SPEED_SCALE * dt;

        // Horizontal movement on XZ plane only
        Vector3f moveDir = new Vector3f(0, 0, 0);
        Vector3f forwardXZ = new Vector3f(front.x, 0, front.z).normalize();
        Vector3f rightXZ   = new Vector3f(right.x, 0, right.z).normalize();

        if (forward)   moveDir.add(forwardXZ);
        if (back)      moveDir.sub(forwardXZ);
        if (rightMove) moveDir.add(rightXZ);
        if (left)      moveDir.sub(rightXZ);

        if (moveDir.lengthSquared() > 0.001f) {
            moveDir.normalize().mul(speed);
            position.add(moveDir);
        }

        // Jump and vertical gravity
        if (jump && onGround) {
            velocityY = JUMP_SPEED;
            onGround = false;
        }

        velocityY += GRAVITY * dt;
        position.y += velocityY * dt;

        // Flat ground collision at Y = 0 (Feet at Y = 1, eye at Y = 1 + EYE_HEIGHT)
        float floorEyeY = 1.0f + EYE_HEIGHT;
        if (position.y <= floorEyeY) {
            position.y = floorEyeY;
            velocityY = 0;
            onGround = true;
        }
    }

    public void rotate(float dx, float dy) {
        yaw += dx * mouseSensitivity;
        pitch -= dy * mouseSensitivity;

        if (pitch > 89.0f)  pitch = 89.0f;
        if (pitch < -89.0f) pitch = -89.0f;

        updateVectors();
    }

    private void updateVectors() {
        float radYaw   = (float) Math.toRadians(yaw);
        float radPitch = (float) Math.toRadians(pitch);

        front.x = (float) (Math.cos(radYaw) * Math.cos(radPitch));
        front.y = (float) Math.sin(radPitch);
        front.z = (float) (Math.sin(radYaw) * Math.cos(radPitch));
        front.normalize();

        front.cross(new Vector3f(0, 1, 0), right).normalize();
        right.cross(front, up).normalize();
    }

    public Matrix4f getViewMatrix(Matrix4f dest) {
        Vector3f target = new Vector3f(position).add(front);
        return dest.identity().lookAt(position, target, up);
    }

    public Vector3f getPosition() { return position; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public Vector3f getFront() { return front; }
    public void setPosition(float x, float y, float z) { position.set(x, y, z); }
}
