package evernight.client.managers.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.input.InputPressEvent;
import evernight.client.events.render.WorldRenderEvent;
import evernight.client.integrations.baritone.BaritoneBridge;
import evernight.client.modules.*;
import evernight.client.modules.Module;
import evernight.client.modules.client.Baritone.BaritoneModule;
import evernight.client.modules.movement.ObstaclePasser.ObstaclePasserModule;
import evernight.client.settings.core.JsonConfigurable;
import evernight.client.utils.core.ExperimentalFeatures;
import evernight.client.utils.core.KeyUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import evernight.client.config.legacy.LegacyBranding;
import evernight.client.modules.client.AntiCheat.AntiCheatModule;
import evernight.client.modules.client.Capes.CapesModule;
import evernight.client.modules.client.Colors.ColorsModule;
import evernight.client.modules.client.Discord.DiscordModule;
import evernight.client.modules.client.Fonts.FontsModule;
import evernight.client.modules.client.HUD.HUDModule;
import evernight.client.modules.client.Notifications.NotificationsModule;
import evernight.client.modules.client.Settings.SettingsModule;
import evernight.client.modules.client.UI.UIModule;
import evernight.client.modules.client.WaypointSettings.WaypointSettingsModule;
import evernight.client.modules.combat.AimAssist.AimAssistModule;
import evernight.client.modules.combat.AnchorAura.AnchorAuraModule;
import evernight.client.modules.combat.AntiBot.AntiBotModule;
import evernight.client.modules.combat.AntiFeetPlace.AntiFeetPlaceModule;
import evernight.client.modules.combat.AntiPearl.AntiPearlModule;
import evernight.client.modules.combat.AntiPhase.AntiPhaseModule;
import evernight.client.modules.combat.Arrows.ArrowsModule;
import evernight.client.modules.combat.Aura.AuraModule;
import evernight.client.modules.combat.AutoArmor.AutoArmorModule;
import evernight.client.modules.combat.AutoBowRelease.AutoBowReleaseModule;
import evernight.client.modules.combat.AutoClicker.AutoClickerModule;
import evernight.client.modules.combat.AutoCrystal.AutoCrystalModule;
import evernight.client.modules.combat.AutoDrop.AutoDropModule;
import evernight.client.modules.combat.AutoExp.AutoExpModule;
import evernight.client.modules.combat.AutoLog.AutoLogModule;
import evernight.client.modules.combat.AutoMace.AutoMaceModule;
import evernight.client.modules.combat.AutoMine.AutoMineModule;
import evernight.client.modules.combat.AutoPot.AutoPotModule;
import evernight.client.modules.combat.AutoTrap.AutoTrapModule;
import evernight.client.modules.combat.BedAura.BedAuraModule;
import evernight.client.modules.combat.Blocker.BlockerModule;
import evernight.client.modules.combat.BowAim.BowAimModule;
import evernight.client.modules.combat.Criticals.CriticalsModule;
import evernight.client.modules.combat.FeetPlace.FeetPlaceModule;
import evernight.client.modules.combat.FishTrap.FishTrapModule;
import evernight.client.modules.combat.HoleFill.HoleFillModule;
import evernight.client.modules.combat.KeyPearl.KeyPearlModule;
import evernight.client.modules.combat.LegacyCrystal.LegacyCrystalModule;
import evernight.client.modules.combat.Mainhand.MainhandModule;
import evernight.client.modules.combat.NoHitDelay.NoHitDelayModule;
import evernight.client.modules.combat.Offhand.OffhandModule;
import evernight.client.modules.combat.PistonCrystal.PistonCrystalModule;
import evernight.client.modules.combat.Pusher.PusherModule;
import evernight.client.modules.combat.SelfFill.SelfFillModule;
import evernight.client.modules.combat.SelfTrap.SelfTrapModule;
import evernight.client.modules.combat.SelfWeb.SelfWebModule;
import evernight.client.modules.combat.Trigger.TriggerModule;
import evernight.client.modules.combat.WebAura.WebAuraModule;
import evernight.client.modules.exploit.AntiHunger.AntiHungerModule;
import evernight.client.modules.exploit.AntiVanish.AntiVanishModule;
import evernight.client.modules.exploit.ChorusControl.ChorusControlModule;
import evernight.client.modules.exploit.FakeVanilla.FakeVanillaModule;
import evernight.client.modules.exploit.FastLatency.FastLatencyModule;
import evernight.client.modules.exploit.HitboxDesync.HitboxDesyncModule;
import evernight.client.modules.exploit.IllegalDisconnect.IllegalDisconnectModule;
import evernight.client.modules.exploit.MultiTask.MultiTaskModule;
import evernight.client.modules.exploit.NewChunks.NewChunksModule;
import evernight.client.modules.exploit.NoMineAnimation.NoMineAnimationModule;
import evernight.client.modules.exploit.Phase.PhaseModule;
import evernight.client.modules.exploit.PingSpoof.PingSpoofModule;
import evernight.client.modules.exploit.PortalGodMode.PortalGodModeModule;
import evernight.client.modules.exploit.RaytraceBypass.RaytraceBypassModule;
import evernight.client.modules.exploit.Reach.ReachModule;
import evernight.client.modules.exploit.RocketExtender.RocketExtenderModule;
import evernight.client.modules.exploit.SpearFix.SpearFixModule;
import evernight.client.modules.exploit.Timer.TimerModule;
import evernight.client.modules.exploit.Trident.TridentModule;
import evernight.client.modules.exploit.XCarry.XCarryModule;
import evernight.client.modules.misc.Announcer.AnnouncerModule;
import evernight.client.modules.misc.AntiAFK.AntiAFKModule;
import evernight.client.modules.misc.AntiAim.AntiAimModule;
import evernight.client.modules.misc.AntiCyrillic.AntiCyrillicModule;
import evernight.client.modules.misc.AntiQuit.AntiQuitModule;
import evernight.client.modules.misc.AntiSpam.AntiSpamModule;
import evernight.client.modules.misc.AutoReconnect.AutoReconnectModule;
import evernight.client.modules.misc.AutoRespawn.AutoRespawnModule;
import evernight.client.modules.misc.AutoSign.AutoSignModule;
import evernight.client.modules.misc.BetterChat.BetterChatModule;
import evernight.client.modules.misc.ChatFilter.ChatFilterModule;
import evernight.client.modules.misc.ChestSearchBar.ChestSearchBarModule;
import evernight.client.modules.misc.CoordLogger.CoordLoggerModule;
import evernight.client.modules.misc.CoordinateHider.CoordinateHiderModule;
import evernight.client.modules.misc.CustomDeathText.CustomDeathTextModule;
import evernight.client.modules.misc.ExtraScreenshot.ExtraScreenshotModule;
import evernight.client.modules.misc.ExtraTab.ExtraTabModule;
import evernight.client.modules.misc.Fakeplayer.FakeplayerModule;
import evernight.client.modules.misc.Heaven.HeavenModule;
import evernight.client.modules.misc.KillEffects.KillEffectsModule;
import evernight.client.modules.misc.MiddleClick.MiddleClickModule;
import evernight.client.modules.misc.NoNarrator.NoNarratorModule;
import evernight.client.modules.misc.NoPacketKick.NoPacketKickModule;
import evernight.client.modules.misc.NoRotate.NoRotateModule;
import evernight.client.modules.misc.SkinFlicker.SkinFlickerModule;
import evernight.client.modules.misc.SoundBlocker.SoundBlockerModule;
import evernight.client.modules.misc.Spammer.SpammerModule;
import evernight.client.modules.misc.StashFinder.StashFinderModule;
import evernight.client.modules.misc.VisualRange.VisualRangeModule;
import evernight.client.modules.misc.WebhookNotifs.WebhookNotifsModule;
import evernight.client.modules.misc.ZenithProxy.ZenithProxyModule;
import evernight.client.modules.movement.AutoWalk.AutoWalkModule;
import evernight.client.modules.movement.Avoid.AvoidModule;
import evernight.client.modules.movement.ElytraFly.ElytraFlyModule;
import evernight.client.modules.movement.EntityControl.EntityControlModule;
import evernight.client.modules.movement.FakeLag.FakeLagModule;
import evernight.client.modules.movement.FastClimb.FastClimbModule;
import evernight.client.modules.movement.FastFall.FastFallModule;
import evernight.client.modules.movement.FastStairs.FastStairsModule;
import evernight.client.modules.movement.FastSwim.FastSwimModule;
import evernight.client.modules.movement.FastWeb.FastWebModule;
import evernight.client.modules.movement.Fireworks.FireworksModule;
import evernight.client.modules.movement.Flight.FlightModule;
import evernight.client.modules.movement.HighJump.HighJumpModule;
import evernight.client.modules.movement.HoleSnap.HoleSnapModule;
import evernight.client.modules.movement.Jesus.JesusModule;
import evernight.client.modules.movement.LongJump.LongJumpModule;
import evernight.client.modules.movement.NoFall.NoFallModule;
import evernight.client.modules.movement.NoJumpDelay.NoJumpDelayModule;
import evernight.client.modules.movement.NoSlow.NoSlowModule;
import evernight.client.modules.movement.SafeWalk.SafeWalkModule;
import evernight.client.modules.movement.Speed.SpeedModule;
import evernight.client.modules.movement.Sprint.SprintModule;
import evernight.client.modules.movement.Step.StepModule;
import evernight.client.modules.movement.Velocity.VelocityModule;
import evernight.client.modules.movement.Warp.WarpModule;
import evernight.client.modules.player.AutoBreed.AutoBreedModule;
import evernight.client.modules.player.AutoCraft.AutoCraftModule;
import evernight.client.modules.player.AutoEat.AutoEatModule;
import evernight.client.modules.player.AutoFarm.AutoFarmModule;
import evernight.client.modules.player.AutoFish.AutoFishModule;
import evernight.client.modules.player.AutoGrindstone.AutoGrindstoneModule;
import evernight.client.modules.player.AutoMining.AutoMiningModule;
import evernight.client.modules.player.AutoMount.AutoMountModule;
import evernight.client.modules.player.AutoNameTag.AutoNameTagModule;
import evernight.client.modules.player.AutoSand.AutoSandModule;
import evernight.client.modules.player.AutoTame.AutoTameModule;
import evernight.client.modules.player.AutoTemplate.AutoTemplateModule;
import evernight.client.modules.player.AutoTool.AutoToolModule;
import evernight.client.modules.player.AutoTrade.AutoTradeModule;
import evernight.client.modules.player.AutoTransport.AutoTransportModule;
import evernight.client.modules.player.ChestStealer.ChestStealerModule;
import evernight.client.modules.player.FastPlace.FastPlaceModule;
import evernight.client.modules.player.Freecam.FreecamModule;
import evernight.client.modules.player.InventoryCleaner.InventoryCleanerModule;
import evernight.client.modules.player.InventorySorter.InventorySorterModule;
import evernight.client.modules.player.InventoryTweaks.InventoryTweaksModule;
import evernight.client.modules.player.ItemSaver.ItemSaverModule;
import evernight.client.modules.player.NameProtect.NameProtectModule;
import evernight.client.modules.player.NoEffects.NoEffectsModule;
import evernight.client.modules.player.NoGlitchBlocks.NoGlitchBlocksModule;
import evernight.client.modules.player.NoInteract.NoInteractModule;
import evernight.client.modules.player.Nuker.NukerModule;
import evernight.client.modules.player.Replenish.ReplenishModule;
import evernight.client.modules.player.RotationLock.RotationLockModule;
import evernight.client.modules.player.Scaffold.ScaffoldModule;
import evernight.client.modules.player.SourceFill.SourceFillModule;
import evernight.client.modules.player.SpeedMine.SpeedMineModule;
import evernight.client.modules.render.Ambience.AmbienceModule;
import evernight.client.modules.render.Animations.AnimationsModule;
import evernight.client.modules.render.Blur.BlurModule;
import evernight.client.modules.render.Borders.BordersModule;
import evernight.client.modules.render.BreakHighlight.BreakHighlightModule;
import evernight.client.modules.render.Chams.ChamsModule;
import evernight.client.modules.render.Crosshair.CrosshairModule;
import evernight.client.modules.render.ESP.ESPModule;
import evernight.client.modules.render.FreeLook.FreeLookModule;
import evernight.client.modules.render.Glint.GlintModule;
import evernight.client.modules.render.Highlight.HighlightModule;
import evernight.client.modules.render.Hitmarker.HitmarkerModule;
import evernight.client.modules.render.HoleESP.HoleESPModule;
import evernight.client.modules.render.LogoutSpots.LogoutSpotsModule;
import evernight.client.modules.render.NameTags.NameTagsModule;
import evernight.client.modules.render.NoBob.NoBobModule;
import evernight.client.modules.render.NoRender.NoRenderModule;
import evernight.client.modules.render.Particles.ParticlesModule;
import evernight.client.modules.render.PhaseESP.PhaseESPModule;
import evernight.client.modules.render.PredictPath.PredictPathModule;
import evernight.client.modules.render.Search.SearchModule;
import evernight.client.modules.render.Shader.ShaderModule;
import evernight.client.modules.render.Skeleton.SkeletonModule;
import evernight.client.modules.render.Skybox.SkyboxModule;
import evernight.client.modules.render.Tooltips.TooltipsModule;
import evernight.client.modules.render.Tracers.TracersModule;
import evernight.client.modules.render.Trails.TrailsModule;
import evernight.client.modules.render.Trajectories.TrajectoriesModule;
import evernight.client.modules.render.Tunnels.TunnelsModule;
import evernight.client.modules.render.ViewClip.ViewClipModule;
import evernight.client.modules.render.ViewModel.ViewModelModule;
import evernight.client.modules.render.VoidESP.VoidESPModule;
import evernight.client.modules.render.Waypoints.WaypointsModule;
import evernight.client.modules.render.Xray.XrayModule;
import evernight.client.modules.render.Zoom.ZoomModule;

