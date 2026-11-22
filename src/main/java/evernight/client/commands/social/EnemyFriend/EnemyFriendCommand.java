package evernight.client.commands.social.EnemyFriend;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.registry.SocialRegistry;
import evernight.client.render.font.FontRenderer;
import evernight.client.render.text.Translations;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.options.SocialRole;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import evernight.client.commands.arguments.ArrayArgumentType;
import evernight.client.commands.arguments.EnumArgumentType;
import evernight.client.commands.arguments.PlayerNameArgumentType;
import evernight.client.commands.Command;

public final class EnemyFriendCommand extends Command {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final SocialRole role;

    public EnemyFriendCommand(SocialRole role) {
        super(role.name().toLowerCase());
        this.role = role;
    }

    @Override
    public void exec(LiteralArgumentBuilder root) {
        LiteralArgumentBuilder commandBuilder = (LiteralArgumentBuilder) Command.literal("add")
                .executes((com.mojang.brigadier.Command<Object>) context -> {
                    if (ClientAccess.evernightMinecraft.hitResult instanceof EntityHitResult hit) {
                        if (hit.getEntity() instanceof Player player) {
                            GameProfile profile = player.getGameProfile();
                            String name = profile.name();
                            SocialRegistry socials = ClientContext.socialRegistry;
                            if (socials.getRole(name) != this.role) {
                                if (this.role == SocialRole.ENEMY) {
                                    socials = ClientContext.socialRegistry;
                                    socials.addEnemy(name);
                                } else {
                                    socials = ClientContext.socialRegistry;
                                    socials.addFriend(name);
                                }
                                Object[] addedFormatArguments = new Object[2];
                                addedFormatArguments[0] = name;
                                addedFormatArguments[1] = this.pluralRoleName();
                                String addedText = Translations.format("commands.EnemyFriend.AddedSingle", addedFormatArguments);
                                Component addedMessage = Component.nullToEmpty(addedText);
                                ChatUtils.printChat(addedMessage, ChatUtils.createSignature(-1));
                            } else {
                                Object[] existingFormatArguments = new Object[2];
                                existingFormatArguments[0] = name;
                                existingFormatArguments[1] = this.pluralRoleName();
                                String existingText = Translations.format("commands.EnemyFriend.ExistingSingle", existingFormatArguments);
                                Component existingMessage = Component.nullToEmpty(existingText);
                                ChatUtils.printChat(existingMessage, ChatUtils.createSignature(-1));
                            }
                        }
                    }
                    return 1;
                });
        ArgumentBuilder addNamesCommand = Command.argument("names", new ArrayArgumentType(new PlayerNameArgumentType()))
                .executes((com.mojang.brigadier.Command<Object>) context -> {
                    String[] names = context.getArgument("names", String[].class);
                    ArrayList<String> addedNames = new ArrayList<>();
                    ArrayList<String> existingNames = new ArrayList<>();
                    for (String name : names) {
                        SocialRegistry socials = ClientContext.socialRegistry;
                        if (socials.getRole(name) == this.role) {
                            existingNames.add(name);
                        } else {
                            if (this.role == SocialRole.ENEMY) {
                                socials = ClientContext.socialRegistry;
                                socials.addEnemy(name);
                            } else {
                                socials = ClientContext.socialRegistry;
                                socials.addFriend(name);
                            }
                            addedNames.add(name);
                        }
                    }
                    int messageId = -1;
                    if (!addedNames.isEmpty()) {
                        MutableComponent addedList = this.formatNames(addedNames);
                        String addedSuffixTemplate = addedNames.size() == 1
                                ? Translations.ui("commands.EnemyFriend.AddedSingleSuffix")
                                : Translations.ui("commands.EnemyFriend.AddedPluralSuffix");
                        String addedSuffix = this.formatRoleTemplate(addedSuffixTemplate);
                        MutableComponent addedMessage = addedList.append(addedSuffix);
                        int addedMessageId = messageId;
                        ChatUtils.printChat(addedMessage, ChatUtils.createSignature(addedMessageId));
                        messageId--;
                    }
                    if (!existingNames.isEmpty()) {
                        MutableComponent existingList = this.formatNames(existingNames);
                        String existingSuffixTemplate = existingNames.size() == 1
                                ? Translations.ui("commands.EnemyFriend.ExistingSingleSuffix")
                                : Translations.ui("commands.EnemyFriend.ExistingPluralSuffix");
                        String existingSuffix = this.formatRoleTemplate(existingSuffixTemplate);
                        MutableComponent existingMessage = existingList.append(existingSuffix);
                        int existingMessageId = messageId;
                        ChatUtils.printChat(existingMessage, ChatUtils.createSignature(existingMessageId));
                    }
                    return 1;
                });
        ArgumentBuilder addBranch = commandBuilder.then(addNamesCommand);
        root.then(addBranch);
        String[] aliases = new String[3];
        aliases[0] = "remove";
        aliases[1] = "delete";
        aliases[(2)] = "del";
        RequiredArgumentBuilder removeBuilder = (RequiredArgumentBuilder) Command.aliasedLiteral(aliases).executes((com.mojang.brigadier.Command<Object>) context -> {
            if (ClientAccess.evernightMinecraft.hitResult instanceof EntityHitResult hit) {
                if (hit.getEntity() instanceof Player player) {
                    GameProfile profile = player.getGameProfile();
                    String name = profile.name();
                    SocialRegistry socials = ClientContext.socialRegistry;
                    if (socials.getRole(name) == this.role) {
                        socials = ClientContext.socialRegistry;
                        socials.removeSocial(name);
                        Object[] removedFormatArguments = new Object[2];
                        removedFormatArguments[0] = name;
                        removedFormatArguments[1] = this.pluralRoleName();
                        String removedText = Translations.format("commands.EnemyFriend.RemovedSingle", removedFormatArguments);
                        Component removedMessage = Component.nullToEmpty(removedText);
                        ChatUtils.printChat(removedMessage, ChatUtils.createSignature(-1));
                    } else {
                        Object[] missingFormatArguments = new Object[2];
                        missingFormatArguments[0] = name;
                        missingFormatArguments[1] = this.pluralRoleName();
                        String missingText = Translations.format("commands.EnemyFriend.MissingSingle", missingFormatArguments);
                        Component missingMessage = Component.nullToEmpty(missingText);
                        ChatUtils.printChat(missingMessage, ChatUtils.createSignature(-1));
                    }
                }
            }
            return 1;
        });
        ArgumentBuilder removeNamesCommand = Command.argument("names", StringArgumentType.greedyString())
                .suggests((com.mojang.brigadier.suggestion.SuggestionProvider<Object>) this::suggestRegisteredNames)
                .executes((com.mojang.brigadier.Command<Object>) context -> {
                    String capturedInput = context.getArgument("names", String.class);
                    String[] names = capturedInput.split(" ");
                    ArrayList<String> removedNames = new ArrayList<>();
                    ArrayList<String> missingNames = new ArrayList<>();
                    for (String name : names) {
                        SocialRegistry socials = ClientContext.socialRegistry;
                        if (socials.getRole(name) == this.role) {
                            SocialRegistry capturedRemover = ClientContext.socialRegistry;
                            capturedRemover.removeSocial(name);
                            removedNames.add(name);
                        } else {
                            missingNames.add(name);
                        }
                    }
                    int messageId = -1;
                    if (!removedNames.isEmpty()) {
                        MutableComponent removedList = this.formatNames(removedNames);
                        String removedTemplate = Translations.ui("commands.EnemyFriend.RemovedSuffix");
                        String removedSuffix = this.formatRoleTemplate(removedTemplate);
                        MutableComponent removedMessage = removedList.append(removedSuffix);
                        int removedMessageId = messageId;
                        ChatUtils.printChat(removedMessage, ChatUtils.createSignature(removedMessageId));
                        messageId--;
                    }
                    if (!missingNames.isEmpty()) {
                        MutableComponent missingList = this.formatNames(missingNames);
                        String missingSuffixTemplate = missingNames.size() == 1
                                ? Translations.ui("commands.EnemyFriend.MissingSingleSuffix")
                                : Translations.ui("commands.EnemyFriend.MissingPluralSuffix");
                        String missingSuffix = this.formatRoleTemplate(missingSuffixTemplate);
                        MutableComponent missingMessage = missingList.append(missingSuffix);
                        int missingMessageId = messageId;
                        ChatUtils.printChat(missingMessage, ChatUtils.createSignature(missingMessageId));
                    }
                    return 1;
                });
        ArgumentBuilder removeBranch = removeBuilder.then(removeNamesCommand);
        root.then(removeBranch);
        ArgumentBuilder listCommand = Command.literal("list")
                .executes((com.mojang.brigadier.Command<Object>) context -> {
                    SocialRegistry socials = ClientContext.socialRegistry;
                    List<Component> nameComponents = socials.getRegistry().stream().filter(entry -> {
                        return entry.role() == this.role;
                    }).map(entry -> {
                        String name = entry.getName();
                        return Component.nullToEmpty(name);
                    }).toList();
                    String heading = this.formatRoleTemplate(Translations.ui("commands.EnemyFriend.ListHeader"));
                    MutableComponent message = Component.literal(heading);
                    message = message.append(ComponentUtils.formatList(nameComponents, Component.nullToEmpty(Translations.ui("commands.EnemyFriend.ListSeparator"))));
                    byte messageId = -1;
                    ChatUtils.printChat(message, ChatUtils.createSignature(messageId));
                    return 1;
                });
        root.then(listCommand);
        if (this.role != SocialRole.ENEMY) {
            commandBuilder = Command.literal("sync");
            ArgumentBuilder importerCommand = Command.argument("importer", new EnumArgumentType(FriendImportFormat.class, "importer"))
                    .executes((com.mojang.brigadier.Command<Object>) context -> {
                        FriendImportFormat importer = context.getArgument("importer", FriendImportFormat.class);
                        List<String> importedNames;
                        try {
                            importedNames = importer.readFriendNames();
                        } catch (Exception failure) {
                            if (failure instanceof FileNotFoundException) {
                                String missingFileText = Translations.format("commands.EnemyFriend.FriendsFileMissing", importer.getDisplayName());
                                MutableComponent missingFileMessage = Component.literal(missingFileText)
                                        .withStyle(style -> {
                                            ChatFormatting red = ChatFormatting.RED;
                                            return style.withColor(red);
                                        });
                                ChatUtils.printChat(
                                        missingFileMessage, ChatUtils.createSignature(-1), ChatUtils.MessagePriority.LOW);
                            } else {
                                LOGGER.warn("Could not import friend names", failure);
                                Object[] failureFormatArguments = new Object[1];
                                failureFormatArguments[0] = importer.getDisplayName();
                                String failureText = Translations.format("commands.EnemyFriend.ImportFailed", failureFormatArguments);
                                MutableComponent failureMessage = Component.literal(failureText)
                                        .withStyle(style -> {
                                            ChatFormatting red = ChatFormatting.RED;
                                            return style.withColor(red);
                                        });
                                ChatUtils.printChat(
                                        failureMessage, ChatUtils.createSignature(-1), ChatUtils.MessagePriority.LOW);
                            }
                            return 1;
                        }
                        int addedCount = 0;
                        Iterator<String> namesIterator = importedNames.iterator();
                        while (namesIterator.hasNext()) {
                            String name = namesIterator.next();
                            SocialRegistry socials = ClientContext.socialRegistry;
                            if (!socials.isFriendName(name)) {
                                socials = ClientContext.socialRegistry;
                                socials.addFriend(name);
                                addedCount++;
                            }
                        }
                        Object[] completionFormatArguments = new Object[2];
                        completionFormatArguments[0] = addedCount;
                        completionFormatArguments[1] = importer.getDisplayName();
                        String completionText = Translations.format("commands.EnemyFriend.Imported", completionFormatArguments);
                        MutableComponent completionMessage = Component.literal(completionText);
                        ChatUtils.printChat(completionMessage, ChatUtils.createSignature(-1));
                        return 1;
                    });
            ArgumentBuilder syncBranch = commandBuilder.then(importerCommand);
            root.then(syncBranch);
        }
    }

