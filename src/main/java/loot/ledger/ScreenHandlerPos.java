package loot.ledger;

import net.minecraft.util.math.BlockPos;

public interface ScreenHandlerPos {
    void lootledger_setPos(BlockPos pos);
    BlockPos lootledger_getPos();
}