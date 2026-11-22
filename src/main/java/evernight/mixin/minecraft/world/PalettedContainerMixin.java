package evernight.mixin.minecraft.world;

import evernight.client.utils.inventory.PalettedContainerAccess;
import evernight.client.utils.inventory.PalettedContainerDataAccess;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainer.Data;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PalettedContainer.class)
public class PalettedContainerMixin implements PalettedContainerAccess {
    @Shadow
    private volatile Data data;

    @Override
    public PalettedContainerDataAccess evernight$data() {
        return (PalettedContainerDataAccess) (Object) this.data;
    }
}