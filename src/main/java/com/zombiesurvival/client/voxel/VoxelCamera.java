package com.zombiesurvival.client.voxel;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * First-person 3D camera with mouse-look, keyboard turning, horizontal WASD
 * movement, sprint, jump, and gravity.
 *
 * Tunable constants (static fields, change at runtime or subclass):
 *   MOUSE_SENSITIVITY – degrees per raw pixel delta from GLFW
 *   ARROW_TURN_SPEED  – yaw degrees per second for Left / Right arrows
 *   ARROW_PITCH_SPEED – pitch degrees per second for Up / Down arrows
 *   PITCH_MAX         – absolute pitch clamp in degrees
 *   FOV               – perspective field-of-view in degrees
 *   SPEED_SCALE       – global movement speed multiplier
 */
public class VoxelCamera {

    // ── Tunable constants ────────────────────────────────────────────────────
    /** Mouse look: degrees of yaw/pitch per raw GLFW pixel delta. */
    public static float MOUSE_SENSITIVITY  = 0.12f;

    /** Arrow-key turning: yaw degrees per second (Left / Right arrows). */
    public static float ARROW_TURN_SPEED   = 120.0f;

    /** Arrow-key pitching: pitch degrees per second (Up / Down arrows). */
    public static float ARROW_PITCH_SPEED  = 80.0f;

    /** Camera pitch clamped to +/- this value in degrees. */
    public static float PITCH_MAX          = 89.0f;

    /** Perspective field-of-view in degrees. */
    public static float FOV         = 70.0f;

    /** Global movement speed multiplier. */
    public static float SPEED_SCALE = 1.0f;

    // ── Movement constants ───────────────────────────────────────────────────
    public static final float WALK_SPEED  = 4.3f;
    public static final float SPRINT_MULT = 1.4f;
    public static final float JUMP_SPEED  = 5.2f;
    public static final float GRAVITY     = -18.0f;
    public static final float EYE_HEIGHT  = 1.62f;

    // ── State ────────────────────────────────────────────────────────────────
    private final Vector3f position = new Vector3f(12.5f, 1.0f + EYE_HEIGHT, 9.0f);
    private float yaw   = -90.0f; // Facing -Z by default
    private float pitch =   0.0f;

    private final Vector3f front = new Vector3f(0, 0, -1);
    private final Vector3f right = new Vector3f(1, 0, 0);
    private final Vector3f up    = new Vector3f(0, 1, 0);

    private float   velocityY = 0.0f;
    private boolean onGround  = true;

    public VoxelCamera() {
        updateVectors();
    }

    // ── Per-frame update ─────────────────────────────────────────────────────

    /**
     * Update camera each frame.
     *
     * @param dt          Delta-time (seconds)
     * @param forward     W key held
     * @param back        S key held
     * @param strafeLeft  A key held
     * @param strafeRight D key held
     * @param sprint      Shift key held
     * @param jump        Space key held
     * @param turnLeft    Left-arrow key held  – rotates yaw left
     * @param turnRight   Right-arrow key held – rotates yaw right
     * @param lookUp      Up-arrow key held    – tilts pitch up
     * @param lookDown    Down-arrow key held  – tilts pitch down
     */
    public void update(float dt,
                       boolean forward, boolean back,
                       boolean strafeLeft, boolean strafeRight,
                       boolean sprint, boolean jump,
                       boolean turnLeft, boolean turnRight,
                       boolean lookUp, boolean lookDown) {

        // ── Arrow-key yaw / pitch ─────────────────────────────────────────────
        if (turnLeft)  yaw   -= ARROW_TURN_SPEED  * dt;
        if (turnRight) yaw   += ARROW_TURN_SPEED  * dt;
        if (lookUp)    pitch += ARROW_PITCH_SPEED * dt;
        if (lookDown)  pitch -= ARROW_PITCH_SPEED * dt;

        clampPitchAndUpdate();

        // ── WASD movement (yaw-relative, XZ plane only) ───────────────────────
        float speed = WALK_SPEED * (sprint ? SPRINT_MULT : 1.0f) * SPEED_SCALE * dt;

        Vector3f moveDir   = new Vector3f(0, 0, 0);
        Vector3f forwardXZ = new Vector3f(front.x, 0, front.z).normalize();
        Vector3f rightXZ   = new Vector3f(right.x, 0, right.z).normalize();

        if (forward)     moveDir.add(forwardXZ);
        if (back)        moveDir.sub(forwardXZ);
        if (strafeRight) moveDir.add(rightXZ);
        if (strafeLeft)  moveDir.sub(rightXZ);

        if (moveDir.lengthSquared() > 0.001f) {
            moveDir.normalize().mul(speed);
            position.add(moveDir);
        }

        // ── Jump + gravity ────────────────────────────────────────────────────
        if (jump && onGround) {
            velocityY = JUMP_SPEED;
            onGround  = false;
        }

        velocityY  += GRAVITY * dt;
        position.y += velocityY * dt;

        // Floor at Y=1 (top of ground blocks); eye is EYE_HEIGHT above feet
        float floorEyeY = 1.0f + EYE_HEIGHT;
        if (position.y <= floorEyeY) {
            position.y = floorEyeY;
            velocityY  = 0;
            onGround   = true;
        }
    }

    // ── Mouse look (called from GLFW cursor-position callback) ───────────────

    /**
     * Apply a raw mouse delta to yaw and pitch.
     * dx positive = turn right; dy positive = look down.
     */
    public void rotate(float dx, float dy) {
        yaw   += dx * MOUSE_SENSITIVITY;
        pitch -= dy * MOUSE_SENSITIVITY;
        clampPitchAndUpdate();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void clampPitchAndUpdate() {
        if (pitch >  PITCH_MAX) pitch =  PITCH_MAX;
        if (pitch < -PITCH_MAX) pitch = -PITCH_MAX;
        updateVectors();
    }

    private void updateVectors() {
        float radYaw   = (float) Math.toRadians(yaw);
        float radPitch = (float) Math.toRadians(pitch);

        front.x = (float) (Math.cos(radYaw) * Math.cos(radPitch));
        front.y = (float)  Math.sin(radPitch);
        front.z = (float) (Math.sin(radYaw) * Math.cos(radPitch));
        front.normalize();

        front.cross(new Vector3f(0, 1, 0), right).normalize();
        right.cross(front, up).normalize();
    }

    // ── View matrix ──────────────────────────────────────────────────────────

    public Matrix4f getViewMatrix(Matrix4f dest) {
        Vector3f target = new Vector3f(position).add(front);
        return dest.identity().lookAt(position, target, up);
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public Vector3f getPosition() { return position; }
    public float    getYaw()      { return yaw; }
    public float    getPitch()    { return pitch; }
    public Vector3f getFront()    { return front; }

    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
        updateVectors();
    }
}
