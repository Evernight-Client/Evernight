package evernight.mixin.minecraft.gui;

import evernight.client.ClientContext;
import evernight.client.modules.misc.ZenithProxy.ZenithProxyModule;
import evernight.client.render.text.Translations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds a "join as spectator" button to ZenithProxy entries in the multiplayer server list.
 *
 * <p>The proxy accepts the login as a spectator when the configuration phase carries the
 * {@code zenith:zenith-spectator} cookie together with {@code zenith:zenith-transfer-src}. Both are
 * handed to {@link ConnectScreen} through a {@link TransferState} instead of a normal join.
 */
@Mixin(ServerSelectionList.class)
public abstract class ServerSelectionListMixin extends ObjectSelectionList<ServerSelectionList.Entry> {
    @Unique
    private static final Identifier ZENITH_TRANSFER_SRC_COOKIE = Identifier.tryBuild("zenith", "zenith-transfer-src");
    @Unique
    private static final Identifier ZENITH_SPECTATOR_COOKIE = Identifier.tryBuild("zenith", "zenith-spectator");
    @Unique
    private static final int BUTTON_WIDTH = 32;
    @Unique
    private static final int BUTTON_HEIGHT = 32;
    @Unique
    private static final int SPRITE_LEFT_OFFSET = 12;

    @Shadow
    @Final
    private JoinMultiplayerScreen screen;

    protected ServerSelectionListMixin(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        ZenithProxyModule module = ClientContext.getModule(ZenithProxyModule.class);
        if (module != null
                && module.shouldShowServerListButton()
                && event.button() == 0
                && this.evernight$handleSpectatorClick(event.x(), event.y())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        ZenithProxyModule module = ClientContext.getModule(ZenithProxyModule.class);
        if (module == null || !module.shouldShowServerListButton()) {
            return;
        }
        List<ServerSelectionList.Entry> entries = this.children();
        for (int index = 0; index < entries.size(); index++) {
            if (!(entries.get(index) instanceof ServerSelectionList.OnlineServerEntry entry)) {
                continue;
            }
            int top = this.getRowTop(index);
            int bottom = this.getRowBottom(index);
            if (bottom < this.getY() || top > this.getBottom()) {
                continue;
            }
            if (!evernight$isZenithServerData(entry.getServerData())) {
                continue;
            }
            int buttonLeft = this.evernight$spectatorButtonLeft();
            boolean hovered = this.evernight$isHoveringButton(mouseX, mouseY, buttonLeft, top);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    hovered ? ServerSelectionList.JOIN_HIGHLIGHTED_SPRITE : ServerSelectionList.JOIN_SPRITE,
                    buttonLeft, top, BUTTON_WIDTH, BUTTON_HEIGHT);
            if (hovered) {
                graphics.setTooltipForNextFrame(
                        Component.literal(Translations.ui("modules.ZenithProxy.JoinSpectator")),
                        mouseX, mouseY);
            }
        }
    }

    @Unique
    private boolean evernight$handleSpectatorClick(double mouseX, double mouseY) {
        List<ServerSelectionList.Entry> entries = this.children();
        for (int index = 0; index < entries.size(); index++) {
            if (!(entries.get(index) instanceof ServerSelectionList.OnlineServerEntry entry)) {
                continue;
            }
            int top = this.getRowTop(index);
            int bottom = this.getRowBottom(index);
            if (bottom < this.getY() || top > this.getBottom()) {
                continue;
            }
            ServerData serverData = entry.getServerData();
            if (!evernight$isZenithServerData(serverData)) {
                continue;
            }
            int buttonLeft = this.evernight$spectatorButtonLeft();
            if (!this.evernight$isHoveringButton(mouseX, mouseY, buttonLeft, top)) {
                continue;
            }
            Map<Identifier, byte[]> cookies = new HashMap<>();
            cookies.put(ZENITH_TRANSFER_SRC_COOKIE, serverData.ip.getBytes(StandardCharsets.UTF_8));
            cookies.put(ZENITH_SPECTATOR_COOKIE, String.valueOf(true).getBytes(StandardCharsets.UTF_8));
            TransferState transferState = new TransferState(cookies, Map.of(), false);
            ConnectScreen.startConnecting(this.screen, this.minecraft,
                    ServerAddress.parseString(serverData.ip), serverData, false, transferState);
            return true;
        }
        return false;
    }

    @Unique
    private int evernight$spectatorButtonLeft() {
        // Six pixels for the scrollbar, three pixels of padding, matching the vanilla join sprite.
        return this.getRowLeft() + this.getRowWidth() + 6 - 3;
    }

    @Unique
    private boolean evernight$isHoveringButton(double mouseX, double mouseY, int buttonLeft, int top) {
        int left = buttonLeft + SPRITE_LEFT_OFFSET;
        int right = buttonLeft + BUTTON_WIDTH;
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= top + BUTTON_HEIGHT;
    }

    @Unique
    private static boolean evernight$isZenithServerData(ServerData serverData) {
        if (serverData == null || serverData.version == null) {
            return false;
        }
        String version = serverData.version.tryCollapseToString();
        return version != null && version.startsWith("ZenithProxy");
    }
}
