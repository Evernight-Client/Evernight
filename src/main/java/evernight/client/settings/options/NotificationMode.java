package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

/**
 * Delivery service used by the WebhookNotifs module.
 *
 * <p>{@link #getName()} is the identifier stored in {@code webhook.json} and accepted by the
 * {@code webhook mode} command, so it must stay stable. Labels are resolved through
 * {@link evernight.client.render.text.Translations#value(String)}.
 */
public enum NotificationMode implements NamedEntry {
    DISCORD("Discord"),
    TELEGRAM("Telegram"),
    QQ("QQ");

    private final String displayName;

    NotificationMode(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Case-insensitive lookup used by the command and by config loading.
     *
     * @return the matching mode, or {@code null} when the text is not a known identifier
     */
    public static NotificationMode fromName(String name) {
        if (name == null) {
            return null;
        }
        String candidate = name.trim();
        for (NotificationMode mode : values()) {
            if (mode.displayName.equalsIgnoreCase(candidate)) {
                return mode;
            }
        }
        return null;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}