public final class ModuleRegistry extends Registry<Module> implements ClientAccess, JsonConfigurable {
    private final Object2ObjectOpenHashMap<Class<?>, Module> modulesByClass = new Object2ObjectOpenHashMap<>();

    public ModuleRegistry() {
        ClientAccess.eventBus.subscribe(this);
        if (!this.registerModule(new evernight.client.modules.combat.AimAssist.AimAssistModule()))
            throw new IllegalStateException("Registration rejected AimAssistModule");
        if (!this.registerModule(new evernight.client.modules.render.Ambience.AmbienceModule()))
            throw new IllegalStateException("Registration rejected AmbienceModule");
        if (!this.registerModule(new evernight.client.modules.combat.AnchorAura.AnchorAuraModule()))
            throw new IllegalStateException("Registration rejected AnchorAuraModule");
        if (!this.registerModule(new evernight.client.modules.render.Animations.AnimationsModule()))
            throw new IllegalStateException("Registration rejected AnimationsModule");
        if (!this.registerModule(new evernight.client.modules.misc.Announcer.AnnouncerModule()))
            throw new IllegalStateException("Registration rejected AnnouncerModule");
        if (!this.registerModule(new evernight.client.modules.misc.AntiAFK.AntiAFKModule()))
            throw new IllegalStateException("Registration rejected AntiAFKModule");
        if (!this.registerModule(new evernight.client.modules.misc.AntiAim.AntiAimModule()))
            throw new IllegalStateException("Registration rejected AntiAimModule");
        if (!this.registerModule(new evernight.client.modules.combat.AntiBot.AntiBotModule()))
            throw new IllegalStateException("Registration rejected AntiBotModule");
        if (!this.registerModule(new evernight.client.modules.client.AntiCheat.AntiCheatModule()))
            throw new IllegalStateException("Registration rejected AntiCheatModule");
        if (!this.registerModule(new evernight.client.modules.misc.AntiCyrillic.AntiCyrillicModule()))
            throw new IllegalStateException("Registration rejected AntiCyrillicModule");
        if (!this.registerModule(new evernight.client.modules.combat.AntiFeetPlace.AntiFeetPlaceModule()))
            throw new IllegalStateException("Registration rejected AntiFeetPlaceModule");
        if (!this.registerModule(new evernight.client.modules.exploit.AntiHunger.AntiHungerModule()))
            throw new IllegalStateException("Registration rejected AntiHungerModule");
        if (!this.registerModule(new evernight.client.modules.combat.AntiPearl.AntiPearlModule()))
            throw new IllegalStateException("Registration rejected AntiPearlModule");
        if (!this.registerModule(new evernight.client.modules.combat.AntiPhase.AntiPhaseModule()))
            throw new IllegalStateException("Registration rejected AntiPhaseModule");
        if (!this.registerModule(new evernight.client.modules.misc.AntiQuit.AntiQuitModule()))
            throw new IllegalStateException("Registration rejected AntiQuitModule");
        if (!this.registerModule(new evernight.client.modules.misc.AntiSpam.AntiSpamModule()))
            throw new IllegalStateException("Registration rejected AntiSpamModule");
        if (!this.registerModule(new evernight.client.modules.exploit.AntiVanish.AntiVanishModule()))
            throw new IllegalStateException("Registration rejected AntiVanishModule");
        if (!this.registerModule(new evernight.client.modules.movement.Avoid.AvoidModule()))
            throw new IllegalStateException("Registration rejected AvoidModule");
        if (!this.registerModule(new evernight.client.modules.combat.Arrows.ArrowsModule()))
            throw new IllegalStateException("Registration rejected ArrowsModule");
        if (!this.registerModule(new evernight.client.modules.combat.Aura.AuraModule()))
            throw new IllegalStateException("Registration rejected AuraModule");
        if (!this.registerModule(new evernight.client.modules.combat.AutoArmor.AutoArmorModule()))
            throw new IllegalStateException("Registration rejected AutoArmorModule");
        if (!this.registerModule(new evernight.client.modules.combat.AutoBowRelease.AutoBowReleaseModule()))
            throw new IllegalStateException("Registration rejected AutoBowReleaseModule");
        if (!this.registerModule(new evernight.client.modules.player.AutoBreed.AutoBreedModule()))
            throw new IllegalStateException("Registration rejected AutoBreedModule");
        if (!this.registerModule(new evernight.client.modules.combat.AutoClicker.AutoClickerModule()))
            throw new IllegalStateException("Registration rejected AutoClickerModule");
        if (!this.registerModule(new evernight.client.modules.player.AutoCraft.AutoCraftModule()))
            throw new IllegalStateException("Registration rejected AutoCraftModule");
        if (!this.registerModule(new evernight.client.modules.combat.AutoCrystal.AutoCrystalModule()))
            throw new IllegalStateException("Registration rejected AutoCrystalModule");
        if (!this.registerModule(new evernight.client.modules.combat.AutoDrop.AutoDropModule()))
            throw new IllegalStateException("Registration rejected AutoDropModule");
        if (!this.registerModule(new evernight.client.modules.player.AutoEat.AutoEatModule()))
            throw new IllegalStateException("Registration rejected AutoEatModule");
        if (!this.registerModule(new evernight.client.modules.combat.AutoExp.AutoExpModule()))
            throw new IllegalStateException("Registration rejected AutoExpModule");
        if (!this.registerModule(new evernight.client.modules.player.AutoFarm.AutoFarmModule()))
            throw new IllegalStateException("Registration rejected AutoFarmModule");
        if (!this.registerModule(new evernight.client.modules.player.AutoFish.AutoFishModule()))
            throw new IllegalStateException("Registration rejected AutoFishModule");
        if (!this.registerModule(new evernight.client.modules.player.AutoGrindstone.AutoGrindstoneModule()))
            throw new IllegalStateException("Registration rejected AutoGrindstoneModule");
        if (!this.registerModule(new evernight.client.modules.combat.AutoLog.AutoLogModule()))
            throw new IllegalStateException("Registration rejected AutoLogModule");
        if (!this.registerModule(new evernight.client.modules.combat.AutoMace.AutoMaceModule()))
            throw new IllegalStateException("Registration rejected AutoMaceModule");
        if (!this.registerModule(new evernight.client.modules.combat.AutoMine.AutoMineModule()))
            throw new IllegalStateException("Registration rejected AutoMineModule");
        if (!this.registerModule(new evernight.client.modules.player.AutoMining.AutoMiningModule()))
            throw new IllegalStateException("Registration rejected AutoMiningModule");
        if (!this.registerModule(new evernight.client.modules.player.AutoMount.AutoMountModule()))
            throw new IllegalStateException("Registration rejected AutoMountModule");
        if (!this.registerModule(new evernight.client.modules.player.AutoNameTag.AutoNameTagModule()))
            throw new IllegalStateException("Registration rejected AutoNameTagModule");
        if (!this.registerModule(new evernight.client.modules.combat.AutoPot.AutoPotModule()))
            throw new IllegalStateException("Registration rejected AutoPotModule");
        if (!this.registerModule(new evernight.client.modules.misc.AutoReconnect.AutoReconnectModule()))
            throw new IllegalStateException("Registration rejected AutoReconnectModule");
        if (!this.registerModule(new evernight.client.modules.misc.AutoRespawn.AutoRespawnModule()))
            throw new IllegalStateException("Registration rejected AutoRespawnModule");
        if (!this.registerModule(new evernight.client.modules.player.AutoSand.AutoSandModule()))
            throw new IllegalStateException("Registration rejected AutoSandModule");
        if (!this.registerModule(new evernight.client.modules.misc.AutoSign.AutoSignModule()))
            throw new IllegalStateException("Registration rejected AutoSignModule");
        if (!this.registerModule(new evernight.client.modules.player.AutoTame.AutoTameModule()))
            throw new IllegalStateException("Registration rejected AutoTameModule");
        if (!this.registerModule(new evernight.client.modules.player.AutoTool.AutoToolModule()))
            throw new IllegalStateException("Registration rejected AutoToolModule");
        if (!this.registerModule(new evernight.client.modules.player.AutoTrade.AutoTradeModule()))
            throw new IllegalStateException("Registration rejected AutoTradeModule");
        if (!this.registerModule(new evernight.client.modules.player.AutoTransport.AutoTransportModule()))
            throw new IllegalStateException("Registration rejected AutoTransportModule");
        if (!this.registerModule(new evernight.client.modules.combat.AutoTrap.AutoTrapModule()))
            throw new IllegalStateException("Registration rejected AutoTrapModule");
        if (!this.registerModule(new evernight.client.modules.player.AutoTemplate.AutoTemplateModule()))
            throw new IllegalStateException("Registration rejected AutoTemplateModule");
        if (!this.registerModule(new evernight.client.modules.movement.AutoWalk.AutoWalkModule()))
            throw new IllegalStateException("Registration rejected AutoWalkModule");
        if (!this.registerModule(new evernight.client.modules.combat.BedAura.BedAuraModule()))
            throw new IllegalStateException("Registration rejected BedAuraModule");
        if (!this.registerModule(new evernight.client.modules.misc.BetterChat.BetterChatModule()))
            throw new IllegalStateException("Registration rejected BetterChatModule");
        if (!this.registerModule(new evernight.client.modules.combat.Blocker.BlockerModule()))
            throw new IllegalStateException("Registration rejected BlockerModule");
        if (!this.registerModule(new evernight.client.modules.render.Blur.BlurModule()))
            throw new IllegalStateException("Registration rejected BlurModule");
        if (!this.registerModule(new evernight.client.modules.render.Borders.BordersModule()))
            throw new IllegalStateException("Registration rejected BordersModule");
        if (!this.registerModule(new evernight.client.modules.combat.BowAim.BowAimModule()))
            throw new IllegalStateException("Registration rejected BowAimModule");
        if (!this.registerModule(new evernight.client.modules.render.BreakHighlight.BreakHighlightModule()))
            throw new IllegalStateException("Registration rejected BreakHighlightModule");
        if (!this.registerModule(new evernight.client.modules.client.Capes.CapesModule()))
            throw new IllegalStateException("Registration rejected CapesModule");
        if (!this.registerModule(new evernight.client.modules.render.Chams.ChamsModule()))
            throw new IllegalStateException("Registration rejected ChamsModule");
        if (!this.registerModule(new evernight.client.modules.misc.ChatFilter.ChatFilterModule()))
            throw new IllegalStateException("Registration rejected ChatFilterModule");
        if (!this.registerModule(new evernight.client.modules.misc.ChestSearchBar.ChestSearchBarModule()))
            throw new IllegalStateException("Registration rejected ChestSearchBarModule");
        if (!this.registerModule(new evernight.client.modules.player.ChestStealer.ChestStealerModule()))
            throw new IllegalStateException("Registration rejected ChestStealerModule");
        if (!this.registerModule(new evernight.client.modules.exploit.ChorusControl.ChorusControlModule()))
            throw new IllegalStateException("Registration rejected ChorusControlModule");
        if (!this.registerModule(new evernight.client.modules.client.Colors.ColorsModule()))
            throw new IllegalStateException("Registration rejected ColorsModule");
        if (!this.registerModule(new evernight.client.modules.misc.CoordinateHider.CoordinateHiderModule()))
            throw new IllegalStateException("Registration rejected CoordinateHiderModule");
        if (!this.registerModule(new evernight.client.modules.misc.CoordLogger.CoordLoggerModule()))
            throw new IllegalStateException("Registration rejected CoordLoggerModule");
        if (!this.registerModule(new evernight.client.modules.combat.Criticals.CriticalsModule()))
            throw new IllegalStateException("Registration rejected CriticalsModule");
        if (!this.registerModule(new evernight.client.modules.render.Crosshair.CrosshairModule()))
            throw new IllegalStateException("Registration rejected CrosshairModule");
        if (!this.registerModule(new evernight.client.modules.misc.CustomDeathText.CustomDeathTextModule()))
            throw new IllegalStateException("Registration rejected CustomDeathTextModule");
        if (!this.registerModule(new evernight.client.modules.client.Discord.DiscordModule()))
            throw new IllegalStateException("Registration rejected DiscordModule");
        if (!this.registerModule(new evernight.client.modules.misc.WebhookNotifs.WebhookNotifsModule()))
            throw new IllegalStateException("Registration rejected WebhookNotifsModule");
        if (!this.registerModule(new evernight.client.modules.render.ESP.ESPModule()))
            throw new IllegalStateException("Registration rejected ESPModule");
        if (!this.registerModule(new evernight.client.modules.movement.ElytraFly.ElytraFlyModule()))
            throw new IllegalStateException("Registration rejected ElytraFlyModule");
        if (!this.registerModule(new evernight.client.modules.movement.EntityControl.EntityControlModule()))
            throw new IllegalStateException("Registration rejected EntityControlModule");
        if (!this.registerModule(new evernight.client.modules.misc.ExtraScreenshot.ExtraScreenshotModule()))
            throw new IllegalStateException("Registration rejected ExtraScreenshotModule");
        if (!this.registerModule(new evernight.client.modules.misc.ExtraTab.ExtraTabModule()))
            throw new IllegalStateException("Registration rejected ExtraTabModule");
        if (!this.registerModule(new evernight.client.modules.movement.FakeLag.FakeLagModule()))
            throw new IllegalStateException("Registration rejected FakeLagModule");
        if (!this.registerModule(new evernight.client.modules.misc.Fakeplayer.FakeplayerModule()))
            throw new IllegalStateException("Registration rejected FakeplayerModule");
        if (!this.registerModule(new evernight.client.modules.exploit.FakeVanilla.FakeVanillaModule()))
            throw new IllegalStateException("Registration rejected FakeVanillaModule");
        if (!this.registerModule(new evernight.client.modules.movement.FastClimb.FastClimbModule()))
            throw new IllegalStateException("Registration rejected FastClimbModule");
        if (!this.registerModule(new evernight.client.modules.movement.FastFall.FastFallModule()))
            throw new IllegalStateException("Registration rejected FastFallModule");
        if (!this.registerModule(new evernight.client.modules.exploit.FastLatency.FastLatencyModule()))
            throw new IllegalStateException("Registration rejected FastLatencyModule");
        if (!this.registerModule(new evernight.client.modules.player.FastPlace.FastPlaceModule()))
            throw new IllegalStateException("Registration rejected FastPlaceModule");
        if (!this.registerModule(new evernight.client.modules.movement.FastStairs.FastStairsModule()))
            throw new IllegalStateException("Registration rejected FastStairsModule");
        if (!this.registerModule(new evernight.client.modules.movement.FastSwim.FastSwimModule()))
            throw new IllegalStateException("Registration rejected FastSwimModule");
        if (!this.registerModule(new evernight.client.modules.movement.FastWeb.FastWebModule()))
            throw new IllegalStateException("Registration rejected FastWebModule");
        if (!this.registerModule(new evernight.client.modules.combat.FeetPlace.FeetPlaceModule()))
            throw new IllegalStateException("Registration rejected FeetPlaceModule");
        if (!this.registerModule(new evernight.client.modules.movement.Fireworks.FireworksModule()))
            throw new IllegalStateException("Registration rejected FireworksModule");
        if (!this.registerModule(new evernight.client.modules.combat.FishTrap.FishTrapModule()))
            throw new IllegalStateException("Registration rejected FishTrapModule");
        if (!this.registerModule(new evernight.client.modules.movement.Flight.FlightModule()))
            throw new IllegalStateException("Registration rejected FlightModule");
        if (!this.registerModule(new evernight.client.modules.client.Fonts.FontsModule()))
            throw new IllegalStateException("Registration rejected FontsModule");
        if (!this.registerModule(new evernight.client.modules.render.FreeLook.FreeLookModule()))
            throw new IllegalStateException("Registration rejected FreeLookModule");
        if (!this.registerModule(new evernight.client.modules.player.Freecam.FreecamModule()))
            throw new IllegalStateException("Registration rejected FreecamModule");
        if (!this.registerModule(new evernight.client.modules.render.Glint.GlintModule()))
            throw new IllegalStateException("Registration rejected GlintModule");
        if (!this.registerModule(new evernight.client.modules.client.HUD.HUDModule()))
            throw new IllegalStateException("Registration rejected HUDModule");
        if (!this.registerModule(new evernight.client.modules.misc.Heaven.HeavenModule()))
            throw new IllegalStateException("Registration rejected HeavenModule");
        if (!this.registerModule(new evernight.client.modules.movement.HighJump.HighJumpModule()))
            throw new IllegalStateException("Registration rejected HighJumpModule");
        if (!this.registerModule(new evernight.client.modules.render.Highlight.HighlightModule()))
            throw new IllegalStateException("Registration rejected HighlightModule");
        if (!this.registerModule(new evernight.client.modules.exploit.HitboxDesync.HitboxDesyncModule()))
            throw new IllegalStateException("Registration rejected HitboxDesyncModule");
        if (!this.registerModule(new evernight.client.modules.render.Hitmarker.HitmarkerModule()))
            throw new IllegalStateException("Registration rejected HitmarkerModule");
        if (!this.registerModule(new evernight.client.modules.render.HoleESP.HoleESPModule()))
            throw new IllegalStateException("Registration rejected HoleESPModule");
        if (!this.registerModule(new evernight.client.modules.combat.HoleFill.HoleFillModule()))
            throw new IllegalStateException("Registration rejected HoleFillModule");
        if (!this.registerModule(new evernight.client.modules.movement.HoleSnap.HoleSnapModule()))
            throw new IllegalStateException("Registration rejected HoleSnapModule");
        if (!this.registerModule(new evernight.client.modules.exploit.IllegalDisconnect.IllegalDisconnectModule()))
            throw new IllegalStateException("Registration rejected IllegalDisconnectModule");
        if (!this.registerModule(new evernight.client.modules.player.InventoryCleaner.InventoryCleanerModule()))
            throw new IllegalStateException("Registration rejected InventoryCleanerModule");
        if (!this.registerModule(new evernight.client.modules.player.InventorySorter.InventorySorterModule()))
            throw new IllegalStateException("Registration rejected InventorySorterModule");
        if (!this.registerModule(new evernight.client.modules.player.InventoryTweaks.InventoryTweaksModule()))
            throw new IllegalStateException("Registration rejected InventoryTweaksModule");
        if (!this.registerModule(new evernight.client.modules.player.ItemSaver.ItemSaverModule()))
            throw new IllegalStateException("Registration rejected ItemSaverModule");
        if (!this.registerModule(new evernight.client.modules.movement.Jesus.JesusModule()))
            throw new IllegalStateException("Registration rejected JesusModule");
        if (!this.registerModule(new evernight.client.modules.combat.KeyPearl.KeyPearlModule()))
            throw new IllegalStateException("Registration rejected KeyPearlModule");
        if (!this.registerModule(new evernight.client.modules.misc.KillEffects.KillEffectsModule()))
            throw new IllegalStateException("Registration rejected KillEffectsModule");
        if (!this.registerModule(new evernight.client.modules.combat.LegacyCrystal.LegacyCrystalModule()))
            throw new IllegalStateException("Registration rejected LegacyCrystalModule");
        if (!this.registerModule(new evernight.client.modules.render.LogoutSpots.LogoutSpotsModule()))
            throw new IllegalStateException("Registration rejected LogoutSpotsModule");
        if (!this.registerModule(new evernight.client.modules.movement.LongJump.LongJumpModule()))
            throw new IllegalStateException("Registration rejected LongJumpModule");
        if (!this.registerModule(new evernight.client.modules.combat.Mainhand.MainhandModule()))
            throw new IllegalStateException("Registration rejected MainhandModule");
        if (!this.registerModule(new evernight.client.modules.misc.MiddleClick.MiddleClickModule()))
            throw new IllegalStateException("Registration rejected MiddleClickModule");
        if (!this.registerModule(new evernight.client.modules.exploit.MultiTask.MultiTaskModule()))
            throw new IllegalStateException("Registration rejected MultiTaskModule");
        if (!this.registerModule(new evernight.client.modules.player.NameProtect.NameProtectModule()))
            throw new IllegalStateException("Registration rejected NameProtectModule");
        if (!this.registerModule(new evernight.client.modules.render.NameTags.NameTagsModule()))
            throw new IllegalStateException("Registration rejected NameTagsModule");
        if (!this.registerModule(new evernight.client.modules.exploit.NewChunks.NewChunksModule()))
            throw new IllegalStateException("Registration rejected NewChunksModule");
        if (!this.registerModule(new evernight.client.modules.render.NoBob.NoBobModule()))
            throw new IllegalStateException("Registration rejected NoBobModule");
        if (!this.registerModule(new evernight.client.modules.player.NoEffects.NoEffectsModule()))
            throw new IllegalStateException("Registration rejected NoEffectsModule");
        if (!this.registerModule(new evernight.client.modules.movement.NoFall.NoFallModule()))
            throw new IllegalStateException("Registration rejected NoFallModule");
        if (!this.registerModule(new evernight.client.modules.player.NoGlitchBlocks.NoGlitchBlocksModule()))
            throw new IllegalStateException("Registration rejected NoGlitchBlocksModule");
        if (!this.registerModule(new evernight.client.modules.combat.NoHitDelay.NoHitDelayModule()))
            throw new IllegalStateException("Registration rejected NoHitDelayModule");
        if (!this.registerModule(new evernight.client.modules.player.NoInteract.NoInteractModule()))
            throw new IllegalStateException("Registration rejected NoInteractModule");
        if (!this.registerModule(new evernight.client.modules.movement.NoJumpDelay.NoJumpDelayModule()))
            throw new IllegalStateException("Registration rejected NoJumpDelayModule");
        if (!this.registerModule(new evernight.client.modules.exploit.NoMineAnimation.NoMineAnimationModule()))
            throw new IllegalStateException("Registration rejected NoMineAnimationModule");
        if (!this.registerModule(new evernight.client.modules.misc.NoNarrator.NoNarratorModule()))
            throw new IllegalStateException("Registration rejected NoNarratorModule");
        if (!this.registerModule(new evernight.client.modules.misc.NoPacketKick.NoPacketKickModule()))
            throw new IllegalStateException("Registration rejected NoPacketKickModule");
        if (!this.registerModule(new evernight.client.modules.render.NoRender.NoRenderModule()))
            throw new IllegalStateException("Registration rejected NoRenderModule");
        if (!this.registerModule(new evernight.client.modules.misc.NoRotate.NoRotateModule()))
            throw new IllegalStateException("Registration rejected NoRotateModule");
        if (!this.registerModule(new evernight.client.modules.movement.NoSlow.NoSlowModule()))
            throw new IllegalStateException("Registration rejected NoSlowModule");
        if (!this.registerModule(new evernight.client.modules.client.Notifications.NotificationsModule()))
            throw new IllegalStateException("Registration rejected NotificationsModule");
        if (!this.registerModule(new evernight.client.modules.player.Nuker.NukerModule()))
            throw new IllegalStateException("Registration rejected NukerModule");
        if (!this.registerModule(new evernight.client.modules.combat.Offhand.OffhandModule()))
            throw new IllegalStateException("Registration rejected OffhandModule");
        if (!this.registerModule(new evernight.client.modules.render.Particles.ParticlesModule()))
            throw new IllegalStateException("Registration rejected ParticlesModule");
        if (!this.registerModule(new evernight.client.modules.player.PearlBot.PearlBotModule()))
            throw new IllegalStateException("Registration rejected PearlBotModule");
        if (!this.registerModule(new evernight.client.modules.exploit.Phase.PhaseModule()))
            throw new IllegalStateException("Registration rejected PhaseModule");
        if (!this.registerModule(new evernight.client.modules.render.PhaseESP.PhaseESPModule()))
            throw new IllegalStateException("Registration rejected PhaseESPModule");
        if (!this.registerModule(new evernight.client.modules.exploit.PingSpoof.PingSpoofModule()))
            throw new IllegalStateException("Registration rejected PingSpoofModule");
        if (!this.registerModule(new evernight.client.modules.exploit.PortalGodMode.PortalGodModeModule()))
            throw new IllegalStateException("Registration rejected PortalGodModeModule");
        if (!this.registerModule(new evernight.client.modules.combat.PistonCrystal.PistonCrystalModule()))
            throw new IllegalStateException("Registration rejected PistonCrystalModule");
        if (!this.registerModule(new evernight.client.modules.render.PredictPath.PredictPathModule()))
            throw new IllegalStateException("Registration rejected PredictPathModule");
        if (!this.registerModule(new evernight.client.modules.combat.Pusher.PusherModule()))
            throw new IllegalStateException("Registration rejected PusherModule");
        if (!this.registerModule(new evernight.client.modules.exploit.RaytraceBypass.RaytraceBypassModule()))
            throw new IllegalStateException("Registration rejected RaytraceBypassModule");
        if (!this.registerModule(new evernight.client.modules.exploit.Reach.ReachModule()))
            throw new IllegalStateException("Registration rejected ReachModule");
        if (!this.registerModule(new evernight.client.modules.player.Replenish.ReplenishModule()))
            throw new IllegalStateException("Registration rejected ReplenishModule");
        if (!this.registerModule(new evernight.client.modules.exploit.RocketExtender.RocketExtenderModule()))
            throw new IllegalStateException("Registration rejected RocketExtenderModule");
        if (!this.registerModule(new evernight.client.modules.player.RotationLock.RotationLockModule()))
            throw new IllegalStateException("Registration rejected RotationLockModule");
        if (!this.registerModule(new evernight.client.modules.movement.SafeWalk.SafeWalkModule()))
            throw new IllegalStateException("Registration rejected SafeWalkModule");
        if (!this.registerModule(new evernight.client.modules.player.Scaffold.ScaffoldModule()))
            throw new IllegalStateException("Registration rejected ScaffoldModule");
        if (!this.registerModule(new evernight.client.modules.render.Search.SearchModule()))
            throw new IllegalStateException("Registration rejected SearchModule");
        if (!this.registerModule(new evernight.client.modules.combat.SelfFill.SelfFillModule()))
            throw new IllegalStateException("Registration rejected SelfFillModule");
        if (!this.registerModule(new evernight.client.modules.combat.SelfTrap.SelfTrapModule()))
            throw new IllegalStateException("Registration rejected SelfTrapModule");
        if (!this.registerModule(new evernight.client.modules.combat.SelfWeb.SelfWebModule()))
            throw new IllegalStateException("Registration rejected SelfWebModule");
        if (!this.registerModule(new evernight.client.modules.client.Settings.SettingsModule()))
            throw new IllegalStateException("Registration rejected SettingsModule");
        if (!this.registerModule(new evernight.client.modules.render.Shader.ShaderModule()))
            throw new IllegalStateException("Registration rejected ShaderModule");
        if (!this.registerModule(new evernight.client.modules.render.Skeleton.SkeletonModule()))
            throw new IllegalStateException("Registration rejected SkeletonModule");
        if (!this.registerModule(new evernight.client.modules.misc.SkinFlicker.SkinFlickerModule()))
            throw new IllegalStateException("Registration rejected SkinFlickerModule");
        if (!this.registerModule(new evernight.client.modules.render.Skybox.SkyboxModule()))
            throw new IllegalStateException("Registration rejected SkyboxModule");
        if (!this.registerModule(new evernight.client.modules.misc.SoundBlocker.SoundBlockerModule()))
            throw new IllegalStateException("Registration rejected SoundBlockerModule");
        if (!this.registerModule(new evernight.client.modules.player.SourceFill.SourceFillModule()))
            throw new IllegalStateException("Registration rejected SourceFillModule");
        if (!this.registerModule(new evernight.client.modules.misc.Spammer.SpammerModule()))
            throw new IllegalStateException("Registration rejected SpammerModule");
        if (!this.registerModule(new evernight.client.modules.exploit.SpearFix.SpearFixModule()))
            throw new IllegalStateException("Registration rejected SpearFixModule");
        if (!this.registerModule(new evernight.client.modules.movement.Speed.SpeedModule()))
            throw new IllegalStateException("Registration rejected SpeedModule");
        if (!this.registerModule(new evernight.client.modules.player.SpeedMine.SpeedMineModule()))
            throw new IllegalStateException("Registration rejected SpeedMineModule");
        if (!this.registerModule(new evernight.client.modules.movement.Sprint.SprintModule()))
            throw new IllegalStateException("Registration rejected SprintModule");
        if (!this.registerModule(new evernight.client.modules.misc.StashFinder.StashFinderModule()))
            throw new IllegalStateException("Registration rejected StashFinderModule");
        if (!this.registerModule(new evernight.client.modules.movement.Step.StepModule()))
            throw new IllegalStateException("Registration rejected StepModule");
        if (!this.registerModule(new evernight.client.modules.exploit.Timer.TimerModule()))
            throw new IllegalStateException("Registration rejected TimerModule");
        if (!this.registerModule(new evernight.client.modules.render.Tooltips.TooltipsModule()))
            throw new IllegalStateException("Registration rejected TooltipsModule");
        if (!this.registerModule(new evernight.client.modules.render.Tracers.TracersModule()))
            throw new IllegalStateException("Registration rejected TracersModule");
        if (!this.registerModule(new evernight.client.modules.render.Trails.TrailsModule()))
            throw new IllegalStateException("Registration rejected TrailsModule");
        if (!this.registerModule(new evernight.client.modules.render.Trajectories.TrajectoriesModule()))
            throw new IllegalStateException("Registration rejected TrajectoriesModule");
        if (!this.registerModule(new evernight.client.modules.exploit.Trident.TridentModule()))
            throw new IllegalStateException("Registration rejected TridentModule");
        if (!this.registerModule(new evernight.client.modules.combat.Trigger.TriggerModule()))
            throw new IllegalStateException("Registration rejected TriggerModule");
        if (!this.registerModule(new evernight.client.modules.render.Tunnels.TunnelsModule()))
            throw new IllegalStateException("Registration rejected TunnelsModule");
        if (!this.registerModule(new evernight.client.modules.client.UI.UIModule()))
            throw new IllegalStateException("Registration rejected UIModule");
        if (!this.registerModule(new evernight.client.modules.movement.Velocity.VelocityModule()))
            throw new IllegalStateException("Registration rejected VelocityModule");
        if (!this.registerModule(new evernight.client.modules.render.ViewClip.ViewClipModule()))
            throw new IllegalStateException("Registration rejected ViewClipModule");
        if (!this.registerModule(new evernight.client.modules.render.ViewModel.ViewModelModule()))
            throw new IllegalStateException("Registration rejected ViewModelModule");
        if (!this.registerModule(new evernight.client.modules.misc.VisualRange.VisualRangeModule()))
            throw new IllegalStateException("Registration rejected VisualRangeModule");
        if (!this.registerModule(new evernight.client.modules.render.VoidESP.VoidESPModule()))
            throw new IllegalStateException("Registration rejected VoidESPModule");
        if (!this.registerModule(new evernight.client.modules.movement.Warp.WarpModule()))
            throw new IllegalStateException("Registration rejected WarpModule");
        if (!this.registerModule(new evernight.client.modules.client.WaypointSettings.WaypointSettingsModule()))
            throw new IllegalStateException("Registration rejected WaypointSettingsModule");
        if (!this.registerModule(new evernight.client.modules.render.Waypoints.WaypointsModule()))
            throw new IllegalStateException("Registration rejected WaypointsModule");
        if (!this.registerModule(new evernight.client.modules.combat.WebAura.WebAuraModule()))
            throw new IllegalStateException("Registration rejected WebAuraModule");
        if (!this.registerModule(new evernight.client.modules.exploit.XCarry.XCarryModule()))
            throw new IllegalStateException("Registration rejected XCarryModule");
        if (!this.registerModule(new evernight.client.modules.render.Xray.XrayModule()))
            throw new IllegalStateException("Registration rejected XrayModule");
        if (!this.registerModule(new evernight.client.modules.misc.ZenithProxy.ZenithProxyModule()))
            throw new IllegalStateException("Registration rejected ZenithProxyModule");
        if (!this.registerModule(new evernight.client.modules.render.Zoom.ZoomModule()))
            throw new IllegalStateException("Registration rejected ZoomModule");
        this.registerPathfindingModules();
        this.entries.sort(Comparator.comparing(Module::getName));
    }

