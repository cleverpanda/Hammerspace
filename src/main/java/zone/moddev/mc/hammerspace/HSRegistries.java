package zone.moddev.mc.hammerspace;

import static zone.moddev.mc.hammerspace.Hammerspace.MOD_ID;

import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import zone.moddev.mc.hammerspace.dock.PocketDockBlock;
import zone.moddev.mc.hammerspace.dock.PocketDockBlockEntity;
import zone.moddev.mc.hammerspace.pocket.PocketContents;
import zone.moddev.mc.hammerspace.pocket.PocketItem;
import zone.moddev.mc.hammerspace.pocket.PocketMenu;
import zone.moddev.mc.hammerspace.pocket.PocketTier;
import zone.moddev.mc.hammerspace.recipe.PocketUpgradeRecipe;

/** Everything Hammerspace registers. Tier sizes are data: see {@link #POCKET_TIER}. */
public final class HSRegistries {
    private HSRegistries() {}

    public static final String[] TIERS = {"redstone", "lapis", "iron", "gold", "diamond", "emerald", "creative"};
    /** Creative tab order within each kind of item: vanilla's colour order (white, red, yellow, green, cyan, blue, purple). */
    private static final List<String> BY_COLOUR = List.of("iron", "redstone", "gold", "emerald", "diamond", "lapis", "creative");
    private static final Rarity[] RARITY = {Rarity.COMMON, Rarity.COMMON, Rarity.UNCOMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.RARE, Rarity.EPIC};

    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);
    static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MOD_ID);
    static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MOD_ID);
    static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PocketContents>> CONTENTS =
            COMPONENTS.registerComponentType("contents", b -> b.persistent(PocketContents.CODEC).networkSynchronized(PocketContents.STREAM_CODEC));

    /** {@code data/<ns>/data_maps/item/pocket_tier.json}: rows, max per slot, infinite. Synced so screens know their size. */
    public static final DataMapType<Item, PocketTier> POCKET_TIER = DataMapType.builder(id("pocket_tier"), Registries.ITEM, PocketTier.CODEC)
            .synced(PocketTier.CODEC, true).build();

    /** Items a pocket refuses: other pockets, and containers whose contents would bloat the pocket. */
    public static final TagKey<Item> NOT_STORABLE = TagKey.create(Registries.ITEM, id("not_storable"));
    /** Items that never take part in unifying, even when they carry an allowed tag. */
    public static final TagKey<Item> NEVER_UNIFY = TagKey.create(Registries.ITEM, id("never_unify"));

    public static final DeferredItem<PocketItem>[] POCKETS = pockets();
    public static final DeferredItem<Item>[] LININGS = linings();

    public static final DeferredBlock<PocketDockBlock> DOCK = BLOCKS.register("pocket_dock", () -> new PocketDockBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(5f, 1200f).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> DOCK_ITEM = ITEMS.registerSimpleBlockItem(DOCK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PocketDockBlockEntity>> DOCK_ENTITY =
            BLOCK_ENTITIES.register("pocket_dock", () -> BlockEntityType.Builder.of(PocketDockBlockEntity::new, DOCK.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<PocketMenu>> POCKET_MENU =
            MENUS.register("pocket", () -> IMenuTypeExtension.create(PocketMenu::fromNetwork));

    public static final DeferredHolder<RecipeSerializer<?>, PocketUpgradeRecipe.Serializer> POCKET_UPGRADE =
            RECIPE_SERIALIZERS.register("pocket_upgrade", PocketUpgradeRecipe.Serializer::new);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.hammerspace"))
            .icon(() -> POCKETS[5].get().getDefaultInstance())
            .displayItems((params, output) -> {
                // Row 1: the pockets and the dock, padded to the tab's 9 columns. Row 2: each lining under its pocket.
                for (String tier : BY_COLOUR) output.accept(POCKETS[List.of(TIERS).indexOf(tier)]);
                output.accept(DOCK_ITEM);
                output.accept(spacer(), CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
                for (String tier : BY_COLOUR) {
                    int i = List.of(TIERS).indexOf(tier);
                    if (i < LININGS.length) output.accept(LININGS[i]);
                }
            })
            .build());

    /** Creative tabs can't have empty slots, so a nameless light gray pane fills the gap (as in LeatherWorks). */
    private static ItemStack spacer() {
        ItemStack pane = new ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("HammerspaceSpacer", true);
        pane.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        pane.set(DataComponents.ITEM_NAME, Component.literal(" "));
        return pane;
    }

    @SuppressWarnings("unchecked")
    private static DeferredItem<PocketItem>[] pockets() {
        DeferredItem<PocketItem>[] items = new DeferredItem[TIERS.length];
        for (int i = 0; i < TIERS.length; i++) {
            Rarity rarity = RARITY[i];
            items[i] = ITEMS.register(TIERS[i] + "_pocket", () -> new PocketItem(new Item.Properties().rarity(rarity).fireResistant()));
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private static DeferredItem<Item>[] linings() {
        DeferredItem<Item>[] items = new DeferredItem[TIERS.length - 1];
        for (int i = 0; i < items.length; i++) {
            Rarity rarity = RARITY[i];
            items[i] = ITEMS.registerSimpleItem(TIERS[i] + "_lining", new Item.Properties().rarity(rarity));
        }
        return items;
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    static void register(IEventBus bus) {
        ITEMS.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        COMPONENTS.register(bus);
        RECIPE_SERIALIZERS.register(bus);
        TABS.register(bus);
    }
}
