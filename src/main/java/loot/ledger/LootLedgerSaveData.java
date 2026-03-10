package loot.ledger;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class LootLedgerSaveData {

    private static File saveFile;

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            saveFile = getSaveFile(server);
            load();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            save();
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 6000 == 0) {
                save();
            }
        });
    }

    private static File getSaveFile(MinecraftServer server) {
        File worldDir = server.getSavePath(WorldSavePath.ROOT).toFile();
        File modDir = new File(worldDir, "lootledger");
        modDir.mkdirs();
        return new File(modDir, "container_log.nbt");
    }

    public static void save() {
        if (saveFile == null) return;

        try {
            NbtCompound root = new NbtCompound();
            NbtCompound containers = new NbtCompound();

            for (Map.Entry<BlockPos, List<ContainerAccessLog.LogEntry>> entry
                    : ContainerAccessLog.getAllEntries().entrySet()) {

                BlockPos pos = entry.getKey();
                String key = pos.getX() + "," + pos.getY() + "," + pos.getZ();
                containers.put(key, ContainerAccessLog.toNbt(pos));
            }

            root.put("containers", containers);
            NbtIo.write(root, saveFile.toPath());
            LootLedger.LOGGER.info("[LootLedger] Gespeichert.");
        } catch (IOException e) {
            LootLedger.LOGGER.error("[LootLedger] Fehler beim Speichern!", e);
        }
    }

    public static void load() {
        if (saveFile == null || !saveFile.exists()) return;

        try {
            NbtCompound root = NbtIo.read(saveFile.toPath());
            if (root == null) return;

            NbtCompound containers = root.getCompoundOrEmpty("containers");

            for (String key : containers.getKeys()) {
                String[] parts = key.split(",");
                BlockPos pos = new BlockPos(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2])
                );
                ContainerAccessLog.fromNbt(pos, containers.getCompoundOrEmpty(key));
            }

            LootLedger.LOGGER.info("[LootLedger] Geladen.");
        } catch (IOException e) {
            LootLedger.LOGGER.error("[LootLedger] Fehler beim Laden!", e);
        }
    }
}