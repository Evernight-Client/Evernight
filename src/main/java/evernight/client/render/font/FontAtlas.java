package evernight.client.render.font;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import evernight.client.ClientAccess;
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Runtime glyph atlas backed by the bundled MiSans face. Glyphs are rasterised the first time
 * they are needed and packed into a texture that grows on demand, so no pre-baked character
 * range is required and characters outside the common set still render correctly.
 */
public final class FontAtlas {
    public static final Identifier ATLAS_TEXTURE = Identifier.fromNamespaceAndPath("evernight", "textures/font.png");
    public static final String ATLAS_LABEL = ATLAS_TEXTURE.toLanguageKey();

    private static final int INITIAL_ATLAS_SIZE = 2048;
    private static final int MAX_ATLAS_SIZE = 8192;
    private static final String FALLBACK_FAMILY = "Dialog";

    private final Char2ObjectOpenHashMap<Glyph> glyphs = new Char2ObjectOpenHashMap<>();
    private final Font font;
    private final Font fallbackFont;

    private int atlasSize;
    private int cursorX;
    private int cursorY;
    private int rowHeight;
    private int fontHeight;
    private BufferedImage atlasImage;
    private BufferedImage mipImage;
    private Graphics2D atlasGraphics;
    private Graphics2D mipGraphics;
    private NativeImage[] mipImages;
    private MipmapFontTexture texture;
    private boolean dirty0;
    private boolean dirty1;
    private int dirty0MinX;
    private int dirty0MinY;
    private int dirty0MaxX;
    private int dirty0MaxY;
    private int dirty1MinX;
    private int dirty1MinY;
    private int dirty1MaxX;
    private int dirty1MaxY;
    private Runnable beforeGrow;

    public FontAtlas(Font font) {
        this.font = font;
        this.fallbackFont = new Font(FALLBACK_FAMILY, font.getStyle(), Math.round(font.getSize2D()));
        this.atlasSize = INITIAL_ATLAS_SIZE;
        this.fontHeight = Math.max(1, font.getSize());
        this.allocateInitialBuffers();
    }

    private static void copyNativeRows(NativeImage source, NativeImage target, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        long rowBytes = (long) width * 4L;
        long sourceStride = (long) source.getWidth() * 4L;
        long targetStride = (long) target.getWidth() * 4L;
        for (int y = 0; y < height; y++) {
            MemoryUtil.memCopy(
                    source.getPointer() + (long) y * sourceStride,
                    target.getPointer() + (long) y * targetStride,
                    rowBytes);
        }
    }

