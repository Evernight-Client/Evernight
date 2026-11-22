package evernight.client.commands.movement.WaypointWalk;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.IBaritoneProvider;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.ICustomGoalProcess;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import evernight.client.ClientContext;
import evernight.client.render.text.Translations;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import evernight.client.commands.arguments.FancyStringArgument;
import evernight.client.commands.world.Waypoints.WaypointsCommand;
import evernight.client.commands.Command;

public class WaypointWalkCommand extends Command {
    public WaypointWalkCommand() {
        super("walk");
    }

    @Override
    public void exec(LiteralArgumentBuilder root) {
        FancyStringArgument nameType = FancyStringArgument.fancyString();
        RequiredArgumentBuilder nameBuilder = Command.argument("name", nameType);
        SuggestionProvider<Object> suggestions = (context, builder) -> {
            String serverKey = WaypointsCommand.getCurrentServerKey();
            return WaypointsCommand.suggestNamesForServer(context, builder, serverKey);
        };
        RequiredArgumentBuilder suggestedNameBuilder = nameBuilder.suggests(suggestions);
        com.mojang.brigadier.Command<Object> executor = context -> {
            if (!ClientContext.baritoneBridge.isAvailable()) {
                ChatUtils.printChat(Component.literal(Translations.ui("commands.WaypointWalk.BaritoneMissing")), ChatUtils.DEFAULT_SIGNATURE);
                return 0;
            }
            Vec3 waypoint = WaypointsCommand.resolveWaypointPosition(context);
            if (waypoint == null) {
                ChatUtils.printChat(Component.literal(Translations.ui("commands.WaypointWalk.WaypointNotFoundPrefix")).append(this.formatText(context.getArgument("name", String.class))).append(Translations.ui("commands.WaypointWalk.WaypointNotFoundSuffix")), ChatUtils.DEFAULT_SIGNATURE, ChatUtils.MessagePriority.LOW);
                return 0;
            }
            IBaritoneProvider provider = BaritoneAPI.getProvider();
            IBaritone baritone = provider.getPrimaryBaritone();
            GoalXZ goal = new GoalXZ((int) waypoint.x(), (int) waypoint.z());
            ICustomGoalProcess process = baritone.getCustomGoalProcess();
            process.setGoalAndPath(goal);
            ChatUtils.printChat(Component.literal(Translations.ui("commands.WaypointWalk.WalkingTo")).append(this.formatText(context.getArgument("name", String.class))), ChatUtils.DEFAULT_SIGNATURE);
            return 1;
        };
        ArgumentBuilder waypointBranch = suggestedNameBuilder.executes(executor);
        root.then(waypointBranch);
    }
}