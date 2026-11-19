package zone.moddev.mc.hammerspace.gametest;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import zone.moddev.mc.hammerspace.HSRegistries;
import zone.moddev.mc.hammerspace.Hammerspace;
import zone.moddev.mc.hammerspace.dock.PocketDockBlockEntity;
import zone.moddev.mc.hammerspace.pocket.PocketHandler;

/** Run with {@code gradlew runGameTestServer}. Tier numbers come from the shipped data map. */
@GameTestHolder(Hammerspace.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PocketTests {
    private static ItemStack pocket(int tier) {
        return new ItemStack(HSRegistries.POCKETS[tier].get());
    }

    private static void check(GameTestHelper helper, boolean ok, String what) {
        if (!ok) helper.fail(what);
    }

    /** A dock emptied under an open screen leaves the handler reading ItemStack.EMPTY; inserting must refuse, not write onto it. */
    @GameTest(template = "empty")
    public static void emptySourceRefusesInsert(GameTestHelper helper) {
        PocketHandler gone = new PocketHandler(() -> ItemStack.EMPTY, () -> {});
        ItemStack left = gone.insert(new ItemStack(Items.COBBLESTONE, 10), false);
        check(helper, left.getCount() == 10, "insert into a missing pocket should hand everything back, kept " + (10 - left.getCount()));
        check(helper, !ItemStack.EMPTY.has(HSRegistries.CONTENTS), "ItemStack.EMPTY must never get pocket contents");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tierLimitsAndKeepMode(GameTestHelper helper) {
        PocketHandler pocket = PocketHandler.of(pocket(0)); // redstone: 9 slots, 256 each
        check(helper, pocket.getSlots() == 9, "redstone pocket should have 9 slots, has " + pocket.getSlots());
        ItemStack left = pocket.insert(new ItemStack(Items.COBBLESTONE, 300), false);
        check(helper, left.getCount() == 44, "300 into 256 should return 44, returned " + left.getCount());
        check(helper, pocket.getStackInSlot(0).getCount() == 256, "slot should hold 256");
        pocket.take(0, 192, true, false);
        check(helper, pocket.getStackInSlot(0).getCount() == 64, "taking 192 should leave 64");
        check(helper, pocket.extractItem(0, 64, false).getCount() == 63, "keep-one mode should hold one back");
        check(helper, pocket.extractItem(0, 64, false).isEmpty(), "last item stays");
        pocket.cycleExtract(0); // ONE -> SIXTEEN
        check(helper, pocket.entry(0).extract() == 16, "extract mode should cycle to sixteen");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oneSlotPerItemAndRefusals(GameTestHelper helper) {
        PocketHandler pocket = PocketHandler.of(pocket(1));
        pocket.insert(new ItemStack(Items.DIRT, 10), false);
        ItemStack again = pocket.insertItem(5, new ItemStack(Items.DIRT, 10), false);
        check(helper, again.getCount() == 10, "dirt must not start a second slot");
        check(helper, pocket.insert(pocket(0), false).getCount() == 1, "a pocket must not go in a pocket");
        check(helper, pocket.insert(new ItemStack(Items.SHULKER_BOX), false).getCount() == 1, "shulker boxes are refused");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hugeCountsSurviveSaving(GameTestHelper helper) {
        ItemStack stack = pocket(5); // emerald: up to Integer.MAX_VALUE
        PocketHandler pocket = PocketHandler.of(stack);
        pocket.insertItem(0, new ItemStack(Items.STONE, Integer.MAX_VALUE - 5), false);
        ItemStack left = pocket.insertItem(0, new ItemStack(Items.STONE, 64), false);
        check(helper, left.getCount() == 59 && pocket.entry(0).count() == Integer.MAX_VALUE, "should cap at the int limit without overflow");
        Tag saved = stack.save(helper.getLevel().registryAccess());
        ItemStack loaded = ItemStack.parseOptional(helper.getLevel().registryAccess(), (net.minecraft.nbt.CompoundTag) saved);
        check(helper, PocketHandler.of(loaded).entry(0).count() == Integer.MAX_VALUE, "count lost on save/load");
        check(helper, ItemStack.isSameItemSameComponents(stack, loaded), "saved pocket should equal the original");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeNeverRunsOut(GameTestHelper helper) {
        PocketHandler pocket = PocketHandler.of(pocket(6));
        pocket.insert(new ItemStack(Items.DIAMOND), false);
        for (int i = 0; i < 5; i++) pocket.take(0, 64, true, false);
        check(helper, pocket.entry(0) != null && pocket.entry(0).count() == Integer.MAX_VALUE, "creative pocket ran down");
        pocket.setLocked(true);
        check(helper, pocket.insert(new ItemStack(Items.GOLD_INGOT), false).getCount() == 1, "locked pocket took a new item");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unifyTurnsOresIntoTheStoredOne(GameTestHelper helper) {
        PocketHandler pocket = PocketHandler.of(pocket(2));
        pocket.insert(new ItemStack(Items.IRON_ORE, 4), false);
        pocket.toggleUnify(0);
        pocket.insert(new ItemStack(Items.DEEPSLATE_IRON_ORE, 3), false);
        check(helper, pocket.entry(0).count() == 7 && pocket.entry(1) == null, "deepslate iron ore should join the iron ore (c:ores/iron)");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void upgradeKeepsContents(GameTestHelper helper) {
        ItemStack redstone = pocket(0);
        PocketHandler.of(redstone).insert(new ItemStack(Items.TORCH, 50), false);
        ItemStack l = new ItemStack(HSRegistries.LININGS[1].get());
        CraftingInput grid = CraftingInput.of(3, 3, List.of(ItemStack.EMPTY, l, ItemStack.EMPTY, l, redstone, l, ItemStack.EMPTY, l, ItemStack.EMPTY));
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel());
        check(helper, recipe.isPresent(), "no upgrade recipe matched");
        ItemStack lapis = recipe.get().value().assemble(grid, helper.getLevel().registryAccess());
        check(helper, lapis.is(HSRegistries.POCKETS[1].get()), "upgrade should make a lapis pocket");
        check(helper, PocketHandler.of(lapis).entry(0) != null && PocketHandler.of(lapis).entry(0).count() == 50, "torches lost in upgrade");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dockExposesThePocket(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, HSRegistries.DOCK.get());
        PocketDockBlockEntity dock = helper.getBlockEntity(pos);
        dock.setPocket(pocket(0));
        IItemHandler pipes = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), null);
        check(helper, pipes != null, "dock should expose an item handler");
        pipes.insertItem(0, new ItemStack(Items.SAND, 32), false);
        check(helper, PocketHandler.of(dock.getPocket()).entry(0).count() == 32, "pipe insert should land in the docked pocket");
        helper.succeed();
    }
}
