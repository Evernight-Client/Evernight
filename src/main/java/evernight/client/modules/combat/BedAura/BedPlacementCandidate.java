package evernight.client.modules.combat.BedAura;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;

public record BedPlacementCandidate(BlockPos foot, BlockPos head, float targetDamage, float selfDamage,
                                    BlockHitResult hitResult) {
}