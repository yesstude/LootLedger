package loot.ledger.mixin;

import loot.ledger.ScreenHandlerPos;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ScreenHandler.class)
public class ScreenHandlerOpenMixin implements ScreenHandlerPos {

    private BlockPos lootledger_pos = null;

    @Override
    public void lootledger_setPos(BlockPos pos) {
        this.lootledger_pos = pos;
    }

    @Override
    public BlockPos lootledger_getPos() {
        return this.lootledger_pos;
    }
}