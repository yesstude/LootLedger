package loot.ledger;

import loot.ledger.network.LootLedgerPackets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LootLedgerEvents {

    private static final Map<String, Map<Integer, ItemStack>> snapshots = new HashMap<>();
    private static final Map<String, Long> snapshotTimestamps = new HashMap<>();

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            BlockEntity be = world.getBlockEntity(pos);

            if (!isTrackedContainer(be)) return ActionResult.PASS;

            BlockPos trackPos = getCanonicalPos(world, pos, be);
            String key = snapshotKey((ServerPlayerEntity) player, trackPos);

            // Nur snapshot wenn dieser Key noch nicht aktiv ist
            if (snapshots.containsKey(key)) return ActionResult.PASS;

            // Kombinierte Inventory für Doppelkisten holen
            Inventory inventory = getInventory(world, pos, be);
            if (inventory == null) return ActionResult.PASS;

            takeSnapshot((ServerPlayerEntity) player, trackPos, inventory);
            snapshotTimestamps.put(key, System.currentTimeMillis());

            ServerPlayNetworking.send(
                    (ServerPlayerEntity) player,
                    new LootLedgerPackets.ContainerOpenedPayload(trackPos)
            );

            return ActionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (snapshots.isEmpty()) return;

            for (ServerWorld world : server.getWorlds()) {
                for (ServerPlayerEntity player : world.getPlayers()) {
                    checkInventoryChanges(player, world);
                }
            }

            cleanupClosedContainers(server.getWorlds());
        });
    }

    private static Inventory getInventory(net.minecraft.world.World world, BlockPos pos, BlockEntity be) {
        // Doppelkiste: kombinierte Inventory über ChestBlock holen
        if (be instanceof ChestBlockEntity) {
            net.minecraft.block.BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof ChestBlock chestBlock) {
                Inventory combined = ChestBlock.getInventory(chestBlock, state, world, pos, true);
                if (combined != null) return combined;
            }
        }
        // Alle anderen Container
        if (be instanceof Inventory inv) return inv;
        return null;
    }

    private static BlockPos getCanonicalPos(net.minecraft.world.World world, BlockPos pos, BlockEntity be) {
        if (be instanceof ChestBlockEntity) {
            net.minecraft.block.BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof ChestBlock) {
                ChestType chestType = state.get(ChestBlock.CHEST_TYPE);
                if (chestType != ChestType.SINGLE) {
                    net.minecraft.util.math.Direction facing = state.get(ChestBlock.FACING);
                    net.minecraft.util.math.Direction otherDir =
                            chestType == ChestType.LEFT
                                    ? facing.rotateYClockwise()
                                    : facing.rotateYCounterclockwise();
                    BlockPos otherPos = pos.offset(otherDir);
                    if (otherPos.compareTo(pos) < 0) {
                        return otherPos;
                    }
                }
            }
        }
        return pos;
    }

    private static boolean isTrackedContainer(BlockEntity be) {
        return be instanceof Inventory;
    }

    private static void takeSnapshot(ServerPlayerEntity player, BlockPos pos, Inventory inventory) {
        String key = snapshotKey(player, pos);
        Map<Integer, ItemStack> snapshot = new HashMap<>();
        for (int i = 0; i < inventory.size(); i++) {
            snapshot.put(i, inventory.getStack(i).copy());
        }
        snapshots.put(key, snapshot);
    }

    private static void checkInventoryChanges(ServerPlayerEntity player, ServerWorld world) {
        for (Map.Entry<String, Map<Integer, ItemStack>> entry : new HashMap<>(snapshots).entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(player.getUuidAsString())) continue;

            Long openedAt = snapshotTimestamps.get(key);
            if (openedAt == null || System.currentTimeMillis() - openedAt < 500) continue;

            BlockPos pos = keyToPos(key);
            if (pos == null) continue;

            BlockEntity be = world.getBlockEntity(pos);
            if (be == null) continue;

            Inventory inventory = getInventory(world, pos, be);
            if (inventory == null) continue;

            Map<Integer, ItemStack> oldSnapshot = entry.getValue();
            Map<Integer, ItemStack> newSnapshot = new HashMap<>();

            for (int i = 0; i < inventory.size(); i++) {
                newSnapshot.put(i, inventory.getStack(i).copy());
            }

            boolean changed = false;
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack oldStack = oldSnapshot.getOrDefault(i, ItemStack.EMPTY);
                ItemStack newStack = newSnapshot.getOrDefault(i, ItemStack.EMPTY);

                if (!ItemStack.areEqual(oldStack, newStack)) {
                    changed = true;

                    // Prüfen ob der Player selbst die Änderung gemacht hat
                    // via currentScreenHandler Slot-Vergleich
                    boolean playerMadeChange = isPlayerHoldingItem(player, oldStack, newStack);

                    // Nur loggen wenn dieser Spieler die Änderung gemacht hat
                    if (playerMadeChange) {
                        if (newStack.isEmpty() && !oldStack.isEmpty()) {
                            ContainerAccessLog.addEntry(pos, player.getName().getString(), oldStack, true);
                        } else if (!newStack.isEmpty() && oldStack.isEmpty()) {
                            ContainerAccessLog.addEntry(pos, player.getName().getString(), newStack, false);
                        } else {
                            int diff = newStack.getCount() - oldStack.getCount();
                            if (diff < 0) {
                                ItemStack diffStack = oldStack.copy();
                                diffStack.setCount(Math.abs(diff));
                                ContainerAccessLog.addEntry(pos, player.getName().getString(), diffStack, true);
                            } else if (diff > 0) {
                                ItemStack diffStack = newStack.copy();
                                diffStack.setCount(diff);
                                ContainerAccessLog.addEntry(pos, player.getName().getString(), diffStack, false);
                            }
                        }
                    }
                }
            }

            if (changed) {
                // Snapshot für ALLE Spieler die diese Kiste offen haben updaten!
                updateAllSnapshotsForPos(pos, newSnapshot);
            }
        }
    }

    private static boolean isPlayerHoldingItem(ServerPlayerEntity player, ItemStack oldStack, ItemStack newStack) {
        // Spieler hat Item rausgenommen: er hält es jetzt in der Hand/Cursor
        ItemStack cursor = player.currentScreenHandler.getCursorStack();
        if (!oldStack.isEmpty() && newStack.isEmpty()) {
            return ItemStack.areItemsEqual(cursor, oldStack);
        }
        // Spieler hat Item reingelegt: Cursor war vorher voll
        if (oldStack.isEmpty() && !newStack.isEmpty()) {
            return ItemStack.areItemsEqual(cursor, newStack) || cursor.isEmpty();
        }
        return true;
    }

    private static void updateAllSnapshotsForPos(BlockPos pos, Map<Integer, ItemStack> newSnapshot) {
        String posStr = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        for (Map.Entry<String, Map<Integer, ItemStack>> entry : snapshots.entrySet()) {
            if (entry.getKey().contains("@" + posStr)) {
                entry.setValue(new HashMap<>(newSnapshot));
            }
        }
    }

    private static void cleanupClosedContainers(Iterable<ServerWorld> worlds) {
        Set<String> activeKeys = new HashSet<>();

        for (ServerWorld world : worlds) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                ScreenHandler openHandler = player.currentScreenHandler;
                if (openHandler != null && openHandler != player.playerScreenHandler) {
                    for (String key : snapshots.keySet()) {
                        if (key.startsWith(player.getUuidAsString())) {
                            activeKeys.add(key);
                        }
                    }
                }
            }
        }

        snapshotTimestamps.keySet().removeIf(key -> !activeKeys.contains(key));
        snapshots.keySet().removeIf(key -> !activeKeys.contains(key));
    }

    private static String snapshotKey(ServerPlayerEntity player, BlockPos pos) {
        return player.getUuidAsString() + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static BlockPos keyToPos(String key) {
        try {
            String[] parts = key.split("@")[1].split(",");
            return new BlockPos(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
            );
        } catch (Exception e) {
            return null;
        }
    }
}