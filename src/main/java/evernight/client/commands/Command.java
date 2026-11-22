package evernight.client.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.NamedFeature;
import evernight.client.managers.task.TaskScheduler;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import evernight.client.commands.arguments.AliasedLiteralArgument;

public abstract class Command extends NamedFeature implements ClientAccess {
    private String[] aliases = new String[0];

    public Command(String name) {
        super(name);
    }

    protected static RequiredArgumentBuilder argument(String name, ArgumentType type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    protected static LiteralArgumentBuilder literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    protected static RequiredArgumentBuilder aliasedLiteral(String... names) {
        String name = names[0];
        AliasedLiteralArgument type = new AliasedLiteralArgument(names);
        return argument(name, type);
    }

    public abstract void exec(LiteralArgumentBuilder builder);

    public String[] getAliases() {
        return this.aliases;
    }

    protected void setAliases(String... aliases) {
        this.aliases = aliases;
    }

    protected void scheduleRotationTask(Runnable task) {
        TaskScheduler scheduler = ClientContext.taskScheduler;
        scheduler.scheduleRotationTask(task, 0);
    }

    protected int scheduleScreenChange(Screen screen) {
        TaskScheduler scheduler = ClientContext.taskScheduler;
        scheduler.scheduleScreenChange(screen);
        return 1;
    }

    protected void addSubcommand(Command command, LiteralArgumentBuilder parent) {
        String name = command.getName();
        LiteralArgumentBuilder child = literal(name);
        command.exec(child);
        parent.then(child);
    }

    protected MutableComponent formatText(String text) {
        MutableComponent emptyComponent = Component.empty();
        return emptyComponent.append(ChatUtils.watermarkText(text));
    }
}