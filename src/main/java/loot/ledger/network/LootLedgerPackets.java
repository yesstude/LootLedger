package loot.ledger.network;

import loot.ledger.ContainerAccessLog;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class LootLedgerPackets {

    // ---- Payload: Client → Server (Anfrage) ----
    public record RequestLogPayload(BlockPos pos) implements CustomPayload {
        public static final CustomPayload.Id<RequestLogPayload> ID =
                new CustomPayload.Id<>(Identifier.of("lootledger", "request_log"));
        public static final PacketCodec<RegistryByteBuf, RequestLogPayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, RequestLogPayload::pos,
                RequestLogPayload::new
        );
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ---- Payload: Server → Client (Container geöffnet + Position) ----
    public record ContainerOpenedPayload(BlockPos pos) implements CustomPayload {
        public static final CustomPayload.Id<ContainerOpenedPayload> ID =
                new CustomPayload.Id<>(Identifier.of("lootledger", "container_opened"));
        public static final PacketCodec<RegistryByteBuf, ContainerOpenedPayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, ContainerOpenedPayload::pos,
                ContainerOpenedPayload::new
        );
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ---- Payload: Server → Client (Log Antwort) ----
    public record LogResponsePayload(
            BlockPos pos,
            List<String> players,
            List<String> itemIds,
            List<String> itemNames,
            List<Integer> counts,
            List<Integer> removed,
            List<Long> timestamps
    ) implements CustomPayload {
        public static final CustomPayload.Id<LogResponsePayload> ID =
                new CustomPayload.Id<>(Identifier.of("lootledger", "log_response"));
        public static final PacketCodec<RegistryByteBuf, LogResponsePayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC,                                           LogResponsePayload::pos,
                PacketCodecs.STRING.collect(PacketCodecs.toList()),              LogResponsePayload::players,
                PacketCodecs.STRING.collect(PacketCodecs.toList()),              LogResponsePayload::itemIds,
                PacketCodecs.STRING.collect(PacketCodecs.toList()),              LogResponsePayload::itemNames,
                PacketCodecs.VAR_INT.collect(PacketCodecs.toList()),             LogResponsePayload::counts,
                PacketCodecs.VAR_INT.collect(PacketCodecs.toList()),             LogResponsePayload::removed,
                PacketCodecs.VAR_LONG.collect(PacketCodecs.toList()),            LogResponsePayload::timestamps,
                LogResponsePayload::new
        );
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ---- Server Registrierung ----
    public static void registerServer() {
        PayloadTypeRegistry.playC2S().register(RequestLogPayload.ID, RequestLogPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ContainerOpenedPayload.ID, ContainerOpenedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LogResponsePayload.ID, LogResponsePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RequestLogPayload.ID, (payload, context) -> {
            BlockPos pos = payload.pos();
            List<ContainerAccessLog.LogEntry> entries = ContainerAccessLog.getEntries(pos);

            List<String>  players    = new ArrayList<>();
            List<String>  itemIds    = new ArrayList<>();
            List<String>  itemNames  = new ArrayList<>();
            List<Integer> counts     = new ArrayList<>();
            List<Integer> removed    = new ArrayList<>();
            List<Long>    timestamps = new ArrayList<>();

            for (ContainerAccessLog.LogEntry entry : entries) {
                players.add(entry.playerName);
                itemIds.add(entry.itemId);
                itemNames.add(entry.itemName);
                counts.add(entry.count);
                removed.add(entry.removed ? 1 : 0);
                timestamps.add(entry.timestamp);
            }

            context.responseSender().sendPacket(
                    new LogResponsePayload(pos, players, itemIds, itemNames, counts, removed, timestamps)
            );
        });
    }
}