# Evernight 运行时 —— ClientContext、事件、指令与管理器

适用范围：写模块行为逻辑时需要拿到的全局入口（当前玩家/世界、事件订阅、发包、转向、定时任务、指令）。
所有类都在 `src/main/java/evernight/client/` 下，包根 `evernight`。

## 目录

- [1. 两个全局入口：ClientAccess 与 ClientContext](#1-两个全局入口clientaccess-与-clientcontext)
- [2. 事件机制](#2-事件机制)
- [3. 事件目录](#3-事件目录)
- [4. 重点事件写法](#4-重点事件写法)
- [5. 指令体系](#5-指令体系)
- [6. 管理器速查](#6-管理器速查)
- [7. 发包：PacketSender](#7-发包packetsender)
- [8. 反馈：聊天 / 通知 / 声音](#8-反馈聊天--通知--声音)
- [9. 集成与版本兼容](#9-集成与版本兼容)
- [10. 常见任务速查](#10-常见任务速查)

## 1. 两个全局入口：ClientAccess 与 ClientContext

`ClientAccess`（`client/ClientAccess.java`）是 **接口**，实现它就能用这些隐式 public static 字段：

| 成员 | 类型 | 用途 |
| --- | --- | --- |
| `evernightMinecraft` | `Minecraft` | 等价于 `Minecraft.getInstance()`，项目里统一用这个名字 |
| `eventBus` | `EventBus` | 全局事件总线（单例） |
| `gson` | `Gson` | 项目统一 Gson（带 Item/BlockItem 适配器 + pretty printing） |
| `logger` | `Logger` | SLF4J，logger 名 = `EvernightClient.NAME` |
| `executorService` | `ExecutorService` | 缓存线程池（网络/IO 用） |
| `isWorldUnavailable()` | default | `player == null || level == null` |
| `ClientAccess.getVersion()` | static | 客户端版本字符串 |
| `ClientAccess.isLocalPlayer(Object)` | static | 是否为本地玩家 |

`ClientContext`（`client/ClientContext.java`）是 **静态可变字段集合**，由 `bootstrap/ClientInitializer` 按依赖顺序赋值：

```java
ClientContext.moduleRegistry     ClientContext.commandRegistry   ClientContext.configurationManager
ClientContext.rotationManager    ClientContext.serverStateManager ClientContext.worldStateManager
ClientContext.inventoryStateManager  ClientContext.holeManager    ClientContext.taskScheduler
ClientContext.inputOverrides     ClientContext.languageTable      ClientContext.soundManager
ClientContext.webhookManager     ClientContext.chatFilterRegistry ClientContext.socialRegistry
ClientContext.waypointRegistry   ClientContext.macroRegistry      ClientContext.swingManager
ClientContext.crystalInteractionManager  ClientContext.movementPredictionManager
ClientContext.blockBreakTracker  ClientContext.playerStateTracker ClientContext.totemPopTracker
ClientContext.shaderRegistry     ClientContext.blockColorRegistry ClientContext.mapDataCache
ClientContext.hudModuleRegistry  ClientContext.stashRegistry      ClientContext.tradeRegistry
ClientContext.kitRegistry        ClientContext.aliasRegistry      ClientContext.glintRegistry
ClientContext.tickRateController ClientContext.playerTickController  ClientContext.frameTiming
ClientContext.autoPresetRegistry ClientContext.featureBoxRenderer ClientContext.baritoneBridge
ClientContext.waypointAdapter      ClientContext.clickGui          ClientContext.languageTable
```

辅助方法：

```java
HoleManager holes = ClientContext.getModule(HoleManager.class);   // 按类型取模块
ClickGuiScreen gui = ClientContext.getClickGui();                 // ClickGUI 实例（可能为 null）
HudEditorScreen editor = ClientContext.getHudEditor();
```

**用哪个**：无状态服务/管理器 → `ClientContext`；`mc` 与事件总线 → 实现 `ClientAccess` 直接用。
初始化前（`ClientInitializer.initialize()` 之前）这些字段是 null，模块构造器里用 `ClientContext.getModule`
会拿到 null——需要延迟到 `onEnable` 或事件回调里取。

## 2. 事件机制

`EventBus`（`events/bus/EventBus.java`）是反射扫描的发布订阅：

- 处理器 = 标了 `@EventHandler` 的方法，**必须只有一个非基本类型参数、返回 `void`**。
- `@EventHandler(priority = N)`：**N 越大越先执行**；同优先级按注册先后。
- `subscribe(Object)` 会扫描该对象类及其**所有父类**的 `@EventHandler` 方法（含 static）。
- `subscribeStatic(Class)` 只注册 static 处理器（模块里的 `subscribeStatic` 用法见 `UIModule`）。
- `post(event)` 返回事件本身；`postCancellable(event)` 返回 `Cancellable`。

```java
public class FooModule extends Module {
    @EventHandler
    private void onTick(LocalPlayerTickEvent event) { ... }

    @EventHandler(priority = 100)   // 先于普通监听执行，适合改包/改输入
    private void onSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundMovePlayerPacket) {
            // event.cancel();  // 取消后原生逻辑不再执行（视注入点而定）
        }
    }
}
```

**三个必须知道的坑**

1. **派发按精确类型匹配**：`listenersByEventClass.get(event.getClass())`。
   监听 `WorldRenderEvent` **收不到** `WorldRenderEvent.Main`。父子类要各写一个处理器。
2. **渲染事件是复用单例**：`WorldRenderEvent.Main.create(...)`、`HudRenderEvent.create(...)`、
   `EntityRenderEvent.Pre.create(...)` 都复用同一个实例、每次 post 前 `resetCancellation()`。
   **不要把 event 存到字段里**，也不要在别的线程用它；需要延迟处理就把必要数据拷出来。
3. **取消 ≠ 拦截**：`CancellableEvent.cancel()` 只是置位，是否真的阻止原逻辑取决于注入点（mixin）
   是否检查 `isCancelled()`。`ModuleToggleEvent`、`StepHeightEvent` 等**不是** `CancellableEvent` 子类。

`CancellableEvent` 提供 `isCancelled()` / `cancel()` / `resetCancellation()`；
另有 `Cancellable` 接口（`isCancelled`/`setCancelled`/`cancel`）。

## 3. 事件目录

> "发布点"列给出真正 `eventBus.post(...)` 的类，多数在 `src/main/java/evernight/mixin/`。

| 事件 | 发布点 / 触发时机 | 可取消 | 关键成员 |
| --- | --- | --- | --- |
| `ClientInitializationEvent` | `ClientInitializer` | 是 | 无 |
| `MinecraftTickEvent` | `MinecraftMixin`（每 tick，单例 `INSTANCE`） | 是 | 无 |
| `MinecraftRunTickEvent` | `MinecraftMixin` | 是 | `hasPendingTicks` |
| `MinecraftDisconnectEvent` | `MinecraftMixin` | 是 | 无 |
| `LocalPlayerTickEvent` | `LocalPlayerMovementMixin` | 是 | 无 |
| `LocalPlayerPreTickEvent` | `LocalPlayerMovementMixin` | 是 | 无 |
| `LocalPlayerAiStepEvent` | `LocalPlayerMovementMixin` | 是 | 无 |
| `LocalPlayerMoveTowardsClosestSpaceEvent` | `LocalPlayerMovementMixin` | 是 | 无 |
| `LocalPlayerTickPhaseEvent` | `LocalPlayerMovementMixin`（`.BeforePassengerCheck` / `.LocalPlayerTickEvent`） | 是 | 子类型 |
| `LocalPlayerMoveEvent` | `LocalPlayerMovementMixin` | 是 | `getVelocity()/setVelocity(Vec3)`、`getOriginalVelocity()`、`getMoverType()`、`setHorizontalVelocity(double,double)`、`setX/Y/Z` |
| `LocalPlayerSendPositionEvent` | `LocalPlayerMovementMixin`（两次） | 是 | `getStage()`、`x/y/z/yaw/pitch/onGround` 的 get/set、`original*`、`isModified()` |
| `InputPressEvent` | `KeyboardHandlerMixin` / `MouseHandlerMixin` | 是 | `getCode()`、`isMouse()` |
| `KeyboardInputTickEvent` | `KeyboardInputMixin`、`FreecamModule` | 是 | `getInput()`、`reset()` |
| `KeyboardHandlerCharTypedEvent` | `KeyboardHandlerMixin` | 是 | `codePoint` |
| `MouseHandlerOnScrollEvent` | `MouseHandlerMixin` | 是 | `horizontalOffset`、`verticalOffset` |
| `MouseHandlerTurnPlayerEvent` | `MouseHandlerMixin` | 是 | `yawDelta`、`pitchDelta` |
| `SneakStateChangeEvent` | `PlayerStateTracker` | 是 | `sneaking` |
| `SelectedSlotChangeEvent` | `InventoryStateManager` | 是 | `getSlot()` |
| `SlotSetEvent` | `SlotMixin` | 是 | `getItemStack()` |
| `InventoryActionEvent` | `InventoryStateManager`（4 处） | 是 | `getStage()` |
| `PacketEvent` | `ConnectionMixin`（`.Send` 发送前 / `.Receive` 接收前 / `.PostSend` 发送后） | 是 | `getPacket()`；`.Send`/`.Receive` 取消会真正阻断收发 |
| `ChatMessageEvent` | `ChatComponentMixin`（Pre/Post） | 是 | `getStage()`、`getMessage()/setMessage()`、`getOriginalMessage()`、`getLine()/setLine()`、`getTag()` |
| `ClientPacketListenerSendChatEvent` | `ClientPacketListenerChatTimeMixin` | 是 | `message` |
| `CommandExecuteEvent` | `CommandRegistry` | 是 | `getCommand()` |
| `ConnectScreenConnectEvent` | `ConnectScreenMixin` | 是 | `serverAddress` |
| `ScreenChangeEvent` | `MinecraftMixin`（`.Before` / `.AfterInit`） | 是 | `getPreviousScreen()`、`getNewScreen()` |
| `ModuleToggleEvent` | `Module.enable/disable` | **否** | `getModule()` |
| `MacroTriggerEvent` | `MacroRegistry` | 是 | `macro` |
| `RotationRequestEvent` | `RotationManager` | 是 | `getPlayerYaw()`、`getPlayerPitch()`（请求转向时给监听者改 yaw/pitch 的机会） |
| `PlayerAttackEvent` | `PlayerMixin`（前后两次，`stage` 区分） | 是 | `getStage()`、`target` |
| `PlayerTotemEvent` | `TotemPopTracker` | 是 | `player`、`popCount`、`kind` |
| `AuraAttackEvent` | `AuraModule` | 是 | `getStage()`、`entityId` |
| `LivingEntityRotationEvent` | `LivingEntityMixin` | 是 | `yaw`、`pitch`、`jump` |
| `LivingEntityJumpFromGroundEvent` | `LivingEntityMixin` | 是 | `jumpPower` |
| `LivingEntityAddEffectEvent` | `LivingEntityMixin` | 是 | `effect` |
| `StatusEffectQueryEvent` | `LivingEntityMixin` | 是 | `effect` |
| `LivingEntityModelSubmitEvent` | `LivingEntityRendererMixin` | 是 | `entity`、`lightCoords`、`red/green/blue/alpha` |
| `StepHeightEvent` | `EntityMixin` | **否** | `getStage()`、`height`、`isModified()` |
| `BlockCollisionShapeEvent` | `BlockCollisionsMixin` / `ChunkAwareBlockCollisionSweeperVoxelShapeMixin` | 是 | `shape`、`pos`、`state` |
| `BlockUpdateAppliedEvent` | `ClientPacketListenerMixin` | 是 | 无 |
| `ClientLevelAddEntityEvent` / `ClientLevelRemoveEntityEvent` | `ClientLevelMixin` | 是 | `entity` / `id`、`entityPresent` |
| `ClientChunkCacheReplaceWithPacketDataEvent` | `ClientChunkCacheMixin` | 是 | `chunk` |
| `ClientPacketListenerHandleExplosionEvent` | `ClientPacketListenerMixin` | 是 | `centerX/Y/Z`、`radius`、`velocityX/Y/Z` |
| `ClientPacketListenerHandleSetEntityMotionEvent` | `ClientPacketListenerMixin` | 是 | `entityId`、`velocityX/Y/Z`、`velocityModified` |
| `ClientPacketListenerHandleOpenSignEditorEvent` | `ClientPacketListenerMixin` | 是 | `position`、`sign`、`frontText` |
| `ClientPacketListenerHandleMoveVehicleEvent` | `ClientPacketListenerChatTimeMixin` | 是 | `position` |
| `GameModeUseItemPacketEvent` | `MultiPlayerGameModeInteractionMixin` | 是 | `packet` |
| `MultiPlayerGameModeUseItemEvent` / `MultiPlayerGameModeUseItemOnEvent` | `MultiPlayerGameModeInteractionMixin` | 是 | `hand` / `hitResult`、`hand` |
| `MultiPlayerGameModeStartDestroyBlockEvent` / `MultiPlayerGameModeDestroyBlockEvent` | `MultiPlayerGameModeInteractionMixin` | 是 | `pos`、`direction` / `pos` |
| `MultiPlayerGameModeHandleInventoryMouseClickEvent` | `MultiPlayerGameModeMixin` | 是 | `containerId`、`slotId`、`button`、`clickType` |
| `MultiPlayerGameModeReleaseUsingItemEvent` | `MultiPlayerGameModeInteractionMixin` | 是 | 无 |
| `AbstractContainerMenuMoveItemStackToEvent` | `AbstractContainerMenuMixin` | 是 | `slot` |
| `AbstractContainerScreenEvent` | `AbstractContainerScreenMixin` | 是 | `screen`、`graphics`、`poseStack`、`mouseX`、`mouseY` |
| `AbstractContainerScreenRenderTooltipEvent` | `AbstractContainerScreenMixin` | 是 | `screen`、`tooltipComponents`、`x`、`y` |
| `ItemUseEvent` | `ItemStackMixin` / `LocalPlayerMixin`（`.BeforeFinish` / `.Slowdown`） | 是 | `getStack()` （仅 BeforeFinish） |
| `VehicleMoveEvent` | `AbstractBoatMixin`、`LivingEntityMixin` | 是 | 继承 `LocalPlayerMoveEvent` |
| `FishingHookPullEntityEvent` | `FishingHookMixin` | 是 | `entity` |
| `FireworkRocketEntityTickEvent` | `FireworkRocketEntityMixin` | 是 | `yaw`、`pitch` |
| `ParticleEngineAddEvent` | `ParticleEngineMixin` | 是 | `particle` |
| `SoundEnginePlayEvent` | `SoundEngineMixin` | 是 | `soundInstance` |
| `CameraSetupEvent` | `CameraMixin` | 是 | `entity`、`partialTick`、`x/y/z` |
| `NameTagRenderEvent` | `AvatarRendererMixin` / `EntityRendererMixin` | 是 | `poseStack`、`entity` |
| `BlockOutlineRenderEvent` | `LevelRendererMixin` | 是 | `poseStack`、`vertexConsumer`、`blockPos`、`blockState` |
| `ModelFeatureRendererRenderModelEvent` | `ModelFeatureRendererMixin` | 是 | `model`、`getStage()` |
| `RenderTypeDrawEvent` | `RenderTypeMixin` | 是 | `getStage()`、`override` |
| `GlintTextureTransformEvent` | `TextureTransformMixin`（`resetAndGet()` 单例） | 是 | 无 |
| `WorldRenderEvent` | `LevelRendererMixin`（见下） | 是 | `getPoseStack()`、`getPartialTick()` |
| `HudRenderEvent` | `GameRendererMixin`（见下） | 是 | `getPoseStack()`、`getGuiGraphics()`、`getPartialTick()` |
| `EntityRenderEvent` | `EntityRenderDispatcherMixin`（`.Pre` / `.Post`） | 是 | `getEntity()`、`getPoseStack()`、`getCollector()` |

渲染事件的子类型（按触发阶段排序）：

```
LevelRenderer.renderLevel 结束时（LevelRendererMixin:112-135）：
    WorldRenderEvent.BeforeDepthClear → clearDepth → WorldRenderEvent.Main → clearDepth → WorldRenderEvent.Overlay
世界中绘制实体/方块的 pass 里（LevelRendererMixin:138-149）：
    WorldRenderEvent.BeforeBufferSource   // 在 RenderBuffers#bufferSource() 之前，适合注入额外 buffer
GameRenderer 渲染 HUD 时（GameRendererMixin）：
    HudRenderEvent.Before → HudRenderEvent → HudRenderEvent.Unconditional
EntityRenderDispatcher 渲染每个实体时（EntityRenderDispatcherMixin）：
    EntityRenderEvent.Pre → EntityRenderEvent.Post
```

## 4. 重点事件写法

**改入站/出站包**（`PacketEvent`）——注意子类是三个独立事件：

```java
@EventHandler
private void onReceive(PacketEvent.Receive event) {
    if (event.getPacket() instanceof ClientboundSetEntityMotionPacket motion) {
        event.cancel();          // 是否生效取决于 ConnectionMixin 是否检查
    }
}
```

**世界空间绘制**（`WorldRenderEvent.Main`，画世界坐标的方框/线段）：

```java
@EventHandler
private void onWorldRender(WorldRenderEvent.Main event) {
    PoseStack poses = event.getPoseStack();
    WorldBoxRenderer.render(poses, boundingBox, fillColor, outlineColor); // 具体签名见 render.md
}
```

**屏幕空间绘制**（`HudRenderEvent` / `HudRenderEvent.Unconditional`）：用 `event.getGuiGraphics()` 或直接
`GuiDrawUtils`（见 `render.md`）。模块里画 HUD 通常不订阅事件，而是实现 `HudWidgetModule`。

**拦截聊天消息**（`ChatMessageEvent` 有阶段）：

```java
@EventHandler
private void onChat(ChatMessageEvent event) {
    if (!event.getStage().isPre()) return;      // 只处理 Pre
    event.setMessage(rewrite(event.getMessage()));
}
```

**改移动向量**（`LocalPlayerMoveEvent`）：`event.setVelocity(new Vec3(...))` 或 `setHorizontalVelocity(x, z)`。

**请求转向**：不要直接改 `player.setYRot`，用 `RotationManager`：

```java
ClientContext.rotationManager.requestRotation(new float[]{yaw, pitch}, priority);
ClientContext.rotationManager.requestRotation(yaw, pitch, priority, true); // 强制
```

## 5. 指令体系

`Command`（`commands/Command.java`）继承 `NamedFeature` 并实现 `ClientAccess`：

- 构造器 `super("name")`，name 是小写指令字面量（也可 `setAliases(...)`）。
- 抽象方法 `public abstract void exec(LiteralArgumentBuilder builder)`：在这里 `builder.then(...)` 挂子命令。
- 可用辅助：`Command.literal(name)`、`Command.argument(name, ArgumentType)`、
  `Command.aliasedLiteral(names...)`、`addSubcommand(command, parent)`、
  `scheduleRotationTask(Runnable)`、`scheduleScreenChange(Screen)`、`formatText(text)`（水印色文本）。
- 注册：`managers/CommandRegistry` 构造器里 `this.registerCommand(new XxxCommand())`，按字母序插入。
- 前缀：`CommandRegistry.getPrefix()` / `getPrefixCharacter()`（默认 `;`）。
- 执行字符串（含前缀）：`CommandRegistry.executeCommand(String input)`。
- 每个模块还有自动生成的同名开关指令（`registerModuleAliasCommands`），不需要手写。

最小骨架：

```java
public final class FooCommand extends Command {
    public FooCommand() {
        super("foo");
        this.setAliases("fo");
    }

    @Override
    public void exec(LiteralArgumentBuilder builder) {
        RequiredArgumentBuilder amount = Command.argument("amount", IntegerArgumentType.integer(1, 10));
        amount.executes(context -> {
            int value = context.getArgument("amount", Integer.class);
            ChatUtils.printChat(Component.literal(Translations.format(
                    "commands.Foo.Done", "Set to %d.", value)), ChatUtils.DEFAULT_SIGNATURE);
            return 1;
        });
        builder.then(amount);
    }
}
```

常用参数类型（`commands/` 下）：

| 参数类型 | 用途 |
| --- | --- |
| `ModuleArgumentType` | 解析模块名/别名 → `context.getArgument("module", Module.class)` |
| `ModuleSettingArgumentType` | 解析 `模块 设置`，`getSetting()` 拿 `Setting` |
| `SettingValueArgument` | 解析设置值，`value()` 工厂 |
| `BooleanSettingArgument` | 布尔设置值，`bool()` |
| `EnumArgumentType` | 枚举值（配合作者提供的枚举类） |
| `ModuleCategoryArgumentType` | 模块分类值（内置 + 插件分类，大小写不敏感） |
| `EnumArgumentType` / `HexColorArgument` | 颜色值（hex 或名称） |
| `KeybindArgumentType` | 按键（键名/鼠标） |
| `KitArgumentType` / `MacroArgumentType` / `PresetArgumentType` / `PresetCategoryArgument` | 各类注册表条目 |
| `RegistryArgumentType` / `RegistryEntryArgument` | 任意注册表的条目（`registry()`、`aliased()`） |
| `PlayerNameArgumentType` | 玩家名 |
| `PositionArgument` / `RelativeFloatArgument` | 坐标 / 相对数值 |
| `ChatFilterArgumentType` / `FancyStringArgument` / `ArrayArgumentType` | 过滤条目 / 带空格文本 / 数组 |
| `AliasedLiteralArgument` | 一组等价字面量（`getManager()` 风格见实现） |

指令反馈文本统一写 `evernight.ui.commands.<Command>.<Key>`（见 `references/i18n.md`）。

## 6. 管理器速查

| 管理器（`ClientContext.`） | 用途 | 关键方法 |
| --- | --- | --- |
| `rotationManager` | 所有转向的权威入口 | `requestRotation(float[] angles, int priority)`、`requestRotation(Float yaw, Float pitch, int priority, boolean force)`、`calculateRotations(Vec3 from, Vec3 to)`、`getRotationsToEntity(Entity)`、`getRotationsToBlock(BlockPos)`、`getRotationsToFace(Vec3, Direction)`、`isSilentRotationMode()`、`executeAction(Runnable)` |
| `serverStateManager` | 服务器 tick/延迟/回弹状态 | `getTickRate()`、`getLatencyMillis()`、`getLatencyTicks()`、`hasElapsedSinceSetback(long)`、`isServerSneaking()`、`isServerSprinting()`、`getServerData()` |
| `worldStateManager` | 预测放置/破坏、区块与方块实体 | `isPlacementOccupied(BlockPos)`、`hasPendingPlacement(BlockPos)`、`markUnconfirmedBreak(BlockPos)`、`getPendingPlacements()`、`getBlockEntities()` |
| `inventoryStateManager` | 点击批处理/限速、槽位变化派发 | `setClickBatching(boolean)`、`isClickRateLimited(...)`、`wouldReachClickLimit(...)` |
| `taskScheduler` | 延迟任务 | `scheduleRotationTask(Runnable, int delay)`、`scheduleRunTickTask(Runnable)`、`scheduleScreenChange(Screen)` |
| `playerTickController` | 额外 tick（`TickRateController.PlayerTicks`，按倍速补 tick） | `requestMultiplier(Module, float)`、`releaseMultiplier(Module)`、`getMultiplier()`；底层 `PlayerTickExecutor.runExtraTick()` 为 static 工具 |
| `tickRateController` | Timer 类倍速 | `requestMultiplier(Module, float)`、`releaseMultiplier(Module)`、`getMultiplier()` |
| `inputOverrides` | 覆盖移动输入（不碰原版 KeyMapping） | `requestForward/Sneak/Jump/Sprint(boolean)`、`requestMovement(float, float)`、`requestReset()`、`blockSprint()` |
| `holeManager` | 基岩/黑曜石洞 | `isInHole()`、`findIntersectingHole(BlockPos)`、`intersectsHole(AABB)`、`getHoles()` |
| `crystalInteractionManager` | 水晶攻击节流/即时攻击 | `attackEntity(int entityId)`、`attackEntity(int, boolean instantAttack)`、`getAttackDelayMillis()` |
| `swingManager` | 挥手/水晶放置节流 | `swing(InteractionHand, SwingAction)`、`swingPacket(...)`、`tryTrackCrystalPlacement(BlockPos)` |
| `movementPredictionManager` | 预测位置/包围盒 | `getPredictedBoundingBox(Player, int tickIndex)`、`getPredictedEyePosition()`、`getAdjustedLocalMotion()` |
| `soundManager` | 自定义音效 | `playSound(NamedIdentifier, float volume)`、`playSound(NamedIdentifier, Vec3, float)`、`getSound(NamedIdentifier)` |
| `webhookManager` | Discord/Telegram 推送 | `isConfigured()`、`sendContent(String)`、`setUrl(String)`、`setTelegram(token, chatId)` |
| `chatFilterRegistry` / `socialRegistry` / `waypointRegistry` / `macroRegistry` / `kitRegistry` / `stashRegistry` / `tradeRegistry` / `aliasRegistry` / `autoPresetRegistry` | 各类持久化注册表 | 均为 `AbstractRegistry`/`Registry` 子类，见 `module-settings.md` |
| `blockBreakTracker` | 正在破坏的方块进度 | 见 `utils.md` |
| `playerStateTracker` | 玩家状态（潜行/使用物品等） | 见 `utils.md` |
| `totemPopTracker` | 图腾弹出记录 | `PlayerTotemEvent.player/popCount` |
| `frameTiming` | 帧耗时统计 | 见类 |
| `baritoneBridge` | Baritone 是否可用 | `isAvailable()`；设置里用 `Setting#hideWithoutBaritone()` |

## 7. 发包：PacketSender

`utils/network/PacketSender.java`（`public final class`，全 static）：

| 方法 | 用途 |
| --- | --- |
| `send(Packet packet)` | 走客户端连接发包（受 PingSpoof/FakeLag 等模块影响） |
| `sendDirect(Packet packet)` | 绕过排队直接发 |
| `sendUseItemOn(InteractionHand hand, BlockHitResult hitResult)` | 右键方块 |
| `sendUseItem(InteractionHand hand)` | 使用物品 |
| `sendUseItem(InteractionHand hand, float yaw, float pitch)` | 带转向的物品使用 |
| `sendSwing(InteractionHand hand)` | 挥手 |
| `sendAttack(int entityId)` | 攻击实体 |
| `sendRotation(float yaw, float pitch, boolean onGround)` / `sendRotation(float[] rotation, boolean onGround)` | 只发转向包 |
| `sendPosition(double x, double y, double z, boolean onGround)` | 只发位置包 |
| `sendPositionRotation(...)` | 位置+转向 |
| `sendPlayerAction(Action, BlockPos, Direction)` | 挖掘/放置动作 |
| `sendPlayerCommand(ServerboundPlayerCommandPacket.Action)` | 潜行/疾跑等状态 |
| `sendStartFallFlying()`、`sendSwapOffhand()`、`sendSelectedSlot(int slot)` | 常用动作 |
| `markImportant(Packet)`、`nextPredictionSequence()` | 配合 `ImportantPacket`（`evernight$isImportant()`）与预测序号 |

一般业务用 `send(...)`，只有需要精确时序/绕过模块处理时才用 `sendDirect(...)`。

## 8. 反馈：聊天 / 通知 / 声音

- **聊天栏反馈（最常用）**：`ChatUtils.printChat(Component, MessageSignature)`，
  自动加 `[Evernight]` 前缀并切主线程；`ChatUtils.printChat(Component, int id)` 用 `createSignature(id)`。
  也可用 `ChatUtils.DEFAULT_SIGNATURE`、`ChatUtils.moduleSignature(Module)`。
  优先级版本：`printChat(Component, MessageSignature, ChatUtils.MessagePriority)`。
  其他工具：`createPrefix()`、`watermarkText(text)`、`bracketedText(text)`、`suggestCommand(cmd)`、
  `sendChatOrCommand(String)`（以 `/` 开头走命令）。
- **屏幕角落的滑动通知**：目前由 `modules/hud/WelcomerModule` 统一管理（`List<HudNotification>`，
  子类 `GreetingNotification`/`LagNotification`/`MusicNotification`）。没有全局 `notify()` 单例；
  想加新的通知类型，最小改动是在某个 HUD 模块里持有自己的通知列表，或用 `ChatUtils`。
- **常驻 HUD 信息**：写一个 `HudWidgetModule`（见 `module-settings.md`）。
- **音效**：`ClientContext.soundManager.playSound(new NamedIdentifier(...), volume)`；
  内置音效见 `audio/BuiltinSound`。

## 9. 集成与版本兼容

| 入口 | 说明 |
| --- | --- |
| `ClientContext.baritoneBridge` | `BaritoneBridge`，`isAvailable()` 判空；未装 Baritone 时是 `DisabledBaritoneBridge` |
| `ClientContext.waypointAdapter` | `WaypointAdapter`（Xaero 小地图路径点读写），未装时是 `DisabledWaypointAdapter` |
| `ClientContext.waypointSync` | `WaypointSync`（客户端路径点与 Xaero `Evernight Waypoints` 集双向同步），开关在 `modules/client/WaypointSettingsModule`；未装 Xaero 时为 `null` |
| `integrations/ProtocolCompatibility` | 协议版本兼容判定（ViaFabricPlus） |
| `integrations/ProtocolVersionAccess` | 读服务端协议版本 |

集成一律先判 `isAvailable()`，不要在 `onEnable` 里硬依赖；设置项用 `Setting#hideWithoutBaritone()` 隐藏。

## 10. 常见任务速查

1. 拿当前玩家 → `ClientAccess.evernightMinecraft.player`
2. 判断是否在服务器 → `!this.isWorldUnavailable()`（实现 `ClientAccess`）
3. 每 tick 做事 → `@EventHandler void onTick(MinecraftTickEvent e)`
4. 只在模块启用时做事 → 写成模块的 `@EventHandler`（enable 时自动订阅）
5. 发包 → `PacketSender.send(packet)`
6. 拦截入站包 → `@EventHandler PacketEvent.Receive`
7. 转向 → `ClientContext.rotationManager.requestRotation(...)`
8. 强制瞬移本地位置（不被拉回）→ `PacketSender.sendPosition(...)` + 改 `player.setPos`
9. 延迟一 tick 做事 → `ClientContext.taskScheduler.scheduleRunTickTask(Runnable)`
10. 下个 tick 再切屏幕 → `scheduleScreenChange(screen)`（Command 里）或 `TaskScheduler`
11. 覆盖移动输入 → `ClientContext.inputOverrides.requestForward(true)` 等
12. 加速游戏 tick → `ClientContext.tickRateController.requestMultiplier(module, 2.0F)`（用完 release）
13. 查服务器延迟 → `ClientContext.serverStateManager.getLatencyMillis()`
14. 查是否在洞里 → `ClientContext.holeManager.isInHole()`
15. 给玩家发消息 → `ChatUtils.printChat(component, ChatUtils.DEFAULT_SIGNATURE)`
16. 执行一条指令 → `CommandRegistry.executeCommand(";toggle aura")`
17. 读/写配置目录 → `config/ClientDataDirectory`
18. 存档状态跨 tick 保存 → 放模块字段（模块常驻单例），并用 `toJson/fromJson` 决定是否持久化
19. 模块之间通信 → `ClientContext.getModule(XxxModule.class)`（注意可能为 null）
20. 世界渲染中画东西 → `@EventHandler WorldRenderEvent.Main` + `RenderUtils`/`WorldBoxRenderer`
