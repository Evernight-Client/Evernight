package evernight.client.render.font;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import evernight.client.ClientContext;
import evernight.client.modules.client.Fonts.FontsModule;
import evernight.client.settings.options.FontsStyleChoice;
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.FormattedCharSequence;

import java.awt.*;
import java.util.Map;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.render.mesh.MeshBuffer;
import evernight.client.render.mesh.MeshDrawContext;
import evernight.client.render.util.ClientRenderTypes;

public final class AtlasTextRenderer {
    static final MeshBuffer glyphMesh = MeshBuffer.create();
    private static final float ATLAS_SCALE = 0.25F;
    private static final String BASE_CHARACTERS = "∞♫♪?";
    private static final Map<Character, ChatFormatting> FORMATTING_CODES = new Char2ObjectOpenHashMap<>();
    private static final FontsModule FONTS = ClientContext.getModule(FontsModule.class);
    private static final ByteBufferBuilder byteBuffer = new ByteBufferBuilder(2048);
    private static final BufferSource immediateBuffers = MultiBufferSource.immediate(byteBuffer);
    private static final MultiBufferSource bufferSource = AtlasTextRenderer::getBuffer;
    private static RenderType lastRenderType;

    static {
        for (ChatFormatting format : ChatFormatting.values()) {
            FORMATTING_CODES.put(format.getChar(), format);
        }
    }

    private final AtlasStringSplitter splitter = new AtlasStringSplitter(this);
    private final FontAtlas atlas;
    private float cursorX;
    private float cursorY;

    public AtlasTextRenderer(FontAtlas atlas) {
        this.atlas = atlas;
        atlas.setBeforeGrow(this::flushBeforeGrow);
    }

    public static void finishBuffers() {
        immediateBuffers.endBatch();
        lastRenderType = null;
    }

    public static AtlasTextRenderer create(int fontSize, FontsStyleChoice style) {
        int awtStyle = switch (style) {
            case PLAIN -> Font.PLAIN;
            case BOLD -> Font.BOLD;
            case ITALIC -> Font.ITALIC;
            case BOLDITALIC -> Font.BOLD | Font.ITALIC;
        };
        FontAtlas atlas = new FontAtlas(FontSupport.createFont(awtStyle, fontSize));
        for (char character = 32; character < 127; character++) {
            atlas.ensureGlyph(character);
        }
        for (char character : BASE_CHARACTERS.toCharArray()) {
            atlas.ensureGlyph(character);
        }
        if (ClientContext.languageTable != null) {
            ClientContext.languageTable.forEachCharacter(character -> atlas.ensureGlyph(character));
        }
        atlas.uploadTexture();
        return new AtlasTextRenderer(atlas);
    }

    private static VertexConsumer getBuffer(RenderType renderType) {
        if (lastRenderType != null && lastRenderType != renderType) {
            GuiDrawUtils.flush();
        }
        lastRenderType = renderType;
        return immediateBuffers.getBuffer(renderType);
    }

    public void flush() {
        this.atlas.uploadDirty();
        if (glyphMesh.isBuilding()) {
            boolean antiAlias = FONTS.antiAliasSetting.getValue();
            MeshDrawContext.setRenderType(ClientRenderTypes.font(FontAtlas.ATLAS_TEXTURE, antiAlias));
            MeshBuffer.draw(glyphMesh);
        }
        finishBuffers();
    }

    private void flushBeforeGrow() {
        this.atlas.uploadDirty();
        boolean building = glyphMesh.isBuilding();
        if (building) {
            boolean antiAlias = FONTS.antiAliasSetting.getValue();
            MeshDrawContext.setRenderType(ClientRenderTypes.font(FontAtlas.ATLAS_TEXTURE, antiAlias));
            MeshBuffer.draw(glyphMesh);
        }
        finishBuffers();
        if (building) {
            glyphMesh.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        }
    }

    public MultiBufferSource getBufferSource() {
        return bufferSource;
    }

    public AtlasStringSplitter getSplitter() {
        return this.splitter;
    }

    public int drawCentered(PoseStack poses, String text, double x, double y, int argb) {
        return this.draw(poses, text, (float) x - this.getWidth(text) / 2.0F, (float) y, argb, true);
    }

    public int getHeight() {
        return (int) (this.atlas.getHeight() * ATLAS_SCALE);
    }

    public int draw(PoseStack poses, String text, float x, float y, int argb) {
        return this.draw(poses, text, x, y, argb, true);
    }

    public int draw(PoseStack poses, String text, double x, double y, int argb) {
        return this.draw(poses, text, (float) x, (float) y, argb, true);
    }

