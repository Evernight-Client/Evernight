package evernight.addon;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import evernight.client.commands.Command;
import evernight.client.render.text.Translations;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * {@code ;addons} - lists loaded addons, their versions and the number of modules they registered.
 */
@SuppressWarnings("unchecked")
public final class AddonsCommand extends Command {
    public AddonsCommand() {
        super("addons");
        this.setAliases("addon");
    }

    @Override
    public void exec(LiteralArgumentBuilder root) {
        com.mojang.brigadier.Command<Object> listAction = context -> {
            List<AddonInfo> addons = AddonManager.getInstance().getAddons();
            if (addons.isEmpty()) {
                return 1;
            }
            ChatUtils.printChat(
                    Component.literal(Translations.format("commands.Addons.Header", addons.size())),
                    ChatUtils.DEFAULT_SIGNATURE);
            for (AddonInfo info : addons) {
                AddonMetadata metadata = info.metadata();
                MutableComponent message = Component.literal(Translations.format("commands.Addons.Entry",
                        metadata.displayName(), metadata.version(), metadata.id(), info.moduleCount()));
                if (!info.isLoaded()) {
                    message.append(Component.literal(Translations.format("commands.Addons.Failed", info.error())));
                }
                ChatUtils.printChat(message, ChatUtils.DEFAULT_SIGNATURE);
            }
            return 1;
        };
        root.executes(listAction);
    }
}
