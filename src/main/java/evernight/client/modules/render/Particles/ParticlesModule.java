package evernight.client.modules.render.Particles;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.render.ParticleEngineAddEvent;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.client.Colors.ColorsModule;
import evernight.client.render.color.ColorUtils;
import evernight.client.render.util.ParticleInitialAlpha;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.math.MathConstants;
import net.minecraft.client.particle.*;
import net.minecraft.client.particle.FireworkParticles.SparkParticle;
import net.minecraft.client.particle.FireworkParticles.Starter;
import net.minecraft.client.player.LocalPlayer;

import java.awt.*;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class ParticlesModule extends Module {
    private static ColorsModule colors;

    static {
        colors = ClientContext.getModule(ColorsModule.class);
    }

    public final Setting<Boolean> totemsSetting;
    public final Setting<Float> totemsScaleSetting;
    public final Setting<Float> totemsVelocitySetting;
    public final Setting<Color> totemsColor1Setting;
    public final Setting<Color> totemsColor2Setting;
    public final Setting<Boolean> ignoreSelfSetting;
    public final Setting<Boolean> rocketsSetting;
    public final Setting<Float> rocketScaleSetting;
    public final Setting<Color> rocketColorSetting;
    public final Setting<Boolean> damageSetting;
    public final Setting<Float> damageScaleSetting;
    public final Setting<Float> damageVelocitySetting;
    public final Setting<Color> damageColorSetting;
    public final Setting<Boolean> portalSetting;
    public final Setting<Float> portalScaleSetting;
    public final Setting<Color> portalColorSetting;
    public final Setting<Boolean> dustSetting;
    public final Setting<Color> fromSetting;
    public final Setting<Color> toSetting;
    private final Random random = new Random();

    public ParticlesModule() {
        super("Particles", ModuleCategory.RENDER);
        this.totemsSetting = this.add(new BooleanSetting("Totems", false).markVisibilityDependency());
        this.totemsScaleSetting = this.add(new NumberSetting<>("TotemsScale", 1.0F, 0.0F, 2.0F).dependsOn(this.totemsSetting));
        this.totemsVelocitySetting = this.add(new NumberSetting<>("TotemsVelocity", 1.0F, 0.0F, 5.0F).dependsOn(this.totemsSetting));
        this.totemsColor1Setting = this.add(new ColorSetting("TotemsColor1", new Color(-4351489, true)).dependsOn(this.totemsSetting));
        this.totemsColor2Setting = this.add(new ColorSetting("TotemsColor2", new Color(-9286761, true)).dependsOn(this.totemsSetting));
        this.ignoreSelfSetting = this.add(new BooleanSetting("IgnoreSelf", false).dependsOn(this.totemsSetting));
        this.rocketsSetting = this.add(new BooleanSetting("Rockets", false).markVisibilityDependency());
        this.rocketScaleSetting = this.add(new NumberSetting<>("RocketScale", 1.0F, 0.0F, 2.0F).dependsOn(this.rocketsSetting));
        this.rocketColorSetting = this.add(new ColorSetting("RocketColor", new Color(-4351489, true)).dependsOn(this.rocketsSetting));
        this.damageSetting = this.add(new BooleanSetting("Damage", false).markVisibilityDependency());
        this.damageScaleSetting = this.add(new NumberSetting<>("DamageScale", 1.0F, 0.0F, 2.0F).dependsOn(this.damageSetting));
        this.damageVelocitySetting = this.add(new NumberSetting<>("DamageVelocity", 1.0F, 0.0F, 5.0F).dependsOn(this.damageSetting));
        this.damageColorSetting = this.add(new ColorSetting("DamageColor", new Color(-4351489, true)).dependsOn(this.damageSetting));
        this.portalSetting = this.add(new BooleanSetting("Portal", false).markVisibilityDependency());
        this.portalScaleSetting = this.add(new NumberSetting<>("PortalScale", 1.0F, 0.0F, 2.0F).dependsOn(this.portalSetting));
        this.portalColorSetting = this.add(new ColorSetting("PortalColor", new Color(-4351489, true)).dependsOn(this.portalSetting));
        this.dustSetting = this.add(new BooleanSetting("Dust", false).markVisibilityDependency());
        this.fromSetting = this.add(new ColorSetting("From", new Color(-4351489, true)).dependsOn(this.dustSetting));
        this.toSetting = this.add(new ColorSetting("To", new Color(-4351489, true)).dependsOn(this.dustSetting));
        this.setDrawn(false);
    }

    public static void bindColorsModule(ModuleRegistry registry) {
        colors = registry.getModule(ColorsModule.class);
    }

    private void setParticleColor(SingleQuadParticle particle, int argb) {
        float alpha = ((argb >>> 24) & 0xFF) / 255.0F;
        float red = ((argb >>> 16) & 0xFF) / 255.0F;
        float green = ((argb >>> 8) & 0xFF) / 255.0F;
        float blue = (argb & 0xFF) / 255.0F;
        particle.setColor(red, green, blue);
        ((ParticleInitialAlpha) particle).evernight$setInitialAlpha(alpha);
    }

    @EventHandler
    private void onParticleEngineAddEvent(ParticleEngineAddEvent event) {
        if (event.getParticle() instanceof TotemParticle totem) {
            if (this.ignoreSelfSetting.getValue()) {
                LocalPlayer player = ClientAccess.evernightMinecraft.player;
                if (player.getBoundingBox().intersects(totem.getBoundingBox())) {
                    event.cancel();
                }
            }
            if (!this.totemsSetting.getValue()) {
                return;
            }
            Setting<Color> colorSetting = this.random.nextInt(4) == 0 ? this.totemsColor1Setting
                    : this.totemsColor2Setting;
            this.setParticleColor(totem, this.getParticleColor(colorSetting));
            totem.scale(this.totemsScaleSetting.getValue());
            totem.setPower(this.totemsVelocitySetting.getValue());
        }
        if (this.rocketsSetting.getValue()) {
            if (event.getParticle() instanceof Starter starter) {
                starter.scale(this.rocketScaleSetting.getValue());
            }
            if (event.getParticle() instanceof SparkParticle spark) {
                this.setParticleColor(spark, this.getParticleColor(this.rocketColorSetting));
                spark.scale(this.rocketScaleSetting.getValue());
            }
        }
        if (this.damageSetting.getValue() && event.getParticle() instanceof CritParticle damageParticle) {
            this.setParticleColor(damageParticle, (this.damageColorSetting.getValue()).getRGB());
            damageParticle.scale(this.damageScaleSetting.getValue());
            damageParticle.setPower(this.damageVelocitySetting.getValue());
        }
        if (this.portalSetting.getValue() && event.getParticle() instanceof PortalParticle portalParticle) {
            this.setParticleColor(portalParticle, (this.portalColorSetting.getValue()).getRGB());
            portalParticle.scale(this.portalScaleSetting.getValue());
        }
        if (this.dustSetting.getValue() && event.getParticle() instanceof BaseAshSmokeParticle dustParticle) {
            if (dustParticle instanceof WhiteAshParticle) {
                Color whiteAshColor = (this.toSetting.getValue()).brighter();
                this.setParticleColor(dustParticle, whiteAshColor.getRGB());
            }
            if (dustParticle instanceof AshParticle) {
                Color from = this.fromSetting.getValue();
                Color to = this.toSetting.getValue();
                Color blended = ColorUtils.blend(from, to, ThreadLocalRandom.current().nextFloat());
                this.setParticleColor(dustParticle, blended.getRGB());
            }
        }
    }

    private int getParticleColor(Setting<Color> setting) {
        Color color = setting.getValue();
        ColorSetting colorSetting = (ColorSetting) setting;
        if (!colorSetting.isRainbow()) {
            return color.getRGB();
        }
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        int period = (int) (MathConstants.FULL_TURN_DEGREES * colors.getRainbowPeriod());
        period = Math.max(period, 1);
        int offset = this.random.nextInt(period);
        Color rainbow = ColorUtils.rainbow(period, offset, hsb[1], hsb[2], color.getAlpha());
        return rainbow.getRGB();
    }
}