    public List<PlacementModule> getPlacementModules() {
        ArrayList<PlacementModule> placementModules = new ArrayList<>();
        List<Module> registeredModules = this.entries;
        registeredModules.forEach(module -> {
            if (module instanceof PlacementModule placementModule) {
                placementModules.add(placementModule);
            }
        });
        return placementModules;
    }

    private void registerPathfindingModules() {
        BaritoneBridge bridge = ClientContext.baritoneBridge;
        BaritoneModule baritoneModule = new BaritoneModule();
        this.registerModule(baritoneModule);
        if (bridge.isAvailable()) {
            ObstaclePasserModule obstaclePasserModule = new ObstaclePasserModule();
            this.registerModule(obstaclePasserModule);
        }
    }

    @Override
    public boolean register(Module module) {
        return this.registerModule(module);
    }

    @Override
    public boolean unregister(Module module) {
        return this.unregisterModule(module);
    }

    public boolean unregisterModule(Module module) {
        Object2ObjectOpenHashMap<Class<?>, Module> cache = this.modulesByClass;
        Class moduleClass = module.getClass();
        cache.remove(moduleClass, module);
        return super.unregisterEntry(module);
    }

    @Override
    public void fromJson(JsonElement config) {
        List<Module> registeredModules = this.getRegistry();
        Iterator<Module> modulesIterator = registeredModules.iterator();
        while (modulesIterator.hasNext()) {
            Module module = modulesIterator.next();
            if (!(module instanceof HudWidgetModule)) {
                try {
                    JsonObject configObject = config.getAsJsonObject();
                    String moduleConfigName = module.getConfigName();
                    JsonElement moduleConfig = evernight.client.config.legacy.LegacyBranding.findModuleConfig(configObject, moduleConfigName);
                    module.fromJson(moduleConfig);
                } catch (Exception ignoredFailure) {
                }
            }
        }
    }