    public int draw(PoseStack poses, FormattedCharSequence sequence, float x, float y, int argb, boolean shadow) {
        if (sequence == null) {
            return 0;
        }
        this.ensureGlyphs(sequence);
        int endX;
        if (shadow) {
            endX = new FormattedTextRenderer(poses, argb, 0.25F, this.atlas)
                    .draw(sequence, x + FontRenderer.getShadowOffset(), y + FontRenderer.getShadowOffset());
            endX = Math.max(endX, new FormattedTextRenderer(poses, argb, 1.0F, this.atlas).draw(sequence, x, y));
        } else {
            endX = new FormattedTextRenderer(poses, argb, 1.0F, this.atlas).draw(sequence, x, y);
        }
        return endX;
    }

    public int draw(PoseStack poses, String text, float x, float y, int argb, boolean shadow) {
        if (!shadow) {
            return this.drawPass(poses, text, x, y, argb, false);
        }
        int shadowEnd = this.drawPass(
                poses, text, x + FontRenderer.getShadowOffset(), y + FontRenderer.getShadowOffset(), argb, true);
        return Math.max(shadowEnd, this.drawPass(poses, text, x, y, argb, false));
    }

    private void ensureGlyphs(String text) {
        boolean formattingCode = false;
        for (char character : text.toCharArray()) {
            if (formattingCode) {
                formattingCode = false;
            } else if (character == '\u00a7') {
                formattingCode = true;
            } else {
                this.atlas.ensureGlyph(character);
            }
        }
    }

    private void ensureGlyphs(FormattedCharSequence sequence) {
        sequence.accept((index, style, codePoint) -> {
            this.atlas.ensureGlyph((char) codePoint);
            return true;
        });
    }

    private void renderString(PoseStack poses, String text, int baseColor) {
        this.ensureGlyphs(text);
        int color = baseColor;
        poses.pushPose();
        try {
            poses.translate(FontRenderer.getTranslate(), FontRenderer.getShift(), 0.0F);
            poses.scale(ATLAS_SCALE, ATLAS_SCALE, 1.0F);
            if (!glyphMesh.isBuilding()) {
                glyphMesh.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            }
            boolean formattingCode = false;
            for (char character : text.toCharArray()) {
                if (formattingCode) {
                    formattingCode = false;
                    ChatFormatting format = FORMATTING_CODES.get(Character.toLowerCase(character));
                    if (format == null || format == ChatFormatting.RESET) {
                        color = baseColor;
                    } else if (format.getColor() != null) {
                        color = format.getColor();
                    }
                } else if (character == '\u00a7') {
                    formattingCode = true;
                } else {
                    this.cursorX += this.atlas.drawGlyph(poses, glyphMesh, character, this.cursorX, this.cursorY, color);
                }
            }
        } finally {
            poses.popPose();
        }
    }

    public float getCharacterWidth(char character) {
        return this.atlas.getGlyphWidth(character) * ATLAS_SCALE;
    }

    private int drawPass(PoseStack poses, String text, float x, float y, int argb, boolean shadowPass) {
        if (text == null) {
            return 0;
        }
        if ((argb >>> 24) == 0) {
            argb |= 0xFF000000;
        }
        if (shadowPass) {
            int alpha = argb & 0xFF000000;
            argb = alpha | ((argb & 0x00FCFCFC) >>> 2);
        }
        this.cursorX = x / ATLAS_SCALE;
        this.cursorY = y / ATLAS_SCALE;
        this.renderString(poses, text, argb);
        return (int) (this.cursorX * ATLAS_SCALE);
    }

    public int drawWithoutShadow(PoseStack poses, String text, float x, float y, int argb) {
        return this.draw(poses, text, x, y, argb, false);
    }

    public int drawWithoutShadow(PoseStack poses, String text, double x, double y, int argb) {
        return this.draw(poses, text, (float) x, (float) y, argb, false);
    }

    public int getWidth(String text) {
        if (text == null) {
            return 0;
        }
        float width = 0.0F;
        boolean formattingCode = false;
        for (char character : text.toCharArray()) {
            if (formattingCode) {
                formattingCode = false;
            } else if (character == '\u00a7') {
                formattingCode = true;
            } else {
                width += this.atlas.getGlyphWidth(character);
            }
        }
        return (int) (width * ATLAS_SCALE + 1.0F);
    }

    public int drawCenteredWithoutShadow(PoseStack poses, String text, double x, double y, int argb) {
        return this.draw(poses, text, (float) x - this.getWidth(text) / 2.0F, (float) y, argb, false);
    }
}
