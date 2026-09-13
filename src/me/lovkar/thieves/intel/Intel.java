package me.lovkar.thieves.intel;

import me.lovkar.thieves.Thieves;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * What a colony has found out about somewhere, and what it has broken there.
 *
 * <p>The other half of {@link me.lovkar.thieves.target.Heat}. Heat is what a place knows about
 * <i>you</i> and it makes everything harder; this is what you know about <i>it</i>, and it makes
 * the next job easier. They are deliberately symmetrical and deliberately both temporary: rob the
 * same village twice and it gets harder, scout it first and it gets easier, and both wear off, so
 * neither a thief nor a target is ever finished with.</p>
 *
 * <p>Kept per place rather than per colony, by the same {@code Target.key()} heat uses, and stored
 * on the overworld with the rest of the world's save data.</p>
 */
public class Intel extends SavedData {
    private static final String NAME = "colonist_thieves_intel";
    private static final String TAG_KEYS = "keys";
    private static final String TAG_LINE = "line";
    private static final String TAG_SCOUTED = "scouted";
    private static final String TAG_SHAKEN = "shaken";

    /** One in-game day. */
    private static final long DAY = 24000L;
    /** How long a scout's reading is worth anything. A week: long enough to plan, short enough to lose. */
    public static final long SCOUT_DAYS = 7;
    /** How long a sabotage keeps a place off its stride. */
    public static final long SABOTAGE_DAYS = 3;
    /** The most vigilance a fresh reading takes off. */
    public static final float KNOWN = 0.25f;
    /** The most a sabotage takes off, on top. */
    public static final float SHAKEN = 0.25f;

    /** What is known about one place. */
    public record Note(String line, long scoutedOn, long shakenOn) {
    }

    private final Map<String, Note> notes = new HashMap<>();

    public static final SavedData.Factory<Intel> FACTORY =
            new SavedData.Factory<>(Intel::new, Intel::load);

    private static Intel load(final CompoundTag tag, final HolderLookup.Provider provider) {
        final Intel out = new Intel();
        final CompoundTag keys = tag.getCompound(TAG_KEYS);
        for (final String key : keys.getAllKeys()) {
            final CompoundTag one = keys.getCompound(key);
            out.notes.put(key, new Note(one.getString(TAG_LINE),
                    one.getLong(TAG_SCOUTED), one.getLong(TAG_SHAKEN)));
        }
        return out;
    }

    public static Intel get(final Level level) {
        return ((ServerLevel) level).getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    // ------------------------------------------------------------------ reading

    /** What is known about this place, or null. */
    public static Note of(final Level level, final String key) {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        return get(server).notes.get(key);
    }

    /** How much of a place's vigilance a fresh scouting takes off, 0 when nobody has been. */
    public static float known(final Level level, final String key) {
        return fade(level, key, true) * KNOWN;
    }

    /** How much a recent sabotage takes off, 0 when nothing has been broken. */
    public static float shaken(final Level level, final String key) {
        return fade(level, key, false) * SHAKEN;
    }

    /**
     * 1 the night it happened, 0 once it has run out, and a straight line in between.
     *
     * <p>A cliff would mean a thief waiting for the seventh day and then going on the sixth; a
     * fade means intelligence is worth most while it is new, which is the true thing about it.</p>
     */
    private static float fade(final Level level, final String key, final boolean scouting) {
        final Note note = of(level, key);
        if (note == null) {
            return 0f;
        }
        final long when = scouting ? note.scoutedOn() : note.shakenOn();
        if (when <= 0) {
            return 0f;
        }
        final long span = (scouting ? SCOUT_DAYS : SABOTAGE_DAYS) * DAY;
        final long age = level.getGameTime() - when;
        if (age < 0 || age >= span) {
            return 0f;
        }
        return 1f - age / (float) span;
    }

    // ------------------------------------------------------------------ writing

    /** A thief came back from a look. */
    public static void scouted(final Level level, final String key, final String line) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        final Intel data = get(server);
        final Note old = data.notes.get(key);
        data.notes.put(key, new Note(line, server.getGameTime(), old == null ? 0 : old.shakenOn()));
        data.setDirty();
        Thieves.LOGGER.info("[intel] {} is known: {}", key, line);
    }

    /** A thief broke something. */
    public static void setBack(final Level level, final String key) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        final Intel data = get(server);
        final Note old = data.notes.get(key);
        data.notes.put(key, new Note(old == null ? "" : old.line(),
                old == null ? 0 : old.scoutedOn(), server.getGameTime()));
        data.setDirty();
        Thieves.LOGGER.info("[intel] {} has been set back", key);
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider provider) {
        final CompoundTag keys = new CompoundTag();
        for (final Map.Entry<String, Note> entry : notes.entrySet()) {
            final CompoundTag one = new CompoundTag();
            one.putString(TAG_LINE, entry.getValue().line());
            one.putLong(TAG_SCOUTED, entry.getValue().scoutedOn());
            one.putLong(TAG_SHAKEN, entry.getValue().shakenOn());
            keys.put(entry.getKey(), one);
        }
        tag.put(TAG_KEYS, keys);
        return tag;
    }
}
