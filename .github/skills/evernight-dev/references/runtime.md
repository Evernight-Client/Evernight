# Evernight 运行时 —— ClientContext、事件、指令与管理器

适用范围：模块行为逻辑的全局入口（玩家/世界、事件、发包、转向、任务、指令）。所有类在 `src/main/java/evernight/client/` 下，包根 `evernight`。

## 1. 两个全局入口：ClientAccess 与 ClientContext

`ClientAccess`（`client/ClientAccess.java`）是**接口**，实现它即可直接用：`evernightMinecraft`（`Minecraft`）、`eventBus`（`EventBus` 单例）、`gson`、`logger`（SLF4J）、`executorService`、`isWorldUnavailable()`（`player == null || level == null`）、`ClientAccess.getVersion()`、`ClientAccess.isLocalPlayer(Object)`。

`ClientContext`（`client/ClientContext.java`）是**静态可变字段集合**，由 `bootstrap/ClientInitializer` 按依赖顺序赋值：

```java
moduleRegistry  commandRegistry  configurationManager  rotationManager  serverStateManager
worldStateManager  inventoryStateManager  holeManager  taskScheduler  inputOverrides
languageTable  soundManager  webhookManager  chatFilterRegistry  socialRegistry
waypointRegistry  waypointSync  macroRegistry  swingManager  crystalInteractionManager
movementPredictionManager  blockBreakTracker  playerStateTracker  totemPopTracker
movementSpeedTracker  fakeplayerManager  screenshotCredentialStore  shaderRegistry
blockColorRegistry  mapDataCache  hudModuleRegistry  stashRegistry  tradeRegistry
kitRegistry  aliasRegistry  glintRegistry  tickRateController  playerTickController
frameTiming  autoPresetRegistry  featureBoxRenderer  baritoneBridge  projectionSource
waypointAdapter  clickGui  configurationLoading(boolean)
```

辅助：`ClientContext.getModule(Class<T>)`、`ClientContext.getClickGui()`、`ClientContext.getHudEditor()`。

**坑**：`ClientInitializer.initialize()` 之前这些字段是 null，模块构造器里 `getModule` 会拿到 null——延迟到 `onEnable` 或事件回调里取。
两处例外：`configurationManager` 在 `initializeClient()` 中、`ClientInitializationEvent` 之后才赋值；`clickGui` 从不由 `ClientInitializer`
赋值，由 `getClickGui()` 懒创建。`configurationLoading` 只是加载期布尔标志，不要当服务用。

## 2. 事件机制

`EventBus`（`events/bus/EventBus.java`）是反射发布订阅：

- 处理器 = 标了 `@EventHandler` 的方法，**必须只有一个非基本类型参数、返回 `void`**。
- `@EventHandler(priority = N)`：**N 越大越先执行**；同优先级按注册先后。
- `subscribe(Object)` 扫描该类及**所有父类**的 `@EventHandler`（含 static）；`subscribeStatic(Class)` 只注册 static。
- `post(event)` 返回事件本身；`postCancellable(event)` 返回 `Cancellable`。

```java
public class FooModule extends Module {
    @EventHandler(priority = 100) private void onSend(PacketEvent.Send event) { /* cancel() */ }
}
```

**三个必须知道的坑**

1. **派发按精确类型匹配**：监听 `WorldRenderEvent` **收不到** `WorldRenderEvent.Main`，父子类要各写处理器；`ScreenChangeEvent` 也收不到 `.Before/.AfterInit`。
2. **渲染事件复用单例**：`WorldRenderEvent.Main`、`HudRenderEvent`、`EntityRenderEvent.Pre`、`GlintTextureTransformEvent` 复用同一实例，post 前 `resetCancellation()`（`GlintTextureTransformEvent` 用 `resetAndGet()`）。**不要把 event 存进字段**，也不要跨线程使用；延迟处理就拷贝数据。
3. **取消 ≠ 拦截**：`CancellableEvent.cancel()` 只置位，是否阻止原逻辑取决于注入点是否检查 `isCancelled()`。`ModuleToggleEvent`（record）、`StepHeightEvent` 等**不是** `CancellableEvent` 子类。

`CancellableEvent` 提供 `isCancelled()`/`cancel()`/`resetCancellation()`；另有 `Cancellable` 接口（`isCancelled`/`setCancelled`/`cancel`）。

