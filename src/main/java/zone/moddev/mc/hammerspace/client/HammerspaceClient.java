package zone.moddev.mc.hammerspace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import zone.moddev.mc.hammerspace.HSConfig;
import zone.moddev.mc.hammerspace.HSRegistries;
import zone.moddev.mc.hammerspace.Hammerspace;
import zone.moddev.mc.hammerspace.dock.PocketDockBlockEntity;
import zone.moddev.mc.hammerspace.network.HSNetwork;
import zone.moddev.mc.hammerspace.pocket.Counts;
import zone.moddev.mc.hammerspace.pocket.PocketContents.Entry;
import zone.moddev.mc.hammerspace.pocket.PocketHandler;
import zone.moddev.mc.hammerspace.pocket.PocketItem;

/** Client entry point: screen, keys, HUD, scroll and pick-block selection, dock renderer. */
@Mod(value = Hammerspace.MOD_ID, dist = Dist.CLIENT)
public final class HammerspaceClient {
    public HammerspaceClient(IEventBus modBus, ModContainer container) {
        // Mods list > Hammerspace > Config edits both config files; server values only where the player may change them.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(HSRegistries.POCKET_MENU.get(), PocketScreen::new));
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            for (var key : HSKeys.ALL) e.register(key);
        });
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAbove(VanillaGuiLayers.HOTBAR,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Hammerspace.MOD_ID, "selected_item"), HammerspaceClient::renderHud));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(HSRegistries.DOCK_ENTITY.get(), ctx -> HammerspaceClient::renderDock));

        NeoForge.EVENT_BUS.addListener(HammerspaceClient::onTick);
        NeoForge.EVENT_BUS.addListener(HammerspaceClient::onScroll);
        NeoForge.EVENT_BUS.addListener(HammerspaceClient::onPickBlock);
    }

    /** The pocket in the main hand, else the off hand, else null. */
    private static InteractionHand heldPocket(LocalPlayer player) {
        if (player == null) return null;
        if (player.getMainHandItem().getItem() instanceof PocketItem) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().getItem() instanceof PocketItem) return InteractionHand.OFF_HAND;
        return null;
    }

    private static void select(InteractionHand hand, int slot) {
        PacketDistributor.sendToServer(new HSNetwork.SelectHeld(hand == InteractionHand.OFF_HAND, slot));
    }

    private static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (HSKeys.OPEN.consumeClick()) PacketDistributor.sendToServer(new HSNetwork.OpenPocket());
        while (HSKeys.TOGGLE_HUD.consumeClick()) {
            HSConfig.SHOW_HUD.set(!HSConfig.SHOW_HUD.get());
            HSConfig.SHOW_HUD.save();
        }
        InteractionHand hand = heldPocket(mc.player);
        while (HSKeys.NEXT.consumeClick()) if (hand != null) select(hand, HSNetwork.SelectHeld.NEXT);
        while (HSKeys.PREVIOUS.consumeClick()) if (hand != null) select(hand, HSNetwork.SelectHeld.PREVIOUS);
    }

    /** Sneak-scroll while holding a pocket cycles its selected item instead of the hotbar. */
    private static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        InteractionHand hand = heldPocket(mc.player);
        if (mc.screen != null || hand == null || !mc.player.isShiftKeyDown() || event.getScrollDeltaY() == 0) return;
        select(hand, event.getScrollDeltaY() < 0 ? HSNetwork.SelectHeld.NEXT : HSNetwork.SelectHeld.PREVIOUS);
        event.setCanceled(true);
    }

    /** Middle-click a block while holding a pocket that has it: select it in the pocket. */
    private static void onPickBlock(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        InteractionHand hand = heldPocket(mc.player);
        if (!event.isPickBlock() || hand == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        ItemStack picked = mc.level.getBlockState(pos).getCloneItemStack(hit, mc.level, pos, mc.player);
        int slot = PocketHandler.of(mc.player.getItemInHand(hand)).find(picked);
        if (slot < 0) return;
        select(hand, slot);
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    /** Selected item and count, just right of the hotbar. */
    private static void renderHud(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        InteractionHand hand = heldPocket(mc.player);
        if (hand == null || mc.options.hideGui || !HSConfig.SHOW_HUD.get()) return;
        PocketHandler pocket = PocketHandler.of(mc.player.getItemInHand(hand));
        Entry selected = pocket.selectedEntry();
        if (selected == null) return;
        int x = graphics.guiWidth() / 2 + 91 + 8;
        int y = graphics.guiHeight() - 19;
        if (hand == InteractionHand.OFF_HAND || !mc.player.getOffhandItem().isEmpty()) x += 29; // clear of the off-hand slot
        graphics.renderItem(selected.item(), x, y);
        String count = pocket.tier().infinite() ? "∞" : Counts.format(selected.count());
        graphics.drawString(mc.font, count, x + 19, y + 4, 0xFFFFFF, true);
    }

    private static void renderDock(PocketDockBlockEntity dock, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                   int light, int overlay) {
        ItemStack pocket = dock.getPocket();
        if (pocket.isEmpty() || dock.getLevel() == null) return;
        pose.pushPose();
        pose.translate(0.5, 1.25, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees((dock.getLevel().getGameTime() + partialTick) * 2f % 360f));
        pose.scale(0.75f, 0.75f, 0.75f);
        // The dock is solid, so its own light is 0; light the pocket from the space above it.
        int above = net.minecraft.client.renderer.LevelRenderer.getLightColor(dock.getLevel(), dock.getBlockPos().above());
        Minecraft.getInstance().getItemRenderer().renderStatic(pocket, ItemDisplayContext.GROUND, above, overlay, pose, buffers,
                dock.getLevel(), 0);
        pose.popPose();
    }

}