    @Override
    public JsonElement toJson() {
        JsonObject config = new JsonObject();
        config.addProperty("client", "evernight");
        config.addProperty("category", "all");
        List<Module> registeredModules = this.getRegistry();
        Iterator<Module> modulesIterator = registeredModules.iterator();
        while (modulesIterator.hasNext()) {
            Module module = modulesIterator.next();
            if (!(module instanceof HudWidgetModule)) {
                try {
                    String moduleConfigName = module.getConfigName();
                    JsonElement moduleConfig = module.toJson();
                    config.add(moduleConfigName, moduleConfig);
                } catch (Exception ignoredFailure) {
                }
            }
        }
        return config;
    }

    @Override
    public String getConfigName() {
        return "modules.json";
    }

    public List<Module> getModulesByCategory(ModuleCategory category) {
        return this.entries.stream()
                .filter(module -> module.getCategory() == category)
                .collect(Collectors.toList());
    }

    @EventHandler
    private void onInputPress(InputPressEvent inputEvent) {
        if (KeyUtils.isPhysicalKeyDown(292)) {
            return;
        }
        List<Module> registeredModules = this.entries;
        Iterator<Module> modulesIterator = registeredModules.iterator();
        while (modulesIterator.hasNext()) {
            Module module = modulesIterator.next();
            Keybind keybindForDeviceCheck = module.getKeybind();
            boolean mouseBinding = keybindForDeviceCheck.mouse();
            if (mouseBinding != inputEvent.isMouse()) {
                continue;
            }
            Keybind keybindForCodeCheck = module.getKeybind();
            int bindingCode = keybindForCodeCheck.code();
            if (bindingCode != inputEvent.getCode()) {
                continue;
            }
            Keybind keybindForModeCheck = module.getKeybind();
            if (keybindForModeCheck.mode() == Keybind.Mode.TOGGLE) {
                module.toggle();
            }
        }
    }

