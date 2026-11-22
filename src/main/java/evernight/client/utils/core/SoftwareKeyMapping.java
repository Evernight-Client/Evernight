package evernight.client.utils.core;

import net.minecraft.client.KeyMapping;

public class SoftwareKeyMapping extends KeyMapping {
    private boolean down;

    public SoftwareKeyMapping() {
        super("evernight-wrapper-keybind", 0, Category.MISC);
        ALL.remove("evernight-wrapper-keybind");
    }

    @Override
    public boolean isDown() {
        return down;
    }

    @Override
    public void setDown(boolean down) {
        this.down = down;
    }
}