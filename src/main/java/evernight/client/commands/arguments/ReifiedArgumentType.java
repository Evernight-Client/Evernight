package evernight.client.commands.arguments;

import com.mojang.brigadier.arguments.ArgumentType;

public interface ReifiedArgumentType extends ArgumentType {
    Class getReifiedType();
}