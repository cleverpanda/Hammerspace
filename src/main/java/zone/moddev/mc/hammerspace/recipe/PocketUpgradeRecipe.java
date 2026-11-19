package zone.moddev.mc.hammerspace.recipe;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import zone.moddev.mc.hammerspace.HSRegistries;
import zone.moddev.mc.hammerspace.pocket.PocketItem;

/**
 * A shaped recipe whose result keeps the contents of the pocket in the grid. Same JSON as
 * {@code minecraft:crafting_shaped}, with {@code "type": "hammerspace:pocket_upgrade"}.
 */
public class PocketUpgradeRecipe extends ShapedRecipe {
    public PocketUpgradeRecipe(ShapedRecipe base) {
        super(base.getGroup(), base.category(), base.pattern, base.getResultItem(null), base.showNotification());
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = super.assemble(input, registries);
        for (ItemStack ingredient : input.items()) {
            if (ingredient.getItem() instanceof PocketItem) {
                result.set(HSRegistries.CONTENTS.get(), ingredient.get(HSRegistries.CONTENTS.get()));
                break;
            }
        }
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return HSRegistries.POCKET_UPGRADE.get();
    }

    public static final class Serializer implements RecipeSerializer<PocketUpgradeRecipe> {
        private static final MapCodec<PocketUpgradeRecipe> CODEC =
                ShapedRecipe.Serializer.CODEC.xmap(PocketUpgradeRecipe::new, r -> r);
        private static final StreamCodec<RegistryFriendlyByteBuf, PocketUpgradeRecipe> STREAM_CODEC =
                ShapedRecipe.Serializer.STREAM_CODEC.map(PocketUpgradeRecipe::new, r -> r);

        @Override
        public MapCodec<PocketUpgradeRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, PocketUpgradeRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
