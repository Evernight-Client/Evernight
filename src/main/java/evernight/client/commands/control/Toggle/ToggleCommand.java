package evernight.client.commands.control.Toggle;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import evernight.client.modules.Module;
import evernight.client.commands.arguments.ModuleArgumentType;
import evernight.client.commands.Command;

public final class ToggleCommand extends Command {
    public ToggleCommand() {
        super("toggle");
    }

    @Override
    public void exec(LiteralArgumentBuilder builder) {
        ModuleArgumentType moduleType = new ModuleArgumentType();
        RequiredArgumentBuilder moduleArgument = Command.argument("module", moduleType);
        BoolArgumentType stateType = BoolArgumentType.bool();
        RequiredArgumentBuilder stateArgument = Command.argument("state", stateType);
        com.mojang.brigadier.Command<Object> setState = context -> {
            Class<Module> moduleClass = Module.class;
            Module module = context.getArgument("module", moduleClass);
            Boolean state = context.getArgument("state", Boolean.class);
            boolean toggled = state.booleanValue();
            module.setToggled(toggled);
            return 1;
        };
        ArgumentBuilder stateCommand = stateArgument.executes(setState);
        RequiredArgumentBuilder moduleWithState = (RequiredArgumentBuilder) moduleArgument.then(stateCommand);
        com.mojang.brigadier.Command<Object> toggle = context -> {
            Class<Module> moduleClass = Module.class;
            Module module = context.getArgument("module", moduleClass);
            module.toggle();
            return 1;
        };
        ArgumentBuilder moduleCommand = moduleWithState.executes(toggle);
        builder.then(moduleCommand);
    }
}