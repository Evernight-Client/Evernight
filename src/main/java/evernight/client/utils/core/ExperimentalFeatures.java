package evernight.client.utils.core;

import evernight.client.modules.Module;
import evernight.client.settings.core.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.util.Properties;

public class ExperimentalFeatures {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static boolean experimentalFeaturesAllowed;

    static {
        experimentalFeaturesAllowed = false;
        FabricLoader loader = FabricLoader.getInstance();
        for (String launchArgument : loader.getLaunchArguments(true)) {
            if ("--evernight-allow-experimental".equals(launchArgument)) {
                experimentalFeaturesAllowed = true;
                break;
            }
        }
        Properties propertiesForPresenceCheck = System.getProperties();
        if (propertiesForPresenceCheck.get("evernight.allowExperimental") != null) {
            String expectedPropertyValue = "true";
            Properties propertiesForValueCheck = System.getProperties();
            String propertyValue = (String) propertiesForValueCheck.get("evernight.allowExperimental");
            if (expectedPropertyValue.equalsIgnoreCase(propertyValue)) {
                experimentalFeaturesAllowed = true;
            }
        }
    }

    public static boolean isExperimentalDisabled() {
        return !experimentalFeaturesAllowed;
    }

    public static boolean isFieldHidden(Field field) {
        return isExperimentalDisabled() && field.isAnnotationPresent(Experimental.class);
    }

    public static boolean isEnumConstantHidden(Enum enumConstant) {
        try {
            if (!isExperimentalDisabled()) {
                return false;
            }
            Class declaringClass = enumConstant.getDeclaringClass();
            String constantName = enumConstant.name();
            return declaringClass.getDeclaredField(constantName).isAnnotationPresent(Experimental.class);
        } catch (NoSuchFieldException missingField) {
            return false;
        }
    }

    public static boolean isClassHidden(Class type) {
        return isExperimentalDisabled() && type.isAnnotationPresent(Experimental.class);
    }

    public static void unregisterHiddenSettings(Module module) {
        Class moduleType = module.getClass();
        for (Field field : moduleType.getDeclaredFields()) {
            if (!isFieldHidden(field)) {
                continue;
            }
            field.setAccessible(true);
            try {
                Setting<?> setting = (Setting<?>) field.get(module);
                module.unregister(setting);
            } catch (IllegalAccessException accessFailure) {
                LOGGER.warn("Could not unregister experimental setting", accessFailure);
            }
        }
    }
}