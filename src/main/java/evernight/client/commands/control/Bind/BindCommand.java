package evernight.client.commands.control.Bind;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.input.InputPressEvent;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.Keybind;
import evernight.client.modules.Module;
import evernight.client.render.text.Translations;
import evernight.client.utils.core.KeyUtils;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;

import java.util.Iterator;
import java.util.function.Function;
import evernight.client.commands.arguments.KeybindArgumentType;
import evernight.client.commands.arguments.ModuleArgumentType;
import evernight.client.commands.control.Preset.PresetCommand;
import evernight.client.commands.Command;

public final class BindCommand extends Command {
    private Module pendingModule;
    private boolean captureSubscribed;

    public BindCommand() {
        super("bind");
    }

    private static int readInputCode(InputPressEvent event) {
        return event.getCode();
    }

    @Override
    public void exec(LiteralArgumentBuilder root) {
        LiteralArgumentBuilder branchBuilder = Command.literal("set");
        ArgumentType moduleTypeForSet = new ModuleArgumentType();
        RequiredArgumentBuilder moduleArgument = Command.argument("module", moduleTypeForSet);
        ArgumentType keyTypeForSet = new KeybindArgumentType();
        ArgumentBuilder keyBuilder = Command.argument("key", keyTypeForSet);
        com.mojang.brigadier.Command<Object> setAction = context -> {
            Module module = context.getArgument("module", Module.class);
            Keybind keybind = context.getArgument("key", Keybind.class);
            this.applyKeybind(module, keybind);
            return 1;
        };
        ArgumentBuilder setKeyAction = keyBuilder.executes(setAction);
        ArgumentBuilder captureKeyActionBuilder = moduleArgument.then(setKeyAction);
        com.mojang.brigadier.Command<Object> captureAction = context -> {
            this.pendingModule = context.getArgument("module", Module.class);
            if (!this.captureSubscribed) {
                ClientAccess.eventBus.subscribe(this);
                this.captureSubscribed = true;
            }
            MutableComponent prompt = Component.literal(Translations.ui("commands.Bind.PressKey"));
            MessageSignature captureSignature = ChatUtils.createSignature(-1);
            ChatUtils.printChat(prompt, captureSignature);
            return 1;
        };
        ArgumentBuilder captureKeyAction = captureKeyActionBuilder.executes(captureAction);
        ArgumentBuilder setCommand = branchBuilder.then(captureKeyAction);
        LiteralArgumentBuilder configuredRoot = (LiteralArgumentBuilder) root.then(setCommand);
        branchBuilder = Command.literal("clear");
        ArgumentType moduleTypeForClear = new ModuleArgumentType();
        ArgumentBuilder clearModuleActionBuilder = Command.argument("module", moduleTypeForClear);
        com.mojang.brigadier.Command<Object> clearAction = context -> {
            Module module = context.getArgument("module", Module.class);
            Function clearBinding = (Function<Keybind, Keybind>) (binding -> {
                return binding.withCode(-1);
            });
            module.modifyKeybind(clearBinding);
            MutableComponent prefix = Component.literal(Translations.ui("commands.Bind.ClearModulePrefix"));
            String moduleName = module.getDisplayName();
            Component formattedClearedModuleName = this.formatText(moduleName);
            MutableComponent namedMessage = prefix.append(formattedClearedModuleName);
            MutableComponent message = namedMessage.append(Translations.ui("commands.Bind.ClearModuleSuffix"));
            MessageSignature clearedSignature = ChatUtils.createSignature(-1);
            ChatUtils.printChat(message, clearedSignature);
            return 1;
        };
        ArgumentBuilder clearModuleAction = clearModuleActionBuilder.executes(clearAction);
        branchBuilder = (LiteralArgumentBuilder) branchBuilder.then(clearModuleAction);
        ArgumentBuilder clearAllActionBuilder = Command.literal("all");
        com.mojang.brigadier.Command<Object> clearAllCallback = context -> {
            ModuleRegistry registry = ClientContext.moduleRegistry;
            Iterator modules = registry.getRegistry().iterator();
            while (modules.hasNext()) {
                Module module = (Module) modules.next();
                Function clearBinding = (Function<Keybind, Keybind>) (binding -> {
                    return binding.withCode(-1);
                });
                module.modifyKeybind(clearBinding);
            }
            MutableComponent message = Component.literal(Translations.ui("commands.Bind.ClearAll"));
            MessageSignature allClearedSignature = ChatUtils.createSignature(-1);
            ChatUtils.printChat(message, allClearedSignature);
            return 1;
        };
        ArgumentBuilder clearAllAction = clearAllActionBuilder.executes(clearAllCallback);
        ArgumentBuilder clearCommand = branchBuilder.then(clearAllAction);
        configuredRoot = (LiteralArgumentBuilder) configuredRoot.then(clearCommand);
        ArgumentBuilder listActionBuilder = Command.literal("list");
        com.mojang.brigadier.Command<Object> listCallback = context -> {
            PresetCommand.reportPresetBinds();
            return 1;
        };
        ArgumentBuilder listAction = listActionBuilder.executes(listCallback);
        configuredRoot.then(listAction);
    }

    private void applyKeybind(Module module, Keybind keybind) {
        Function updateBinding = (Function<Keybind, Keybind>) (previousBinding -> {
            int keyCode = keybind.code();
            Keybind bindingWithCode = previousBinding.withCode(keyCode);
            boolean capturedMouse = keybind.mouse();
            return bindingWithCode.withMouse(capturedMouse);
        });
        module.modifyKeybind(updateBinding);
        MutableComponent prefix = Component.literal(Translations.ui("commands.Bind.SetPrefix"));
        String moduleName = module.getDisplayName();
        Component formattedModuleName = this.formatText(moduleName);
        MutableComponent namedMessage = prefix.append(formattedModuleName);
        MutableComponent messageWithSuffix = namedMessage.append(Translations.ui("commands.Bind.SetMiddle"));
        int displayCode = keybind.code();
        boolean displayMouse = keybind.mouse();
        String capturedKeyName = KeyUtils.getKeyName(displayCode, displayMouse);
        Component formattedKeyName = this.formatText(capturedKeyName);
        MutableComponent message = messageWithSuffix.append(formattedKeyName);
        MessageSignature boundSignature = ChatUtils.createSignature(-1);
        ChatUtils.printChat(message, boundSignature);
    }

    @EventHandler
    private void onInputPress(InputPressEvent event) {
        Module moduleForBind = this.pendingModule;
        if (moduleForBind == null) {
            this.stopCapturing();
            return;
        }
        Keybind keybind = new Keybind(readInputCode(event), readPendingMode(), event.isMouse());
        this.applyKeybind(moduleForBind, keybind);
        this.pendingModule = null;
        this.stopCapturing();
    }

    private void stopCapturing() {
        if (this.captureSubscribed) {
            ClientAccess.eventBus.unsubscribe(this);
            this.captureSubscribed = false;
        }
    }

    private Keybind.Mode readPendingMode() {
        Keybind currentKeybind = this.pendingModule.getKeybind();
        return currentKeybind.mode();
    }
}