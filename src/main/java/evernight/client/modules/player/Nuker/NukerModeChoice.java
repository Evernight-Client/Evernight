package evernight.client.modules.player.Nuker;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.registry.SocialRegistry;
import evernight.client.settings.core.NamedEntry;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Set;

enum NukerModeChoice implements NamedEntry {
    WHITELIST("WhiteList") {
        @Override
        public boolean matchesBlock(BlockPos blockPos, Block block, NukerModule nukerModule) {
            return nukerModule.blocksSetting.getValue().contains(block);
        }
    },
    BLACKLIST("BlackList") {
        @Override
        public boolean matchesBlock(BlockPos blockPos, Block block, NukerModule nukerModule) {
            NukerModeChoice whitelistMode = WHITELIST;
            return !whitelistMode.matchesBlock(blockPos, block, nukerModule);
        }
    },
    SHULKERS("Shulkers") {
        @Override
        public boolean matchesBlock(BlockPos blockPos, Block block, NukerModule nukerModule) {
            if (!(block instanceof ShulkerBoxBlock)) {
                return false;
            }
            if (nukerModule.playerProximitySetting.getValue()) {
                Iterator<AbstractClientPlayer> players = ClientAccess.evernightMinecraft.level.players().iterator();
                while (players.hasNext()) {
                    AbstractClientPlayer player = players.next();
                    if (!ClientAccess.isLocalPlayer(player)) {
                        SocialRegistry friendManager = ClientContext.socialRegistry;
                        if (!friendManager.isFriendPlayer(player)) {
                            Vec3 eyePositionSnapshot = player.getEyePosition();
                            Vec3 blockCenter = blockPos.getCenter();
                            if (!(eyePositionSnapshot.distanceTo(blockCenter) > 6.5)) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            } else {
                return true;
            }
        }
    },
    BEDS("Bed") {
        @Override
        public boolean matchesBlock(BlockPos blockPos, Block block, NukerModule nukerModule) {
            if (block instanceof BedBlock) {
                BlockState stateSnapshot = ClientAccess.evernightMinecraft.level.getBlockState(blockPos);
                EnumProperty partProperty = BedBlock.PART;
                return stateSnapshot.getValue(partProperty) == BedPart.HEAD;
            }
            return false;
        }
    },
    CLICK("Click") {
        @Override
        public boolean matchesBlock(BlockPos blockPos, Block block, NukerModule nukerModule) {
            return nukerModule.getClickedBlock() == block;
        }
    };
    private final String displayName;

    NukerModeChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }

    public abstract boolean matchesBlock(BlockPos blockPos, Block block, NukerModule nukerModule);
}