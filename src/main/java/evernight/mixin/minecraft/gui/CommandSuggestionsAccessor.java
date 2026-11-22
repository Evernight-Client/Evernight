package evernight.mixin.minecraft.gui;

import com.mojang.brigadier.ParseResults;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.CommandSuggestions.SuggestionsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public interface CommandSuggestionsAccessor {
    @Accessor("pendingSuggestions")
    CompletableFuture getSuggestion();

    @Accessor("suggestions")
    SuggestionsList getWindow();

    @Accessor("currentParse")
    ParseResults getParse();
}