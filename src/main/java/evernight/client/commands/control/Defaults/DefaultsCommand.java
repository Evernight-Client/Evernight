package evernight.client.commands.control.Defaults;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.Keybind;
import evernight.client.modules.Module;
import evernight.client.modules.client.Fonts.FontsModule;
import evernight.client.modules.client.UI.UIModule;
import evernight.client.modules.render.Blur.BlurModule;
import evernight.client.render.text.Translations;
import evernight.client.settings.core.Setting;
import evernight.client.utils.chat.ChatUtils;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

import java.util.Iterator;
import evernight.client.commands.Command;

public final class DefaultsCommand extends Command {
    public DefaultsCommand() {
        super("defaults");
    }

    private static Component resetConfirmationTitle() {
        return Component.nullToEmpty(Translations.ui("commands.Defaults.ConfirmTitle"));
    }

    private static int defaultUiKeyCode() {
        return 344;
    }

    @Override
    public void exec(LiteralArgumentBuilder commandBuilder) {
        com.mojang.brigadier.Command<Object> resetCommand = context -> {
            ConfirmScreen confirmationScreen = new ConfirmScreen(confirmed -> {
                if (confirmed) {
                    ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
                    Iterator moduleIterator = moduleRegistry.getRegistry().iterator();
                    while (moduleIterator.hasNext()) {
                        Module module = (Module) moduleIterator.next();
                        Iterator settingIterator = module.getSettings().iterator();
                        while (settingIterator.hasNext()) {
                            Setting<?> setting = (Setting<?>) settingIterator.next();
                            setting.reset();
                        }
                        if (module instanceof UIModule) {
                            Keybind defaultUiBinding = new Keybind(defaultUiKeyCode(), Keybind.Mode.TOGGLE, false);
                            module.setKeybind(defaultUiBinding);
                        } else {
                            Keybind defaultBinding = Keybind.UNBOUND;
                            module.setKeybind(defaultBinding);
                        }
                        if (!(module instanceof FontsModule) && !(module instanceof BlurModule)) {
                            module.setToggled(false);
                        }
                    }
                    this.scheduleRotationTask(() -> ChatUtils.printChat(Component.literal(Translations.ui("commands.Defaults.Reset")), ChatUtils.DEFAULT_SIGNATURE));
                }
                Minecraft client = ClientAccess.evernightMinecraft;
                client.setScreen(null);
            }, resetConfirmationTitle(), Component.nullToEmpty(Translations.ui("commands.Defaults.ConfirmContinue")));
            Runnable showConfirmation = () -> {
                Minecraft client = ClientAccess.evernightMinecraft;
                client.setScreen(confirmationScreen);
            };
            this.scheduleRotationTask(showConfirmation);
            return 1;
        };
        commandBuilder.executes(resetCommand);
    }
}