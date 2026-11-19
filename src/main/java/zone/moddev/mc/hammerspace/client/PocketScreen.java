package zone.moddev.mc.hammerspace.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import zone.moddev.mc.hammerspace.network.HSNetwork;
import zone.moddev.mc.hammerspace.pocket.Counts;
import zone.moddev.mc.hammerspace.pocket.KeepMode;
import zone.moddev.mc.hammerspace.pocket.PocketContents.Entry;
import zone.moddev.mc.hammerspace.pocket.PocketMenu;
import zone.moddev.mc.hammerspace.pocket.PocketMenu.Action;

/** Chest-style screen; slots show short counts (1.2k, 2.1B), the selected slot is outlined. */
public class PocketScreen extends AbstractContainerScreen<PocketMenu> {
    private static final ResourceLocation CHEST = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private final int rows;
    private Button lock;

    public PocketScreen(PocketMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.rows = menu.pocketSlots() / 9;
        this.imageHeight = 114 + rows * 18;
        this.inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        if (menu.tier().infinite() && minecraft.player.hasInfiniteMaterials()) {
            lock = addRenderableWidget(Button.builder(Component.empty(), b -> send(-1, Action.TOGGLE_LOCK))
                    .bounds(leftPos + imageWidth - 52, topPos - 22, 50, 20).build());
        }
    }

    @Override
    protected void containerTick() {
        if (lock != null) {
            lock.setMessage(Component.translatable(menu.handler().contents().locked() ? "hammerspace.gui.unlock" : "hammerspace.gui.lock"));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ResourceLocation background = background();
        graphics.blit(background, leftPos, topPos, 0, 0, imageWidth, rows * 18 + 17);
        graphics.blit(background, leftPos, topPos + rows * 18 + 17, 0, 126, imageWidth, 96);
    }

    /** {@code textures/gui/<pocket id>.png}, laid out like the chest screen; the chest itself until the pocket has synced. */
    private ResourceLocation background() {
        ItemStack pocket = menu.handler().pocket();
        return pocket.isEmpty() ? CHEST : BuiltInRegistries.ITEM.getKey(pocket.getItem()).withPath(p -> "textures/gui/" + p + ".png");
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Integer color = menu.handler().pocket().getRarity().color().getColor();
        graphics.drawString(font, title, titleLabelX, titleLabelY, color == null ? 0x404040 : darken(color), false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    private static int darken(int rgb) {
        return (rgb >> 1) & 0x7F7F7F; // rarity colours are made for dark backgrounds; halve them for the grey panel
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (!(slot instanceof PocketMenu.PocketSlot)) {
            super.renderSlot(graphics, slot);
            return;
        }
        Entry entry = menu.handler().entry(slot.getContainerSlot());
        if (entry == null) return;
        int x = slot.x, y = slot.y;
        if (slot.getContainerSlot() == menu.handler().selected()) {
            graphics.renderOutline(x - 1, y - 1, 18, 18, 0xFFFFD43B);
        }
        graphics.renderItem(entry.item(), x, y);
        graphics.renderItemDecorations(font, entry.item(), x, y, "");
        String count = menu.tier().infinite() ? "∞" : Counts.format(entry.count());
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.pose().scale(0.5f, 0.5f, 1f);
        graphics.drawString(font, count, (x + 17) * 2 - font.width(count), (y + 16) * 2 - 8, 0xFFFFFF, true);
        if (entry.unify()) graphics.drawString(font, "U", x * 2, y * 2, 0x55FFFF, true);
        graphics.pose().popPose();
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        if (!(hoveredSlot instanceof PocketMenu.PocketSlot) || !menu.getCarried().isEmpty()) {
            super.renderTooltip(graphics, x, y);
            return;
        }
        Entry entry = menu.handler().entry(hoveredSlot.getContainerSlot());
        if (entry == null) return;
        List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(entry.item()));
        String count = menu.tier().infinite() ? "∞" : String.format("%,d", entry.count());
        lines.add(Component.translatable("hammerspace.gui.stored", count).withStyle(ChatFormatting.GRAY));
        lines.add(KeepMode.describe(entry.extract(), "extract").withStyle(ChatFormatting.AQUA));
        lines.add(KeepMode.describe(entry.place(), "place").withStyle(ChatFormatting.AQUA));
        if (entry.unify()) lines.add(Component.translatable("hammerspace.gui.unify_on").withStyle(ChatFormatting.AQUA));
        if (Screen.hasShiftDown()) {
            lines.add(Component.translatable("hammerspace.gui.help.extract", Component.literal("Ctrl")).withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("hammerspace.gui.help.place", HSKeys.CYCLE_PLACE.getTranslatedKeyMessage()).withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("hammerspace.gui.help.unify", HSKeys.TOGGLE_UNIFY.getTranslatedKeyMessage()).withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("hammerspace.gui.help.select", Component.literal("Alt")).withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.add(Component.translatable("hammerspace.gui.help.more").withStyle(ChatFormatting.DARK_GRAY));
        }
        graphics.renderTooltip(font, lines, Optional.empty(), x, y);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredSlot instanceof PocketMenu.PocketSlot && hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            Action action = Screen.hasControlDown() ? Action.CYCLE_EXTRACT
                    : Screen.hasAltDown() ? Action.SELECT
                    : held(HSKeys.CYCLE_PLACE) ? Action.CYCLE_PLACE
                    : held(HSKeys.TOGGLE_UNIFY) ? Action.TOGGLE_UNIFY
                    : null;
            if (action != null) {
                send(hoveredSlot.getContainerSlot(), action);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Key mappings aren't updated while a screen is open, so read the key itself. */
    private boolean held(KeyMapping key) {
        InputConstants.Key k = key.getKey();
        return k.getType() == InputConstants.Type.KEYSYM && k.getValue() != InputConstants.UNKNOWN.getValue()
                && InputConstants.isKeyDown(minecraft.getWindow().getWindow(), k.getValue());
    }

    private static void send(int slot, Action action) {
        PacketDistributor.sendToServer(new HSNetwork.SlotAction(slot, action));
    }
}
