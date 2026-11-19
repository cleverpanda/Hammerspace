package zone.moddev.mc.hammerspace.compat.jei;

import java.util.Arrays;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import zone.moddev.mc.hammerspace.HSRegistries;
import zone.moddev.mc.hammerspace.Hammerspace;

/** JEI info pages. Crafting and upgrade recipes are shaped recipes, so JEI shows them on its own. Loaded only when JEI is installed. */
@JeiPlugin
public class HammerspaceJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(Hammerspace.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addItemStackInfo(Arrays.stream(HSRegistries.POCKETS).map(p -> new ItemStack(p.get())).toList(),
                Component.translatable("jei.hammerspace.pocket"));
        registration.addItemStackInfo(Arrays.stream(HSRegistries.LININGS).map(l -> new ItemStack(l.get())).toList(),
                Component.translatable("jei.hammerspace.lining"));
        registration.addItemStackInfo(new ItemStack(HSRegistries.DOCK_ITEM.get()), Component.translatable("jei.hammerspace.pocket_dock"));
    }
}