    private static void copyPixels(BufferedImage source, NativeImage target, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int[] pixels = new int[width * height];
        source.getRGB(x, y, width, height, pixels, 0, width);
        int index = 0;
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                target.setPixel(x + column, y + row, pixels[index++]);
            }
        }
    }

    private static void configureGraphics(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static int align(int value, int alignment) {
        return (value + alignment - 1) / alignment * alignment;
    }

    public void setBeforeGrow(Runnable beforeGrow) {
        this.beforeGrow = beforeGrow;
    }

    private void allocateInitialBuffers() {
        this.atlasImage = new BufferedImage(this.atlasSize, this.atlasSize, BufferedImage.TYPE_INT_ARGB);
        this.atlasGraphics = this.atlasImage.createGraphics();
        configureGraphics(this.atlasGraphics);
        this.atlasGraphics.setColor(Color.WHITE);
        int mipSize = Math.max(1, this.atlasSize / 2);
        this.mipImage = new BufferedImage(mipSize, mipSize, BufferedImage.TYPE_INT_ARGB);
        this.mipGraphics = this.mipImage.createGraphics();
        configureGraphics(this.mipGraphics);
        this.mipGraphics.setColor(Color.WHITE);
        this.mipImages = new NativeImage[]{
                new NativeImage(this.atlasSize, this.atlasSize, true),
                new NativeImage(mipSize, mipSize, true)
        };
    }

    private Font glyphFont(char character) {
        return this.font.canDisplay(character) ? this.font : this.fallbackFont;
    }

    public Glyph ensureGlyph(char character) {
        Glyph glyph = this.glyphs.get(character);
        if (glyph != null) {
            return glyph;
        }
        if (Character.isSurrogate(character) || Character.isISOControl(character)) {
            return null;
        }
        Font glyphFont = this.glyphFont(character);
        if (!glyphFont.canDisplay(character)) {
            return null;
        }
        return this.bakeGlyph(character, glyphFont);
    }

    private Glyph bakeGlyph(char character, Font glyphFont) {
        String text = Character.toString(character);
        // Never ask Graphics2D.getFontMetrics(): that routes to FontDesignMetrics.getDefaultFrc(),
        // which resolves the default screen device and throws in a headless JVM (FCL / Android).
        FontRenderContext frc = this.atlasGraphics.getFontRenderContext();
        Rectangle2D bounds = glyphFont.getStringBounds(text, frc);
        int ascent = (int) Math.ceil(glyphFont.getLineMetrics(text, frc).getAscent());
        int width = align(bounds.getBounds().width + 8, 2);
        int height = align(bounds.getBounds().height, 2);
        if (width > MAX_ATLAS_SIZE || height > MAX_ATLAS_SIZE) {
            return null;
        }
        if (!this.reserve(width, height)) {
            return null;
        }
        int x = this.cursorX;
        int y = this.cursorY;

        this.atlasGraphics.setFont(glyphFont);
        this.atlasGraphics.setColor(Color.WHITE);
        this.atlasGraphics.drawString(text, x + 2, y + ascent);

        int mipSize = Math.max(1, this.atlasSize / 2);
        Font mipFont = glyphFont.deriveFont(glyphFont.getSize2D() / 2.0F);
        FontRenderContext mipFrc = this.mipGraphics.getFontRenderContext();
        int mipAscent = (int) Math.ceil(mipFont.getLineMetrics(text, mipFrc).getAscent());
        this.mipGraphics.setFont(mipFont);
        this.mipGraphics.setColor(Color.WHITE);
        this.mipGraphics.drawString(text, x / 2 + 1, y / 2 + mipAscent);

        if (width > 0 && height > 0) {
            copyPixels(this.atlasImage, this.mipImages[0], x, y, width, height);
            this.markDirty(0, x, y, width, height);
        }
        int mipX = Math.max(0, x / 2 - 2);
        int mipY = Math.max(0, y / 2 - 2);
        int mipWidth = Math.min(mipSize - mipX, (width + 1) / 2 + 4);
        int mipHeight = Math.min(mipSize - mipY, (height + 1) / 2 + 4);
        if (mipWidth > 0 && mipHeight > 0) {
            copyPixels(this.mipImage, this.mipImages[1], mipX, mipY, mipWidth, mipHeight);
            this.markDirty(1, mipX, mipY, mipWidth, mipHeight);
        }

        this.cursorX += width;
        this.rowHeight = Math.max(this.rowHeight, height);
        this.fontHeight = Math.max(this.fontHeight, height);
        Glyph glyph = new Glyph(x, y, width, height);
        this.glyphs.put(character, glyph);
        return glyph;
    }

    private boolean reserve(int width, int height) {
        while (true) {
            if (width >= this.atlasSize) {
                if (!this.grow()) {
                    return false;
                }
                continue;
            }
            if (this.cursorX + width >= this.atlasSize) {
                this.cursorX = 0;
                this.cursorY += this.rowHeight + 2;
                this.rowHeight = 0;
            }
            if (this.cursorY + height >= this.atlasSize) {
                if (!this.grow()) {
                    return false;
                }
                continue;
            }
            return true;
        }
    }

    private boolean grow() {
        if (this.atlasSize >= MAX_ATLAS_SIZE) {
            return false;
        }
        if (this.beforeGrow != null) {
            this.beforeGrow.run();
        }
        int oldSize = this.atlasSize;
        int newSize = Math.min(MAX_ATLAS_SIZE, oldSize * 2);

        BufferedImage oldAtlas = this.atlasImage;
        BufferedImage oldMip = this.mipImage;
        Graphics2D oldAtlasGraphics = this.atlasGraphics;
        Graphics2D oldMipGraphics = this.mipGraphics;

        this.atlasImage = new BufferedImage(newSize, newSize, BufferedImage.TYPE_INT_ARGB);
        this.atlasGraphics = this.atlasImage.createGraphics();
        configureGraphics(this.atlasGraphics);
        this.atlasGraphics.setColor(Color.WHITE);
        this.atlasGraphics.drawImage(oldAtlas, 0, 0, null);
        oldAtlasGraphics.dispose();

        int newMipSize = Math.max(1, newSize / 2);
        this.mipImage = new BufferedImage(newMipSize, newMipSize, BufferedImage.TYPE_INT_ARGB);
        this.mipGraphics = this.mipImage.createGraphics();
        configureGraphics(this.mipGraphics);
        this.mipGraphics.setColor(Color.WHITE);
        this.mipGraphics.drawImage(oldMip, 0, 0, null);
        oldMipGraphics.dispose();

        NativeImage oldImage0 = this.mipImages[0];
        NativeImage oldImage1 = this.mipImages[1];
        NativeImage newImage0 = new NativeImage(newSize, newSize, true);
        NativeImage newImage1 = new NativeImage(newMipSize, newMipSize, true);
        copyNativeRows(oldImage0, newImage0, oldSize, oldSize);
        copyNativeRows(oldImage1, newImage1, oldSize / 2, oldSize / 2);
        this.mipImages = new NativeImage[]{newImage0, newImage1};

        if (this.texture != null) {
            MipmapFontTexture resized = new MipmapFontTexture(() -> ATLAS_LABEL, this.mipImages);
            ClientAccess.evernightMinecraft.getTextureManager().register(ATLAS_TEXTURE, resized);
            this.texture = resized;
        } else {
            oldImage0.close();
            oldImage1.close();
        }
        this.atlasSize = newSize;
        this.dirty0 = false;
        this.dirty1 = false;
        return true;
    }

    private void markDirty(int level, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int size = level == 0 ? this.atlasSize : Math.max(1, this.atlasSize / 2);
        int minX = Math.max(0, x);
        int minY = Math.max(0, y);
        int maxX = Math.min(size, x + width);
        int maxY = Math.min(size, y + height);
        if (maxX <= minX || maxY <= minY) {
            return;
        }
        if (level == 0) {
            if (this.dirty0) {
                this.dirty0MinX = Math.min(this.dirty0MinX, minX);
                this.dirty0MinY = Math.min(this.dirty0MinY, minY);
                this.dirty0MaxX = Math.max(this.dirty0MaxX, maxX);
                this.dirty0MaxY = Math.max(this.dirty0MaxY, maxY);
            } else {
                this.dirty0 = true;
                this.dirty0MinX = minX;
                this.dirty0MinY = minY;
                this.dirty0MaxX = maxX;
                this.dirty0MaxY = maxY;
            }
        } else if (this.dirty1) {
            this.dirty1MinX = Math.min(this.dirty1MinX, minX);
            this.dirty1MinY = Math.min(this.dirty1MinY, minY);
            this.dirty1MaxX = Math.max(this.dirty1MaxX, maxX);
            this.dirty1MaxY = Math.max(this.dirty1MaxY, maxY);
        } else {
            this.dirty1 = true;
            this.dirty1MinX = minX;
            this.dirty1MinY = minY;
            this.dirty1MaxX = maxX;
            this.dirty1MaxY = maxY;
        }
    }

    public void uploadTexture() {
        MipmapFontTexture uploaded = new MipmapFontTexture(() -> ATLAS_LABEL, this.mipImages);
        ClientAccess.evernightMinecraft.getTextureManager().register(ATLAS_TEXTURE, uploaded);
        this.texture = uploaded;
        this.dirty0 = false;
        this.dirty1 = false;
    }

    public void uploadDirty() {
        if (this.texture == null) {
            return;
        }
        if (this.dirty0) {
            int width = this.dirty0MaxX - this.dirty0MinX;
            int height = this.dirty0MaxY - this.dirty0MinY;
            if (width > 0 && height > 0) {
                this.texture.uploadRegion(0, this.dirty0MinX, this.dirty0MinY, width, height);
            }
            this.dirty0 = false;
        }
        if (this.dirty1) {
            int width = this.dirty1MaxX - this.dirty1MinX;
            int height = this.dirty1MaxY - this.dirty1MinY;
            if (width > 0 && height > 0) {
                this.texture.uploadRegion(1, this.dirty1MinX, this.dirty1MinY, width, height);
            }
            this.dirty1 = false;
        }
    }

    public float drawGlyph(PoseStack poses, VertexConsumer consumer, char character, float x, float y, int argb) {
        Glyph glyph = this.ensureGlyph(character);
        if (glyph == null) {
            return 0.0F;
        }
        float z = FontRenderer.getZOffset();
        float minU = glyph.x / (float) this.atlasSize;
        float minV = glyph.y / (float) this.atlasSize;
        float maxU = (glyph.x + glyph.width) / (float) this.atlasSize;
        float maxV = (glyph.y + glyph.height) / (float) this.atlasSize;
        consumer.addVertex(poses.last(), x, y + glyph.height, z).setColor(argb).setUv(minU, maxV);
        consumer.addVertex(poses.last(), x + glyph.width, y + glyph.height, z).setColor(argb).setUv(maxU, maxV);
        consumer.addVertex(poses.last(), x + glyph.width, y, z).setColor(argb).setUv(maxU, minV);
        consumer.addVertex(poses.last(), x, y, z).setColor(argb).setUv(minU, minV);
        return glyph.width - 8.0F;
    }

    public float getGlyphWidth(char character) {
        Glyph glyph = this.ensureGlyph(character);
        if (glyph == null) {
            glyph = this.ensureGlyph('?');
        }
        return glyph == null ? 0.0F : glyph.width - 8.0F;
    }

    public int getHeight() {
        return this.fontHeight;
    }

    public void release() {
        if (this.texture != null) {
            ClientAccess.evernightMinecraft.getTextureManager().release(ATLAS_TEXTURE);
            this.texture = null;
        } else if (this.mipImages != null) {
            for (NativeImage image : this.mipImages) {
                image.close();
            }
            this.mipImages = null;
        }
        if (this.atlasGraphics != null) {
            this.atlasGraphics.dispose();
            this.atlasGraphics = null;
        }
        if (this.mipGraphics != null) {
            this.mipGraphics.dispose();
            this.mipGraphics = null;
        }
        this.glyphs.clear();
    }

    public record Glyph(int x, int y, int width, int height) {
    }
}
