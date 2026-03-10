package loot.ledger;

import loot.ledger.network.LootLedgerPackets;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LootLedger implements ModInitializer {
	public static final String MOD_ID = "lootledger";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("LootLedger gestartet!");
		LootLedgerEvents.register();
		LootLedgerPackets.registerServer();
		LootLedgerSaveData.register();
	}
}