package evernight.client.commands.control.Drawn;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import evernight.client.ClientContext;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.text.Translations;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;

import java.util.Iterator;
import evernight.client.commands.arguments.ModuleArgumentType;
import evernight.client.commands.arguments.ModuleCategoryArgumentType;
import evernight.client.commands.Command;

public final class DrawnCommand extends Command {
    public DrawnCommand() {
        super("drawn");
    }

    @Override
    public void exec(LiteralArgumentBuilder root) {
        String moduleNodeName = "module";
        ArgumentType moduleNodeType = new ModuleArgumentType();
        ArgumentBuilder moduleToggleBuilder = Command.argument(moduleNodeName, moduleNodeType);
        com.mojang.brigadier.Command moduleToggleCallback = (com.mojang.brigadier.Command<Object>) context -> {
            Module module = context.getArgument("module", Module.class);
            boolean nextDrawn = !module.isDrawn();
            module.setDrawn(nextDrawn);
            String moduleName = module.getDisplayName();
            String stateText = Translations.ui(module.isDrawn() ? "commands.Drawn.StateShown" : "commands.Drawn.StateHidden");
            String confirmationText = Translations.format("commands.Drawn.Set", moduleName, stateText);
            MutableComponent confirmation = Component.literal(confirmationText);
            Component moduleConfirmationMessage = confirmation;
            MessageSignature moduleConfirmationSignature = ChatUtils.createSignature(-1);
            ChatUtils.printChat(moduleConfirmationMessage, moduleConfirmationSignature);
            return 1;
        };
        ArgumentBuilder moduleToggleBranch = moduleToggleBuilder.executes(moduleToggleCallback);
        root.then(moduleToggleBranch);
        LiteralArgumentBuilder allLiteral = Command.literal("all");
        String allStateNodeName = "state";
        ArgumentType allStateNodeType = BoolArgumentType.bool();
        ArgumentBuilder allStateActionBuilder = Command.argument(allStateNodeName, allStateNodeType);
        com.mojang.brigadier.Command allStateActionCallback = (com.mojang.brigadier.Command<Object>) context -> {
            Boolean boxedState = context.getArgument("state", Boolean.class);
            boolean drawn = boxedState;
            ModuleRegistry registry = ClientContext.moduleRegistry;
            Iterator modules = registry.getRegistry().iterator();
            while (modules.hasNext()) {
                Module module = (Module) modules.next();
                module.setDrawn(drawn);
            }
            String stateText = Translations.ui(drawn ? "commands.Drawn.StateShown" : "commands.Drawn.StateHidden");
            String confirmationText = Translations.format("commands.Drawn.SetAll", stateText);
            MutableComponent confirmation = Component.literal(confirmationText);
            Component allConfirmationMessage = confirmation;
            MessageSignature allConfirmationSignature = ChatUtils.createSignature(-1);
            ChatUtils.printChat(allConfirmationMessage, allConfirmationSignature);
            return 1;
        };
        ArgumentBuilder allStateBranch = allStateActionBuilder.executes(allStateActionCallback);
        ArgumentBuilder allBranch = allLiteral.then(allStateBranch);
        root.then(allBranch);
        String categoryNodeName = "category";
        ArgumentType categoryNodeType = new ModuleCategoryArgumentType();
        RequiredArgumentBuilder categoryArgument = Command.argument(categoryNodeName, categoryNodeType);
        String categoryStateNodeName = "state";
        ArgumentType categoryStateNodeType = BoolArgumentType.bool();
        ArgumentBuilder categoryStateActionBuilder = Command.argument(categoryStateNodeName, categoryStateNodeType);
        com.mojang.brigadier.Command categoryStateActionCallback = (com.mojang.brigadier.Command<Object>) context -> {
            ModuleCategory category = context.getArgument("category", ModuleCategory.class);
            Boolean boxedState = context.getArgument("state", Boolean.class);
            boolean drawn = boxedState;
            ModuleRegistry registry = ClientContext.moduleRegistry;
            Iterator modules = registry.getRegistry().iterator();
            while (modules.hasNext()) {
                Module module = (Module) modules.next();
                if (category == module.getCategory()) {
                    module.setDrawn(drawn);
                }
            }
            Object[] formatArguments = new Object[2];
            formatArguments[0] = category.getDisplayName();
            formatArguments[1] = Translations.ui(drawn ? "commands.Drawn.StateShown" : "commands.Drawn.StateHidden");
            String formattedText = Translations.format("commands.Drawn.SetCategory", formatArguments);
            MutableComponent confirmation = Component.literal(formattedText);
            Component categoryConfirmationMessage = confirmation;
            MessageSignature categoryConfirmationSignature = ChatUtils.createSignature(-1);
            ChatUtils.printChat(categoryConfirmationMessage, categoryConfirmationSignature);
            return 1;
        };
        ArgumentBuilder categoryStateBranch = categoryStateActionBuilder.executes(categoryStateActionCallback);
        ArgumentBuilder categoryBranch = categoryArgument.then(categoryStateBranch);
        root.then(categoryBranch);
    }
}