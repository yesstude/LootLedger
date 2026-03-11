package loot.ledger.mixin;

import loot.ledger.LootLedgerEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class ContainerCloseMixin {

    @Inject(method = "closeHandledScreen", at = @At("HEAD"))
    private void onCloseHandledScreen(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        LootLedgerEvents.onContainerClosed(player);
    }
}