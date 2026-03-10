package loot.ledger;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class ContainerAccessLog {

    public static final int MAX_ENTRIES = 50;

    private static final Map<BlockPos, List<LogEntry>> log = new HashMap<>();

    public static void addEntry(BlockPos pos, String playerName, ItemStack stack, boolean removed) {
        List<LogEntry> entries = log.computeIfAbsent(pos, k -> new ArrayList<>());

        String itemId   = Registries.ITEM.getId(stack.getItem()).toString();
        String itemName = stack.getName().getString();
        int count       = stack.getCount();

        entries.add(0, new LogEntry(playerName, itemId, itemName, count, removed, System.currentTimeMillis()));

        if (entries.size() > MAX_ENTRIES) {
            entries.subList(MAX_ENTRIES, entries.size()).clear();
        }
    }

    public static List<LogEntry> getEntries(BlockPos pos) {
        return log.getOrDefault(pos, Collections.emptyList());
    }

    public static Map<BlockPos, List<LogEntry>> getAllEntries() {
        return Collections.unmodifiableMap(log);
    }

    public static void clearAll() {
        log.clear();
    }

    public static NbtCompound toNbt(BlockPos pos) {
        NbtCompound compound = new NbtCompound();
        NbtList list = new NbtList();
        for (LogEntry entry : log.getOrDefault(pos, Collections.emptyList())) {
            list.add(entry.toNbt());
        }
        compound.put("entries", list);
        return compound;
    }

    public static void fromNbt(BlockPos pos, NbtCompound compound) {
        NbtList list = compound.getListOrEmpty("entries");
        List<LogEntry> entries = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i).orElseGet(NbtCompound::new);
            entries.add(LogEntry.fromNbt(entry));
        }
        log.put(pos, entries);
    }

    // ---- Inner Class ----

    public static class LogEntry {
        public final String playerName;
        public final String itemId;
        public final String itemName;
        public final int count;
        public final boolean removed;
        public final long timestamp;

        public LogEntry(String playerName, String itemId, String itemName, int count, boolean removed, long timestamp) {
            this.playerName = playerName;
            this.itemId     = itemId;
            this.itemName   = itemName;
            this.count      = count;
            this.removed    = removed;
            this.timestamp  = timestamp;
        }

        public NbtCompound toNbt() {
            NbtCompound compound = new NbtCompound();
            compound.putString("player",   playerName);
            compound.putString("itemId",   itemId);
            compound.putString("itemName", itemName);
            compound.putInt("count",       count);
            compound.putBoolean("removed", removed);
            compound.putLong("timestamp",  timestamp);
            return compound;
        }

        public static LogEntry fromNbt(NbtCompound compound) {
            String player   = compound.getString("player").orElse("Unknown");
            String itemId   = compound.getString("itemId").orElse("minecraft:air");
            String itemName = compound.getString("itemName").orElse("Unknown Item");
            int count       = compound.getInt("count").orElse(1);
            boolean removed = compound.getBoolean("removed").orElse(true);
            long timestamp  = compound.getLong("timestamp").orElse(0L);
            return new LogEntry(player, itemId, itemName, count, removed, timestamp);
        }
    }
}