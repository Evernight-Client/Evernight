package evernight.client.gui.components.base;

public enum ComponentFlag {
    PADDING_SHIFT, SILENT, SILENT_HOVER;
    private final int mask = 1 << this.ordinal();

    public int getMask() {
        return this.mask;
    }
}