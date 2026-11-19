package zone.moddev.mc.hammerspace.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import zone.moddev.mc.hammerspace.HSRegistries;
import zone.moddev.mc.hammerspace.Hammerspace;
import zone.moddev.mc.hammerspace.dock.PocketDockBlockEntity;
import zone.moddev.mc.hammerspace.pocket.PocketHandler;
import zone.moddev.mc.hammerspace.pocket.PocketMenu;

/**
 * Dev only (gradlew runShowcase): makes a flat creative world, fills some pockets, checks pickup and placing with a
 * real player (results in the log as "SHOWCASE ..."), saves screenshots to run/screenshots/hs_*.png and quits.
 * Does nothing without -Dhammerspace.showcase.
 */
@EventBusSubscriber(modid = Hammerspace.MOD_ID, value = Dist.CLIENT)
public final class HammerspaceShowcase {
    private static final boolean ENABLED = Boolean.getBoolean("hammerspace.showcase");
    private static final int Y = -60;
    private static boolean worldRequested;
    private static int tick = -1;
    private static int torchesBefore;
    private static int planksBefore;

    private HammerspaceShowcase() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!worldRequested && mc.screen != null && mc.getOverlay() == null) {
                worldRequested = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.onboardAccessibility = false;
                mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
                createWorld(mc);
            }
            return;
        }
        if (tick < 0 && (mc.screen != null || mc.player == null || mc.getSingleplayerServer() == null)) return;
        tick++;
        MinecraftServer server = mc.getSingleplayerServer();
        switch (tick) {
            case 1 -> server.execute(() -> setUp(server));
            case 10 -> server.execute(() -> startChecks(server));
            case 30 -> server.execute(() -> finishChecks(server));
            case 45 -> shot(mc, "world_hud_dock");
            case 50 -> server.execute(() -> PocketMenu.openHeld(player(server), 0));
            case 62 -> hover(mc, 0, 5);
            case 72 -> shot(mc, "diamond_pocket_tooltip");
            case 76 -> mc.player.closeContainer();
            case 80 -> server.execute(() -> PocketMenu.openHeld(player(server), 1));
            case 95 -> shot(mc, "emerald_pocket");
            case 99 -> mc.player.closeContainer();
            case 103 -> server.execute(() -> PocketMenu.openHeld(player(server), 2));
            case 118 -> shot(mc, "creative_pocket");
            case 122 -> mc.player.closeContainer();
            case 126 -> server.execute(() -> PocketMenu.openDock(player(server), dock(server)));
            case 141 -> shot(mc, "dock_menu");
            case 150 -> mc.stop();
            default -> {
            }
        }
    }

    private static void createWorld(Minecraft mc) {
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        LevelSettings settings = new LevelSettings("hs-showcase-" + System.currentTimeMillis(), GameType.CREATIVE, false,
                Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(settings.levelName(), settings, new WorldOptions(1L, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                mc.screen);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static PocketDockBlockEntity dock(MinecraftServer server) {
        return (PocketDockBlockEntity) server.overworld().getBlockEntity(new BlockPos(0, Y, 4));
    }

    private static ItemStack pocket(int tier) {
        return new ItemStack(HSRegistries.POCKETS[tier].get());
    }

    private static void put(PocketHandler pocket, ItemStack item, int count) {
        pocket.insert(item.copyWithCount(1), false);
        int slot = pocket.find(item);
        pocket.insertItem(slot, item.copyWithCount(count - 1), false);
    }

    private static void setUp(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = server.overworld();
        for (String command : new String[] {"time set 6000", "weather clear",
                "tp " + player.getGameProfile().getName() + " 0.5 " + Y + " 0.5 0 15"}) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
        }

        ItemStack diamond = pocket(4);
        PocketHandler d = PocketHandler.of(diamond);
        put(d, new ItemStack(Items.STONE), 3200);
        put(d, new ItemStack(Items.OAK_PLANKS), 1500);
        put(d, new ItemStack(Items.IRON_INGOT), 812);
        put(d, new ItemStack(Items.TORCH), 245);
        put(d, new ItemStack(Items.COBBLESTONE), 3200);
        put(d, new ItemStack(Items.GLASS), 64);
        put(d, new ItemStack(Items.REDSTONE), 999);
        put(d, new ItemStack(Items.DIAMOND), 17);
        d.toggleUnify(d.find(new ItemStack(Items.IRON_INGOT)));
        d.cycleExtract(d.find(new ItemStack(Items.STONE)));
        d.cyclePlace(d.find(new ItemStack(Items.OAK_PLANKS)));
        d.select(d.find(new ItemStack(Items.OAK_PLANKS)));

        ItemStack emerald = pocket(5);
        PocketHandler e = PocketHandler.of(emerald);
        put(e, new ItemStack(Items.DIRT), 2_000_000_000);
        put(e, new ItemStack(Items.SAND), 45_300);
        put(e, new ItemStack(Items.GRAVEL), 1_250_000);

        ItemStack creative = pocket(6);
        PocketHandler c = PocketHandler.of(creative);
        for (var item : new net.minecraft.world.item.Item[] {Items.NETHER_STAR, Items.ELYTRA, Items.BEACON, Items.DIAMOND_BLOCK}) {
            c.insert(new ItemStack(item), false);
        }

        player.getInventory().clearContent();
        player.getInventory().setItem(0, diamond);
        player.getInventory().setItem(1, emerald);
        player.getInventory().setItem(2, creative);
        for (int i = 0; i < 4; i++) player.getInventory().setItem(3 + i, pocket(i));
        player.getInventory().setItem(7, new ItemStack(HSRegistries.LININGS[4].get(), 4));
        player.getInventory().setItem(8, new ItemStack(HSRegistries.DOCK_ITEM.get()));
        player.getInventory().selected = 0;

        level.setBlockAndUpdate(new BlockPos(0, Y, 4), HSRegistries.DOCK.get().defaultBlockState());
        ItemStack docked = pocket(2);
        PocketHandler.of(docked).insert(new ItemStack(Items.COAL, 300), false);
        dock(server).setPocket(docked);
        level.setBlockAndUpdate(new BlockPos(1, Y, 4), Blocks.HOPPER.defaultBlockState());
    }

    /** Pickup: drop torches (held by a pocket) and gold nuggets (not). Placing: right-click the ground with the pocket. */
    private static void startChecks(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = server.overworld();
        PocketHandler d = PocketHandler.of(player.getInventory().getItem(0));
        torchesBefore = d.entry(d.find(new ItemStack(Items.TORCH))).count();
        planksBefore = d.entry(d.find(new ItemStack(Items.OAK_PLANKS))).count();
        for (var stack : new ItemStack[] {new ItemStack(Items.TORCH, 10), new ItemStack(Items.GOLD_NUGGET, 5)}) {
            ItemEntity item = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack);
            item.setNoPickUpDelay();
            level.addFreshEntity(item);
        }
        player.setGameMode(GameType.SURVIVAL); // so placing uses up the planks
        BlockPos ground = new BlockPos(-2, Y - 1, 2);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false));
    }

    private static void finishChecks(MinecraftServer server) {
        ServerPlayer player = player(server);
        PocketHandler d = PocketHandler.of(player.getInventory().getItem(0));
        int torches = d.entry(d.find(new ItemStack(Items.TORCH))).count();
        int planks = d.entry(d.find(new ItemStack(Items.OAK_PLANKS))).count();
        boolean placed = server.overworld().getBlockState(new BlockPos(-2, Y, 2)).is(Blocks.OAK_PLANKS);
        int nuggets = player.getInventory().countItem(Items.GOLD_NUGGET);
        Hammerspace.LOGGER.info("SHOWCASE pickup: torches {} -> {} ({}), gold nuggets (no pocket holds them) in inventory {} ({})", torchesBefore, torches,
                torches == torchesBefore + 10 ? "OK" : "FAIL", nuggets, nuggets == 5 ? "OK" : "FAIL");
        Hammerspace.LOGGER.info("SHOWCASE place: block placed {}, planks {} -> {} ({})", placed, planksBefore, planks,
                placed && planks == planksBefore - 1 ? "OK" : "FAIL");
        player.setGameMode(GameType.CREATIVE);
    }

    /** Puts the mouse over a pocket slot so its tooltip shows. */
    private static void hover(Minecraft mc, int slot, int rows) {
        double scale = mc.getWindow().getGuiScale();
        int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
        int top = (mc.getWindow().getGuiScaledHeight() - (114 + rows * 18)) / 2;
        // Moving the real cursor doesn't reach the game while it's unfocused; set the mouse position it reads instead.
        try {
            for (String field : new String[] {"xpos", "ypos"}) {
                var f = net.minecraft.client.MouseHandler.class.getDeclaredField(field);
                f.setAccessible(true);
                f.setDouble(mc.mouseHandler, field.equals("xpos") ? (left + 8 + slot % 9 * 18 + 8) * scale : (top + 18 + slot / 9 * 18 + 8) * scale);
            }
        } catch (ReflectiveOperationException e) {
            Hammerspace.LOGGER.warn("SHOWCASE couldn't move the mouse", e);
        }
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "hs_" + name + ".png", mc.getMainRenderTarget(), message -> {
        });
    }

}
