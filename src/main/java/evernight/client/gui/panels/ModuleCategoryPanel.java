package evernight.client.gui.panels;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.NamedFeature;
import evernight.client.gui.core.GuiElement;
import evernight.client.gui.components.ModuleComponent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.font.TextUtils;
import net.minecraft.client.gui.components.EditBox;

import java.util.Comparator;
import java.util.List;

public class ModuleCategoryPanel extends Panel implements ClientAccess {
    public ModuleCategory category;

    public ModuleCategoryPanel(ModuleCategory category) {
        // The stable English name is kept as the panel identifier; the localised label is resolved
        // by getTitle() on every render so language changes apply immediately.
        super(category.getName());
        this.category = category;
        this.populateModules();
    }

    private void populateModules() {
        List<Module> modules = ClientContext.moduleRegistry.getRegistry();
        modules.stream().filter(module -> module.getCategory() == this.category)
                .sorted(Comparator.comparing(NamedFeature::getName)).forEach(module -> {
                    ModuleComponent component = new ModuleComponent(module, this, 0);
                    this.children.add(component);
                });
    }

    @Override
    public String getTitle() {
        return this.category.getDisplayName();
    }

    @Override
    public void init() {
        this.updateLayout();
        super.init();
    }

    @Override
    protected boolean isChildVisible(GuiElement child) {
        EditBox searchBox = ClientContext.getClickGui().getSearchBox();
        if (searchBox == null) {
            return true;
        }
        if (child instanceof ModuleComponent moduleComponent && !searchBox.getValue().isEmpty()) {
            String query = TextUtils.normalizeKeyboardLayout(searchBox.getValue());
            Module module = moduleComponent.getModule();
            for (String alias : module.getAliases()) {
                if (TextUtils.containsIgnoreCase(alias, query)) {
                    return true;
                }
            }
            return TextUtils.containsIgnoreCase(module.getDisplayName(), query);
        } else {
            return true;
        }
    }
}