package evernight.client.gui.screens.misc;

import evernight.client.ClientContext;
import evernight.client.gui.core.CloseState;
import evernight.client.gui.components.base.ActionButtonComponent;
import evernight.client.gui.components.base.LabelComponent;
import evernight.client.gui.components.setting.StringSettingComponent;
import evernight.client.gui.panels.Panel;
import evernight.client.managers.player.FakeplayerManager;
import evernight.client.modules.misc.Fakeplayer.FakeplayerModule;
import evernight.client.render.text.Translations;

import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import evernight.client.gui.screens.core.ClientScreen;

/**
 * Graphical control panel for the fake player.
 *
 * <p>The feature switch (enable/disable the fake player) is the first row, followed by the remaining
 * actions of the {@code /fakeplayer} command: kill the dummy, record the local player, replay the
 * recording and manage the logout spot. The status panel reflects the live
 * {@link FakeplayerManager} state on every frame.
 */
public final class FakeplayerScreen extends ClientScreen {
    private final FakeplayerModule module;
    private final Panel controlPanel = new Panel("Fakeplayer");
    private final Panel statusPanel = new Panel("Status");
    private final StringSettingComponent nameComponent;

    public FakeplayerScreen(FakeplayerModule module) {
        this.module = module;
        this.nameComponent = new StringSettingComponent(this.controlPanel, CloseState.ALWAYS_OPEN,
                module.getNameSetting());
        this.controlPanel.registerChild(this.nameComponent);
        this.controlPanel.registerChild(new ActionButtonComponent(this.controlPanel,
                this::featureLabel, this::toggleFeature));
        this.controlPanel.registerChild(new ActionButtonComponent(this.controlPanel,
                this::killLabel, () -> this.manager().killPlayer()));
        this.controlPanel.registerChild(new ActionButtonComponent(this.controlPanel,
                this::recordLabel, () -> this.manager().toggleRecording()));
        this.controlPanel.registerChild(new ActionButtonComponent(this.controlPanel,
                this::playbackLabel, () -> this.manager().togglePlayback()));
        this.controlPanel.registerChild(new ActionButtonComponent(this.controlPanel,
                this::logoutSpotLabel, () -> this.manager().toggleLogoutSpot()));
        this.statusPanel.registerChild(new LabelComponent(this.statusPanel, this::dummyStatus));
        this.statusPanel.registerChild(new LabelComponent(this.statusPanel, this::recordingStatus));
        this.statusPanel.registerChild(new LabelComponent(this.statusPanel, this::playbackStatus));
        this.statusPanel.registerChild(new LabelComponent(this.statusPanel, this::logoutSpotStatus));
        this.statusPanel.setX(this.controlPanel.getX() + this.controlPanel.getWidth() + (4));
        this.panels.add(this.controlPanel);
        this.panels.add(this.statusPanel);
    }

    private FakeplayerManager manager() {
        return ClientContext.fakeplayerManager;
    }

    private void toggleFeature() {
        this.module.toggle();
    }

    // These suppliers return evernight.ui keys, not resolved text: the button translates them on every
    // frame, so already-localised strings would be looked up again and rendered as keys.
    private String featureLabel() {
        return this.module.isToggled()
                ? "modules.Fakeplayer.FeatureOn"
                : "modules.Fakeplayer.FeatureOff";
    }

    private String killLabel() {
        return "modules.Fakeplayer.Kill";
    }

    private String recordLabel() {
        return this.manager().isRecording()
                ? "modules.Fakeplayer.StopRecording"
                : "modules.Fakeplayer.Record";
    }

    private String playbackLabel() {
        return this.manager().isPlaying()
                ? "modules.Fakeplayer.StopPlayback"
                : "modules.Fakeplayer.Play";
    }

    private String logoutSpotLabel() {
        return this.manager().isLogoutSpotActive()
                ? "modules.Fakeplayer.ClearLogoutSpot"
                : "modules.Fakeplayer.LogoutSpot";
    }

    private String dummyStatus() {
        FakeplayerManager manager = this.manager();
        return manager.hasPlayer()
                ? Translations.format("modules.Fakeplayer.StatusSpawned",
                manager.getPlayerName())
                : Translations.ui("modules.Fakeplayer.StatusNotSpawned");
    }

    private String recordingStatus() {
        FakeplayerManager manager = this.manager();
        return manager.isRecording()
                ? Translations.format("modules.Fakeplayer.StatusRecording",
                manager.getRecordedFrameCount())
                : Translations.ui("modules.Fakeplayer.StatusNotRecording");
    }

    private String playbackStatus() {
        return this.manager().isPlaying()
                ? Translations.ui("modules.Fakeplayer.StatusPlaybackOn")
                : Translations.ui("modules.Fakeplayer.StatusPlaybackOff");
    }

    private String logoutSpotStatus() {
        return this.manager().isLogoutSpotActive()
                ? Translations.ui("modules.Fakeplayer.StatusSpotOn")
                : Translations.ui("modules.Fakeplayer.StatusSpotOff");
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && this.nameComponent.editing) {
            // The name row is edited in place; the first Escape only leaves the text field.
            this.nameComponent.keyPressed(event.key());
            return false;
        }
        return super.keyPressed(event);
    }
}
