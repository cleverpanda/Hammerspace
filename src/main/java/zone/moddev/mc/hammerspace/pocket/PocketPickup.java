package zone.moddev.mc.hammerspace.pocket;

import net.minecraft.stats.Stats;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/**
 * Picked-up items that a pocket already holds go into it. Once a pocket has taken some, whatever doesn't fit is
 * voided, like /dev/null; items no pocket holds are picked up normally.
 */
public final class PocketPickup {
    private PocketPickup() {}

    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        Player player = event.getPlayer();
        ItemEntity entity = event.getItemEntity();
        ItemStack stack = entity.getItem();
        if (player.level().isClientSide || stack.isEmpty() || entity.hasPickUpDelay()
                || entity.getTarget() != null && !entity.getTarget().equals(player.getUUID())) {
            return;
        }
        Inventory inventory = player.getInventory();
        ItemStack left = stack.copy();
        boolean taken = false;
        for (int i = 0; i < inventory.getContainerSize() && !left.isEmpty(); i++) {
            ItemStack pocket = inventory.getItem(i);
            if (!(pocket.getItem() instanceof PocketItem)) continue;
            PocketHandler handler = PocketHandler.of(pocket);
            int slot = handler.find(left);
            if (slot < 0) continue;
            taken = true;
            left = handler.insertItem(slot, left, false);
        }
        if (!taken) return;
        int count = stack.getCount();
        player.take(entity, count);
        player.awardStat(Stats.ITEM_PICKED_UP.get(stack.getItem()), count);
        entity.discard();
        inventory.setChanged();
        event.setCanPickup(TriState.FALSE);
    }
}
