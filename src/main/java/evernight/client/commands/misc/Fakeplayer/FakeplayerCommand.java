package evernight.client.commands.misc.Fakeplayer;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import evernight.client.ClientContext;
import evernight.client.managers.player.FakeplayerManager;
import evernight.client.modules.misc.Fakeplayer.FakeplayerModule;
import evernight.client.commands.arguments.FancyStringArgument;
import evernight.client.commands.Command;

/**
 * Brigadier front-end for the fake player.
 *
 * <p>The plain command toggles the {@link FakeplayerModule}, so the module switch, the ClickGUI
 * control panel and the command always agree on whether the feature is enabled. The extra actions
 * (record, play, logout, kill) operate on the shared {@link FakeplayerManager} state.
 */
public final class FakeplayerCommand extends Command {
    public FakeplayerCommand() {
        super("fakeplayer");
        this.setAliases("dummy");
    }

    private static FakeplayerModule module() {
        return ClientContext.getModule(FakeplayerModule.class);
    }

    private static FakeplayerManager manager() {
        return ClientContext.fakeplayerManager;
    }

    @Override
    public void exec(LiteralArgumentBuilder builder) {
        ArgumentBuilder nameArgument = Command.argument("name", FancyStringArgument.fancyString())
                .executes((com.mojang.brigadier.Command<Object>) context -> {
                    FakeplayerModule fakeplayerModule = FakeplayerCommand.module();
                    fakeplayerModule.getNameSetting().setValue(context.getArgument("name", String.class));
                    fakeplayerModule.toggle();
                    return 1;
                });
        LiteralArgumentBuilder commandBranch = (LiteralArgumentBuilder) builder.then(nameArgument);
        ArgumentBuilder recordAction = Command.literal("record")
                .executes((com.mojang.brigadier.Command<Object>) context -> {
                    FakeplayerCommand.manager().toggleRecording();
                    return 1;
                });
        commandBranch = (LiteralArgumentBuilder) commandBranch.then(recordAction);
        ArgumentBuilder playAction = Command.literal("play")
                .executes((com.mojang.brigadier.Command<Object>) context -> {
                    FakeplayerCommand.manager().togglePlayback();
                    return 1;
                });
        commandBranch = (LiteralArgumentBuilder) commandBranch.then(playAction);
        ArgumentBuilder logoutAction = Command.literal("logout")
                .executes((com.mojang.brigadier.Command<Object>) context -> {
                    FakeplayerCommand.manager().toggleLogoutSpot();
                    return 1;
                });
        commandBranch = (LiteralArgumentBuilder) commandBranch.then(logoutAction);
        ArgumentBuilder killAction = Command.literal("kill")
                .executes((com.mojang.brigadier.Command<Object>) context -> {
                    FakeplayerCommand.manager().killPlayer();
                    return 1;
                });
        commandBranch.then(killAction)
                .executes((com.mojang.brigadier.Command<Object>) context -> {
                    FakeplayerCommand.module().toggle();
                    return 1;
                });
    }
}
