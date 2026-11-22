package evernight.client.audio;

import evernight.client.utils.collections.NamedIdentifier;

public enum BuiltinSound {
    NONE("none"),

    // General UI feedback
    HOVER("hover"),
    CLICK("click"),
    RCLICK("rclick"),
    BLIP("blip"),
    BLIP2("blip2"),
    PICKUP("pickup"),
    PICKUP2("pickup2"),
    WARNING("warning"),
    BODYSPLAT("bodysplat"),
    TOOLBOX("toolbox"),
    HORROR("horror"),

    // Notifications and combat feedback
    CHIME("chime"),
    CLEAR("clear"),
    BEEP("beep"),
    HITMARKER("hitmarker"),
    IMPACT("impact"),
    SUIT("suit"),
    NOTIFY("notify"),
    MESSAGE("message"),
    ALERT("alert"),
    PING("ping"),
    RADIO("radio");

    private final NamedIdentifier identifier;

    BuiltinSound(String name) {
        if (name.equals("none")) {
            this.identifier = NamedIdentifier.NONE;
        } else {
            this.identifier = new NamedIdentifier(name);
        }
    }

    public NamedIdentifier getIdentifier() {
        return this.identifier;
    }
}
