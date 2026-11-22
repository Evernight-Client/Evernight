package evernight.client.utils.inventory;

import evernight.client.ClientAccess;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection.CraftableStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recipe book driven crafting for the auto miner.
 *
 * <p>Instead of clicking ingredients into the crafting grid by hand this delegates to
 * {@link MultiPlayerGameMode#handlePlaceRecipe}: the server moves the ingredients from the player
 * inventory into the grid, exactly like the vanilla recipe book does. The caller then only has to
 * click the result slot. This is the same mechanism {@code AutoCraftModule} uses.
 *
 * <p>Recipes are looked up through the client recipe book, so the recipe has to be unlocked on the
 * server; when it is not, {@link #findRecipe} returns null and the module degrades gracefully.
 */
public final class AutoMiningCrafting implements ClientAccess {
    private static final Map<Item, RecipeDisplayEntry> RECIPE_CACHE = new HashMap<>();

    private AutoMiningCrafting() {
        throw new IllegalArgumentException("Illegal constructor call");
    }

    /**
     * Drops the cached recipe lookups, e.g. after the recipe book was re-synced.
     */
    public static void invalidateCache() {
        RECIPE_CACHE.clear();
    }

    /**
     * @return the recipe display entry producing the given item, or null when the recipe is not
     * unlocked
     */
    public static RecipeDisplayEntry findRecipe(Item output) {
        RecipeDisplayEntry cached = RECIPE_CACHE.get(output);
        if (cached != null) {
            return cached;
        }
        LocalPlayer player = evernightMinecraft.player;
        ClientLevel level = evernightMinecraft.level;
        if (player == null || level == null) {
            return null;
        }
        ContextMap displayContext = SlotDisplayContext.fromLevel(level);
        ClientRecipeBook recipeBook = player.getRecipeBook();
        for (RecipeCollection collection : recipeBook.getCollections()) {
            List<RecipeDisplayEntry> recipes = collection.getSelectedRecipes(CraftableStatus.ANY);
            for (RecipeDisplayEntry recipe : recipes) {
                RecipeDisplay display = recipe.display();
                ItemStack result = display.result().resolveForFirstStack(displayContext);
                if (!result.isEmpty() && result.is(output)) {
                    RECIPE_CACHE.put(output, recipe);
                    return recipe;
                }
            }
        }
        return null;
    }

    /**
     * Asks the server to fill the crafting grid for the recipe.
     *
     * @return whether the request was sent
     */
    public static boolean placeRecipe(AbstractContainerMenu menu, RecipeDisplayEntry recipe, boolean placeAll) {
        MultiPlayerGameMode gameMode = evernightMinecraft.gameMode;
        if (gameMode == null || menu == null || recipe == null) {
            return false;
        }
        gameMode.handlePlaceRecipe(menu.containerId, recipe.id(), placeAll);
        return true;
    }
}
