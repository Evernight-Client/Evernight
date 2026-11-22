package evernight.client.render.font;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.lwjgl.opengl.GL11;

import java.util.function.Supplier;

public final class MipmapFontTexture extends AbstractTexture {
    private final int mipLevelCount;
    private NativeImage[] mipImages;

    public MipmapFontTexture(Supplier<String> label, NativeImage[] mipImages) {
        this.mipImages = mipImages;
        this.mipLevelCount = mipImages.length;
        this.createTexture(label);
        this.upload();
    }

    private static void resetUnpackState() {
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
    }

    private void createTexture(Supplier<String> label) {
        GpuDevice device = RenderSystem.getDevice();
        NativeImage baseImage = this.mipImages[0];
        this.texture = device.createTexture(label, 5,
                TextureFormat.RGBA8, baseImage.getWidth(), baseImage.getHeight(), 1, this.mipLevelCount);
        this.sampler = RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.LINEAR, true);
        this.textureView = device.createTextureView(this.texture, 0, this.mipLevelCount);
    }

    public void upload() {
        if (this.mipImages == null || this.texture == null) {
            return;
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try {
            for (int level = 0; level < this.mipImages.length; level++) {
                NativeImage image = this.mipImages[level];
                encoder.writeToTexture(this.texture, image, level, 0, 0, 0, image.getWidth(), image.getHeight(), 0, 0);
            }
        } finally {
            resetUnpackState();
        }
    }

    public void uploadRegion(int level, int x, int y, int width, int height) {
        if (this.mipImages == null || this.texture == null || level < 0 || level >= this.mipImages.length) {
            return;
        }
        NativeImage image = this.mipImages[level];
        if (width <= 0 || height <= 0 || x < 0 || y < 0
                || x + width > image.getWidth() || y + height > image.getHeight()) {
            return;
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try {
            encoder.writeToTexture(this.texture, image, level, 0, x, y, width, height, x, y);
        } finally {
            resetUnpackState();
        }
    }

    public NativeImage getPixels() {
        return this.mipImages != null ? this.mipImages[0] : null;
    }

    public void setPixels(NativeImage image) {
        if (this.mipImages != null) {
            for (NativeImage mipImage : this.mipImages) {
                mipImage.close();
            }
        }
        this.mipImages = new NativeImage[]{image};
    }

    @Override
    public void close() {
        if (this.mipImages != null) {
            for (NativeImage image : this.mipImages) {
                image.close();
            }
            this.mipImages = null;
        }
        super.close();
    }
}