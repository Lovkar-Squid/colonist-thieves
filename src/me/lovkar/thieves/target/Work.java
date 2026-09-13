package me.lovkar.thieves.target;

/**
 * What a thief is being sent to <b>do</b>.
 *
 * <p>A target is a place; this is the verb. Robbing is what the mod is for and needs nothing else
 * installed. The other two exist only when Colonies at War stands beside it and the colony has a
 * War Room, because they are the seam between the two mods: a thief is how a colony finds out what
 * it is about to march into, and how it makes sure what it marches into is weaker than it was.</p>
 */
public enum Work {
    /** Go and take something. The default, and the only one that needs no war. */
    ROB("rob", 1.0f, true),
    /**
     * Go and look, take nothing.
     *
     * <p>Easier than a theft - nothing has to be carried back out past anybody - and it leaves a
     * reading that makes later jobs against that place go better while it stays fresh.</p>
     */
    SCOUT("scout", 0.7f, false),
    /**
     * Go and break something.
     *
     * <p>The hardest of the three, because it has to be done where somebody would notice, and the
     * one with real consequences: the place is measurably weaker for a while afterwards.</p>
     */
    SABOTAGE("sabotage", 1.35f, false);

    private final String id;
    private final float difficulty;
    private final boolean loot;

    Work(final String id, final float difficulty, final boolean loot) {
        this.id = id;
        this.difficulty = difficulty;
        this.loot = loot;
    }

    public String id() {
        return id;
    }

    /** Multiplies what the thief has to beat. Looking is easier than taking; breaking is worse. */
    public float difficulty() {
        return difficulty;
    }

    /** Whether anything comes home in the sack. */
    public boolean loot() {
        return loot;
    }

    /** True if this job needs the war mod and a War Room to be offered at all. */
    public boolean needsWarRoom() {
        return this != ROB;
    }

    public String nameKey() {
        return "com.colonist_thieves.work." + id;
    }

    public static Work byId(final String name) {
        for (final Work work : values()) {
            if (work.id.equalsIgnoreCase(name)) {
                return work;
            }
        }
        return ROB;
    }
}
