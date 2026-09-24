package com.zombiesurvival.shared;

import java.io.Serializable;

/** One entry in the kill/infection feed shown in the top-right corner. */
public class KillFeedEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Method { SHOT, MELEE, INFECTED, NPC_ATTACK }

    private final String killerName;
    private final String victimName;
    private final Method method;
    private final long   timestamp;

    public KillFeedEntry(String killerName, String victimName, Method method) {
        this.killerName = killerName;
        this.victimName = victimName;
        this.method     = method;
        this.timestamp  = System.currentTimeMillis();
    }

    public String getKillerName() { return killerName; }
    public String getVictimName() { return victimName; }
    public Method getMethod()     { return method; }
    public long   getTimestamp()  { return timestamp; }
    public boolean isExpired()    { return System.currentTimeMillis() - timestamp > 6_000; }

    public String icon() {
        return switch (method) {
            case SHOT      -> "🔫";
            case MELEE     -> "🏏";
            case INFECTED  -> "☣";
            case NPC_ATTACK-> "🧟";
        };
    }
}
