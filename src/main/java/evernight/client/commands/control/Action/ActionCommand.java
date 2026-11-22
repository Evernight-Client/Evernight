package evernight.client.commands.control.Action;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.bus.EventBus;
import evernight.client.managers.player.InputOverrideManager;
import evernight.client.settings.core.NamedEntry;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.network.PacketSender;
import evernight.mixin.minecraft.client.MinecraftAccessor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.Equippable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import evernight.client.commands.arguments.EnumArgumentType;
import evernight.client.commands.arguments.RegistryEntryArgument;
import evernight.client.commands.player.Inventory.InventoryCommand;
import evernight.client.commands.Command;

public class ActionCommand extends Command {
    private final Map<SwapMode, List<SwapHistoryEntry>> swapHistory = new HashMap<>();
    private boolean actionBlocked;

    public ActionCommand() {
        super("action");
        EventBus eventBus = ClientAccess.eventBus;
        eventBus.subscribe(this);
        for (ActionCommand.SwapMode mode : ActionCommand.SwapMode.values()) {
            this.swapHistory.put(mode, new ArrayList<>());
        }
    }

    public static boolean canEquip(Item item) {
        var stackForComponent = item.getDefaultInstance();
        var equippableType = DataComponents.EQUIPPABLE;
        Equippable equippable = stackForComponent.get(equippableType);
        if (equippable != null) {
            LocalPlayer playerForType = ClientAccess.evernightMinecraft.player;
            EntityType playerType = playerForType.getType();
            return equippable.canBeEquippedBy(playerType);
        }
        return false;
    }

    @Override
    public void exec(LiteralArgumentBuilder root) {
        RequiredArgumentBuilder swapModeArgumentBuilder = Command.argument("swap", new EnumArgumentType(ActionCommand.SwapMode.class));
        DefaultedRegistry<Item> itemRegistry = BuiltInRegistries.ITEM;
        BiPredicate<CommandContext, Item> itemFilter = this::acceptsSwapItem;
        IntFunction<Item[]> itemArrayFactory = Item[]::new;
        ArgumentType itemType = RegistryEntryArgument.aliased(itemRegistry, itemFilter, itemArrayFactory);
        ArgumentBuilder itemArgument = Command.argument("item", itemType).executes(
                (com.mojang.brigadier.Command<Object>) context -> {
                    Item[] items = context.getArgument("item", Item[].class);
                    ActionCommand.SwapMode swapMode = context.getArgument("swap", SwapMode.class);
                    ActionSwapStrategy strategyForLookup = swapMode.getStrategy();
                    SwapSlots slots = strategyForLookup.findSlots(items);
                    if (slots.isInvalid()) {
                        this.actionBlocked = true;
                    }
                    ActionCommand.SwapHistoryEntry entry = new ActionCommand.SwapHistoryEntry(
                            swapMode.getStrategy(), slots);
                    this.swapHistory.get(swapMode).add(entry);
                    if (!slots.isInvalid()) {
                        swapMode.getStrategy().apply(slots);
                    }
                    return 1;
                }
        );
        swapModeArgumentBuilder = (RequiredArgumentBuilder) swapModeArgumentBuilder.then(itemArgument);
        ArgumentBuilder backArgument = Command.literal("back").executes(
                (com.mojang.brigadier.Command<Object>) context -> {
                    ActionCommand.SwapMode swapMode = context.getArgument("swap", SwapMode.class);
                    List<SwapHistoryEntry> history = this.swapHistory.get(swapMode);
                    if (history.isEmpty()) {
                        return 1;
                    }
                    ActionCommand.SwapHistoryEntry entry = history.removeLast();
                    SwapSlots savedSlots = entry.slots();
                    if (savedSlots.isInvalid()) {
                        return 1;
                    }
                    entry.strategy().undo(entry.slots);
                    return 1;
                }
        );
        ArgumentBuilder swapWithBack = swapModeArgumentBuilder.then(backArgument);
        root.then(swapWithBack);
        ArgumentBuilder attackArgument = Command.literal("attack").executes(
                (com.mojang.brigadier.Command<Object>) context -> {
                    if (this.actionBlocked) {
                        return 1;
                    }
                    Runnable attackAction = () -> ((MinecraftAccessor) ClientAccess.evernightMinecraft).attack();
                    if (ClientAccess.evernightMinecraft.screen instanceof ChatScreen) {
                        this.scheduleRotationTask(attackAction);
                    } else {
                        attackAction.run();
                    }
                    return 1;
                }
        );
        root.then(attackArgument);
        ArgumentBuilder interactArgument = Command.literal("interact").executes(
                (com.mojang.brigadier.Command<Object>) context -> {
                    if (this.actionBlocked) {
                        return 1;
                    }
                    Runnable interactAction = () -> ((MinecraftAccessor) ClientAccess.evernightMinecraft).interact();
                    if (ClientAccess.evernightMinecraft.screen instanceof ChatScreen) {
                        this.scheduleRotationTask(interactAction);
                    } else {
                        interactAction.run();
                    }
                    return 1;
                }
        );
        root.then(interactArgument);
        ArgumentBuilder jumpArgument = Command.literal("jump").executes((com.mojang.brigadier.Command<Object>) context -> {
            InputOverrideManager inputOverrides = ClientContext.inputOverrides;
            inputOverrides.requestJump(true);
            return 1;
        });
        root.then(jumpArgument);
        ArgumentBuilder takeoffArgument = Command.literal("takeoff").executes((com.mojang.brigadier.Command<Object>) context -> {
            if (!this.actionBlocked) {
                if (PlayerStateUtils.isLocalWearingElytra()) {
                    LocalPlayer playerForGroundCheck = ClientAccess.evernightMinecraft.player;
                    if (!playerForGroundCheck.onGround()) {
                        PacketSender.sendStartFallFlying();
                        LocalPlayer playerForTakeoff = ClientAccess.evernightMinecraft.player;
                        playerForTakeoff.startFallFlying();
                    }
                }
            }
            return 1;
        });
        root.then(takeoffArgument);
        InventoryCommand inventoryCommand = new InventoryCommand();
        this.addSubcommand(inventoryCommand, root);
    }

    private boolean acceptsSwapItem(CommandContext context, Item item) {
        ActionCommand.SwapMode mode = (ActionCommand.SwapMode) context.getArgument("swap", ActionCommand.SwapMode.class);
        return mode.acceptsItem(item);
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        this.actionBlocked = false;
    }

    private enum SwapMode implements NamedEntry {
        SWAP("swap", ignoredItem -> true, new HotbarSwapStrategy()),
        ALTERNATIVE("alternative", ignoredItem -> true, new AlternativeSwapStrategy()),
        ARMOR("armor", ActionCommand::canEquip, new ArmorSwapStrategy()),
        OFFHAND("offhand", ignoredItem -> true, new OffhandSwapStrategy());
        private final String name;
        private final ActionSwapStrategy strategy;
        private final Predicate<Item> itemFilter;

        SwapMode(String name, Predicate<Item> itemFilter, ActionSwapStrategy strategy) {
            this.name = name;
            this.itemFilter = itemFilter;
            this.strategy = strategy;
        }

        public ActionSwapStrategy getStrategy() {
            return this.strategy;
        }

        @Override
        public String getName() {
            return this.name;
        }

        public boolean acceptsItem(Item item) {
            return this.itemFilter.test(item);
        }
    }

    private record SwapHistoryEntry(ActionSwapStrategy strategy, SwapSlots slots) {
    }
}