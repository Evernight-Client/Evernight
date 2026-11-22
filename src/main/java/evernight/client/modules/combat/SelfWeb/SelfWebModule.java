package evernight.client.modules.combat.SelfWeb;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.RotationRequestEvent;
import evernight.client.managers.player.RotationManager;
import evernight.client.managers.registry.SocialRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.text.Translations;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.chat.ChatUtils;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.math.MathConstants;
import evernight.client.utils.world.BlockInteractionUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class SelfWebModule extends Module {
    protected final Setting<Float> enemyRangeSetting;
    protected final Setting<Boolean> aggressiveSetting;
    final Setting<Boolean> smartSetting;
    private final Setting<Boolean> rotateSetting;
    private final Setting<Boolean> autoDisableSetting;
    private final Setting<Boolean> onlyHoleSetting;

    public SelfWebModule() {
        super("SelfWeb", ModuleCategory.COMBAT);
        this.rotateSetting = this.add(new BooleanSetting("Rotate", true));
        this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", true));
        this.onlyHoleSetting = this.add(new BooleanSetting("OnlyHole", false));
        this.smartSetting = this.add(new BooleanSetting("Smart", false).markVisibilityDependency());
        Predicate enemyRangeVisibility = unused -> this.smartSetting.isExpanded();
        this.enemyRangeSetting = this.add(new NumberSetting<>("EnemyRange", 3.0F, 1.0F, 6.0F, enemyRangeVisibility).withUnit("m"));
        this.aggressiveSetting = this.add(new BooleanSetting("Aggressive", false).dependsOn(this.smartSetting));
    }

    private Entity findNearestEnemy(Setting<Float> rangeSetting) {
        Stream<Entity> enemyStream = StreamSupport.stream(ClientAccess.evernightMinecraft.level.entitiesForRendering().spliterator(), false).filter(candidate -> {
            if (candidate == ClientAccess.evernightMinecraft.player) {
                return false;
            }
            float distanceToEnemy = ClientAccess.evernightMinecraft.player.distanceTo(candidate);
            Float enemyRange = rangeSetting.getValue();
            if (!(distanceToEnemy <= enemyRange)) {
                return false;
            }
            if (!this.meetsHeightPolicy(candidate) || !(candidate instanceof Player)) {
                return false;
            }
            SocialRegistry friendManagerSnapshot = ClientContext.socialRegistry;
            String candidateName = candidate.getName().getString();
            return !friendManagerSnapshot.isFriendName(candidateName) && candidate.isAlive();
        });
        LocalPlayer comparatorPlayerSnapshot = ClientAccess.evernightMinecraft.player;
        Optional<Entity> nearestEnemySnapshot = enemyStream.min(Comparator.comparingDouble(comparatorPlayerSnapshot::distanceToSqr));
        return nearestEnemySnapshot.orElse(null);
    }

    @EventHandler
    private void onPlayerUpdate(RotationRequestEvent event) {
        Setting<Float> rangeSettingSnapshot = this.enemyRangeSetting;
        Entity nearestEnemy = this.findNearestEnemy(rangeSettingSnapshot);
        if (!this.smartSetting.getValue() || nearestEnemy != null) {
            this.placeWeb(event);
        }
    }

    private boolean meetsHeightPolicy(Entity candidate) {
        if (this.aggressiveSetting.getValue()) {
            double inclusiveCandidateY = candidate.getY();
            LocalPlayer inclusivePlayer = ClientAccess.evernightMinecraft.player;
            return inclusiveCandidateY >= inclusivePlayer.getY();
        } else {
            double strictCandidateY = candidate.getY();
            LocalPlayer strictPlayer = ClientAccess.evernightMinecraft.player;
            return strictCandidateY > strictPlayer.getY();
        }
    }

    private void placeWeb(RotationRequestEvent event) {
        if (ClientAccess.evernightMinecraft.player.isPassenger()) {
            return;
        }
        if (!ClientContext.holeManager.isInHole()) {
            if (this.onlyHoleSetting.getValue()) {
                return;
            }
        }
        BlockPos feetPosition = BlockPos.containing(ClientAccess.evernightMinecraft.player.position());
        BlockState feetState = ClientAccess.evernightMinecraft.level.getBlockState(feetPosition);
        if (!feetState.canBeReplaced()) {
            return;
        }
        if (feetState.liquid()) {
            return;
        }
        if (!ClientAccess.evernightMinecraft.player.onGround()) {
            return;
        }
        Item webItemSnapshot = Items.COBWEB;
        int webSlot = InventoryUtils.findHotbarSlot(webItemSnapshot);
        int previousSlot = ClientAccess.evernightMinecraft.player.getInventory().getSelectedSlot();
        if (webSlot == -1) {
            String moduleName = this.getDisplayName();
            MutableComponent messageBaseSnapshot = Component.literal(moduleName);
            MutableComponent outOfBlocksMessage = messageBaseSnapshot.append(Translations.ui("modules.Placement.OutOfBlocks"));
            ChatUtils.printChat(outOfBlocksMessage, ChatUtils.createSignature(-2), ChatUtils.MessagePriority.HIGH
            );
            this.disable();
            return;
        }
        Direction placementDirection = BlockInteractionUtils.findSupportDirection(feetPosition);
        if (placementDirection == null) {
            return;
        }
        InventoryUtils.selectHotbarSlotForPlacement(webSlot);
        AABB previousBounds = ClientAccess.evernightMinecraft.player.getBoundingBox();
        LocalPlayer clearBoundsPlayerSnapshot = ClientAccess.evernightMinecraft.player;
        AABB emptyBounds = new AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        clearBoundsPlayerSnapshot.setBoundingBox(emptyBounds);
        InteractionHand placementHand = InteractionHand.MAIN_HAND;
        boolean placementFlag = false;
        Direction placementFace = placementDirection;
        try {
            BlockInteractionUtils.placeBlockIgnoringCrystals(
                    feetPosition, placementFace, placementFlag, placementHand);
        } finally {
            ClientAccess.evernightMinecraft.player.setBoundingBox(previousBounds);
            InventoryUtils.selectHotbarSlotForPlacement(previousSlot);
        }
        if (this.rotateSetting.getValue()) {
            RotationManager rotationManagerSnapshot = ClientContext.rotationManager;
            float[] rotations = new float[2];
            rotations[0] = event.getPlayerYaw();
            rotations[1] = MathConstants.NINETY_DEGREES;
            rotationManagerSnapshot.requestRotation(rotations, 5);
        }
        if (this.autoDisableSetting.getValue()) {
            this.disable();
        }
    }
}