package evernight.client.modules.combat.AntiPhase;

import net.minecraft.world.entity.player.Player;

public interface AntiPhasePlacementPlanner {
    AntiPhasePlacement findPlacement(Player target);
}