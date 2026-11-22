package evernight.client.utils.inventory;

import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;

public interface PalettedContainerDataAccess {
    static BitStorage getStorage(PalettedContainer container) {
        PalettedContainerDataAccess data = ((PalettedContainerAccess) container).evernight$data();
        return data.evernight$storage();
    }

    static Palette getPalette(PalettedContainer container) {
        PalettedContainerDataAccess data = ((PalettedContainerAccess) container).evernight$data();
        return data.evernight$palette();
    }

    BitStorage evernight$storage();

    Palette evernight$palette();
}