package loot.ledger.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class HistoryOverlayScreen extends Screen {

    private final List<ClientLogEntry> entries;
    private final BlockPos pos;

    private static final int PANEL_WIDTH     = 320;
    private static final int PANEL_HEIGHT    = 260;
    private static final int ENTRY_HEIGHT    = 26;
    private static final int VISIBLE_ENTRIES = 8;

    private int scrollOffset = 0;
    private int hoveredEntry = -1;

    public HistoryOverlayScreen(List<ClientLogEntry> entries, BlockPos pos) {
        super(Text.literal("LootLedger"));
        this.entries = entries;
        this.pos     = pos;
    }

    @Override
    protected void init() {
        int panelX = (this.width  - PANEL_WIDTH)  / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("X"), btn -> this.close())
                .dimensions(panelX + PANEL_WIDTH - 20, panelY + 4, 16, 16)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("A"), btn -> {
            if (scrollOffset > 0) scrollOffset--;
        }).dimensions(panelX + PANEL_WIDTH - 20, panelY + 40, 16, 16).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("V"), btn -> {
            if (scrollOffset < Math.max(0, entries.size() - VISIBLE_ENTRIES)) scrollOffset++;
        }).dimensions(panelX + PANEL_WIDTH - 20, panelY + PANEL_HEIGHT - 24, 16, 16).build());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x88000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int panelX = (this.width  - PANEL_WIDTH)  / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xF0101020);

        context.fill(panelX,                   panelY,                    panelX + PANEL_WIDTH, panelY + 1,            0xFF5865F2);
        context.fill(panelX,                   panelY + PANEL_HEIGHT - 1, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT,  0xFF5865F2);
        context.fill(panelX,                   panelY,                    panelX + 1,            panelY + PANEL_HEIGHT, 0xFF5865F2);
        context.fill(panelX + PANEL_WIDTH - 1, panelY,                    panelX + PANEL_WIDTH,  panelY + PANEL_HEIGHT, 0xFF5865F2);

        context.fill(panelX + 1, panelY + 1, panelX + PANEL_WIDTH - 1, panelY + 20, 0xFF1E1E3A);
        context.drawText(this.textRenderer, "Container History", panelX + 8, panelY + 6, 0xFFFFFFFF, true);

        context.fill(panelX + 1, panelY + 20, panelX + PANEL_WIDTH - 1, panelY + 32, 0xFF16162A);
        context.drawText(this.textRenderer,
                "Pos: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ(),
                panelX + 8, panelY + 23, 0xFFAAAAAA, true);

        context.fill(panelX + 1, panelY + 32, panelX + PANEL_WIDTH - 1, panelY + 33, 0xFF5865F2);

        hoveredEntry = -1;
        int entryStartY = panelY + 36;

        if (entries.isEmpty()) {
            context.drawText(this.textRenderer,
                    "No entries yet.",
                    panelX + 10, entryStartY + 10, 0xFFAAAAAA, true);
        }

        for (int i = 0; i < VISIBLE_ENTRIES; i++) {
            int idx = i + scrollOffset;
            if (idx >= entries.size()) break;

            ClientLogEntry entry = entries.get(idx);
            int entryY = entryStartY + i * ENTRY_HEIGHT;

            int rowBg = (i % 2 == 0) ? 0x33FFFFFF : 0x1AFFFFFF;
            context.fill(panelX + 4, entryY, panelX + PANEL_WIDTH - 22, entryY + ENTRY_HEIGHT - 1, rowBg);

            if (mouseX >= panelX + 4 && mouseX <= panelX + PANEL_WIDTH - 22
                    && mouseY >= entryY && mouseY < entryY + ENTRY_HEIGHT) {
                hoveredEntry = idx;
                context.fill(panelX + 4, entryY, panelX + PANEL_WIDTH - 22, entryY + ENTRY_HEIGHT - 1, 0x44FFFFFF);
            }

            context.drawText(this.textRenderer,
                    entry.removed ? "-" : "+",
                    panelX + 8, entryY + 9,
                    entry.removed ? 0xFFFF4444 : 0xFF44FF44, true);

            ItemStack icon = getItemStack(entry.itemId);
            if (!icon.isEmpty()) {
                context.drawItem(icon, panelX + 18, entryY + 4);
            }

            context.drawText(this.textRenderer,
                    entry.playerName,
                    panelX + 38, entryY + 4,
                    0xFF55FFFF, true);

            String localizedName = getLocalizedName(entry.itemId, entry.itemName);
            context.drawText(this.textRenderer,
                    entry.count + "x " + localizedName,
                    panelX + 38, entryY + 14,
                    0xFFFFDD44, true);

            renderPlayerAvatar(context, entry.playerName, panelX + PANEL_WIDTH - 80, entryY + 4, 16);

            context.drawText(this.textRenderer,
                    formatTime(entry.timestamp),
                    panelX + PANEL_WIDTH - 60, entryY + 9,
                    0xFF888888, true);
        }

        if (hoveredEntry >= 0) {
            ClientLogEntry entry = entries.get(hoveredEntry);
            String localizedName = getLocalizedName(entry.itemId, entry.itemName);
            List<Text> tooltip = List.of(
                    Text.literal("§bItem: §f"   + localizedName),
                    Text.literal("§bID: §7"     + entry.itemId),
                    Text.literal("§bAmount: §f" + entry.count),
                    Text.literal("§bPlayer: §f" + entry.playerName),
                    Text.literal("§bAction: "   + (entry.removed ? "§cRemoved" : "§aAdded")),
                    Text.literal("§bTime: §7"   + formatTimeFull(entry.timestamp))
            );
            context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderPlayerAvatar(DrawContext context, String playerName, int x, int y, int size) {
        int color = getPlayerColor(playerName);
        context.fill(x, y, x + size, y + size, color);
        context.fill(x + 1, y + 1, x + size - 1, y + size - 1, darken(color));
        if (!playerName.isEmpty()) {
            context.drawText(this.textRenderer,
                    String.valueOf(playerName.charAt(0)).toUpperCase(),
                    x + 4, y + 4,
                    0xFFFFFFFF, true);
        }
    }

    private int getPlayerColor(String playerName) {
        int[] colors = {
                0xFF5865F2, 0xFF57F287, 0xFFFEE75C,
                0xFFEB459E, 0xFFED4245, 0xFF3BA55D,
                0xFF4752C4, 0xFF2D7D46
        };
        int idx = Math.abs(playerName.hashCode()) % colors.length;
        return colors[idx];
    }

    private int darken(int color) {
        int r = ((color >> 16) & 0xFF) / 2;
        int g = ((color >> 8)  & 0xFF) / 2;
        int b = ((color)       & 0xFF) / 2;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private String getLocalizedName(String itemId, String fallback) {
        try {
            return Registries.ITEM.getOptionalValue(Identifier.of(itemId))
                    .map(item -> item.getName().getString())
                    .orElse(fallback);
        } catch (Exception e) {
            return fallback;
        }
    }

    private ItemStack getItemStack(String itemId) {
        try {
            return Registries.ITEM.getOptionalValue(Identifier.of(itemId))
                    .map(item -> new ItemStack(item.getDefaultStack().getItem()))
                    .orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount < 0) {
            if (scrollOffset < Math.max(0, entries.size() - VISIBLE_ENTRIES)) scrollOffset++;
        } else {
            if (scrollOffset > 0) scrollOffset--;
        }
        return true;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (this.client != null) {
            if (this.client.options.inventoryKey.matchesKey(input) || input.key() == 256) {
                this.close();
                if (this.client.player != null) {
                    this.client.player.closeHandledScreen();
                }
                return true;
            }
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        if (this.client != null && this.client.player != null) {
            this.client.player.closeHandledScreen();
        }
        super.close();
    }

    private String formatTime(long timestamp) {
        return new SimpleDateFormat("HH:mm").format(new Date(timestamp));
    }

    private String formatTimeFull(long timestamp) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date(timestamp));
    }

    public record ClientLogEntry(
            String playerName,
            String itemId,
            String itemName,
            int count,
            boolean removed,
            long timestamp
    ) {}
}