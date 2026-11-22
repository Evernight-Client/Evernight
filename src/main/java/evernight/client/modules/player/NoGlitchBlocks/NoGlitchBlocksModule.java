package evernight.client.modules.player.NoGlitchBlocks;

import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.network.MultiPlayerGameModeDestroyBlockEvent;
import evernight.client.events.network.MultiPlayerGameModeUseItemOnEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.network.PacketSender;
import evernight.client.utils.world.BlockInteractionUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;

public class NoGlitchBlocksModule extends Module {
    public final Setting<Boolean> breakSetting;
    private final Setting<Boolean> placeSetting;

    public NoGlitchBlocksModule() {
        super("NoGlitchBlocks", ModuleCategory.PLAYER);
        Boolean defaultPlace = true;
        BooleanSetting placeOption = new BooleanSetting("Place", defaultPlace);
        this.placeSetting = this.add(placeOption);
        Boolean defaultBreak = true;
        BooleanSetting breakOption = new BooleanSetting("Break", defaultBreak);
        this.breakSetting = this.add(breakOption);
    }

    @EventHandler
    private void onMultiPlayerGameModeUseItemOnEvent(MultiPlayerGameModeUseItemOnEvent event) {
        InteractionHand interactionHand = event.getHand();
        Item heldItem = ClientAccess.evernightMinecraft.player.getItemInHand(interactionHand).getItem();
        if ((this.placeSetting.getValue()) && heldItem instanceof BlockItem blockItem) {
            BlockPos clickedPos = event.getHitResult().getBlockPos();
            Direction clickedFace = event.getHitResult().getDirection();
            BlockPos placementPos = clickedPos.relative(clickedFace);
            Block placedBlock = blockItem.getBlock();
            if (BlockInteractionUtils.canPlaceBlock(placementPos, placedBlock, false)) {
                InteractionHand packetHand = event.getHand();
                BlockHitResult packetHit = event.getHitResult();
                PacketSender.sendUseItemOn(packetHand, packetHit);
                LocalPlayer playerForSwing = ClientAccess.evernightMinecraft.player;
                InteractionHand swingHand = event.getHand();
                playerForSwing.swing(swingHand);
                event.cancel();
            }
        }
    }

    @EventHandler
    private void onMultiPlayerGameModeDestroyBlockEvent(MultiPlayerGameModeDestroyBlockEvent event) {
        if (this.breakSetting.getValue()) {
            event.cancel();
        }
    }
}