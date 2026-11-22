package evernight.client.utils.core;

public interface Toggleable {
    void enable();

    boolean isToggled();

    default void setToggled(boolean toggled) {
        if (this.isToggled() != toggled) {
            this.toggle();
        }
    }

    void disable();

    default void toggle() {
        if (this.isToggled()) {
            this.disable();
        } else {
            this.enable();
        }
    }
}