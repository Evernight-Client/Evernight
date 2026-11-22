package evernight.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import evernight.client.ClientAccess;
import evernight.client.modules.Keybind;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * Vanilla {@link KeyMapping}s owned by the client.
 *
 * <p>Bindings registered here go through Fabric's key binding API, so they appear in the
 * vanilla "Options &gt; Controls &gt; Key Binds" screen and are written to {@code options.txt}
 * like any other Minecraft key. The client never keeps a second, private copy of the key:
 * {@link #getClickGuiKeybind()} projects the vanilla binding into the client's own
 * {@link Keybind} model, which is what the ClickGUI displays and what the module registry
 * checks against an {@code InputPressEvent}.
 */
public final class ClientKeybinds {
    /**
     * Translation key of the ClickGUI binding. Vanilla key bind labels are resolved through
     * Minecraft's language table, so the name lives in
     * {@code assets/evernight/lang/*.json} instead of the client's ini table.
     */
    public static final String CLICK_GUI_KEY = "key.evernight.click_gui";
    private static final Logger LOGGER = ClientAccess.logger;
    private static KeyMapping clickGui;

    private ClientKeybinds() {
    }

    /**
     * Registers every client binding.
     *
     * <p>Has to run during client initialisation: Fabric refuses key binding registrations
     * once {@code Minecraft.options} exists, because {@code options.txt} has already been
     * read at that point and the binding would never be displayed or saved.
     */
    public static void register() {
        if (clickGui != null) {
            return;
        }
        try {
            clickGui = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                    CLICK_GUI_KEY,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_RIGHT_SHIFT,
                    KeyMapping.Category.MISC
            ));
        } catch (RuntimeException registrationFailure) {
            LOGGER.warn("Could not register the {} key binding", CLICK_GUI_KEY, registrationFailure);
        }
    }

    public static KeyMapping getClickGuiKey() {
        return clickGui;
    }

    /**
     * Projects the vanilla binding into the client {@link Keybind} model. Returns
     * {@link Keybind#UNBOUND} while the key binding is unavailable or unbound.
     */
    public static Keybind getClickGuiKeybind() {
        return toKeybind(clickGui);
    }

    /**
     * Writes a client {@link Keybind} back into the vanilla binding, so picking a key inside the
     * ClickGUI is mirrored in the vanilla Controls screen, {@code options.txt} included.
     */
    public static void setClickGuiKeybind(Keybind keybind) {
        applyKeybind(clickGui, keybind);
    }

    private static Keybind toKeybind(KeyMapping mapping) {
        if (mapping == null) {
            return Keybind.UNBOUND;
        }
        InputConstants.Key boundKey = mapping.key;
        if (boundKey == null || boundKey == InputConstants.UNKNOWN || boundKey.getValue() < 0) {
            return Keybind.UNBOUND;
        }
        return Keybind.toggle(boundKey.getValue(), boundKey.getType() == InputConstants.Type.MOUSE);
    }

    private static void applyKeybind(KeyMapping mapping, Keybind keybind) {
        if (mapping == null || keybind == null) {
            return;
        }
        if (keybind.isUnbound()) {
            mapping.setKey(InputConstants.UNKNOWN);
        } else {
            InputConstants.Type type = keybind.mouse() ? InputConstants.Type.MOUSE : InputConstants.Type.KEYSYM;
            mapping.setKey(type.getOrCreate(keybind.code()));
        }
        Options options = ClientAccess.evernightMinecraft.options;
        if (options != null) {
            // Persist immediately: the vanilla screen writes options.txt on close, and a key
            // picked inside the ClickGUI must survive a crash-free restart too.
            options.save();
        }
    }
}
