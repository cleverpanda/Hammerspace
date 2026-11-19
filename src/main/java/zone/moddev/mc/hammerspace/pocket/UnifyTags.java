package zone.moddev.mc.hammerspace.pocket;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import zone.moddev.mc.hammerspace.Hammerspace;

/**
 * Which item tags a pocket may unify on, from {@code data/<namespace>/hammerspace/unify/*.json}:
 * {@code {"tags": ["c:ingots/*", "c:gems/diamond"]}}. A trailing {@code /*} matches every tag under that path.
 * Every file is merged; a datapack adds tags with its own file, or replaces ours at the same path.
 */
public final class UnifyTags extends SimpleJsonResourceReloadListener {
    private static final Codec<List<String>> FILE = Codec.STRING.listOf().fieldOf("tags").codec();
    private static volatile Set<String> exact = Set.of();
    private static volatile List<String> prefixes = List.of();

    public UnifyTags() {
        super(new com.google.gson.Gson(), Hammerspace.MOD_ID + "/unify");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        Set<String> newExact = new HashSet<>();
        Set<String> newPrefixes = new HashSet<>();
        files.forEach((id, json) -> FILE.parse(JsonOps.INSTANCE, json)
                .ifError(e -> Hammerspace.LOGGER.error("Skipping unify file {}: {}", id, e.message()))
                .ifSuccess(tags -> tags.forEach(t -> {
                    if (t.endsWith("/*")) newPrefixes.add(t.substring(0, t.length() - 1));
                    else newExact.add(t);
                })));
        exact = Set.copyOf(newExact);
        prefixes = List.copyOf(newPrefixes);
    }

    private static boolean allowed(String tag) {
        return exact.contains(tag) || prefixes.stream().anyMatch(tag::startsWith);
    }

    /** Whether the two items share a tag a pocket may unify on. */
    public static boolean share(ItemStack stored, ItemStack incoming) {
        return stored.getTags().anyMatch(tag -> allowed(tag.location().toString()) && incoming.is(tag));
    }
}