## 3. 事件目录

多数发布点在 `src/main/java/evernight/mixin/`（`MinecraftMixin`、`LocalPlayerMovementMixin`、`ConnectionMixin`、`ClientPacketListenerMixin`、`LevelRendererMixin`、`GameRendererMixin` 等），少数由管理器/模块主动 post（`ClientInitializationEvent`、`CommandExecuteEvent`、`ModuleToggleEvent`、`RotationRequestEvent`、`CrystalPlacementEvent`、`PlayerTotemEvent`、`SneakStateChangeEvent`、`PacketEvent.Receive` 重发）。除 `ModuleToggleEvent`、`StepHeightEvent` 外均为 `CancellableEvent` 子类。

无成员事件：`ClientInitializationEvent`、`MinecraftTickEvent`（`INSTANCE`）、`MinecraftDisconnectEvent`、`LocalPlayerTickEvent`、`LocalPlayerPreTickEvent`、`LocalPlayerAiStepEvent`、`LocalPlayerMoveTowardsClosestSpaceEvent`、`BlockUpdateAppliedEvent`、`MultiPlayerGameModeReleaseUsingItemEvent`、`CrystalPlacementEvent`、`AbstractClientPlayerTickEvent`、`PlayerIsStayingOnGroundSurfaceEvent`。

| 事件 | 关键成员 |
| --- | --- |
| `MinecraftRunTickEvent` | `hasPendingTicks` |
| `LocalPlayerTickPhaseEvent` | 子类型 `.BeforePassengerCheck` / `.LocalPlayerTickEvent` |
| `GlintTextureTransformEvent` | `getScale()/setScale(float)`、静态 `resetAndGet()`（单例） |
| `LocalPlayerMoveEvent` | `getVelocity()/setVelocity(Vec3)`、`setHorizontalVelocity(double,double)`、`getX/Y/Z`、`setY(double)`、`getMoverType()/setMoverType`、`getOriginalVelocity()` |
| `LocalPlayerSendPositionEvent` | `getStage()`、`x/y/z/yaw/pitch/onGround` 的 get/set、`isModified()` |
| `InputPressEvent` | `getCode()`、`isMouse()` |
| `KeyboardHandlerCharTypedEvent` | `getText()`、`getCharacter()`（没有 `getCodePoint()`） |
| `KeyboardInputTickEvent` / `.Pre` | `getInput()`、`reset()` |
| `MouseHandlerOnScrollEvent`、`MouseHandlerTurnPlayerEvent` | `horizontalOffset`、`verticalOffset` / `yawDelta`、`pitchDelta` |
| `SneakStateChangeEvent` | `sneaking` |
| `SelectedSlotChangeEvent` / `SlotSetEvent` | `getSlot()` / `getItemStack()`；嵌套 `SelectedSlotChangeEvent.ClientPacketListenerHandleSetHeldSlotEvent` |
| `InventoryActionEvent` | `getStage()` |
| `PacketEvent`（`.Send`/`.Receive`/`.PostSend`） | `getPacket()`；`.Send`/`.Receive` 取消会真正阻断收发 |
| `ChatMessageEvent`（Pre/Post） | `getStage()`、`getMessage()/setMessage()` |
| `ClientPacketListenerSendChatEvent` | `message` |
| `CommandExecuteEvent`、`MacroTriggerEvent` | `getCommand()` / `macro` |
| `ConnectScreenConnectEvent`、`ScreenChangeEvent` | `serverAddress` / `getPreviousScreen()`、`getNewScreen()`；嵌套 `.Before` / `.AfterInit` |
| `ModuleToggleEvent` | record，访问器 `module()` |
| `RotationRequestEvent` | `getPlayerYaw()`、`getPlayerPitch()` |
| `PlayerAttackEvent`、`AuraAttackEvent` | `getStage()`、`target` / `getStage()`、`entityId` |
| `PlayerTotemEvent` | `player`、`popCount`、`kind` |
| `LivingEntityRotationEvent` / `.LivingEntityAiStepEvent`、`LivingEntityJumpFromGroundEvent`、`LivingEntityAddEffectEvent`、`StatusEffectQueryEvent` | `yaw/pitch/jump` / `jumpPower` / `effect` / `effect` |
| `LivingEntityModelSubmitEvent` | `entity`、`lightCoords`、`red/green/blue/alpha` |
| `StepHeightEvent` | `getStage()`、`height`、`isModified()` |
| `BlockCollisionShapeEvent` | `shape`、`pos`、`state` |
| `ClientLevelAddEntityEvent`、`ClientLevelRemoveEntityEvent`、`ClientChunkCacheReplaceWithPacketDataEvent` | `entity` / `id`、`entityPresent` / `chunk` |
| `ClientPacketListenerHandleExplosionEvent`、`...SetEntityMotionEvent`、`...OpenSignEditorEvent`、`...MoveVehicleEvent` | `centerX/Y/Z`、`radius`、`velocityX/Y/Z` / `entityId` / `position`、`sign`、`frontText` / `position` |
| `GameModeUseItemPacketEvent`、`MultiPlayerGameModeUseItemEvent`、`MultiPlayerGameModeUseItemOnEvent` | `packet` / `hand` / `hitResult`、`hand` |
| `MultiPlayerGameModeStartDestroyBlockEvent`、`MultiPlayerGameModeDestroyBlockEvent` | `pos`、`direction` / `pos` |
| `MultiPlayerGameModeHandleInventoryMouseClickEvent` | `containerId`、`slotId`、`button`、`clickType`；嵌套 `.After` / `.AfterStackHash` / `.BeforeContainerClick` |
| `AbstractContainerMenuMoveItemStackToEvent` | `slot` |
| `AbstractContainerScreenEvent`、`AbstractContainerScreenRenderTooltipEvent` | `screen`、`poseStack` / `tooltipComponents`、`x`、`y` |
| `ItemUseEvent` | `getStack()`（仅 `BeforeFinish`）；`.Slowdown` 是另一子类 |
| `VehicleMoveEvent` | 继承 `LocalPlayerMoveEvent` |
| `FishingHookPullEntityEvent`、`FireworkRocketEntityTickEvent` | `entity` / `yaw`、`pitch` |
| `ParticleEngineAddEvent`、`SoundEnginePlayEvent` | `particle` / `soundInstance` |
| `CameraSetupEvent` | `getEntity()`、`getPartialTick()`、`getPosition()/setPosition(Vec3)` |
| `NameTagRenderEvent`、`BlockOutlineRenderEvent` | `poseStack`、`entity` / `poseStack`、`vertexConsumer`、`blockPos`、`blockState` |
| `ModelFeatureRendererRenderModelEvent`、`RenderTypeDrawEvent` | `model`、`getStage()` / `getStage()`、`override` |
| `WorldRenderEvent`、`HudRenderEvent`、`EntityRenderEvent` | `getPoseStack()`、`getPartialTick()` / `getGuiGraphics()` / `getEntity()`、`getCollector()` |

