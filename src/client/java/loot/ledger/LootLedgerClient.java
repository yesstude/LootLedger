package loot.ledger;

import loot.ledger.gui.HistoryOverlayScreen;
import loot.ledger.network.LootLedgerPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class LootLedgerClient implements ClientModInitializer {

	public static List<HistoryOverlayScreen.ClientLogEntry> pendingEntries = new ArrayList<>();
	public static BlockPos pendingPos = null;

	@Override
	public void onInitializeClient() {
		// Empfange Container-Position vom Server
		ClientPlayNetworking.registerGlobalReceiver(
				LootLedgerPackets.ContainerOpenedPayload.ID,
				(payload, context) -> {
					context.client().execute(() -> {
						// Setze Position in der aktuellen GUI
						if (context.client().currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?> screen) {
							if (screen instanceof LootLedgerScreen lootScreen) {
								lootScreen.setLootLedgerPos(payload.pos());
							}
						}
						pendingPos = payload.pos();
					});
				}
		);

		// Empfange Log-Antwort vom Server
		ClientPlayNetworking.registerGlobalReceiver(
				LootLedgerPackets.LogResponsePayload.ID,
				(payload, context) -> {
					List<HistoryOverlayScreen.ClientLogEntry> entries = new ArrayList<>();

					for (int i = 0; i < payload.players().size(); i++) {
						entries.add(new HistoryOverlayScreen.ClientLogEntry(
								payload.players().get(i),
								payload.itemIds().get(i),
								payload.itemNames().get(i),
								payload.counts().get(i),
								payload.removed().get(i) == 1,
								payload.timestamps().get(i)
						));
					}

					pendingEntries = entries;
					pendingPos = payload.pos();

					context.client().execute(() ->
							context.client().setScreen(new HistoryOverlayScreen(entries, payload.pos()))
					);
				}
		);
	}
}