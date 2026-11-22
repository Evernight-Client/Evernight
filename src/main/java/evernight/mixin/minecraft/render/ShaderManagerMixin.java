package evernight.mixin.minecraft.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import evernight.client.EvernightClient;
import evernight.client.render.resource.BuiltinResourcePack;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.io.InputStream;
import java.util.Map;

@Mixin(ShaderManager.class)
public class ShaderManagerMixin {
    @Unique
    private static void addBuiltinShaderFiles(Map resources, String shaderBasePath) {
        String[] extensions = {"fsh", "vsh", "json"};
        for (String extension : extensions) {
            InputStream resourceStream = EvernightClient.class.getResourceAsStream(
                    "/assets/minecraft/%s.%s".formatted(shaderBasePath, extension)
            );
            if (resourceStream != null) {
                try {
                    resourceStream.close();
                } catch (Exception ignoredCloseFailure) {
                }
                putBuiltinShaderResource(resources, "%s.%s".formatted(shaderBasePath, extension));
            }
        }
    }

    @Unique
    private static void putBuiltinShaderResource(Map resources, String shaderPath) {
        Resource resource = new Resource(new BuiltinResourcePack(), () -> EvernightClient.class.getResourceAsStream("/assets/minecraft/" + shaderPath));
        resources.put(Identifier.fromNamespaceAndPath("minecraft", shaderPath), resource);
    }

    @ModifyExpressionValue(
            method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Lnet/minecraft/client/renderer/ShaderManager$Configs;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/ResourceManager;listResources(Ljava/lang/String;Ljava/util/function/Predicate;)Ljava/util/Map;")
    )
    private Map includeBuiltinShaderResources(Map resources) {
        if (resources.keySet().stream().noneMatch(resourceId -> resourceId.toString().contains("evernight_lightmap"))) {
            addBuiltinShaderFiles(resources, "shaders/core/evernight_lightmap");
        }
        if (resources.keySet().stream().noneMatch(resourceId -> resourceId.toString().contains("evernight_occlusion"))) {
            addBuiltinShaderFiles(resources, "shaders/core/evernight_occlusion");
        }
        if (resources.keySet().stream().noneMatch(resourceId -> resourceId.toString().contains("evernight_pos_tex_color"))) {
            addBuiltinShaderFiles(resources, "shaders/core/evernight_pos_tex_color");
        }
        if (resources.keySet().stream().noneMatch(resourceId -> resourceId.toString().contains("evernight_lines_smooth"))) {
            addBuiltinShaderFiles(resources, "shaders/core/evernight_lines_smooth");
        }
        return resources;
    }
}