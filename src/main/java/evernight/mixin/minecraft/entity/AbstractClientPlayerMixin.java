package evernight.mixin.minecraft.entity;

import com.mojang.authlib.GameProfile;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.player.AbstractClientPlayerTickEvent;
import evernight.client.modules.client.Capes.CapesModule;
import evernight.client.modules.player.NameProtect.NameProtectModule;
import evernight.client.render.CapeEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.ClientAsset.ResourceTexture;
import net.minecraft.core.ClientAsset.Texture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin extends Player {
    private static final Identifier protectedSkinTexture = Identifier.fromNamespaceAndPath(
            "evernight-mount", "textures/skin_protect.png"
    );
    private static final NameProtectModule nameProtectModule = ClientContext.getModule(NameProtectModule.class);
    @Unique
    private static final List<CapeEntry> playerCapes = List.of(
            new CapeEntry("cattyyyn", "evernight.png"),
            new CapeEntry("u3o", "deepseek.png"),
            new CapeEntry("asphyxia1337", "deepseekchan.png")
    );

    public AbstractClientPlayerMixin(Level level, GameProfile profile) {
        super(level, profile);
    }

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void overridePlayerSkin(CallbackInfoReturnable<PlayerSkin> callback) {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        PlayerSkin originalSkin = callback.getReturnValue();
        Texture bodyTexture = originalSkin.body();
        Texture capeTexture = originalSkin.cape();
        PlayerModelType modelType = originalSkin.model();
        String playerName = this.getGameProfile().name();
        if (localPlayer != null && nameProtectModule.isToggled() && nameProtectModule.skinSetting.getValue()
                && localPlayer.getGameProfile().name().equals(playerName)) {
            bodyTexture = new ResourceTexture(protectedSkinTexture, protectedSkinTexture);
            modelType = nameProtectModule.slimSetting.getValue() ? PlayerModelType.SLIM : PlayerModelType.WIDE;
        }
        CapesModule capesModule = ClientAccess.isLocalPlayer(this) ? ClientContext.getModule(CapesModule.class) : null;
        if (capesModule != null && capesModule.isToggled()) {
            capeTexture = capesModule.getCapeTexture();
        } else {
            for (CapeEntry candidate : playerCapes) {
                if (candidate.playerName().equals(playerName)) {
                    Identifier textureId = candidate.getTexture();
                    capeTexture = new ResourceTexture(textureId, textureId);
                    break;
                }
            }
        }
        if (bodyTexture != originalSkin.body() || capeTexture != originalSkin.cape()) {
            callback.setReturnValue(new PlayerSkin(bodyTexture, capeTexture, originalSkin.elytra(), modelType, originalSkin.secure()));
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void postLocalPlayerTickEvent(CallbackInfo callback) {
        if (ClientAccess.isLocalPlayer(this)) {
            ClientAccess.eventBus.post(new AbstractClientPlayerTickEvent());
        }
    }
}