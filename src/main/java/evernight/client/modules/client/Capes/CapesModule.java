package evernight.client.modules.client.Capes;

import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.core.NamedEntry;
import net.minecraft.core.ClientAsset.ResourceTexture;
import net.minecraft.core.ClientAsset.Texture;
import net.minecraft.resources.Identifier;

public final class CapesModule extends Module {
    private final EnumSetting<Cape> capeSetting;

    public CapesModule() {
        super("Capes", ModuleCategory.CLIENT);
        this.capeSetting = this.add(new EnumSetting<>("Cape", Cape.EVERNIGHT));
    }

    public Texture getCapeTexture() {
        return this.capeSetting.getValue().texture;
    }

    /**
     * Cape textures use the vanilla 64x32 canvas (a 2:1 image): the cape islands sit in the top-left
     * 22x18 texels and the elytra wing islands in the 22..46 x 0..22 texels, which the client falls
     * back to whenever a player wears an elytra without a dedicated elytra texture.
     */
    private enum Cape implements NamedEntry {
        EVERNIGHT("Evernight", "evernight.png"),
        DEEPSEEK("DeepSeek", "deepseek.png"),
        DEEPSEEK_CHAN("DeepSeekChan", "deepseekchan.png");

        private final String name;
        private final ResourceTexture texture;

        Cape(String name, String fileName) {
            this.name = name;
            Identifier textureId = Identifier.fromNamespaceAndPath("evernight", "capes/" + fileName);
            this.texture = new ResourceTexture(textureId, textureId);
        }

        @Override
        public String getName() {
            return this.name;
        }
    }
}
