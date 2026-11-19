package zone.moddev.mc.hammerspace.compat.jade;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import zone.moddev.mc.hammerspace.Hammerspace;
import zone.moddev.mc.hammerspace.dock.PocketDockBlock;
import zone.moddev.mc.hammerspace.dock.PocketDockBlockEntity;

/** Jade line for the dock: which pocket sits on it. Jade's own item-storage view lists the contents. Loaded only when Jade is installed. */
@WailaPlugin
public class HammerspaceJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(DockProvider.INSTANCE, PocketDockBlock.class);
    }

    /** The client already has the docked pocket's item (the dock syncs it for its renderer), so no server data is needed. */
    enum DockProvider implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Hammerspace.MOD_ID, "pocket_dock");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof PocketDockBlockEntity dock)) return;
            ItemStack pocket = dock.getPocket();
            tooltip.add(pocket.isEmpty() ? Component.translatable("jade.hammerspace.dock_empty")
                    : Component.translatable("jade.hammerspace.dock_holds", pocket.getHoverName()));
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
