package evernight.client.managers.player;

import evernight.client.ClientAccess;
import evernight.client.access.LocalPlayerTickAccess;
import evernight.mixin.minecraft.entity.LivingEntityAccessor;
import net.minecraft.world.phys.Vec3;

public final class PlayerTickExecutor implements ClientAccess {
    private static boolean runningExtraTick;

    private PlayerTickExecutor() {
    }

    public static boolean isRunningExtraTick() {
        return runningExtraTick;
    }

    public static void runExtraTick() {
        runningExtraTick = true;
        int useItemRemainingTicks = ClientAccess.evernightMinecraft.player.getUseItemRemainingTicks();
        float moveDistance = ClientAccess.evernightMinecraft.player.moveDist;
        float walkAnimationSpeed = ClientAccess.evernightMinecraft.player.walkAnimation.speed();
        float walkDistance = ClientAccess.evernightMinecraft.player.avatarState().walkDist;
        float previousWalkDistance = ClientAccess.evernightMinecraft.player.avatarState().walkDistO;
        float verticalBob = ClientAccess.evernightMinecraft.player.yBob;
        float previousVerticalBob = ClientAccess.evernightMinecraft.player.yBobO;
        float horizontalBob = ClientAccess.evernightMinecraft.player.xBob;
        float previousHorizontalBob = ClientAccess.evernightMinecraft.player.xBobO;
        Vec3 previousPosition = new Vec3(ClientAccess.evernightMinecraft.player.xo,
                ClientAccess.evernightMinecraft.player.yo, ClientAccess.evernightMinecraft.player.zo);
        int attackStrengthTicks = ClientAccess.evernightMinecraft.player.attackStrengthTicker;
        int swingTime = ClientAccess.evernightMinecraft.player.swingTime;
        float previousAttackAnimation = ClientAccess.evernightMinecraft.player.oAttackAnim;
        float attackAnimation = ClientAccess.evernightMinecraft.player.attackAnim;
        LocalPlayerTickAccess baseTickPlayer = (LocalPlayerTickAccess) ClientAccess.evernightMinecraft.player;
        baseTickPlayer.superTick();
        LocalPlayerTickAccess eventResetPlayer = (LocalPlayerTickAccess) ClientAccess.evernightMinecraft.player;
        eventResetPlayer.resetEvent();
        ClientAccess.evernightMinecraft.player.attackStrengthTicker = attackStrengthTicks;
        ((LivingEntityAccessor) ClientAccess.evernightMinecraft.player)
                .setItemUseTimeLeft(useItemRemainingTicks);
        ClientAccess.evernightMinecraft.player.moveDist = moveDistance;
        ClientAccess.evernightMinecraft.player.walkAnimation.setSpeed(walkAnimationSpeed);
        ClientAccess.evernightMinecraft.player.avatarState().walkDist = walkDistance;
        ClientAccess.evernightMinecraft.player.avatarState().walkDistO = previousWalkDistance;
        ClientAccess.evernightMinecraft.player.yBob = verticalBob;
        ClientAccess.evernightMinecraft.player.yBobO = previousVerticalBob;
        ClientAccess.evernightMinecraft.player.xBob = horizontalBob;
        ClientAccess.evernightMinecraft.player.xBobO = previousHorizontalBob;
        ClientAccess.evernightMinecraft.player.swingTime = swingTime;
        ClientAccess.evernightMinecraft.player.oAttackAnim = previousAttackAnimation;
        ClientAccess.evernightMinecraft.player.attackAnim = attackAnimation;
        LocalPlayerTickAccess movementPacketPlayer = (LocalPlayerTickAccess) ClientAccess.evernightMinecraft.player;
        movementPacketPlayer.sendMovementPacketsWrapper();
        LocalPlayerTickAccess rotationResetPlayer = (LocalPlayerTickAccess) ClientAccess.evernightMinecraft.player;
        rotationResetPlayer.resetRotations();
        ClientAccess.evernightMinecraft.player.xo = previousPosition.x;
        ClientAccess.evernightMinecraft.player.yo = previousPosition.y;
        ClientAccess.evernightMinecraft.player.zo = previousPosition.z;
        runningExtraTick = false;
    }
}