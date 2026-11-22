package evernight.client.commands.arguments;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import evernight.client.render.color.ColorUtils;
import evernight.client.render.text.Translations;

import java.awt.*;

public class HexColorArgument implements ArgumentType {
    @Override
    public Color parse(StringReader reader) throws CommandSyntaxException {
        try {
            if (reader.canRead() && reader.peek() == '#') reader.skip();
            String text = reader.readString();
            return text.isEmpty() ? Color.WHITE : ColorUtils.parseHexColor(text);
        } catch (IllegalArgumentException failure) {
            throw new SimpleCommandExceptionType(new LiteralMessage(Translations.ui("commands.Arguments.InvalidColor"))).createWithContext(reader);
        }
    }
}