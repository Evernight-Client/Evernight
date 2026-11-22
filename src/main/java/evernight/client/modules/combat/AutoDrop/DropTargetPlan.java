package evernight.client.modules.combat.AutoDrop;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public record DropTargetPlan(Player player, DropPlacementMode mode, @Nullable Direction cageDirection) {
}