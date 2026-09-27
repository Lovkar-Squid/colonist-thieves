package me.lovkar.thieves.job;

/**
 * How a night's work ended. Three answers, and the difference between them is the whole game.
 *
 * <p>{@link #CLEAN} is the only one that costs nothing: on a server, a colony that is robbed
 * cleanly knows only that its stores are short. {@link #SPOTTED} means somebody saw a stranger in
 * the warehouse and can guess where he came from. {@link #CAUGHT} means they have him - no loot, a
 * wounded or held thief, and a raid on its way. That is Lovkar's first rule in three constants:
 * <b>standing falls only when the job goes wrong.</b></p>
 */
public enum Outcome {
    /** In and out. Nobody knows, and nothing is owed. */
    CLEAN("clean"),
    /** Seen, not held. The loot comes home; so does the grudge. */
    SPOTTED("spotted"),
    /** Held. Nothing comes home but the thief, and not always him. */
    CAUGHT("caught");

    private final String id;

    Outcome(final String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public boolean keepsLoot() {
        return this != CAUGHT;
    }

    /** True if the victim can put a name to it - the only case that moves standing. */
    public boolean discovered() {
        return this != CLEAN;
    }

    /** What this does to the place's nerves. */
    public int heat() {
        return switch (this) {
            case CLEAN -> 6;
            case SPOTTED -> 18;
            case CAUGHT -> 30;
        };
    }

    public static Outcome byId(final String id) {
        for (final Outcome o : values()) {
            if (o.id.equals(id)) {
                return o;
            }
        }
        return CLEAN;
    }
}