渲染顺序：`WorldRenderEvent.BeforeDepthClear` → `clearDepth` → `WorldRenderEvent.Main` → `clearDepth` → `WorldRenderEvent.Overlay`，pass 内 `WorldRenderEvent.BeforeBufferSource`（`RenderBuffers#bufferSource()` 前）；HUD `HudRenderEvent.Before` → `HudRenderEvent` → `.Unconditional`；实体 `EntityRenderEvent.Pre` → `.Post`。

## 4. 重点事件写法

`PacketEvent` 三个子类：判 `getPacket()` 后 `cancel()`（是否生效看注入点）。世界绘制订 `WorldRenderEvent.Main`，`getPoseStack()` + `WorldBoxRenderer.fillBox/outlineBox(...)`（需要时手动 `flush()`）；屏幕绘制用 `HudRenderEvent` 的 `getGuiGraphics()`/`GuiDrawUtils`（见 `render.md`），HUD 写 `HudWidgetModule`。`ChatMessageEvent` 判 `getStage().isPre()` 后 `setMessage(...)`。

## 5. 指令体系

`Command`（`commands/Command.java`）继承 `NamedFeature` 并实现 `ClientAccess`。构造器 `super("name")`（小写字面量，可 `setAliases(...)`）；抽象方法 `public abstract void exec(LiteralArgumentBuilder builder)`，在这里 `builder.then(...)` 挂子命令。辅助：`Command.literal(name)`、`Command.argument(name, ArgumentType)`、`Command.aliasedLiteral(names...)`、`addSubcommand(command, parent)`、`scheduleRotationTask(Runnable)`、`scheduleScreenChange(Screen)`、`formatText(text)`。注册进 `managers/registry/CommandRegistry` 构造器（`this.registerCommand(new XxxCommand())`，字母序）；前缀 `getPrefix()`/`getPrefixCharacter()`（默认 `;`）；执行 `executeCommand(String input)`；每模块自动有同名开关指令（`registerModuleAliasCommands`）。

