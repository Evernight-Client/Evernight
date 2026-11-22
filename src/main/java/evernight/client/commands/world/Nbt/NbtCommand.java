package evernight.client.commands.world.Nbt;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import evernight.client.ClientAccess;
import evernight.client.render.text.Translations;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.commands.arguments.NbtPathArgument.NbtPath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.commands.data.DataAccessor;
import net.minecraft.server.commands.data.EntityDataAccessor;

import java.util.List;
import evernight.client.commands.Command;

public final class NbtCommand extends Command {
    public NbtCommand() {
        super("nbt");
    }

    @Override
    public void exec(LiteralArgumentBuilder builder) {
        com.mojang.brigadier.Command<Object> action = context -> {
            DataAccessor accessor = new EntityDataAccessor(ClientAccess.evernightMinecraft.player);
            NbtPath path = NbtPath.of("SelectedItem");
            MutableComponent message = Component.empty();
            message = message.append(Translations.ui("commands.Nbt.Prefix"));
            try {
                CompoundTag data = accessor.getData();
                List<Tag> values = path.get(data);
                if (!values.isEmpty()) {
                    Tag first = values.getFirst();
                    Component formatted = NbtUtils.toPrettyComponent(first);
                    message.append(formatted);
                }
            } catch (CommandSyntaxException exception) {
                message.append("{}");
            }
            MessageSignature signature = ChatUtils.createSignature(-1);
            ChatUtils.printChat(message, signature);
            return 1;
        };
        builder.executes(action);
    }
}