    private String pluralRoleName() {
        if (this.role == SocialRole.ENEMY) {
            return Translations.ui("commands.EnemyFriend.RoleEnemies");
        }
        return Translations.ui("commands.EnemyFriend.RoleFriends");
    }

    private String formatRoleTemplate(String template) {
        String roleName = this.pluralRoleName();
        String lowercaseFormatted = template.replace("%s", roleName);
        return lowercaseFormatted.replace("%S", FontRenderer.capitalize(roleName));
    }

    private MutableComponent formatNames(Collection<String> names) {
        List<MutableComponent> nameComponents = names.stream().map(this::formatText).toList();
        return (MutableComponent) ComponentUtils.formatList(nameComponents, Component.nullToEmpty(Translations.ui("commands.EnemyFriend.ListSeparator")));
    }

    private CompletableFuture<Suggestions> suggestRegisteredNames(
            CommandContext unusedContext, SuggestionsBuilder builder) {
        int offset;
        findSuggestionOffset:
        {
            String fullInput = builder.getInput();
            offset = fullInput.lastIndexOf((" "));
            if (offset != -1) {
                String capturedInput = builder.getInput();
                if (offset != capturedInput.length()) {
                    offset++;
                    break findSuggestionOffset;
                }
            }
            offset = builder.getStart();
        }
        SuggestionsBuilder offsetBuilder = builder.createOffset(offset);
        SocialRegistry socials = ClientContext.socialRegistry;
        Stream<String> registeredNames = socials.getRegistry().stream().filter(entry -> {
            return entry.role() == this.role;
        }).map(NamedEntry::getName);
        return SharedSuggestionProvider.suggest(registeredNames, offsetBuilder);
    }
}