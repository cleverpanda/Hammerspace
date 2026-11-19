package zone.moddev.mc.hammerspace;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.slf4j.Logger;
import zone.moddev.mc.hammerspace.network.HSNetwork;
import zone.moddev.mc.hammerspace.pocket.PocketHandler;
import zone.moddev.mc.hammerspace.pocket.PocketPickup;
import zone.moddev.mc.hammerspace.pocket.UnifyTags;

/**
 * Hammerspace: tiered pockets with huge slots, a rewrite of p455w0rd's /dank/null (MIT) for NeoForge 1.21.1.
 * Client-only setup lives in {@code client.HammerspaceClient}.
 */
@Mod(Hammerspace.MOD_ID)
public final class Hammerspace {
    public static final String MOD_ID = "hammerspace";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Hammerspace(IEventBus modBus, ModContainer container) {
        HSRegistries.register(modBus);
        modBus.addListener(HSNetwork::register);
        modBus.addListener(Hammerspace::registerDataMaps);
        modBus.addListener(Hammerspace::registerCapabilities);
        container.registerConfig(ModConfig.Type.CLIENT, HSConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER, HSConfig.SERVER_SPEC);

        NeoForge.EVENT_BUS.addListener(PocketPickup::onPickup);
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(new UnifyTags()));
    }

    private static void registerDataMaps(RegisterDataMapTypesEvent event) {
        event.register(HSRegistries.POCKET_TIER);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (var pocket : HSRegistries.POCKETS) {
            event.registerItem(Capabilities.ItemHandler.ITEM, (stack, ctx) -> PocketHandler.of(stack), pocket);
        }
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, HSRegistries.DOCK_ENTITY.get(), (dock, side) -> dock.handler());
    }
}
