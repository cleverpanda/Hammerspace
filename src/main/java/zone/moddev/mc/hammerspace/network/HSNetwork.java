package zone.moddev.mc.hammerspace.network;

import static zone.moddev.mc.hammerspace.Hammerspace.MOD_ID;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import zone.moddev.mc.hammerspace.pocket.PocketHandler;
import zone.moddev.mc.hammerspace.pocket.PocketItem;
import zone.moddev.mc.hammerspace.pocket.PocketMenu;

/** Client-to-server messages. Everything they change is checked again on the server. */
public final class HSNetwork {
    private HSNetwork() {}

    /** A mode click on a pocket slot in the open pocket screen. */
    public record SlotAction(int slot, PocketMenu.Action action) implements CustomPacketPayload {
        public static final Type<SlotAction> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "slot_action"));
        public static final StreamCodec<ByteBuf, SlotAction> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SlotAction::slot,
                ByteBufCodecs.idMapper(i -> PocketMenu.Action.values()[i], PocketMenu.Action::ordinal), SlotAction::action,
                SlotAction::new);

        @Override
        public Type<SlotAction> type() {
            return TYPE;
        }
    }

    /** Change the selected slot of the pocket in a hand: {@code slot >= 0} selects it, -1 next, -2 previous. */
    public record SelectHeld(boolean offhand, int slot) implements CustomPacketPayload {
        public static final int NEXT = -1, PREVIOUS = -2;
        public static final Type<SelectHeld> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "select_held"));
        public static final StreamCodec<ByteBuf, SelectHeld> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, SelectHeld::offhand,
                ByteBufCodecs.VAR_INT, SelectHeld::slot,
                SelectHeld::new);

        @Override
        public Type<SelectHeld> type() {
            return TYPE;
        }
    }

    /** The "open pocket" key: opens the first pocket in the hands, then the inventory. */
    public record OpenPocket() implements CustomPacketPayload {
        public static final Type<OpenPocket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "open_pocket"));
        public static final StreamCodec<ByteBuf, OpenPocket> CODEC = StreamCodec.unit(new OpenPocket());

        @Override
        public Type<OpenPocket> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SlotAction.TYPE, SlotAction.CODEC, HSNetwork::onSlotAction);
        registrar.playToServer(SelectHeld.TYPE, SelectHeld.CODEC, HSNetwork::onSelectHeld);
        registrar.playToServer(OpenPocket.TYPE, OpenPocket.CODEC, HSNetwork::onOpenPocket);
    }

    private static void onSlotAction(SlotAction msg, IPayloadContext ctx) {
        if (ctx.player().containerMenu instanceof PocketMenu menu) menu.apply(msg.slot(), msg.action(), ctx.player());
    }

    private static void onSelectHeld(SelectHeld msg, IPayloadContext ctx) {
        ItemStack held = ctx.player().getItemInHand(msg.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!(held.getItem() instanceof PocketItem)) return;
        PocketHandler pocket = PocketHandler.of(held);
        switch (msg.slot()) {
            case SelectHeld.NEXT -> pocket.cycleSelected(true);
            case SelectHeld.PREVIOUS -> pocket.cycleSelected(false);
            default -> pocket.select(msg.slot());
        }
    }

    private static void onOpenPocket(OpenPocket msg, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        Inventory inv = player.getInventory();
        int[] order = new int[inv.items.size() + 1];
        order[0] = inv.selected;
        order[1] = Inventory.SLOT_OFFHAND;
        for (int i = 0, n = 2; i < inv.items.size() && n < order.length; i++) {
            if (i != inv.selected) order[n++] = i;
        }
        for (int slot : order) {
            if (inv.getItem(slot).getItem() instanceof PocketItem) {
                PocketMenu.openHeld(player, slot);
                return;
            }
        }
    }
}
