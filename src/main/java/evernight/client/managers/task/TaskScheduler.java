package evernight.client.managers.task;

import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.ui.MinecraftRunTickEvent;
import evernight.client.events.player.RotationRequestEvent;
import evernight.client.events.bus.EventBus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.thread.BlockableEventLoop;

import java.util.*;

public final class TaskScheduler implements ClientAccess {
    private final ClientThreadTaskQueue rotationTasks = new ClientThreadTaskQueue();
    private final ClientThreadTaskQueue runTickTasks = new ClientThreadTaskQueue();

    public TaskScheduler() {
        EventBus eventBus = ClientAccess.eventBus;
        eventBus.subscribe(this);
    }

    public void scheduleRotationTask(Runnable task, int delay) {
        this.rotationTasks.enqueue(task, delay);
    }

    public void scheduleRunTickTask(Runnable task) {
        this.runTickTasks.enqueue(task, 1);
    }

    @EventHandler(priority = -100)
    private void onRotationEvent(RotationRequestEvent event) {
        this.rotationTasks.run();
    }

    @EventHandler(priority = -100)
    private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
        this.runTickTasks.run();
    }

    public void scheduleScreenChange(Screen screen) {
        this.scheduleRunTickTask(() -> {
            ClientAccess.evernightMinecraft.setScreen(screen);
        });
    }

    private static final class ClientThreadTaskQueue {
        private final List<CountdownTask> activeTasks = Collections.synchronizedList(new ArrayList<>());
        private final Queue<CountdownTask> pendingTasks = new ArrayDeque<>();

        private void run() {
            synchronized (this.activeTasks) {
                while (!this.pendingTasks.isEmpty()) {
                    this.activeTasks.add(this.pendingTasks.poll());
                }
                this.activeTasks.removeIf(CountdownTask::runIfReady);
            }
        }

        private void enqueue(CountdownTask task) {
            synchronized (this.activeTasks) {
                this.pendingTasks.add(task);
            }
        }

        private void enqueue(Runnable task, int delay) {
            Minecraft minecraft = Minecraft.getInstance();
            CountdownTask countdownTask = new CountdownTask(task, delay);
            if (minecraft.isSameThread()) {
                this.enqueue(countdownTask);
            } else {
                minecraft.executeIfPossible(() -> this.enqueue(countdownTask));
            }
        }
    }

    private static final class CountdownTask {
        private final Runnable task;
        private int remainingPasses;

        private CountdownTask(Runnable task, int remainingPasses) {
            this.task = task;
            this.remainingPasses = remainingPasses;
        }

        private boolean runIfReady() {
            if (--this.remainingPasses < 0) {
                this.task.run();
                return true;
            } else {
                return false;
            }
        }
    }
}