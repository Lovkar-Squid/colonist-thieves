package me.lovkar.thieves;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The few things a server owner has to be able to decide.
 *
 * <p>Deliberately short. Robbing another player's colony is <b>off</b> until somebody turns it
 * on, because a mod that lets a stranger empty your warehouse by default is a mod that gets
 * removed from the pack on the first evening.</p>
 */
public final class ThievesConfig {
    private ThievesConfig() {
    }

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue PLAYER_COLONIES;
    private static final ModConfigSpec.BooleanValue RETALIATION;
    private static final ModConfigSpec.IntValue MAX_STEAL;
    private static final ModConfigSpec.BooleanValue TELL_THE_VICTIM;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Colonist Thieves").push("thieves");
        PLAYER_COLONIES = builder
                .comment("Allow marks on colonies owned by other players. Off by default: on a shared server",
                        "this turns the mod from flavour into PvP, and that has to be somebody's decision.")
                .define("allowPlayerColonyTheft", false);
        MAX_STEAL = builder
                .comment("At most this many item stacks may be taken out of another player's warehouse in one job.")
                .defineInRange("maxStacksFromPlayerColony", 3, 1, 27);
        RETALIATION = builder
                .comment("A thief who is caught brings a raid down on his own colony the following night.")
                .define("retaliation", true);
        TELL_THE_VICTIM = builder
                .comment("Tell the robbed colony's owner when a thief is caught or seen. A clean job is never",
                        "announced - that is the whole point of a clean job.")
                .define("tellTheVictim", true);
        builder.pop();
        SPEC = builder.build();
    }

    public static boolean allowPlayerColonyTheft() {
        return read(PLAYER_COLONIES, false);
    }

    public static boolean retaliation() {
        return read(RETALIATION, true);
    }

    public static boolean tellTheVictim() {
        return read(TELL_THE_VICTIM, true);
    }

    public static int maxStacksFromPlayerColony() {
        return read(MAX_STEAL, 3);
    }

    private static boolean read(final ModConfigSpec.BooleanValue value, final boolean fallback) {
        try {
            return value.get();
        } catch (final Throwable t) {
            return fallback;
        }
    }

    private static int read(final ModConfigSpec.IntValue value, final int fallback) {
        try {
            return value.get();
        } catch (final Throwable t) {
            return fallback;
        }
    }
}
