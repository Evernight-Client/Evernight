package evernight.client.integrations.entityculling;

import dev.tr7zw.entityculling.EntityCullingModBase;
import dev.tr7zw.entityculling.versionless.EntityCullingVersionlessBase;

public class EntityCullingAdapter {
    public boolean isTickCullingEnabled() {
        return EntityCullingModBase.instance.config.tickCulling;
    }

    public void setTickCullingEnabled(boolean enabled) {
        EntityCullingModBase.instance.config.tickCulling = enabled;
    }

    public boolean isCullingEnabled() {
        return EntityCullingVersionlessBase.enabled;
    }

    public void setCullingEnabled(boolean enabled) {
        EntityCullingVersionlessBase.enabled = enabled;
    }
}