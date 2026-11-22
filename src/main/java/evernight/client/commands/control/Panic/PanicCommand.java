package evernight.client.commands.control.Panic;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.access.ChatLineAccess;
import evernight.client.access.VisibleChatLinesAccess;
import evernight.client.managers.registry.CommandRegistry;
import evernight.client.managers.config.ConfigurationManager;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.Keybind;
import evernight.client.modules.Module;
import evernight.client.render.text.Translations;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.GuiMessage.Line;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Predicate;
import evernight.client.commands.Command;

public final class PanicCommand extends Command {
    private static final String HIDDEN_PREFIX = "ㅤ";
    public static boolean unloaded;

    static {
        unloaded = false;
    }

    public PanicCommand() {
        super("panic");
    }

    private void unload() {
        ConfigurationManager configuration = ClientContext.configurationManager;
        configuration.save();
        unloaded = true;
        String previousPrefix = CommandRegistry.getPrefix();
        CommandRegistry.setPrefix(HIDDEN_PREFIX);
        ModuleRegistry modules = ClientContext.moduleRegistry;
        Iterator moduleIterator = modules.getRegistry().iterator();
        while (moduleIterator.hasNext()) {
            Module module = (Module) moduleIterator.next();
            Function<Keybind, Keybind> unbind = binding -> {
                return binding.withCode(-1);
            };
            module.modifyKeybind(unbind);
            if (module.isToggled()) {
                module.toggle();
            }
        }
        Gui recentChatGui = ClientAccess.evernightMinecraft.gui;
        ChatComponent recentChat = recentChatGui.getChat();
        recentChat.getRecentChat().removeIf(message -> message.startsWith(previousPrefix));
        Gui visibleChatGui = ClientAccess.evernightMinecraft.gui;
        ((VisibleChatLinesAccess) visibleChatGui.getChat()).getVisible().removeIf((Predicate<Line>) line -> {
            ChatLineAccess access = (ChatLineAccess) (Object) line;
            if (access.getSignature() != null) {
                MessageSignature signature = access.getSignature();
                ByteBuffer signatureBytes = signature.asByteBuffer();
                return signatureBytes.getInt() < 0;
            }
            return false;
        });
        Gui remainingChatGui = ClientAccess.evernightMinecraft.gui;
        ChatComponent remainingChat = remainingChatGui.getChat();
        remainingChat.getRecentChat().removeIf(message -> message.startsWith(previousPrefix));
    }

    @Override
    public void exec(LiteralArgumentBuilder builder) {
        com.mojang.brigadier.Command<Object> command = context -> {
            this.scheduleRotationTask(() -> {
                Minecraft minecraft = ClientAccess.evernightMinecraft;
                ConfirmScreen confirmationScreen = new ConfirmScreen(confirmed -> {
                    if (confirmed) {
                        this.unload();
                    }
                    Minecraft currentMinecraft = ClientAccess.evernightMinecraft;
                    currentMinecraft.setScreen(null);
                },
                        Component.literal(Translations.ui("commands.Panic.ConfirmTitle")),
                        Component.literal(Translations.ui("commands.Panic.ConfirmContinue"))
                );
                minecraft.setScreen(confirmationScreen);
            });
            return 1;
        };
        builder.executes(command);
    }
}