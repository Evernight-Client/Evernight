package evernight.client.render.font;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.modules.client.Fonts.FontsModule;
import evernight.client.modules.player.NameProtect.NameProtectModule;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;

import java.awt.*;

public final class FontRenderer implements ClientAccess {
    public static final FontRenderer INSTANCE = new FontRenderer();
    private static final NameProtectModule nameProtect = ClientContext.getModule(NameProtectModule.class);
    private AtlasTextRenderer renderer;

    private FontRenderer() {
    }

    public static int getZOffset() {
        return 0;
    }

    public static int getTranslate() {
        return FontsModule.INSTANCE.translateSetting.getValue();
    }

    public static int getShift() {
        return FontsModule.INSTANCE.shiftSetting.getValue();
    }

    public static float getShadowOffset() {
        return FontsModule.INSTANCE.shadowSetting.getValue();
    }

    public static String capitalize(Object value) {
        return Character.toUpperCase(value.toString().charAt(0)) + value.toString().toLowerCase().substring(1);
    }

    public static String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9.\\-]", "_");
    }

    public static boolean containsCharacter(String text, int character) {
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == character) {
                return true;
            }
        }
        return false;
    }

    public static String capitalizeWords(Object value) {
        StringBuilder result = new StringBuilder();
        String text = value.toString();
        for (String word : text.split(" ")) {
            result.append(capitalize(word));
            result.append(" ");
        }
        result.deleteCharAt(Math.max(result.length() - 1, 0));
        return result.toString();
    }

    public AtlasTextRenderer createRenderer() {
        FontsModule fonts = FontsModule.INSTANCE;
        return AtlasTextRenderer.create(fonts.sizeSetting.getValue() * 2, fonts.styleSetting.getValue());
    }

    public int getHeight() {
        if (FontsModule.INSTANCE.isToggled() && this.renderer != null) {
            return this.getRenderer().getHeight() - 1;
        } else {
            if (ClientAccess.evernightMinecraft.font == null) {
                return 9;
            }
            java.util.Objects.requireNonNull(ClientAccess.evernightMinecraft.font);
            return 9;
        }
    }

    public void flush() {
        if (FontsModule.INSTANCE.isToggled() && this.renderer != null) {
            this.renderer.flush();
        }
    }

    public boolean isFontLoading() {
        return FontsModule.INSTANCE.hasReloadCooldown();
    }

    public AtlasTextRenderer getRenderer() {
        if (this.renderer == null) {
            this.renderer = this.createRenderer();
        }
        return this.renderer;
    }

    public void setRenderer(AtlasTextRenderer renderer) {
        if (renderer != null) {
            this.renderer = renderer;
        }
    }

    public float getWidth(String text) {
        if (nameProtect.isToggled() && !this.isWorldUnavailable()) {
            text = nameProtect.protectNames(text);
        }
        return FontsModule.INSTANCE.isToggled() && this.renderer != null ? this.getRenderer().getWidth(text)
                : ClientAccess.evernightMinecraft.font.width(text);
    }

    public void drawWithoutShadow(PoseStack poses, String text, float x, float y, Color color) {
        this.drawWithoutShadow(poses, Component.nullToEmpty(text), x, y, color);
    }

    public float getWidth(FormattedCharSequence sequence) {
        StringBuilder text = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            text.appendCodePoint(codePoint);
            return true;
        });
        return this.getWidth(text.toString());
    }

    public void drawWithShadow(PoseStack poses, Component text, float x, float y, int argb) {
        this.drawWithShadow(poses, text, x, y, argb, DisplayMode.NORMAL);
    }

    public void drawWithShadow(
            PoseStack poses, Component text, float x, float y, int argb, DisplayMode displayMode) {
        if (ARGB.alpha(argb) == 0) {
            return;
        }
        if (FontsModule.INSTANCE.isToggled()) {
            this.getRenderer().draw(poses, text.getVisualOrderText(), x, y, argb, true);
        } else {
            ClientAccess.evernightMinecraft.font.drawInBatch(text, (int) x, (int) y, argb, true,
                    poses.last().pose(), ClientAccess.evernightMinecraft.renderBuffers().bufferSource(),
                    displayMode, 0, 15728880);
        }
    }

    public void drawWithoutShadow(PoseStack poses, Component text, float x, float y, Color color) {
        this.drawWithoutShadow(poses, text, x, y, color.hashCode(), DisplayMode.NORMAL);
    }

    public void drawWithoutShadow(
            PoseStack poses, String text, float x, float y, int argb, DisplayMode displayMode) {
        this.drawWithoutShadow(poses, Component.nullToEmpty(text), x, y, argb, displayMode);
    }

    public void drawWithoutShadow(
            PoseStack poses, Component text, float x, float y, int argb, DisplayMode displayMode) {
        if (ARGB.alpha(argb) == 0) {
            return;
        }
        if (FontsModule.INSTANCE.isToggled()) {
            this.getRenderer().draw(poses, text.getVisualOrderText(), x, y, argb, false);
        } else {
            ClientAccess.evernightMinecraft.font.drawInBatch(text, (int) x, (int) y, argb, false,
                    poses.last().pose(), ClientAccess.evernightMinecraft.renderBuffers().bufferSource(),
                    displayMode, 0, 15728880);
        }
    }

    public float getWidth(Component component) {
        return this.getWidth(component.getString());
    }

    public void drawWithShadow(PoseStack poses, Component text, float x, float y, float scale, Color color) {
        poses.pushPose();
        try {
            poses.scale(scale, scale, scale);
            this.drawWithShadow(poses, text, x / scale, y / scale, color);
        } finally {
            poses.popPose();
        }
    }

    public void drawWithShadow(PoseStack poses, String text, float x, float y, float scale, Color color) {
        this.drawWithShadow(poses, Component.nullToEmpty(text), x, y, scale, color);
    }

    public void drawWithShadow(PoseStack poses, Component text, float x, float y, Color color) {
        this.drawWithShadow(poses, text, x, y, color.hashCode());
    }

    public void drawWithShadow(
            PoseStack poses, Component text, float x, float y, Color color, DisplayMode displayMode) {
        this.drawWithShadow(poses, text, x, y, color.hashCode(), displayMode);
    }

    public void drawWithShadow(
            PoseStack poses, String text, float x, float y, Color color, DisplayMode displayMode) {
        this.drawWithShadow(poses, Component.nullToEmpty(text), x, y, color.hashCode(), displayMode);
    }

    public void drawWithShadow(PoseStack poses, String text, float x, float y, Color color) {
        this.drawWithShadow(poses, Component.nullToEmpty(text), x, y, color);
    }
}