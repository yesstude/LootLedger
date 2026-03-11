package loot.ledger.mixin;

import loot.ledger.LootLedgerEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(ScreenHandler.class)
public class SlotClickMixin {

    private Map<Integer, ItemStack> lootledger_snapshot = new HashMap<>();

    @Inject(method = "onSlotClick", at = @At("HEAD"))
    private void onSlotClickHead(int slotIndex, int button, SlotActionType actionType,
                                 PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        ScreenHandler handler = (ScreenHandler) (Object) this;
        if (handler == serverPlayer.playerScreenHandler) return;

        lootledger_snapshot.clear();
        for (Slot slot : handler.slots) {
            if (!(slot.inventory instanceof PlayerInventory)) {
                lootledger_snapshot.put(handler.slots.indexOf(slot), slot.getStack().copy());
            }
        }
    }

    @Inject(method = "onSlotClick", at = @At("TAIL"))
    private void onSlotClickTail(int slotIndex, int button, SlotActionType actionType,
                                 PlayerEntity player, CallbackInfo ci) {
        if (lootledger_snapshot.isEmpty()) return;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        ScreenHandler handler = (ScreenHandler) (Object) this;
        if (handler == serverPlayer.playerScreenHandler) return;

        for (Slot slot : handler.slots) {
            if (slot.inventory instanceof PlayerInventory) continue;
            int idx = handler.slots.indexOf(slot);
            ItemStack before = lootledger_snapshot.getOrDefault(idx, ItemStack.EMPTY);
            ItemStack after = slot.getStack().copy();
            if (!ItemStack.areEqual(before, after)) {
                LootLedgerEvents.afterSlotClick(serverPlayer, handler, idx, before, after);
            }
        }

        lootledger_snapshot.clear();
    }
}