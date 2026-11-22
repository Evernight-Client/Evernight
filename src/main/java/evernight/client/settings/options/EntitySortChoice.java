package evernight.client.settings.options;

import evernight.client.ClientAccess;
import evernight.client.managers.player.RotationManager;
import evernight.client.settings.core.NamedEntry;
import evernight.client.utils.entity.EntityUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.Comparator;

public enum EntitySortChoice implements NamedEntry {
    DISTANCE("Distance", Comparator.comparingDouble((Entity entity) -> {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        float distance = entity.distanceTo(player);
        return distance;
    })),
    CROSSHAIR("Crosshair", Comparator.comparingDouble((Entity entity) -> {
        GameRenderer renderer = ClientAccess.evernightMinecraft.gameRenderer;
        Camera camera = renderer.getMainCamera();
        float cameraYaw = camera.yaw();
        float targetYaw = RotationManager.getRotationsToEntity(entity)[0];
        return Mth.degreesDifferenceAbs(cameraYaw, targetYaw);
    })),
    HEALTH("Health", Comparator.comparingDouble(EntityUtils::getHealthWithAbsorption));
    private final String name;
    private final Comparator<Entity> comparator;

    EntitySortChoice(String name, Comparator<Entity> comparator) {
        this.name = name;
        this.comparator = comparator;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public Comparator<Entity> getComparator() {
        return this.comparator;
    }
}