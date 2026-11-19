package zone.moddev.mc.hammerspace.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Keys; the world keys are unbound by default like /dank/null's. */
public final class HSKeys {
    private static final String CATEGORY = "key.categories.hammerspace";

    public static final KeyMapping OPEN = world("open", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping NEXT = world("next", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping PREVIOUS = world("previous", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping TOGGLE_HUD = world("toggle_hud", InputConstants.UNKNOWN.getValue());
    /** Held while clicking a pocket slot. */
    public static final KeyMapping CYCLE_PLACE = gui("cycle_place", GLFW.GLFW_KEY_P);
    public static final KeyMapping TOGGLE_UNIFY = gui("toggle_unify", GLFW.GLFW_KEY_O);

    public static final KeyMapping[] ALL = {OPEN, NEXT, PREVIOUS, TOGGLE_HUD, CYCLE_PLACE, TOGGLE_UNIFY};

    private HSKeys() {}

    private static KeyMapping world(String name, int key) {
        return new KeyMapping("key.hammerspace." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, key, CATEGORY);
    }

    private static KeyMapping gui(String name, int key) {
        return new KeyMapping("key.hammerspace." + name, KeyConflictContext.GUI, InputConstants.Type.KEYSYM, key, CATEGORY);
    }
}
