package loot.ledger;

import loot.ledger.network.LootLedgerPackets;
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
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class LootLedgerEvents {

    private static final Map<String, Map<Integer, ItemStack>> snapshots = new HashMap<>();

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            BlockEntity be = world.getBlockEntity(pos);
            if (!isTrackedContainer(be)) return ActionResult.PASS;

            BlockPos trackPos = getCanonicalPos(world, pos, be);
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;

            Inventory inventory = getInventory(world, pos, be);
            if (inventory == null) return ActionResult.PASS;

            pendingPos.put(serverPlayer.getUuidAsString(), trackPos);
            pendingInventorySize.put(serverPlayer.getUuidAsString(), inventory.size());

            ServerPlayNetworking.send(
                    serverPlayer,
                    new LootLedgerPackets.ContainerOpenedPayload(trackPos)
            );

            return ActionResult.PASS;
        });

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (pendingPos.isEmpty()) return;
            for (net.minecraft.server.world.ServerWorld world : server.getWorlds()) {
                for (ServerPlayerEntity player : world.getPlayers()) {
                    String uuid = player.getUuidAsString();
                    BlockPos pos = pendingPos.get(uuid);
                    if (pos == null) continue;

                    ScreenHandler handler = player.currentScreenHandler;
                    if (handler == player.playerScreenHandler) continue;

                    ((ScreenHandlerPos)(Object) handler).lootledger_setPos(pos);

                    String key = snapshotKey(player, pos);
                    if (!snapshots.containsKey(key)) {
                        BlockEntity be = world.getBlockEntity(pos);
                        if (be != null) {
                            Inventory inventory = getInventory(world, pos, be);
                            if (inventory != null) {
                                takeSnapshot(player, pos, inventory);
                            }
                        }
                    }

                    pendingPos.remove(uuid);
                    pendingInventorySize.remove(uuid);
                }
            }
        });
    }

    private static final Map<String, BlockPos> pendingPos = new HashMap<>();
    private static final Map<String, Integer> pendingInventorySize = new HashMap<>();

    public static void afterSlotClick(ServerPlayerEntity player, ScreenHandler handler,
                                      int slotIndex, ItemStack oldStack, ItemStack newStack) {
        if (handler == player.playerScreenHandler) return;
        if (ItemStack.areEqual(oldStack, newStack)) return;

        BlockPos pos = ((ScreenHandlerPos)(Object) handler).lootledger_getPos();
        if (pos == null) return;

        String key = snapshotKey(player, pos);
        if (!snapshots.containsKey(key)) return;

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

        updateSnapshotSlotForAll(pos, slotIndex, newStack);
    }

    public static void onContainerClosed(ServerPlayerEntity player) {
        pendingPos.remove(player.getUuidAsString());
        pendingInventorySize.remove(player.getUuidAsString());
        snapshots.keySet().removeIf(key -> key.startsWith(player.getUuidAsString()));
    }

    private static void updateSnapshotSlotForAll(BlockPos pos, int slotIndex, ItemStack newStack) {
        String posStr = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        for (Map.Entry<String, Map<Integer, ItemStack>> entry : snapshots.entrySet()) {
            if (entry.getKey().contains("@" + posStr)) {
                entry.getValue().put(slotIndex, newStack.copy());
            }
        }
    }

    private static Inventory getInventory(net.minecraft.world.World world, BlockPos pos, BlockEntity be) {
        if (be instanceof ChestBlockEntity) {
            net.minecraft.block.BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof ChestBlock chestBlock) {
                Inventory combined = ChestBlock.getInventory(chestBlock, state, world, pos, true);
                if (combined != null) return combined;
            }
        }
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
                    if (otherPos.compareTo(pos) < 0) return otherPos;
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

    public static String snapshotKey(ServerPlayerEntity player, BlockPos pos) {
        return player.getUuidAsString() + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}