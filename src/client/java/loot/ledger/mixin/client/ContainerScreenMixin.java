package loot.ledger.mixin.client;

import loot.ledger.LootLedgerScreen;
import loot.ledger.network.LootLedgerPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class ContainerScreenMixin<T extends ScreenHandler> extends Screen implements LootLedgerScreen {

    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected int backgroundWidth;
    @Shadow protected int backgroundHeight;

    private ButtonWidget lootLedgerButton;
    private BlockPos lootLedgerPos;

    protected ContainerScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        int btnX = this.x + this.backgroundWidth + 4;
        int btnY = this.y + (this.backgroundHeight / 2) - 10;

        lootLedgerButton = ButtonWidget.builder(Text.empty(), btn -> {
                    if (lootLedgerPos != null) {
                        ClientPlayNetworking.send(new LootLedgerPackets.RequestLogPayload(lootLedgerPos));
                    }
                })
                .dimensions(btnX, btnY, 20, 20)
                .tooltip(Tooltip.of(Text.literal("Show Container History")))
                .build();

        lootLedgerButton.visible = false;
        this.addDrawableChild(lootLedgerButton);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (lootLedgerButton != null && lootLedgerPos != null) {
            lootLedgerButton.visible = true;
            // Buch als Icon auf dem Button zeichnen
            context.drawItem(
                    new ItemStack(Items.WRITABLE_BOOK),
                    lootLedgerButton.getX() + 2,
                    lootLedgerButton.getY() + 2
            );
        } else if (lootLedgerButton != null) {
            lootLedgerButton.visible = false;
        }
    }

    @Override
    public void setLootLedgerPos(BlockPos pos) {
        this.lootLedgerPos = pos;
        if (lootLedgerButton != null) {
            lootLedgerButton.visible = true;
        }
    }
}