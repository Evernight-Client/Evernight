package evernight.client.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.Collection;
import java.util.List;
import evernight.client.commands.CommandStringReader;

public class FancyStringArgument implements ArgumentType<String> {
    private static final List<String> EXAMPLES = List.of("words_with_underscores_any_language", "слова_на_любом_языке");

    public static FancyStringArgument fancyString() {
        return new FancyStringArgument();
    }

    public String description() {
        return "fancyString()";
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        return CommandStringReader.readString(reader);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}