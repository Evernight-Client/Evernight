package evernight.mixin.minecraft.world;

import evernight.client.utils.inventory.PalettedContainerDataAccess;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer.Data;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Data.class)
public class PalettedContainerDataMixin implements PalettedContainerDataAccess {
    @Shadow
    @Final
    BitStorage storage;
    @Shadow
    @Final
    Palette palette;

    @Override
    public BitStorage evernight$storage() {
        return this.storage;
    }

    @Override
    public Palette evernight$palette() {
        return this.palette;
    }
}