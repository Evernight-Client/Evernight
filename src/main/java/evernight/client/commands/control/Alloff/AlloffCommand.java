package evernight.client.commands.control.Alloff;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import evernight.client.ClientContext;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.HudWidgetModule;
import evernight.client.modules.Module;
import evernight.client.render.text.Translations;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import evernight.client.commands.Command;

public final class AlloffCommand extends Command {
    private final Set previouslyEnabledModules = new HashSet();

    public AlloffCommand() {
        super("alloff");
    }

    @Override
    public void exec(LiteralArgumentBuilder commandBuilder) {
        com.mojang.brigadier.Command<Object> disableCommand = context -> {
            this.previouslyEnabledModules.clear();
            ModuleRegistry registry = ClientContext.moduleRegistry;
            Iterator modules = registry.getRegistry().iterator();
            while (modules.hasNext()) {
                Module module = (Module) modules.next();
                if (!(module instanceof HudWidgetModule) && module.isToggled()) {
                    module.toggle();
                    this.previouslyEnabledModules.add(module);
                }
            }
            this.scheduleRotationTask(() -> ChatUtils.printChat(
                    Component.literal(Translations.ui("commands.Alloff.DisabledPrefix")).append(this.formatText(String.valueOf(this.previouslyEnabledModules.size()))).append(Translations.ui("commands.Alloff.DisabledSuffix")),
                    ChatUtils.DEFAULT_SIGNATURE));
            return 1;
        };
        commandBuilder.executes(disableCommand);
        String[] aliasArray = {"restore", "backup"};
        ArgumentBuilder restoreBuilder = Command.aliasedLiteral(aliasArray);
        com.mojang.brigadier.Command<Object> restoreCommand = context -> {
            ModuleRegistry registry = ClientContext.moduleRegistry;
            Iterator modules = registry.getRegistry().iterator();
            while (modules.hasNext()) {
                Module module = (Module) modules.next();
                if (!(module instanceof HudWidgetModule)) {
                    boolean wasEnabled = this.previouslyEnabledModules.contains(module);
                    module.setToggled(wasEnabled);
                }
            }
            this.scheduleRotationTask(() -> ChatUtils.printChat(
                    Component.literal(Translations.ui("commands.Alloff.RestoredPrefix")).append(this.formatText(String.valueOf(this.previouslyEnabledModules.size()))).append(Translations.ui("commands.Alloff.RestoredSuffix")),
                    ChatUtils.DEFAULT_SIGNATURE));
            return 1;
        };
        ArgumentBuilder restoreBranch = restoreBuilder.executes(restoreCommand);
        commandBuilder.then(restoreBranch);
    }
}