    @EventHandler(priority = -200)
    private void onWorldRender(WorldRenderEvent.Main renderEvent) {
        List<Module> registeredModules = this.entries;
        Iterator<Module> modulesIterator = registeredModules.iterator();
        while (modulesIterator.hasNext()) {
            Module module = modulesIterator.next();
            Keybind keybind = module.getKeybind();
            boolean holdBinding = keybind.mode() == Keybind.Mode.HOLD;
            if (keybind.isUnbound()) {
                continue;
            }
            if (keybind.mode() == Keybind.Mode.TOGGLE) {
                continue;
            }
            if (ClientAccess.evernightMinecraft.screen != null && module.isToggled() != holdBinding) {
                continue;
            }
            boolean shouldToggle = holdBinding == KeyUtils.isPhysicallyDown(keybind);
            module.setToggled(shouldToggle);
        }
    }

    public boolean registerModule(Module module) {
        Class moduleType = module.getClass();
        if (ExperimentalFeatures.isClassHidden(moduleType)) {
            return false;
        }
        ExperimentalFeatures.unregisterHiddenSettings(module);
        Object2ObjectOpenHashMap<Class<?>, Module> cache = this.modulesByClass;
        Class moduleClass = module.getClass();
        cache.put(moduleClass, module);
        return super.registerEntry(module);
    }

    /**
     * 按类型取模块。内部各处直接持有注册表，因此该方法不是 deprecated；
     * 插件与外部代码请改用 {@link evernight.client.ClientContext#getModule(Class)}（它会判空 registry）。
     */
    public <S extends Module> S getModule(Class<S> moduleType) {
        return moduleType.cast(this.modulesByClass.get(moduleType));
    }
}