package me.lovkar.thieves.target;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * How nervous a place is about being robbed.
 *
 * <p>Heat is what stops one fat camp from being farmed forever. Every job raises the target's
 * heat - a little when the thief is clean, a lot when he is seen, more when he is caught - and it
 * cools on its own, a point a day. High heat means more guards awake, a worse roll and a thinner
 * take, so the answer is always to go somewhere else for a while, which is what a thief would do.</p>
 *
 * <p>Kept on the overworld with the rest of the world's save data, keyed by {@link Target#key()},
 * so it belongs to the <i>place</i> rather than to whoever robbed it - two colonies robbing the
 * same camp both find it jumpy.</p>
 */
public class Heat extends SavedData {
    private static final String NAME = "colonist_thieves_heat";
    private static final String TAG_KEYS = "keys";
    private static final String TAG_HEAT = "heat";
    private static final String TAG_WHEN = "when";

    /** One in-game day. */
    private static final long DAY = 24000L;
    /** Heat cools this much per day. */
    private static final int COOLING = 8;
    public static final int MAX = 100;

    private final Map<String, int[]> heat = new HashMap<>();

    public static final SavedData.Factory<Heat> FACTORY = new SavedData.Factory<>(Heat::new, Heat::load);

    private static Heat load(final CompoundTag tag, final HolderLookup.Provider provider) {
        final Heat out = new Heat();
        final CompoundTag keys = tag.getCompound(TAG_KEYS);
        for (final String key : keys.getAllKeys()) {
            final CompoundTag one = keys.getCompound(key);
            out.heat.put(key, new int[]{one.getInt(TAG_HEAT), (int) (one.getLong(TAG_WHEN) / DAY)});
        }
        return out;
    }

    public static Heat get(final Level level) {
        return ((ServerLevel) level).getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** The heat on a place right now, already cooled for the days since it was last touched. */
    public static int of(final Level level, final String key) {
        if (!(level instanceof ServerLevel server)) {
            return 0;
        }
        return get(server).read(server, key);
    }

    /** Raise a place's heat and remember when. */
    public static void raise(final Level level, final String key, final int by) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        final Heat data = get(server);
        final int now = data.read(server, key);
        data.heat.put(key, new int[]{Math.min(MAX, now + by), (int) (server.getGameTime() / DAY)});
        data.setDirty();
    }

    private int read(final ServerLevel level, final String key) {
        final int[] entry = heat.get(key);
        if (entry == null) {
            return 0;
        }
        final int today = (int) (level.getGameTime() / DAY);
        final int cooled = Math.max(0, entry[0] - Math.max(0, today - entry[1]) * COOLING);
        return cooled;
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider provider) {
        final CompoundTag keys = new CompoundTag();
        for (final Map.Entry<String, int[]> e : heat.entrySet()) {
            final CompoundTag one = new CompoundTag();
            one.putInt(TAG_HEAT, e.getValue()[0]);
            one.putLong(TAG_WHEN, e.getValue()[1] * DAY);
            keys.put(e.getKey(), one);
        }
        tag.put(TAG_KEYS, keys);
        return tag;
    }
}