常用参数类型（`commands/arguments/` 与 `commands/` 下，非穷举）：`ModuleArgumentType`、`ModuleSettingArgumentType`、`SettingValueArgument`、`BooleanSettingArgument`、`EnumArgumentType`、`ModuleCategoryArgumentType`、`HexColorArgument`、`KeybindArgumentType`、`KitArgumentType`/`MacroArgumentType`/`PresetArgumentType`/`PresetCategoryArgument`、`RegistryArgumentType`/`RegistryEntryArgument`、`PlayerNameArgumentType`、`PositionArgument`/`RelativeFloatArgument`、`ChatFilterArgumentType`/`FancyStringArgument`/`ArrayArgumentType`、`AliasedLiteralArgument`、`EnchantmentIdArgumentType`；`ReifiedArgumentType` 是它们的接口基类。反馈文本写 `evernight.ui.commands.<Command>.<Key>`。

## 6. 管理器速查

| 管理器（`ClientContext.`） | 用途 | 关键方法 |
| --- | --- | --- |
| `rotationManager` | 转向入口 | `requestRotation(float[] angles, int priority)`、`requestRotation(Float yaw, Float pitch, int priority, boolean force)`、`calculateRotations(Vec3 from, Vec3 to)`、`getRotationsToEntity(Entity)`、`getRotationsToBlock(BlockPos)` |
| `serverStateManager` | tick/延迟/回弹 | `getTickRate()`、`getLatencyMillis()`、`hasElapsedSinceSetback(long)`、`isServerSneaking()`、`getServerData()` |
| `worldStateManager` | 预测放置/破坏 | `isPlacementOccupied(BlockPos)`、`hasPendingPlacement(BlockPos)`、`markUnconfirmedBreak(BlockPos)` |
| `inventoryStateManager` | 点击批处理 | `setClickBatching(boolean)`、`isClickRateLimited(...)`、`wouldReachClickLimit(...)` |
| `taskScheduler` | 延迟任务 | `scheduleRotationTask(Runnable, int delay)`、`scheduleRunTickTask(Runnable)`、`scheduleScreenChange(Screen)` |
| `playerTickController` / `tickRateController` | 额外 tick / Timer 倍速 | `requestMultiplier(Module, float)`、`releaseMultiplier(Module)`、`getMultiplier()`；底层 `PlayerTickExecutor.runExtraTick()`（static） |
| `inputOverrides` | 覆盖移动输入 | `requestForward/Sneak/Jump/Sprint(boolean)`、`requestMovement(float, float)` |
| `holeManager` | 洞检测 | `isInHole()`、`findIntersectingHole(BlockPos)`、`intersectsHole(AABB)` |
| `crystalInteractionManager` | 水晶攻击节流 | `attackEntity(int entityId)`、`attackEntity(int, boolean instantAttack)` |
| `swingManager` | 挥手/放置节流 | `swing(InteractionHand, SwingAction)`、`swingPacket(...)` |
| `movementPredictionManager` | 预测位置 | `getPredictedBoundingBox(Player, int tickIndex)`、`getAdjustedLocalMotion()` |
| `movementSpeedTracker` | 速度统计 | `getCurrentSpeed()`、`getAverageSpeed()`、`getDisplaySpeed()` |
| `fakeplayerManager` | 假人录制/回放 | 假人生成、录制与回放（`FakeplayerScreen` / `;fakeplayer`） |
| `screenshotCredentialStore` | 截图上传凭据 | `evernight/screenshot.json`，模块设置中排除 |
| `soundManager` | 音效 | `playSound(NamedIdentifier, float volume)`、`playSound(NamedIdentifier, Vec3, float)` |
| `webhookManager` | Discord/Telegram | `isConfigured()`、`sendContent(String)`、`setTelegram(token, chatId)` |
| `chatFilterRegistry` / `socialRegistry` / `waypointRegistry` / `macroRegistry` / `kitRegistry` / `stashRegistry` / `tradeRegistry` / `aliasRegistry` / `autoPresetRegistry` | 持久化注册表 | 见 `module-settings.md` |
| `blockBreakTracker` / `playerStateTracker` | 破坏进度 / 玩家状态 | 见 `utils.md` |
| `totemPopTracker` / `frameTiming` / `baritoneBridge` / `projectionSource` | 图腾 / 帧耗时 / Baritone / Litematica 投影 | 见类；`isAvailable()` 与 `Setting#hideWithoutBaritone()` |

