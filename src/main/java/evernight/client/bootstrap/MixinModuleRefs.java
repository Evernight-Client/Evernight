package evernight.client.bootstrap;

import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.client.UI.UIModule;
import evernight.client.modules.combat.Arrows.ArrowsModule;
import evernight.client.modules.auto.AutoClicker.AutoClickerModule;
import evernight.client.modules.combat.AutoCrystal.AutoCrystalModule;
import evernight.client.modules.combat.NoHitDelay.NoHitDelayModule;
import evernight.client.modules.exploit.MultiTask.MultiTaskModule;
import evernight.client.modules.exploit.Reach.ReachModule;
import evernight.client.modules.exploit.Trident.TridentModule;
import evernight.client.modules.misc.AntiQuit.AntiQuitModule;
import evernight.client.modules.movement.ElytraFly.ElytraFlyModule;
import evernight.client.modules.movement.EntityControl.EntityControlModule;
import evernight.client.modules.movement.FastClimb.FastClimbModule;
import evernight.client.modules.movement.FastWeb.FastWebModule;
import evernight.client.modules.movement.HighJump.HighJumpModule;
import evernight.client.modules.movement.Jesus.JesusModule;
import evernight.client.modules.movement.NoJumpDelay.NoJumpDelayModule;
import evernight.client.modules.movement.NoSlow.NoSlowModule;
import evernight.client.modules.movement.Sprint.SprintModule;
import evernight.client.modules.movement.Velocity.VelocityModule;
import evernight.client.modules.auto.AutoEat.AutoEatModule;
import evernight.client.modules.player.Freecam.FreecamModule;
import evernight.client.modules.player.Scaffold.ScaffoldModule;
import evernight.client.modules.render.Ambience.AmbienceModule;
import evernight.client.modules.render.Chams.ChamsModule;
import evernight.client.modules.render.FreeLook.FreeLookModule;
import evernight.client.modules.render.Glint.GlintModule;
import evernight.client.modules.render.NoRender.NoRenderModule;
import evernight.client.modules.render.Tooltips.TooltipsModule;
import evernight.client.modules.render.ViewModel.ViewModelModule;

/**
 * Holds the module references used by mixins whose targets are loaded before the
 * module registry is published (for example {@code Block}, {@code Entity},
 * {@code Level} and {@code Minecraft}).
 *
 * <p>Mixin static fields are merged into the target class initialiser, which may run
 * while {@link evernight.client.ClientContext#moduleRegistry} is still null, so those
 * mixins must not resolve modules themselves. {@link ModuleDependencies} calls
 * {@link #bind(ModuleRegistry)} once the registry is ready.</p>
 */
public final class MixinModuleRefs {
    public static AmbienceModule ambience;
    public static AntiQuitModule antiQuit;
    public static ArrowsModule arrows;
    public static AutoClickerModule autoClicker;
    public static AutoCrystalModule autoCrystal;
    public static AutoEatModule autoEat;
    public static ChamsModule chams;
    public static ElytraFlyModule elytraFly;
    public static EntityControlModule entityControl;
    public static FastClimbModule fastClimb;
    public static FastWebModule fastWeb;
    public static FreecamModule freecam;
    public static FreeLookModule freeLook;
    public static GlintModule glint;
    public static HighJumpModule highJump;
    public static JesusModule jesus;
    public static MultiTaskModule multiTask;
    public static NoHitDelayModule noHitDelay;
    public static NoJumpDelayModule noJumpDelay;
    public static NoRenderModule noRender;
    public static NoSlowModule noSlow;
    public static ReachModule reach;
    public static ScaffoldModule scaffold;
    public static SprintModule sprint;
    public static TooltipsModule tooltips;
    public static TridentModule trident;
    public static UIModule ui;
    public static VelocityModule velocity;
    public static ViewModelModule viewModel;

    private MixinModuleRefs() {
    }

    /**
     * Publishes the module instances used by the early-loaded mixins.
     */
    public static void bind(ModuleRegistry registry) {
        ambience = registry.getModule(AmbienceModule.class);
        antiQuit = registry.getModule(AntiQuitModule.class);
        arrows = registry.getModule(ArrowsModule.class);
        autoClicker = registry.getModule(AutoClickerModule.class);
        autoCrystal = registry.getModule(AutoCrystalModule.class);
        autoEat = registry.getModule(AutoEatModule.class);
        chams = registry.getModule(ChamsModule.class);
        elytraFly = registry.getModule(ElytraFlyModule.class);
        entityControl = registry.getModule(EntityControlModule.class);
        fastClimb = registry.getModule(FastClimbModule.class);
        fastWeb = registry.getModule(FastWebModule.class);
        freecam = registry.getModule(FreecamModule.class);
        freeLook = registry.getModule(FreeLookModule.class);
        glint = registry.getModule(GlintModule.class);
        highJump = registry.getModule(HighJumpModule.class);
        jesus = registry.getModule(JesusModule.class);
        multiTask = registry.getModule(MultiTaskModule.class);
        noHitDelay = registry.getModule(NoHitDelayModule.class);
        noJumpDelay = registry.getModule(NoJumpDelayModule.class);
        noRender = registry.getModule(NoRenderModule.class);
        noSlow = registry.getModule(NoSlowModule.class);
        reach = registry.getModule(ReachModule.class);
        scaffold = registry.getModule(ScaffoldModule.class);
        sprint = registry.getModule(SprintModule.class);
        tooltips = registry.getModule(TooltipsModule.class);
        trident = registry.getModule(TridentModule.class);
        ui = registry.getModule(UIModule.class);
        velocity = registry.getModule(VelocityModule.class);
        viewModel = registry.getModule(ViewModelModule.class);
    }
}
