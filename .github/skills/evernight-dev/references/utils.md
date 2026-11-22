# Evernight 工具类 —— client/utils 全量速查

适用范围：要在 Evernight 里新增模块 / 新增界面 / 复用既有工具类时，先在这里查到真实类名、签名与调用方式，再照抄代码。
覆盖 `src/main/java/evernight/client/utils/` 下全部 120 个 Java 文件（chat、collections、combat、entity、
inventory、math、movement、network、world 9 个子包 + 根目录）。只写**真实存在**的 public API，路径均相对仓库根。

## 目录

- [1. 使用前提](#1-使用前提)
- [2. utils 根目录](#2-utils-根目录)
- [3. utils/chat](#3-utilschat)
- [4. utils/collections](#4-utilscollections)
- [5. utils/combat](#5-utilscombat)
- [6. utils/entity](#6-utilsentity)
- [7. utils/inventory](#7-utilsinventory)
- [8. utils/math](#8-utilsmath)
- [9. utils/movement](#9-utilsmovement)
- [10. utils/network](#10-utilsnetwork)
- [11. utils/world](#11-utilsworld)
- [常见任务速查](#常见任务速查)

## 1. 使用前提
工具类大多 `implements ClientAccess`（纯算法类如 `FileUtils`/`ElapsedTimer`/`InterpolatedFloat` 不实现）；无论是否实现，都能直接用它的接口静态字段拿客户端实例（`src/main/java/evernight/client/ClientAccess.java:17`）：

```java
Minecraft mc = ClientAccess.evernightMinecraft;            // Minecraft.getInstance()
boolean noWorld = mc.player == null || mc.level == null;   // 世界没就绪时别调工具类
```

- 全局管理器走 `ClientContext`（`src/main/java/evernight/client/ClientContext.java:48`）：`rotationManager`、`playerStateTracker`、`inventoryStateManager`、`worldStateManager`、`socialRegistry`、`swingManager`、`blockBreakTracker`、`movementSpeedTracker`、`totemPopTracker`、`frameTiming`、`mapDataCache`。
- 取模块实例用 `ClientContext.getModule(XxxModule.class)`；注册表尚未初始化时可能返回 `null`。
- 线程：除特别标注处，所有方法**只能在客户端线程**调用（读 `level`/`player`/GUI 状态）；标了「任意线程」的可以跨线程。
- 构造器都是 `private`。实例类（`BlockBreakTracker`、`PlayerStateTracker`、`MovementSpeedTracker`、`MapDataCache`、`NukerBlockQueue`）构造时自己订阅事件/注册，**不要 new 第二个**，从 `ClientContext` 取。

## 2. utils 根目录
源码目录：`src/main/java/evernight/client/utils/`

| 类 | 用途 | 关键 public 静态方法 |
| --- | --- | --- |
| `ClampedInterpolatedFloat` | 0~1 范围的 InterpolatedFloat（开关动画） | 继承 `InterpolatedFloat`，另有 `setTarget(boolean)`、`setValue(boolean)`、`reset()` |
| `CompressionUtils` | zlib 压缩 | `inflate(byte[])`、`deflate(byte[], int level)` |
| `Experimental` | 实验功能注解 | 标注类/字段，配合 `ExperimentalFeatures` 隐藏 |
| `ExperimentalFeatures` | 实验功能开关（启动参数 `--evernight-allow-experimental` 或 `-Devernight.allowExperimental=true`） | `isExperimentalDisabled()`、`isFieldHidden(Field)`、`isClassHidden(Class)`、`unregisterHiddenSettings(Module)` |
| `FrameTiming` | 帧率与插值系数（`ClientContext.frameTiming`） | `recordFrame()`、`getFrameFactor(float rateScale)`、`getFramesPerSecond()` |
| `ServerAddressUtils` | 当前服务器地址 | `getCurrentServerAddress()`（失败/本地返回 `singleplayer`） |
| `SoftwareKeyMapping` | 可程序控制的 KeyMapping | 继承原版 `KeyMapping`，覆写 `isDown()`/`setDown(boolean)` |
| `Toggleable` | 开关接口 | `enable()`、`disable()`、`isToggled()`、默认 `toggle()`/`setToggled(boolean)` |
| `UncheckedExceptions` | sneaky throw | `<T extends Throwable> RuntimeException rethrow(Throwable)` |

### `ElapsedTimer` — 毫秒计时器（`.../utils/ElapsedTimer.java:5`）
- `reset()`、`hasElapsed(long millis)`、`hasElapsedAndReset(long millis)`、`elapsedMillis()`、`clear()`、`setTime(long|double, TimeUnit)`，另有 `hasElapsed(double, TimeUnit)` 带单位重载。
- **语义陷阱**：字段初值 `lastResetMillis = -1`，新建或 `clear()` 后 `elapsedMillis()` 是极大值、`hasElapsed(x)` 立刻为 `true`；要「等一段时间」请先 `reset()`。
- 非线程安全（普通 `long` 字段），只在客户端线程用。

### `InterpolatedFloat` — 逐帧平滑的 float（`.../utils/InterpolatedFloat.java:9`）
- 构造：`(float speed)`、`(float speed, boolean easingEnabled)`、`(Supplier<Float> speedSupplier, boolean easingEnabled)`。
- `setTarget(float)` 设目标（值不变时不动）；`updateAndGet()` **每帧调一次**并返回当前插值；`setValue(float)` 瞬间赋值；`setEasingEnabled`/`setSpeedSupplier`/`getSpeedSupplier`。
- 依赖 `ClientContext.frameTiming`，须在客户端初始化后使用；FPS ≤ 15 时直接吸附到目标值。

```java
private final InterpolatedFloat width = new InterpolatedFloat(0.25F);   // 速度越大越快
width.setTarget(expanded ? 120.0F : 0.0F);                             // 状态/布局变化时
float renderedWidth = width.updateAndGet();                            // 渲染每帧取一次
```

### `FloatAnimation` — 定时线性补间（`.../utils/FloatAnimation.java:7`）
- `setTarget(float target, long durationMillis)`、`setTarget(boolean target, long durationMillis)`（true=1/false=0）、`setValue(float)` 立即生效。
- `getValue()` 按 `System.currentTimeMillis()` 线性插值；`getValue(Function easing)` 接受缓动函数。不依赖帧率，适合固定时长动画。

### `InputUtils` — 改写本地输入状态（`.../utils/InputUtils.java:7`）
- `setSneak/setJump/setForward/setBack/setLeft/setRight/setSprint(ClientInput, boolean)`：直接改写 `ClientInput.keyPresses`（mixin accessor），**不自己发包**，由后续 tick 的移动/输入包体现。
- `setForwardMovement(ClientInput, float)`/`setSidewaysMovement(ClientInput, float)` 改 `moveVector`；`clearKeyPresses(ClientInput)` 全清；`createInput()` 造一个 `moveVector=(0,1)` 的空输入。真实用例：`client/managers/player/InputOverrideManager.java:104`。

```java
ClientInput input = ClientAccess.evernightMinecraft.player.input;
InputUtils.setForward(input, true);
InputUtils.setSprint(input, true);
InputUtils.setJump(input, true);
```

### `KeyUtils` — 物理按键/鼠标与键名（`.../utils/KeyUtils.java:17`）
- `isPhysicallyDown(KeyMapping|Keybind)`、`isPhysicalKeyDown(int)`、`isPhysicalMouseButtonDown(int)`：直接问 GLFW，绕过原版 KeyMapping 的 GUI 影响。
- `isShiftDown()`、`isControlDown()`、`isAltDown()`：只测**左**侧键。
- `parseKeybind(String)` → `evernight.client.modules.Keybind`：支持 `mouse3`、别名 `shift/ctrl/alt/rshift/rctrl/ralt/capslock`、`key.keyboard.<name>`（`_` 转 `.`）；解析失败抛 `IllegalArgumentException`。
- `getKeyName(int keyCode, boolean mouse)` / `getKeyName(Keybind)`：负数返回 `NONE`，鼠标返回 `MOUSE<n>`；`getKeyAliases()` 返回别名表。

### `FileUtils` — UTF-8/字节读写（`.../utils/FileUtils.java:13`）与 `LockUtils`（`.../utils/LockUtils.java:6`）
- `readBytes(Path)`、`writeBytes(Path, byte[])`、`readUtf8(Path)`、`writeUtf8(Path, String)`，都抛 `IOException`；`findExistingWithSuffixes(Path, String...)` 找带后缀的已存在文件。配置读写范例见 `client/managers/config/ConfigurationManager.java:73`。
- `LockUtils`：`runWithSharedLock(Runnable)`、`wrapWithSharedLock(Runnable)`、`runWithLock(Lock, Runnable)`、`wrapWithLock(Lock, Runnable)`；锁内不要做长阻塞。

## 3. utils/chat
源码目录：`src/main/java/evernight/client/utils/chat/`

| 类 | 用途 | 关键 public 成员 |
| --- | --- | --- |
| `ChatFilterEntry` | 聊天过滤器条目（`ChatFilterRegistry` 的数据载体） | `record ChatFilterEntry(String id, String filter)` |

### `ChatUtils` — 聊天输出/前缀/水印（`.../utils/chat/ChatUtils.java:30`）
- `printChat(Component message, MessageSignature signature)`：**真正的聊天反馈入口**，自动加 `[Evernight]` 前缀并投递到主线程（内部 `Minecraft.execute`，**任意线程可调**）。
- `printChat(Component, int id)` 用数字生成签名；`printChat(Component, MessageSignature, MessagePriority)` 额外加彩色 `[!]` 前缀（`MessagePriority.HIGH/MID/LOW`）。
- 签名：常量 `DEFAULT_SIGNATURE`、`createSignature(int)`、`moduleSignature(Module)`、`objectSignature(Object)`。
- `sendChatOrCommand(String)`：以 `/` 开头走 `sendCommand`，否则走 `sendChat`（**真的以玩家身份发出**）。
- 装饰：`createPrefix()`、`bracketedText(String)`、`watermarkText(String)`、`rainbowText(String, Supplier<Integer>, Color)`、`withDynamicColor(Style, Supplier<Integer>)`、`suggestCommand(String)` → `ClickEvent`。
- 私聊判定：`isIncomingWhisper(String)`、`isOutgoingWhisper(String)`、`INCOMING_WHISPER_PATTERNS`/`OUTGOING_WHISPER_PATTERNS`、`MESSAGE_TAG`。
- 玩家可见文本要走语言表：`Component.literal(Translations.ui(key, fallback))`。

```java
ChatUtils.printChat(
        Component.literal(Translations.ui("modules.Example.Done", "Done.")),
        ChatUtils.DEFAULT_SIGNATURE);
```

## 4. utils/collections
源码目录：`src/main/java/evernight/client/utils/collections/`

| 类 | 用途 | 关键 public 成员 |
| --- | --- | --- |
| `IntPair` | 整数对 | `record IntPair(int first, int second)`；`add(IntPair)`、`distanceTo(IntPair)`、`squaredDistanceTo(IntPair)` |
| `ObjectPair` | 任意对象对 | `record ObjectPair(Object first, Object second)` |
| `NamedIdentifier` | 带分类的 Identifier | `new NamedIdentifier(String)` / `(String category, String name)`；`getCategory()`、`getName()`；常量 `NONE` |
| `NamedRegistryEntry` | 注册表条目 | `record NamedRegistryEntry(String name, Object value)` |
| `RegistryGroupMatcher` | 注册表「分组名 → 条目」 | `forRegistry(Registry)`、`getRegistryId()`、`getGroupNames()`、`getGroupEntries(String)`；`MATCHERS`、`EMPTY` |
| `EmptyRegistryGroupMatcher` | 空实现 | 同接口，返回空集合 |

两个实现不在本包：`utils/world/BlockRegistryGroupMatcher`、`utils/inventory/ItemRegistryGroupMatcher`。

## 5. utils/combat
源码目录：`src/main/java/evernight/client/utils/combat/`
**本包没有 `CombatUtils`**：战斗通用能力分别是 `DamageUtils`（伤害）、`RaycastUtils`/`BlockRaycastContext`（射线）、`TargetSelector`（选目标）、`BoundingBoxUtils`（包围盒）。全部是纯计算/查询，**不发包、不改世界**。

### `DamageUtils` — 爆炸伤害预测（`.../combat/DamageUtils.java:40`）
- `getCrystalDamage(Vec3 explosionPosition, LivingEntity target[, AABB bounds])` → 水晶伤害，内部 power = **6.0**；`getBedDamage(...)` → 床/锚伤害，power = **5.0**。
- `getExplosionDamage(Vec3 pos, LivingEntity target, AABB bounds, double power, boolean ignoreLowResistance, BlockPos ignoredPosition, BlockPos solidPosition)`（另有 `Collection ignoredPositions` 重载）：完整公式（难度缩放 → 护甲 → 抗性 → 盾牌格挡）。
- `getExposure(Vec3 explosionPosition, Entity target, AABB bounds, Collection ignoredPositions, BlockPos solidPosition, boolean ignoreLowResistance)` → 可见采样比例 0~1（内部走 `RaycastUtils` + `BlockRaycastClassifier`）。
- `applyDamageReduction(LivingEntity, float)`、`scaleForDifficulty(float)`、`getDurabilityPercent(ItemStack)`（1~100）。
- **副作用**：读 `AutoCrystalModule.assumeBestArmorSetting` 与 `level.getDifficulty()`；不改状态。

```java
float damage = DamageUtils.getCrystalDamage(crystalPosition, target);              // 对目标的预测伤害
float selfDamage = DamageUtils.getCrystalDamage(crystalPosition, ClientAccess.evernightMinecraft.player);
```

### `RaycastUtils` — 方块/实体射线（`.../combat/RaycastUtils.java:32`）
- `raycast(BlockRaycastContext)` → `BlockHitResult`：支持「忽略/强制实心方块」分类器的射线（原版 `level.clip` 做不到）。
- `raycastFromRotation(Vec3 origin, float yaw, float pitch, float range)` → `HitResult`：只测方块、忽略流体、不含实体。
- `raycastWithEntityTrace(LocalPlayer)` → `HitResult`：走原版 `player.raycastHitResult`，期间临时压制 Reach 的 noEntityTrace（**全局副作用，用完自动复位**）。
- `getGroundDistance()`（int；下方无方块会测到世界底，结果不可用）、`getCollisionBlockYOffset()`、`hasVisibleEntitySample(Entity)`、`isLineVisible(Vec3 start, Vec3 end)`（只判方块遮挡）。

### `TargetSelector` — 实体目标筛选器（`.../combat/TargetSelector.java:27`）
- 非静态、builder 风格：`new TargetSelector()` 链式设置后 `select()` 返回最佳 `Entity`（无目标返回 `null`）。
- 开关：`withPlayers/withNeutrals/withAnimals/withVillagers/withHostiles/withCrystals/withProjectiles/withVehicles(boolean)`。
- 距离：`range(float)`（同时设可见/隔墙）、`visibleRange(float)`、`wallRange(float)`、`useExtraRange(boolean)`；其他：`ignoreNamed`、`ignoreNakeds`（要求有甲）、`ignoreInvisibles`、`sortBy(Comparator<Entity>)`、`filter(Predicate<Entity>)`。默认排序是到玩家距离平方升序。
- **坑**：默认 `visibleRange = wallRange = 0`，不调 `range()` 永远选不到东西；`select()` 内部用 `WorldUtils.isTargetable` 已排除自己/好友/死亡。

```java
Entity target = new TargetSelector().withPlayers(true).withHostiles(false).range(4.5F).select();
if (target != null) { /* ... */ }
```

### `RotationRequest` — 转向请求数据（`.../combat/RotationRequest.java:3`）
- `record RotationRequest(float[] angles, int priority)`，`angles = {yaw, pitch}`；只是数据载体，由 `RotationManager` 消费：新请求只有 `priority >= 当前请求` 才替换。
- 一般别直接 `new`，用 `ClientContext.rotationManager.requestRotation(float[] angles, int priority)`（或带 `boolean force` 的重载）；角度用 `RotationManager.getRotationsToPosition(Vec3)` 算；当前请求用 `getRequest()` 读。

```java
float[] angles = RotationManager.getRotationsToPosition(hitPoint);
ClientContext.rotationManager.requestRotation(angles, 100);   // 优先级越高越不容易被覆盖
```

| 类 | 用途 | 关键 public 成员 |
| --- | --- | --- |
| `BoundingBoxUtils` | AABB 工具（战斗/放置高频） | `blockCornerInset`、`getInterpolationBounds(LivingEntity)`、`getRenderBounds(Entity, float)`、`hasVolumeOverlap(AABB, AABB)`、`clampToBounds(Vec3, AABB)`、`getBlockShapeBounds/getBlockCollisionBounds(BlockPos)`、`placeAtBlock(AABB, BlockPos)`、`getBottomCenter/getTopCenter/getLowerQuarterCenter(AABB)`、`recenter(AABB, Vec3)`、`intersectsBlock(AABB, BlockPos)`、`getPredictionTicks(Setting<Integer>)` |
| `BlockRaycastContext` | 射线上下文 | `new BlockRaycastContext.Builder(start, end).withEntity(Entity).withClassifier(s).build()`；`getStart/getEnd/getClassifier/getCollisionContext/getBlockShape(...)` |
| `BlockRaycastClassifier` | 射线方块判定 | `NO_OVERRIDE`、`IGNORE_LOW_RESISTANCE`、`test(BlockPos, BlockState)`、`ignorePosition(BlockPos)`、`ignorePositions(Collection|Iterable)`、`solidPosition(BlockPos)`、`solidPositions(Iterable)`、`firstNonNone(...)` |
| `BlockRaycastOverride` | 分类器返回值 | `enum {NONE, IGNORE, SOLID}` |
| `TotemPopTracker` | 图腾计数（`ClientContext.totemPopTracker`） | `getPopCount(Player)`、`getPopCount(String name)` |
| `ExtraBreakTarget` | 多模块共享的额外破坏进度 | `getPosition()`、`getClampedProgress()`、`addProgress(float)`、`setProgress(float)` |
| `PendingPlacement` | 待确认放置记录 | `record PendingPlacement(long createdAtMillis, Block block)` |
| `EndCrystalState` / `RemotePlayerDamageAccess` / `ZombifiedPiglinAttackState` | mixin 状态/访问接口 | 由 mixin 实现，**不要自己实现**（如 `EndCrystalState#getSpawnTime()`、`evernight$isAttacking()`） |

## 6. utils/entity
源码目录：`src/main/java/evernight/client/utils/entity/`

### `EntityUtils` — 实体分类/血量（`.../entity/EntityUtils.java:46`）
- `isHostile(Entity)`：末影人 `isCreepy()`、僵尸猪灵攻击中、狼的 angry 纹理、中立怪 `isAngry()`、猪灵 `armPose`、`Enemy`/`PolarBear` 都算敌对。`isNeutral(Entity)`、`isPassive(Entity)`、`isLookingAtPlayer(Entity)`。
- `getHealthWithAbsorption(Entity)` → 血量+吸收（非 `LivingEntity` 返回 `2.0F`）；`getHealthFraction(LivingEntity)` → clamp 0~1；`getPlayerHealthWithAbsorption()`。
- `hasCollisionBelow(LivingEntity)`、`hasFirework(Entity shooter)`、`getFireworkPositions()`、`isFirstPersonCameraEntity(Entity)`。
- `setStepHeight(LivingEntity, float)` / `getStepHeight(LivingEntity)`：改 `Attributes.STEP_HEIGHT` 的 base value（**会残留**，用完还原）。

### `PlayerStateUtils` — 本地玩家状态（`.../entity/PlayerStateUtils.java:44`）
- 移动：`getMovementDirection()` → `Direction`、`getMovementVector()` → 水平 `Vec3`、`getBodySampleHeights()` → `float[3] = {1.62, 1.27, 0.4}`（**内部数组别改**）、`streamBodySamplePositions()`（按 `Attributes.SCALE` 缩放）。
- 姿势/地形：`isLowProfile(Player)`、`isLocalLowProfile()`、`isLocalHeightBelowOne()`、`isInCobweb(Player)`、`isInWater(Player)`、`isInLiquid(Player)`、`getRoundedBlockPosition(LivingEntity)`、`getLocalRoundedBlockPosition()`。
- 装备/状态：`hasArmor(LivingEntity)`、`isWearingElytra(Player)`、`isLocalWearingElytra()`、`isEnderPearlOnCooldown()`、`isHungerLimited()`（饱食度 ≤ 6 且非创造/旁观）、`getTrackedFallDistance()`。
- **`isFallFlying()` 有副作用**：临时把 `ElytraFlyModule.hook` 置 true 再读，会短暂影响鞘翅模块。
- 占位：`getOccupiedBlockPositions(LivingEntity)`、`getOffsetBlockPositions(LivingEntity, List<? extends Vec3i>)`、`getSurroundPositions(LivingEntity)`（脚下邻格，洞填充/围脚类模块广泛使用）；`oppositeHand(InteractionHand)`。

```java
Set<BlockPos> surround = PlayerStateUtils.getSurroundPositions(target);   // 目标脚边可占位格
boolean wearingElytra = PlayerStateUtils.isLocalWearingElytra();
```

### `PlayerStateTracker` — 发包侧玩家状态（`.../entity/PlayerStateTracker.java:20`，实例在 `ClientContext.playerStateTracker`）
- 读：`isPacketOnGround()`、`getPacketPosition()`、`getPacketRotations()`（`{yaw,pitch}`，从最后发出的移动包解出）、`getLastSentInput()`、`getGroundedTicks()`/`getAirborneTicks()`、`getFallDistance()`、`getTickDisplacement()`、`getCurrentTickY()`/`getPreviousTickY()`/`getLastGroundedY()`、`getFallFlyingTimer()`。
- 写：`setSneaking(boolean)`（发 `SneakStateChangeEvent`，可被取消，最终调 `ProtocolCompatibility.setSneaking`）、`setFallDistance(float)`、`runSneaking(Runnable)`/`runWithoutSneaking(Runnable)`（**会改真实发包状态**）。
- 它自己订阅事件，**不要 new**。

| 类 | 用途 | 关键 public 成员 |
| --- | --- | --- |
| `PlayerState` | 注入 `Player` 的扩展状态接口（`mixin/PlayerMixin.java:48`） | `evernight$setRole(SocialRole)`、`evernight$getRole()`、`evernight$isNextToWall()`、`evernight$wasCrawling()`、`evernight$getHealthFactor()`、`getItemUseProgress(ItemStack, LivingEntity)` |
| `SwingAction` | 挥手类型 | `enum {INTERACT, MINING, HIT}`；`isEnabled(AntiCheatModule)` 按反作弊设置判断该类挥手是否发包 |
| `SidePlayer` | 侧影假玩家实体（FakeLag/Chams/LogoutSpots） | `SIDE_PLAYER_TAG`、`restoreFrom(Entity)`、`readSnapshot(DataInputStream)`、`copyPlayerState(Player)`；**模块内部，不要复用** |

## 7. utils/inventory
源码目录：`src/main/java/evernight/client/utils/inventory/`

### `InventoryUtils` — 槽位查找与切换（`.../inventory/InventoryUtils.java:30`）
- 选中/点击：`selectHotbarSlot(int 0..8)`（改 selected slot 后调 `MultiPlayerGameModeAccessor.sync()`，即原版 `ensureHasSentCarriedItem()`，必要时发出 selected-slot 包）、`selectHotbarSlotForPlacement(int)`、`swapWithHotbarSlot(int menuSlot, int hotbarSlot)`、`swapMenuSlots(int, int)`、`swapSlotsWithPickup(int, int)`、`clickSlot(int slot, int button, ClickType)` / `clickSlot(int containerId, int slot, int button, ClickType)`、`getSelectedSlot()`、`getCarriedStack()`。
- 查找（`-1` = 没找到）：`findHotbarSlot(Item...)` / `(Item)` / `(Predicate<ItemStack>)`（先看主手）、`findHotbarSlotInItemOrder(Item...)`、`findInventorySlot(Item...)` / `(Predicate, boolean excludeHotbar)`（背包索引 0~35 倒序）、`findMenuSlot(Item[, includeCrafting])` / `(Item, Predicate, boolean)`（容器槽，快捷栏 0~8 映射 36~44）。
- `findMenuSlot` 返回 `-1` 有两种含义：没找到，或「物品正好在副手且没有待处理的本地实体事件」（此时不用换槽）；索引换算用 `toMenuSlot(int)`。
- 数量/耐久：`countItems(Predicate<ItemStack>)`（含合成格）、`getDurabilityPercent(ItemStack)`（剩余百分比，不可损坏 100）、`getLowestArmorDurabilityPercent(Player)`。
- 其他：`isInventoryFull()`、`isHotbarFull()`、`canEquip(EquipmentSlot, ItemStack)`（看 `EQUIPPABLE`，鞘翅算 CHEST）、`getHandForItem(Item)`、`findBestToolSlot(BlockPos, boolean includeInventory)`、`isEnderChestScreen(Screen)`。
- 静默换槽（推荐）：`findSwapSlot(Item|Predicate, boolean includeInventory)` → `SwapSlot`，再 `runWithSwap(previousSlot, slot, action)`；另有 `swapTo(SwapSlot)`、`swapBack(int, SwapSlot)`。`record SwapSlot(int slot, boolean hotbar, InteractionHand hand)` 提供 `isFound()`/`getStack()`/`isInHand()`，副手命中是 `(-2, false, OFF_HAND)`。

```java
InventoryUtils.SwapSlot slot = InventoryUtils.findSwapSlot(Items.END_CRYSTAL, true);
if (slot.isFound()) {
    int previousSlot = InventoryUtils.getSelectedSlot();
    InventoryUtils.runWithSwap(previousSlot, slot, () -> {
        PacketSender.sendUseItemOn(InteractionHand.MAIN_HAND, hitResult);   // 此时主手已是水晶
    });
}
```

### `EnchantmentUtils` — 附魔等级（`.../inventory/EnchantmentUtils.java:27`）
- `getLevel(ResourceKey enchantment, ItemStack)` / `(ResourceKey, EquipmentSlot, LivingEntity)` / `(ResourceKey, EquipmentSlot)`（最后一个查本地玩家）→ `int`，空物品/无附魔返回 0；附魔书自动读 `DataComponents.STORED_ENCHANTMENTS`。
- `hasEnchantment(...)` 三种重载 = `getLevel > 0`；`getLevels(ItemStack)` → `Map<ResourceKey, Integer>`。
- `getHolder(ResourceKey|String)` → `Holder`（原始类型；查不到 `null`）；`getKey(String)` → `ResourceKey`（默认命名空间 `minecraft:`）。

### `ItemUtils` — 物品分类（`.../inventory/ItemUtils.java:25`）与 `EquipmentUtils`（`.../inventory/EquipmentUtils.java:25`）
- `ItemUtils`：`isSword/isPickaxe/isAxe(ItemStack)` 走标签；`isSpear`/`isMace(ItemStack)` 兼容服务器自定义名；`isGlass(Item)`、`isShulkerBox(Item)`、`isSmithingTemplate(Item)`、`providesTrimMaterial(Item)`、`isFood(Item|ItemStack)`；`getFireworkDuration(ItemStack)` → `(flightDuration+1)*10+1`。
- `EquipmentUtils.getArmorStacks(Player)` → 4 件护甲 `List<ItemStack>`；`hasRequiredEnchantments(ItemStack)` 按槽位校验固定附魔组合（头/胸=保护4+耐久3+经验修补1，腿=爆炸保护4 同前，靴子再加摔落4；武器=锋利5+火2+击退2+横扫3+耐久3+经验修补1；鞘翅=耐久3+经验修补1）；`isRepairMaterial(ItemStack, Item)` 依据 `REPAIRABLE`。

### `PotionUtils` — 药水识别（`.../inventory/PotionUtils.java:27`）
- `getPotion(ItemStack)` → `Potion`（读 `DataComponents.POTION_CONTENTS`），无组件返回 `MISSING_POTION`（名字 `empty`）；`getEffectsByParticleColor(LivingEntity)` → `Set<Holder<MobEffect>>`（从药水粒子颜色反查效果）。

| 类 | 用途 | 关键 public 成员 |
| --- | --- | --- |
| `ArmorSlot` | 护甲槽枚举 | `getInventoryIndex()`、`getArmorIndex()`、`getContainerSlot()` |
| `AutoMineProvider` / `AutoMineProviderBase` / `AutoMineFeetProvider` / `AutoMineHeadProvider` / `AutoMineTerrainProvider` / `AutoMineCandidates` | AutoMine 候选方块收集 | `collectCandidates(AutoMineCandidates)`、`isEnabled()`；**模块内部** |
| `ChestSearchController` | 箱子搜索栏（`INSTANCE`） | `isFull(ItemStack, String)`、`namesMatch(ItemStack, String)`、`onDrawGui(...)`、`afterInit(...)`；**模块内部** |
| `ContainerTooltipComponent` | 容器/潜影盒 tooltip 渲染 | `record ContainerTooltipComponent(List<ItemStack> items)` + `getHeight/getWidth/renderImage`；**模块内部** |
| `CustomItemRegistries` | 自定义注册表（食物/锻造模板/纹饰材料） | `foodItems`、`smithingTemplates`、`trimMaterials`、`initialize()` |
| `ExtraBreakTracker` | 记录自己额外破坏的方块 | `trackBreak(BlockPos)`、`isTracked(BlockPos)`、`pruneExpired()`；**模块内部** |
| `InventorySortStrategy` | 背包整理策略接口 | `getSourceSlots(InventorySorterScopeChoice)`、静态 `getInventoryStack(int)` |
| `Kit` | 预设背包组合（`KitRegistry`） | `getName()`、`getItemsBySlot()`、`matches(ItemStack)` |
| `ShulkerBoxItemPreview` | 潜影盒预览 | `record ShulkerBoxItemPreview(ItemStack containerStack, ItemStack previewStack)` + `create(ItemStack)` |
| `TradeDefinition` / `TradeEnchantmentEntry` / `TradeOfferSnapshot` | 交易系统数据（`TradeRegistry`） | `TradeDefinition#matches(TradeOfferSnapshot)`、`TradeOfferSnapshot.fromOffer(MerchantOffer)`、`OUT_OF_STOCK` |
| `ItemRegistryGroupMatcher` / `ItemRegistryJsonAdapter` | 物品注册表匹配 / Gson 适配器 | `getGroupEntries(String)`；适配器已注册进 `ClientAccess.gson`，一般不用直接调 |
| `RegistryContainer<T>` / `CharacterConsumer` | 可注册集合接口 / 字符消费函数 | `register(T)`、`unregister(T)`、`getRegistry()`；`accept(char)` |
| `NoSlowInventoryMode` / `NoSlowInventoryNcpMode` | NoSlow 背包交互模式 | 事件回调；**模块内部** |
| `NoOpVertexConsumer` | 空 `VertexConsumer` 单例 | `INSTANCE` |
| `PalettedContainerAccess` / `PalettedContainerDataAccess` | 区块调色板 mixin 接口 | 由 mixin 实现，**不要自己实现** |

## 8. utils/math
源码目录：`src/main/java/evernight/client/utils/math/`

### `MathUtils` — 通用数学（`.../math/MathUtils.java:25`）
- 距离/角度：`distanceToCamera(Vec3)`、`distanceToBlockCenter(BlockPos)`、`cameraRotationDifference(Vec3)`、`yawDifferenceToPlayer(Player)`、`directionFromPitchYaw(float pitch, float yaw)` → 单位 `Vec3`（**pitch 在前**）、`wrapAngle(float)` → 0~360。
- 补间：`interpolateFrame(double start, double end)` / `(float, float)` 用 `RenderUtils.getPartialTick()`；`lerp(double start, double end, double delta)`（**顺序是 start/end/delta**，不是 `Mth.lerp` 的 delta 在前）。
- 取整/区间：`floorToDecimals(double|float, int)`（BigDecimal FLOOR，负数抛异常）、`rangeProgress(float value, float lower, float upper)`（自动交换边界，0~1）、`nearestBound(double, double, double)`。
- 随机：`randomFloat(float min, float max)`、`randomInt(int min, int max)`、`passesRandomThreshold(int threshold)`（**threshold=0 永远 false**，即「0 关闭」）。
- 几何：`faceCorners(BlockPos, Direction)` → 该面 4 个角 `List<Vec3>`；`getDistanceScale(Vec3 target, double scale)`；`getPositionDelta(Entity)` → 本 tick 位移。

### `Vec3Utils` — Vec3 小工具（`.../math/Vec3Utils.java:6`）
- `setComponents(Vec3 vector, double x, double y, double z)`：**原地修改**传入的 Vec3（依赖 `Vec3Accessor` mixin），只能给自己新建的临时对象用；`horizontalDistance(Vec3, Vec3)` → `float`；`lerp(float fraction, Vec3 start, Vec3 end)`。

| 类 | 用途 | 关键 public 成员 |
| --- | --- | --- |
| `MathConstants` | 常量 | `FORTY_FIVE_DEGREES`、`NINETY_DEGREES`、`FULL_TURN_DEGREES`、`PI`、`ONE_HALF`/`HALF`、`ONE_QUARTER`、`RADIANS_PER_DEGREE`、`DEGREES_PER_RADIAN` |
| `FloatBounds` | float 矩形（GUI 布局） | `record FloatBounds(float minX, float minY, float maxX, float maxY)`；`getWidth/getHeight`、`expand(float[,float])`、`union(FloatBounds...)`、`intersects(FloatBounds)`、`contains(double,double)`、`fromPositionAndSize(...)` |

## 9. utils/movement
源码目录：`src/main/java/evernight/client/utils/movement/`

### `MovementPhysics` — 移动物理（`.../movement/MovementPhysics.java:18`）
- 常量：`public static float baseMoveSpeed = 0.2873F`、`scaledBaseMoveSpeed`（**public 可变，别随手改**）。
- `getHorizontalMotion(float yaw, ClientInput input, double speed)` → `double[]{x, z}`：按 yaw + 按键算这一 tick 的水平位移（已处理 45° 斜向）；`getMovementYaw([float yaw])`；`isMoving(Player)`、`isLocalPlayerMoving()`、`isMovingToward(Player, Vec3)`/`(Vec3)`。
- `applySpeedEffects(boolean includeSlowness, double baseSpeed)`、`getBaseMoveSpeed(boolean)`、`getJumpBoost()`（0 或 `(amplifier+1)*0.1`）、`findStepHeight(double maximumHeight)`（仅 `onGround && horizontalCollision`）、`getHorizontalSpeedKmh(Entity)`、`accelerateTo(target, initial, durationSeconds, startedAtMillis)`、`applyHorizontalMotion(LocalPlayerMoveEvent, double speed)`。

```java
LocalPlayer player = ClientAccess.evernightMinecraft.player;
double[] motion = MovementPhysics.getHorizontalMotion(player.getYRot(), player.input, 1.0D);
Vec3 next = player.position().add(motion[0], 0.0D, motion[1]);
```

| 类 | 用途 | 关键 public 成员 |
| --- | --- | --- |
| `MovementDirection` | 8 方向输入枚举 | `getForwardInput()`、`getSidewaysInput()`、`createInput(Input)` |
| `MovementInputProcessor` | 输入规范化/相对方向 | `applyInputPreservingBobbing()`、`normalizeInput(Vec2)`、`getRelativeDirection(float movementYaw, float rotationYaw)` |
| `MotionHistory` / `MotionSample` | 运动采样环形历史 | `append(MotionSample)`、`getLatest()`、`weightedAverage(ToDoubleFunction)`、`average(...)`、`clear()`；`record MotionSample(...)` |
| `MovementSpeedTracker` | 速度统计（`ClientContext.movementSpeedTracker`） | `getCurrentSpeed()`、`getAverageSpeed()`、`getDisplaySpeed()`、`calculateCurrentSpeed()`（km/h，自订阅 tick） |
| `PlayerMotionPredictor` | 运动预测包围盒 | `updatePredictions()`、`getPredictedBoxes()`、`reset()`；**MovementPredictionManager 内部** |
| `ElytraFlyModeHandler` + `BoostElytraFlyHandler` / `BounceElytraFlyHandler` / `ControlElytraFlyHandler` / `NewGrimElytraFlyHandler` / `VanillaElytraFlyHandler` | ElytraFly 各模式 | 事件回调（`handleMovement`、`handlePlayerTick`…）；**ElytraFlyModule 内部，不要复用** |
| `VelocityModeHandler` + `GrimVelocityHandler` / `WallsVelocityHandler` | Velocity 各模式 | 事件回调（`onExplosion`、`onEntityMotion`、`onSendPacket`…）；**VelocityModule 内部** |
| `GrimSpeedHandler` / `StrafeSpeedHandler` / `StrictStrafeSpeedHandler` | Speed 各模式 | `onMove(LocalPlayerMoveEvent)`、`onSendPosition(...)`；**SpeedModule 内部** |
| `EntityMotionAccess` | 实体运动量 mixin 接口 | 由 mixin 实现，**不要自己实现** |

## 10. utils/network
源码目录：`src/main/java/evernight/client/utils/network/`

### `PacketSender` — 发包（`.../network/PacketSender.java:27`）
**这些方法全部真的发包**，且都**不等待服务端响应**、不做预测/回滚（预测只在 `BlockInteractionUtils.placeBlock` 那条路径里）。
- `send(Packet)` → `player.connection.send(...)`，走 `Connection.sendPacket`，会触发 `PacketEvent.Send` / `PacketEvent.PostSend`（可被取消）。
- `sendDirect(Packet)` → 直接 `connection.channel.writeAndFlush`，**跳过 `Connection` 层与 `PacketEvent.Send/PostSend`**，但仍过 netty pipeline（PingSpoof 延迟 handler 照常生效）。用于伪造延迟、快速回包。
- 交互：`sendUseItemOn(InteractionHand, BlockHitResult)`、`sendUseItem(InteractionHand)`、`sendUseItem(InteractionHand, float yaw, float pitch)`、`sendSwing(InteractionHand)`。
- 攻击：`sendAttack(int entityId)`——手工构造 `ServerboundInteractPacket`（entityId + attack 动作 + 当前潜行状态），**不触发本地挥手/攻击冷却**。
- 移动：`sendRotation(float yaw, float pitch, boolean onGround)`、`sendRotation(float[] rotation, boolean onGround)`、`sendPosition(double x, double y, double z, boolean onGround)`、`sendPositionRotation(...)`。
- 其他：`sendPlayerAction(ServerboundPlayerActionPacket.Action, BlockPos, Direction)`（`ABORT_DESTROY_BLOCK` 的 sequence 固定 0）、`sendPlayerCommand(ServerboundPlayerCommandPacket.Action[, Entity])`、`sendStartFallFlying()`、`sendSwapOffhand()`、`sendSelectedSlot(int)`、`nextPredictionSequence()`、`markImportant(Packet)`。

```java
BlockHitResult hit = new BlockHitResult(hitPoint, Direction.UP, position, false);
PacketSender.sendUseItemOn(InteractionHand.MAIN_HAND, hit);
PacketSender.sendSwing(InteractionHand.MAIN_HAND);
```

### `InteractPacketUtils` — 解析 `ServerboundInteractPacket`（`.../network/InteractPacketUtils.java:10`）
- `getEntity(ServerboundInteractPacket)` → `Entity`（查不到 `null`）；`getAction(ServerboundInteractPacket)` → `InteractPacketUtils.Action {INTERACT, ATTACK, INTERACT_AT}`。实现是重写 buffer 再读，用于 `PacketEvent` 里判断「这包在打谁/交互谁」（例：`client/commands/misc/Fakeplayer/FakeplayerCommand.java:201`）。

```java
if (event.getPacket() instanceof ServerboundInteractPacket packet
        && InteractPacketUtils.getAction(packet) == InteractPacketUtils.Action.ATTACK) {
    Entity target = InteractPacketUtils.getEntity(packet);
}
```

### `HttpRequests` — HTTP 客户端（`.../network/HttpRequests.java:18`）
- `get(String url)` / `post(String url, BodyPublisher)` / `newRequest(String url)` → `HttpRequest.Builder`（已带 30s 超时）。
- `send(HttpRequest)` → `HttpResponse<String>`（**阻塞**，失败抛 `CompletionException`/`UncheckedIOException`）；`send(HttpRequest, BodyHandler<T>)`；`sendAsync(...)` → `CompletableFuture`（30s 超时）。不要放客户端线程，参考 `client/commands/social/Namehistory/NamehistoryCommand.java:121`。

```java
HttpRequest request = HttpRequests.get("https://api.example.com/ping").header("User-Agent", "Evernight").build();
HttpResponse<String> response = HttpRequests.send(request);
String body = response.body();
```

| 类 | 用途 | 关键 public 成员 |
| --- | --- | --- |
| `AutoPresetServerEntry` | 自动预设的服务器配置 | `getServer()`、`getPresets()`、`getKitName()`、`hasKit()`、`setKitName(String)` |
| `ImportantPacket` | 「重要包」标记 mixin 接口 | `evernight$markImportant()`；配合 `PacketSender.markImportant` |
| `PingSpoofDelayHandler` | PingSpoof 的 netty 延迟 handler | `NAME`；**PingSpoofModule 内部**，由 `ConnectionMixin` 安装 |
| `ServerSideDeathState` | 服务端死亡状态 mixin 接口 | 由 mixin 实现，**不要自己实现** |

## 11. utils/world
源码目录：`src/main/java/evernight/client/utils/world/`

### `BlockInteractionUtils` — 放置/交互/攻击方块（`.../world/BlockInteractionUtils.java:82`）
- **两条放置路径**，返回值都只表示「已发起」，**不等待服务端确认**：
  - `placeBlock(BlockPos, Vec3 hitPosition, Direction, boolean airPlace, InteractionHand)` / `placeBlock(BlockPos, boolean airPlace, InteractionHand)`：走 `MultiPlayerGameMode#useItemOn`，**带客户端预测**（本地世界立即变化）。先过 `canPlaceBlock`（越界/占用/实体碰撞）；需要潜行交互时临时 `setSneaking(true)` 再还原；`hitPosition` 传 `null` 会自动补「方块中心 + 面偏移」。
  - `sendPlacement(BlockPos, Vec3 hitPosition, Direction, boolean airPlace, InteractionHand)`、`placeBlockIgnoringCrystals(...)`：**只发包 + 挥手，不做本地预测**，也不等回包。
- `public static boolean isPlacingBlock`：放置期间为 `true`，`PlayerMixin` 靠它强制 `isSecondaryUseActive`（避免误触方块交互）。
- `isInteractionCooldownActive()` / `resetInteractionCooldown()`：50ms 手动冷却闸门；**仓库里没有任何地方调 `resetInteractionCooldown()`**，所以它默认恒为 `false`，要用得自己 arm。
- 找面：`findSupportDirection(BlockPos[, boolean strictDirection[, boolean checkRaytrace]])` → `Direction`/`null`（顺序 DOWN,NORTH,EAST,SOUTH,WEST,UP，跳过顶界/世界边界/不可见面）；`getStrictDirections(BlockPos)` → 裸 `List`（实际 `List<Direction>`）、`getStrictDirectionsOrFallback(BlockPos, Direction)`、`isStrictDirectionEnabled()`。
- 找位：`findSupportPosition(BlockPos, int maxDistance, boolean checkPlayers, boolean strictDirection[, Predicate<? super BlockPos>])` → `BlockPos`/`null`；`findSupportPath(...)` → 从远到近的 `List<BlockPos>`。
- 可行性：`canPlaceBlock(BlockPos, Block|null, boolean playersOnly[, boolean ignoreCrystals])`、`canPlaceAt(BlockPos, boolean playersOnly[, boolean ignoreCrystals])`、`isOutsidePlacementBounds(BlockPos)`、`hasBlockingEntity(AABB, boolean playersOnly, boolean ignoreCrystals)`、`isCollidable(BlockPos)`、`canPlayerInteract()`。
- 水晶/实体：`canPlaceCrystal(BlockPos base, boolean requireTwoAirBlocks, boolean checkEntities, boolean checkCrystals, boolean allowFire, boolean shortHeight, boolean expandPlayers)`（基座必须黑曜石/基岩）、`attackCrystalAt(BlockPos, int minimumAge)`、`attackEntity(Entity)`（委托 `crystalInteractionManager`，成功重置攻击冷却）、`interactWithEntity(Entity, InteractionHand)`（`interactAt`+`interact`+挥手，**实际发包**）。
- 杂项：`requiresSneakForInteraction(BlockPos)`、`isInteractiveBlock(Block)`、`isThrowableItem(Item)`、`isGlass(BlockState)`、`getBlock(BlockPos)`。

```java
BlockPos target = BlockPos.containing(ClientAccess.evernightMinecraft.player.position()).below();
Direction support = BlockInteractionUtils.findSupportDirection(target, false);
if (support != null && BlockInteractionUtils.canPlaceBlock(target, null, false)) {
    BlockInteractionUtils.placeBlock(target, null, support, false, InteractionHand.MAIN_HAND);
}
```

### `WorldUtils` — 世界查询与可见性（`.../world/WorldUtils.java:65`）
- 地形：`getTopY()` → `minY + level.getHeight()`（float 上界）、`getDimension()` → `evernight.client.settings.options.DimensionType`、`getBlock`、`isAir`、`isReplaceable`、`hasFluid`、`hasCollision`、`getHardness`、`isBedrockOrObsidian`、`canMineBlock(BlockPos)`（空气/流体/负硬度 false）。
- 区块：`getLoadedChunks()`（玩家为中心、渲染距离内）、`getLoadedBlockEntities()`、`reloadRenderer()`（`levelRenderer.allChanged()`，**开销大**）。
- 可见性：`canSee(Vec3 origin, Vec3 target)` / `canSee(Vec3, Vec3, BlockPos targetBlock, Direction targetFace)`（距离 > 128 直接 false；MISS 或命中点==target 为可见）、`canSeeFromEyes(Vec3)`、`canSeeBlock(Vec3i)`、`canSeeAny(Iterable)`、`canSeeAny(Vec3, Iterable)`、`findVisiblePoint(BlockPos, BlockSampleMode)`。
- 扫描：`streamSphereBlocks(Vec3 center, float radius)`、`collectSphereBlocks(Vec3 center, float radius, boolean excludeAir)` → `List<BlockPos>`。
- 实体：`isTargetable(Entity)`（排除自己/好友/死亡）、`getEntityDistance(Entity, boolean sampleBody)`、`getEntityRange(Entity, boolean)` → `WorldUtils.EntityRange`（`record EntityRange(double distance, Vec3 origin)`）。
- 其他：`getBreakProgress(ItemStack, BlockState, boolean onGround)` → 每 tick 挖掘进度增量；`isInteractive(HitResult)`；`isStructureChest(ChunkAccess, BlockPos, boolean fortress, boolean bastion, boolean ancientCity, boolean dungeon, boolean trialChambers)`；`distanceToBounds(Vec3, AABB)`；常量 `BASTION_FLOOR_BLOCKS`/`ANCIENT_CITY_FLOOR_BLOCKS`/`TRIAL_CHAMBER_FLOOR_BLOCKS`。

```java
boolean visible = WorldUtils.canSee(ClientAccess.evernightMinecraft.player.getEyePosition(), targetPos);
for (BlockPos pos : WorldUtils.collectSphereBlocks(targetPos, 8.0F, true)) {
    if (!WorldUtils.isAir(pos)) { /* 半径 8 格内的非空气方块 */ }
}
```

### `BlockBreakTracker` — 挖掘进度跟踪（`.../world/BlockBreakTracker.java:36`，实例在 `ClientContext.blockBreakTracker`）
- `getBreakProgress(BlockPos)` → 0~1 累计进度（无记录/不可挖返回 0）；`getEntries()` → 可读 `List<BlockBreakEntry>`。
- 构造时自己 `eventBus.subscribe(this)`，订阅 `LocalPlayerTickEvent` 累计进度、订阅 `PacketEvent` 跟踪 `ClientboundBlockDestructionPacket`/`BlockUpdate`，并与 `SpeedMine` 联动；**不要自己 new**。
- `BlockBreakEntry` 只读数据：`getPosition()`、`getProgress()`、`getInterpolatedProgress(float partialTick)`、`isBroken()`、`isExtraBreak()`、`getEntityId()`、`getAirTicks()`。

| 类 | 用途 | 关键 public 成员 |
| --- | --- | --- |
| `BlockStateUtils` | 方块挡路/可炸判定 | `isBlocking(BlockState)`/`isBlocking(BlockPos)`、`isBlockingExceptCarpet(BlockPos)`、`isBlastResistantUnbreakable/Breakable(BlockState)`、`isWeakOrInstantlyBreakable(BlockState)` |
| `ChunkScanner` | 异步分区块扫描器 | 构造 `(int workerCount, ChunkScanner.Listener)` 或 3 个 `BiConsumer` 回调；`start()`、`stop()`、`scanLoadedChunks()`、`restartExecutor()`、`getExecutor()`；被 MultiTask/NewChunks/Search/Tunnels 复用 |
| `MapDataCache` | 地图数据缓存（`ClientContext.mapDataCache`） | `load(String address)`、`save(String address)`、`getMapData(ItemStack, MapId)`、`getMapData(ItemStack, int)` |
| `Waypoint` | 路径点（`NamedEntry`） | `new Waypoint(String name, double x, double y, double z, String dimension, String server)` / `(String, Vec3, String, String)`；`getPosition()`、`distanceTo(Vec3)`、`getDimension()`、`getServer()`、`getDisplayComponent()`、`matches(String, String)`、`setToggled(boolean)` |
| `HoleInfo` | 洞数据（`HoleManager` 产出） | `record HoleInfo(HoleInfo.Safety safety, BlockPos pos, AABB bounds, boolean trapped)` + `enum Safety {SAFE, UNSAFE}` |
| `NukerBlockQueue` | Nuker 待挖队列 | `enqueue(BlockPos)`、`getNextPositions(boolean includeSecond)`、`pruneInvalidPositions()`、`getPositions()`；**NukerModule 内部** |
| `BlockStateSnapshot` | 方块状态快照（`WorldStateManager`） | `record BlockStateSnapshot(BlockState state, BlockPos position, long capturedAtMillis)`；`hasExpired()`、静态 `captureState(BlockPos)` |
| `BlockBreakingState` | 破坏状态 mixin 接口（在 `MultiPlayerGameMode` 上） | `getCurrentBreakingBlock()`、`setBreakingProgress(float)` 等；由 mixin 实现 |
| `BlockRegistryGroupMatcher` | `RegistryGroupMatcher` 的方块实现 | `getRegistryId()`、`getGroupNames()`、`getGroupEntries(String)` |
| `Season` | 季节枚举（`UIModule`/`ClientScreen` 主题） | `WINTER/SPRING/SUMMER/AUTUMN`；`fromMonth(int)`、`containsMonth(int)` |

## 常见任务速查

| 我想做 X | 调 Y |
| --- | --- |
| 发聊天反馈 / 以玩家身份发消息 | `ChatUtils.printChat(Component, ChatUtils.DEFAULT_SIGNATURE)`（任意线程）；发聊天或指令用 `ChatUtils.sendChatOrCommand(String)` |
| 发一个交互包（右键方块） | `PacketSender.sendUseItemOn(InteractionHand, BlockHitResult)` |
| 发一个攻击包 | `PacketSender.sendAttack(entityId)` |
| 发挖掘开始/结束/中止 | `PacketSender.sendPlayerAction(Action.START_DESTROY_BLOCK / STOP_DESTROY_BLOCK / ABORT_DESTROY_BLOCK, pos, dir)` |
| 请求转向（由 RotationManager 平滑发出） | `ClientContext.rotationManager.requestRotation(RotationManager.getRotationsToPosition(pos), priority)` |
| 放方块（要预测 / 只要发包） | `BlockInteractionUtils.placeBlock(pos, hitPos, dir, false, hand)`（带预测）；`sendPlacement(pos, hitPos, dir, false, hand)`（只发包） |
| 找一个方块周围可放置的坐标 | `BlockInteractionUtils.findSupportPosition(pos, 2, false, false)` 或 `findSupportDirection(pos)` |
| 判断某坐标能不能放方块 | `BlockInteractionUtils.canPlaceBlock(pos, heldBlock, false)` / `canPlaceAt(pos, false)` |
| 判断能不能放水晶 / 放水晶 | `BlockInteractionUtils.canPlaceCrystal(base, true, true, true, false, false, false)` 后 `placeBlock` |
| 取某物品的槽位并切过去 | `InventoryUtils.findHotbarSlot(item)`（或 `findMenuSlot(item)`）后 `selectHotbarSlot(slot)` |
| 临时换物品并在用完后换回 | `InventoryUtils.findSwapSlot(item, true)` + `runWithSwap(previousSlot, slot, action)` |
| 数背包里有多少某物品 | `InventoryUtils.countItems(stack -> stack.is(item))` |
| 算距离 / 朝向角度 | 距离：`a.distanceTo(b)`、`WorldUtils.getEntityDistance(entity, true)`、`MathUtils.distanceToCamera(point)`；角度：`RotationManager.getRotationsToPosition(Vec3)` → `{yaw,pitch}`，pitch/yaw→向量用 `MathUtils.directionFromPitchYaw(pitch, yaw)` |
| 找附近可攻击的实体 | `new TargetSelector().withPlayers(true).range(4.5F).select()`（**必须设 range**） |
| 算爆炸伤害 | `DamageUtils.getCrystalDamage(explosionPos, target)` / `getBedDamage(...)` |
| 判断能不能看见某个点 | `WorldUtils.canSee(eyePos, point)` / `WorldUtils.findVisiblePoint(pos, sampleMode)` |
| 找半径内的方块 | `WorldUtils.collectSphereBlocks(center, radius, true)` |
| 查某物品的附魔等级 | `EnchantmentUtils.getLevel(Enchantments.X, stack)` |
| 查玩家脚边可占位的格子 | `PlayerStateUtils.getSurroundPositions(target)` / `getOccupiedBlockPositions(target)` |
| 平滑动画一个 float | `InterpolatedFloat`（逐帧 `updateAndGet()`）或 `FloatAnimation`（定时补间） |
| 做「每 N 毫秒一次」的节流 | `ElapsedTimer#hasElapsedAndReset(N)`（新建后先 `reset()`） |
| 读/写 UTF-8 配置文件 | `FileUtils.readUtf8(path)` / `writeUtf8(path, text)` |
| 发一个 HTTP 请求 | `HttpRequests.get(url).build()` 后 `HttpRequests.send(request)` |
| 在包里判断对方在攻击谁 | `InteractPacketUtils.getAction(packet)` + `getEntity(packet)` |
