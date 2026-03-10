package loot.ledger.mixin.client;

import loot.ledger.LootLedgerClient;
import loot.ledger.LootLedgerScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientContainerOpenMixin {

    @Inject(method = "onOpenScreen", at = @At("TAIL"))
    private void onOpenScreen(OpenScreenS2CPacket packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        client.execute(() -> {
            // Wenn wir bereits eine pendingPos vom Server haben, setze sie
            if (LootLedgerClient.pendingPos != null
                    && client.currentScreen instanceof HandledScreen<?> screen
                    && screen instanceof LootLedgerScreen lootScreen) {
                lootScreen.setLootLedgerPos(LootLedgerClient.pendingPos);
            }
        });
    }
}