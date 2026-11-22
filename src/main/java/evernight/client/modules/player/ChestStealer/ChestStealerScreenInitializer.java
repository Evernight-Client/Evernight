package evernight.client.modules.player.ChestStealer;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.task.TaskScheduler;
import evernight.client.settings.options.ChestStealerModeChoice;
import evernight.mixin.minecraft.gui.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterInit;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button.Builder;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.network.chat.Component;

class ChestStealerScreenInitializer implements ClientAccess, AfterInit {
    private final ChestStealerModule module;

    ChestStealerScreenInitializer(ChestStealerModule module) {
        this.module = module;
    }

    @Override
    public void afterInit(Minecraft minecraft, Screen screen, int screenWidth, int screenHeight) {
        if (screen instanceof ContainerScreen || screen instanceof ShulkerBoxScreen) {
            if (this.module.isToggled()) {
                if (this.module.onlyButtonsSetting.getValue()) {
                    this.module.requestMode(null);
                    int buttonWidth = 45;
                    int buttonX = (screen.width - ((AbstractContainerScreenAccessor) screen).getBgWidth()) / (2) - buttonWidth;
                    buttonX -= 2;
                    int buttonY = (screen.height - ((AbstractContainerScreenAccessor) screen).getBgHeight()) / (2);
                    for (ChestStealerModeChoice mode : ChestStealerModeChoice.values()) {
                        Builder buttonBuilder = new Builder(Component.literal(mode.getDisplayName()), button -> {
                            if (mode == this.module.getRequestedMode()) {
                                TaskScheduler scheduler = ClientContext.taskScheduler;
                                Runnable clearFocus = () -> {
                                    screen.setFocused(null);
                                };
                                scheduler.scheduleRotationTask(clearFocus, 0);
                                this.module.requestMode(null);
                            } else {
                                this.module.requestMode(mode);
                            }
                        }
                        );
                        buttonBuilder.bounds(buttonX, buttonY, buttonWidth, 15);
                        buttonY += 17;
                        Screens.getButtons(screen).add(buttonBuilder.build());
                    }
                }
            }
        }
    }
}