## 7. 发包：PacketSender

`utils/network/PacketSender.java`（`public final class`，全 static）：

| 方法 | 用途 |
| --- | --- |
| `send(Packet packet)` / `sendDirect(Packet packet)` | 走连接发包（受 PingSpoof 影响） / 绕过排队直接发 |
| `sendUseItemOn(InteractionHand hand, BlockHitResult hitResult)` / `sendUseItem(InteractionHand hand)` / `sendUseItem(InteractionHand hand, float yaw, float pitch)` | 右键方块 / 使用物品 / 带转向使用 |
| `sendSwing(InteractionHand hand)` / `sendAttack(int entityId)` | 挥手 / 攻击实体 |
| `sendRotation(float yaw, float pitch, boolean onGround)` / `sendRotation(float[] rotation, boolean onGround)` | 只发转向包 |
| `sendPosition(double x, double y, double z, boolean onGround)` / `sendPositionRotation(...)` | 位置 / 位置+转向 |
| `sendPlayerAction(Action, BlockPos, Direction)` / `sendPlayerCommand(ServerboundPlayerCommandPacket.Action)` | 挖掘/放置 / 状态包 |
| `sendStartFallFlying()`、`sendSwapOffhand()`、`sendSelectedSlot(int slot)` | 常用动作 |
| `markImportant(Packet)`、`nextPredictionSequence()` | 配合 `ImportantPacket` 与预测序号 |

一般业务用 `send(...)`；精确时序/绕过模块处理用 `sendDirect(...)`。

## 8. 反馈：聊天 / 通知 / 声音

- **聊天栏**：`ChatUtils.printChat(Component, MessageSignature)`（自动加 `[Evernight]` 前缀并切主线程）；另有 `printChat(Component, int id)`、`printChat(Component, MessageSignature, ChatUtils.MessagePriority)`、`ChatUtils.DEFAULT_SIGNATURE`、`moduleSignature(Module)`。
- **屏幕角落通知**：由 `modules/hud/Welcomer/WelcomerModule` 管理（`List<HudNotification>`，子类 `GreetingNotification`/`LagNotification`/`MusicNotification`）；**没有全局 `notify()` 单例**，新类型在 HUD 模块里持有列表或用 `ChatUtils`。
- **常驻 HUD** 写 `HudWidgetModule`（见 `module-settings.md`）；**音效** `ClientContext.soundManager.playSound(new NamedIdentifier(...), volume)`，内置音效见 `audio/BuiltinSound`。

## 9. 集成与版本兼容

| 入口 | 说明 |
| --- | --- |
| `ClientContext.baritoneBridge` | `BaritoneBridge`，`isAvailable()` 判空；未装为 `DisabledBaritoneBridge` |
| `ClientContext.waypointAdapter` / `ClientContext.waypointSync` | `WaypointAdapter`（Xaero 读写）/ `WaypointSync`（双向同步，开关 `WaypointSettingsModule`）；未装分别为 `DisabledWaypointAdapter` / `null` |
| `ClientContext.projectionSource` | `ProjectionSource`（Litematica 投影）；未装为 `DisabledProjectionSource` |
| `integrations/ProtocolCompatibility` / `ProtocolVersionAccess` | 协议兼容判定（ViaFabricPlus）/ 读 ViaFabricPlus 的**目标**协议版本（不是服务端真实版本），用于 `olderEq_1_20_6`/`1_21_4`/`1_21_5` 判断 |

集成先判 `isAvailable()`，不要硬依赖；设置项用 `Setting#hideWithoutBaritone()` 或 `hideUnlessModLoaded(...)` 隐藏。
