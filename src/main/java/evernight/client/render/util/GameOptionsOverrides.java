package evernight.client.render.util;

import evernight.client.ClientAccess;
import evernight.client.access.OptionValueAccess;
import evernight.client.settings.core.NamedEntry;
import net.minecraft.client.CameraType;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

import java.util.HashSet;
import java.util.Set;

public final class GameOptionsOverrides implements ClientAccess {
    private static final Set<String> CAMERA_OVERRIDE_OWNERS = new HashSet<>();
    private static CameraType savedCameraType;

    private GameOptionsOverrides() {
    }

    public static void forceSetOptionValue(OptionInstance option, Object value) {
        OptionValueAccess optionValue = (OptionValueAccess) (Object) option;
        optionValue.forceSetValue(value);
    }

    public static void registerCameraOverride(NamedEntry owner) {
        if (CAMERA_OVERRIDE_OWNERS.isEmpty()) {
            Options gameOptions = ClientAccess.evernightMinecraft.options;
            savedCameraType = gameOptions.getCameraType();
        }
        Set<String> owners = CAMERA_OVERRIDE_OWNERS;
        String ownerName = owner.getName();
        owners.add(ownerName);
    }

    public static void unregisterCameraOverride(NamedEntry owner) {
        Set<String> owners = CAMERA_OVERRIDE_OWNERS;
        String ownerName = owner.getName();
        owners.remove(ownerName);
        if (CAMERA_OVERRIDE_OWNERS.isEmpty() && savedCameraType != null) {
            Options gameOptions = ClientAccess.evernightMinecraft.options;
            CameraType cameraTypeToRestore = savedCameraType;
            gameOptions.setCameraType(cameraTypeToRestore);
            savedCameraType = null;
        }
    }
}