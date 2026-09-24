package com.zombiesurvival.shared;

/** Sent by a zombie player when activating a special ability. */
public class AbilityCommand implements NetworkMessage {
    private static final long serialVersionUID = 1L;

    public enum Type { LUNGE, HOWL }

    private final Type abilityType;

    public AbilityCommand(Type type) { this.abilityType = type; }

    public Type getAbilityType() { return abilityType; }
}
