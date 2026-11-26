# Evernight 工具类 —— client/utils 全量速查

覆盖 `src/main/java/evernight/client/utils/` 全部 137 个文件（chat、collections、combat、entity、inventory、math、
movement、network、world 9 个子包 + 根目录），路径相对仓库根。方法名齐全，参数只保留有语义的部分（类型见源码）；
`**坑**` 为必须遵守的约束。

## 1. 使用前提

大多 `implements ClientAccess`（`FileUtils`/`ElapsedTimer`/`InterpolatedFloat` 等纯算法类不实现）；实例用
`ClientAccess.evernightMinecraft`（`ClientAccess.java:17`），`mc.player == null || mc.level == null` 时别调。

- 全局管理器在 `ClientContext`（`ClientContext.java:48`）：`rotationManager`、`playerStateTracker`、
  `inventoryStateManager`、`worldStateManager`、`socialRegistry`、`swingManager`、`blockBreakTracker`、
  `movementSpeedTracker`、`totemPopTracker`、`frameTiming`、`mapDataCache`；模块用
  `ClientContext.getModule(XxxModule.class)`（可能 `null`）。
- 除标注处，方法**只能在客户端线程**调用。构造器全 `private`；实例类（`BlockBreakTracker`、
  `PlayerStateTracker`、`MovementSpeedTracker`、`MapDataCache`、`NukerBlockQueue`）自订阅事件，**不要 new**。

## 2. utils 根目录

`src/main/java/evernight/client/utils/`

| 类 | 关键 API |
| --- | --- |
| `ClampedInterpolatedFloat` 0~1 开关动画 | 继承 `InterpolatedFloat`；`setTarget`、`setValue`、`reset` |
| `CompressionUtils` zlib | `inflate`、`deflate` |
| `Experimental` 实验注解 | 配合 `ExperimentalFeatures` 隐藏 |
| `ExperimentalFeatures` 实验开关（`--evernight-allow-experimental` / `-Devernight.allowExperimental=true`） | `isExperimentalDisabled`、`isFieldHidden`、`isClassHidden`、`unregisterHiddenSettings` |
| `FrameTiming` 帧率/插值（`ClientContext.frameTiming`） | `recordFrame`、`getFrameFactor`、`getFramesPerSecond` |
| `ServerAddressUtils` | `getCurrentServerAddress`（失败/本地 → `singleplayer`） |
| `SoftwareKeyMapping` | 继承 `KeyMapping`，覆写 `isDown`/`setDown` |
| `Toggleable` | `enable`、`disable`、`isToggled`、默认 `toggle`/`setToggled` |
| `UncheckedExceptions` | `rethrow`（sneaky throw） |

### `ElapsedTimer`（`utils/ElapsedTimer.java:5`）毫秒计时器

`reset`、`hasElapsed(long)`、`hasElapsedAndReset(long)`、`elapsedMillis`、`clear`、`setTime(long|double, TimeUnit)`、
`hasElapsed(double, TimeUnit)`。**坑**：初值 `lastResetMillis = -1`，新建或 `clear()` 后 `elapsedMillis()` 极大、
`hasElapsed(x)` 立刻 `true`，要等待先 `reset()`；非线程安全。

### `InterpolatedFloat`（`utils/InterpolatedFloat.java:9`）逐帧平滑 float

构造 `(float speed)` / `(float speed, boolean easingEnabled)` / `(Supplier<Float> speedSupplier, boolean
easingEnabled)`；`setTarget`、`updateAndGet`（**每帧一次**）、`setValue`、`setEasingEnabled`、`setSpeedSupplier`、
`getSpeedSupplier`。**坑**：依赖 `ClientContext.frameTiming`；FPS ≤ 15 直接吸附。

### `FloatAnimation`（`utils/FloatAnimation.java:7`）定时线性补间

`setTarget(float target, long durationMillis)`、`setTarget(boolean target, long durationMillis)`（true=1/false=0）、
`setValue`、`getValue`、`getValue(Function easing)`。不依赖帧率。

### `InputUtils`（`utils/InputUtils.java:7`）改写本地输入

`setSneak`/`setJump`/`setForward`/`setBack`/`setLeft`/`setRight`/`setSprint` 改写 `ClientInput.keyPresses`
（mixin accessor）；`setForwardMovement`、`setSidewaysMovement` 改 `moveVector`；`clearKeyPresses`、
`createInput`（`moveVector=(0,1)`）。**坑**：**不自己发包**，由后续 tick 的移动包体现；用例
`client/managers/player/InputOverrideManager.java:104`。

### `KeyUtils`（`utils/KeyUtils.java:17`）物理键/鼠标与键名

`isPhysicallyDown`、`isPhysicalKeyDown`、`isPhysicalMouseButtonDown`（直接问 GLFW，绕过 KeyMapping 的 GUI 影响；
`isShiftDown`/`isControlDown`/`isAltDown` 只测**左**侧键）；`parseKeybind(String)` → `evernight.client.modules.Keybind`
（`mouse3`、别名 `shift/ctrl/alt/rshift/rctrl/ralt/capslock`、`key.keyboard.<name>`；失败抛
`IllegalArgumentException`）；`getKeyName`（`int, boolean` / `Keybind`；负数 → `NONE`，鼠标 → `MOUSE<n>`）、
`getKeyAliases`。

### `FileUtils`（`utils/FileUtils.java:13`）与 `LockUtils`（`utils/LockUtils.java:6`）

`readBytes`、`writeBytes`、`readUtf8`、`writeUtf8`（均抛 `IOException`）、`findExistingWithSuffixes`；范例
`client/managers/config/ConfigurationManager.java:73`。`LockUtils`：`runWithSharedLock`、`wrapWithSharedLock`、
`runWithLock`、`wrapWithLock`；**坑**：锁内别长阻塞。

## 3. utils/chat

`src/main/java/evernight/client/utils/chat/`

| 类 | 关键 API |
| --- | --- |
| `ChatFilterEntry` 过滤器条目 | `record ChatFilterEntry(String id, String filter)`（`ChatFilterRegistry` 数据载体） |
| `RandomTagReplacer` 随机标签替换 | `TOKEN="<随机>"`、`ASCII_ALIAS="<random>"`、`RANDOM_LENGTH=5`；`containsTag`、`apply`（每次出现生成新的 5 位 `[A-Za-z0-9]`，无标签原样返回） |

### `ChatUtils`（`utils/chat/ChatUtils.java:30`）聊天输出/前缀/水印

`printChat(Component message, MessageSignature signature)`：**聊天反馈入口**，自动加 `[Evernight]` 前缀并投递主线程
（**任意线程可调**），另有 `printChat(Component, int id)`、`printChat(Component, MessageSignature,
MessagePriority)`（彩色 `[!]`；`MessagePriority.HIGH/MID/LOW`）；`DEFAULT_SIGNATURE`、`createSignature`、
`moduleSignature`、`objectSignature`；`sendChatOrCommand`（`/` 开头走 `sendCommand`，**真以玩家身份发出**）；
`createPrefix`、`bracketedText`、`watermarkText`、`rainbowText`、`withDynamicColor`、`suggestCommand` →
`ClickEvent`；`isIncomingWhisper`、`isOutgoingWhisper`、`INCOMING_WHISPER_PATTERNS`、`OUTGOING_WHISPER_PATTERNS`、
`MESSAGE_TAG`。**坑**：可见文本走语言表
`ChatUtils.printChat(Component.literal(Translations.ui("modules.Example.Done")), ChatUtils.DEFAULT_SIGNATURE)`。

## 4. utils/collections

`src/main/java/evernight/client/utils/collections/`

| 类 | 关键 API |
| --- | --- |
| `IntPair` 整数对 | `record IntPair(int first, int second)`；`add`、`distanceTo`、`squaredDistanceTo` |
| `ObjectPair` | `record ObjectPair(Object first, Object second)` |
| `NamedIdentifier` 带分类 Identifier | `new NamedIdentifier(String)` / `(String category, String name)`；`getCategory`、`getName`；`NONE` |
| `NamedRegistryEntry` | `record NamedRegistryEntry(String name, Object value)` |
| `RegistryGroupMatcher` 分组名→条目 | `forRegistry`、`getRegistryId`、`getGroupNames`、`getGroupEntries`；`MATCHERS`、`EMPTY` |
| `EmptyRegistryGroupMatcher` 空实现 | 同接口，返回空集合 |

两个实现不在本包：`utils/world/BlockRegistryGroupMatcher`、`utils/inventory/ItemRegistryGroupMatcher`。

## 5. utils/combat

`src/main/java/evernight/client/utils/combat/`；**本包没有 `CombatUtils`**：战斗能力 = `DamageUtils`（伤害）、
`RaycastUtils`/`BlockRaycastContext`（射线）、`TargetSelector`（选目标）、`BoundingBoxUtils`（包围盒）；
纯计算/查询，**不发包、不改世界**。

### `DamageUtils`（`combat/DamageUtils.java:40`）爆炸伤害预测

`getCrystalDamage(Vec3 explosionPosition, LivingEntity target[, AABB bounds])` power **6.0**、`getBedDamage(...)`
**5.0**；`getExplosionDamage(Vec3, LivingEntity, AABB, double power, boolean ignoreLowResistance, BlockPos
ignoredPosition, BlockPos solidPosition)`（另 `Collection ignoredPositions` 重载）；`getExposure(Vec3, Entity, AABB,
Collection ignoredPositions, BlockPos solidPosition, boolean ignoreLowResistance)` 0~1；`applyDamageReduction`、
`scaleForDifficulty`、`getDurabilityPercent`（1~100）。**坑**：读 `AutoCrystalModule.assumeBestArmorSetting` 与
`level.getDifficulty()`，不改状态。

### `RaycastUtils`（`combat/RaycastUtils.java:32`）方块/实体射线

`raycast(BlockRaycastContext)`（可忽略/强制实心方块，原版 `level.clip` 做不到）；`raycastFromRotation(Vec3 origin,
float yaw, float pitch, float range)`（只测方块、忽略流体、不含实体）；`raycastWithEntityTrace(LocalPlayer)`（走原版
`player.raycastHitResult`）；`getGroundDistance`、`getCollisionBlockYOffset`、`hasVisibleEntitySample`、
`isLineVisible`。**坑**：`raycastWithEntityTrace` 期间压制 Reach 的 noEntityTrace（**全局副作用，用完自动复位**）；
`getGroundDistance` 下方无方块会测到世界底，不可用。

### `TargetSelector`（`combat/TargetSelector.java:27`）目标筛选

非静态 builder：`new TargetSelector()` 链式后 `select()` 返回最佳 `Entity`（无则 `null`）；`withPlayers`/
`withNeutrals`/`withAnimals`/`withVillagers`/`withHostiles`/`withCrystals`/`withProjectiles`/`withVehicles`、
`range`（同时设可见/隔墙）、`visibleRange`、`wallRange`、`useExtraRange`、`ignoreNamed`、`ignoreNakeds`、
`ignoreInvisibles`、`sortBy`、`filter`；默认按距离平方升序。**坑**：默认 `visibleRange = wallRange = 0`，不调
`range()` 永远选不到；`select()` 已用 `WorldUtils.isTargetable` 排除自己/好友/死亡。

### `RotationRequest`（`combat/RotationRequest.java:3`）转向请求

`record RotationRequest(float[] angles, int priority)`（`angles = {yaw, pitch}`）：新请求 `priority >= 当前` 才替换。
**坑**：别直接 `new`，用 `ClientContext.rotationManager.requestRotation(float[] angles, int priority)`（或带
`boolean force` 的重载）；角度用 `RotationManager.getRotationsToPosition(Vec3)`，当前请求 `getRequest`。

| 类 | 关键 API |
| --- | --- |
| `BoundingBoxUtils` AABB 工具 | `getInterpolationBounds`、`getRenderBounds`、`hasVolumeOverlap`、`clampToBounds`、`getBlockShapeBounds`/`getBlockCollisionBounds`、`placeAtBlock`、`getBottomCenter`/`getTopCenter`/`getLowerQuarterCenter`、`recenter`、`intersectsBlock`、`getPredictionTicks` |
| `BlockRaycastContext` 射线上下文 | `new BlockRaycastContext.Builder(start, end).withEntity(Entity).withClassifier(s).build()`；`getStart`/`getEnd`/`getClassifier`/`getCollisionContext`/`getBlockShape` |
| `BlockRaycastClassifier` 方块判定 | `NO_OVERRIDE`、`IGNORE_LOW_RESISTANCE`、`test`、`ignorePosition`、`ignorePositions`、`solidPosition`、`solidPositions`、`firstNonNone` |
| `BlockRaycastOverride` | `enum {NONE, IGNORE, SOLID}` |
| `TotemPopTracker` 图腾计数（`ClientContext.totemPopTracker`） | `getPopCount`（`Player` / `String` 两种） |
| `ExtraBreakTarget` 共享额外破坏进度 | `getPosition`、`getClampedProgress`、`addProgress`、`setProgress` |
| `PendingPlacement` 待确认放置 | `record PendingPlacement(long createdAtMillis, Block block)` |
| `EndCrystalState` / `RemotePlayerDamageAccess` / `ZombifiedPiglinAttackState` mixin 状态接口 | 由 mixin 实现，**不要自己实现**（`EndCrystalState#getSpawnTime`、`evernight$isAttacking`） |

## 6. utils/entity

`src/main/java/evernight/client/utils/entity/`

### `EntityUtils`（`entity/EntityUtils.java:46`）分类/血量

`isHostile`（末影人 `isCreepy()`、僵尸猪灵攻击中、狼 angry 纹理、中立怪 `isAngry()`、猪灵 `armPose`、
`Enemy`/`PolarBear` 都算敌对）、`isNeutral`、`isPassive`、`isLookingAtPlayer`、`getHealthWithAbsorption`
（非 `LivingEntity` 返回 `2.0F`）、`getHealthFraction`（clamp 0~1）、`getPlayerHealthWithAbsorption`、
`hasCollisionBelow`、`hasFirework`、`getFireworkPositions`、`isFirstPersonCameraEntity`。**坑**：`setStepHeight` /
`getStepHeight` 改 `Attributes.STEP_HEIGHT` base value，**会残留**，用完还原。

### `PlayerStateUtils`（`entity/PlayerStateUtils.java:44`）本地玩家状态

`getMovementDirection`（→ `Direction`）、`getMovementVector`（水平 `Vec3`）、`getBodySampleHeights`
（`float[3] = {1.62, 1.27, 0.4}`，**别改内部数组**）、`streamBodySamplePositions`、`isLowProfile`、
`isLocalLowProfile`、`isLocalHeightBelowOne`、`isInCobweb`、`isInWater`、`isInLiquid`、`getRoundedBlockPosition`、
`getLocalRoundedBlockPosition`、`hasArmor`、`isWearingElytra`、`isLocalWearingElytra`、`isEnderPearlOnCooldown`、
`isHungerLimited`（饱食度 ≤ 6 且非创造/旁观）、`getTrackedFallDistance`、`getOccupiedBlockPositions`、
`getOffsetBlockPositions`、`getSurroundPositions`（脚下邻格）、`oppositeHand`。**坑**：`isFallFlying()` 有副作用
（临时把 `ElytraFlyModule.hook` 置 true 再读），会短暂影响鞘翅模块。

### `PlayerStateTracker`（`entity/PlayerStateTracker.java:20`，`ClientContext.playerStateTracker`）

读：`isPacketOnGround`、`getPacketPosition`、`getPacketRotations`（`{yaw,pitch}`，从最后发出的移动包解出）、
`getLastSentInput`、`getGroundedTicks`/`getAirborneTicks`、`getFallDistance`、`getTickDisplacement`、
`getCurrentTickY`/`getPreviousTickY`/`getLastGroundedY`、`getFallFlyingTimer`。写：`setSneaking`（发
`SneakStateChangeEvent`，可被取消）、`setFallDistance`、`runSneaking`/`runWithoutSneaking`。**坑**：写方法**会改
真实发包状态**；自订阅事件，**不要 new**。

| 类 | 关键 API |
| --- | --- |
| `PlayerState` 注入 `Player` 的扩展接口（`mixin/PlayerMixin.java:48`） | `evernight$setRole`、`evernight$getRole`、`evernight$isNextToWall`、`evernight$wasCrawling`、`evernight$getHealthFactor`、`getItemUseProgress` |
| `SwingAction` 挥手类型 | `enum {INTERACT, MINING, HIT}`；`isEnabled(AntiCheatModule)` 判断该类挥手是否发包 |
| `SidePlayer` 侧影假玩家（FakeLag/Chams/LogoutSpots） | `SIDE_PLAYER_TAG`、`restoreFrom`、`readSnapshot`、`copyPlayerState`；**模块内部，不要复用** |

## 7. utils/inventory

`src/main/java/evernight/client/utils/inventory/`

### `InventoryUtils`（`inventory/InventoryUtils.java:30`）槽位查找与切换

- 选中/点击：`selectHotbarSlot(int 0..8)`（改 slot 后调 `MultiPlayerGameModeAccessor.sync()`）、
  `selectHotbarSlotForPlacement`、`swapWithHotbarSlot(int menuSlot, int hotbarSlot)`、`swapMenuSlots`、
  `swapSlotsWithPickup`、`clickSlot(int slot, int button, ClickType)` / `clickSlot(int containerId, int slot,
  int button, ClickType)`、`getSelectedSlot`、`getCarriedStack`。
- 查找（`-1` = 没找到）：`findHotbarSlot`（`Item...`/`Item`/`Predicate<ItemStack>`，先看主手）、
  `findHotbarSlotInItemOrder`、`findInventorySlot`（`Item...` / `(Predicate, boolean excludeHotbar)`，0~35 倒序）、
  `findMenuSlot`（`Item[, includeCrafting]` / `(Item, Predicate, boolean)`，快捷栏 0~8 映射 36~44）。**坑**：
  `findMenuSlot` 的 `-1` 有两种含义——没找到，或物品在副手且无待处理本地实体事件（此时不用换槽）；索引用
  `toMenuSlot`。
- 数量/耐久：`countItems`、`getDurabilityPercent`（不可损坏 100）、`getLowestArmorDurabilityPercent`。
- 其他：`isInventoryFull`、`isHotbarFull`、`canEquip`（看 `EQUIPPABLE`，鞘翅算 CHEST）、`getHandForItem`、
  `findBestToolSlot`、`isEnderChestScreen`。
- 静默换槽（推荐）：`findSwapSlot(Item|Predicate, boolean includeInventory)` → `SwapSlot`，再
  `runWithSwap(previousSlot, slot, action)`；`swapTo`、`swapBack`。`record SwapSlot(int slot, boolean hotbar,
  InteractionHand hand)`：`isFound`/`getStack`/`isInHand`，副手命中 = `(-2, false, OFF_HAND)`。

### `EnchantmentUtils`（`inventory/EnchantmentUtils.java:27`）附魔等级

`getLevel`（`(ResourceKey enchantment, ItemStack)` / `(ResourceKey, EquipmentSlot, LivingEntity)` /
`(ResourceKey, EquipmentSlot)`）→ `int`，空物品/无附魔 0，附魔书读 `DataComponents.STORED_ENCHANTMENTS`；
`hasEnchantment`（三种重载 = `getLevel > 0`）、`getLevels`、`getHolder`（`ResourceKey|String`；查不到 `null`）、
`getKey`（`String` → `ResourceKey`，默认 `minecraft:`）。

### `ItemUtils`（`inventory/ItemUtils.java:25`）与 `EquipmentUtils`（`inventory/EquipmentUtils.java:25`）

`isSword`/`isPickaxe`/`isAxe`（走标签）、`isSpear`/`isMace`（兼容自定义名）、`isGlass`、`isShulkerBox`、
`isSmithingTemplate`、`providesTrimMaterial`、`isFood`、`getFireworkDuration`（`(flightDuration+1)*10+1`）。
`EquipmentUtils.getArmorStacks`（4 件护甲）、`hasRequiredEnchantments`（校验固定组合：头/胸 = 保护4+耐久3+经验修补1，
腿 = 爆炸保护4 同前，靴再加摔落4；武器 = 锋利5+火2+击退2+横扫3+耐久3+经验修补1；鞘翅 = 耐久3+经验修补1）、
`isRepairMaterial`（依据 `REPAIRABLE`）。

### `PotionUtils`（`inventory/PotionUtils.java:27`）药水识别

`getPotion`（读 `DataComponents.POTION_CONTENTS`；无组件返回 `MISSING_POTION`，名 `empty`）、
`getEffectsByParticleColor`（`Set<Holder<MobEffect>>`，从粒子颜色反查）。

| 类 | 关键 API |
| --- | --- |
| `ArmorSlot` 护甲槽枚举 | `getInventoryIndex`、`getArmorIndex`、`getContainerSlot` |
| `AutoMineProvider` / `AutoMineProviderBase` / `AutoMineFeetProvider` / `AutoMineHeadProvider` / `AutoMineTerrainProvider` / `AutoMineCandidates` AutoMine 候选方块 | `collectCandidates`、`isEnabled`；**模块内部** |
| `ChestSearchController` 箱子搜索栏（`INSTANCE`） | `isFull`、`namesMatch`、`onDrawGui`、`afterInit`；**模块内部** |
| `ContainerTooltipComponent` 容器 tooltip | `record ContainerTooltipComponent(List<ItemStack> items)` + `getHeight`/`getWidth`/`renderImage`；**模块内部** |
| `CustomItemRegistries` 自定义注册表 | `foodItems`、`smithingTemplates`、`trimMaterials`、`initialize` |
| `ExtraBreakTracker` 额外破坏记录 | `trackBreak`、`isTracked`、`pruneExpired`；**模块内部** |
| `InventorySortStrategy` 整理策略接口 | `getSourceSlots`、静态 `getInventoryStack` |
| `Kit` 预设背包组合（`KitRegistry`） | `getName`、`getItemsBySlot`、`matches` |
| `ShulkerBoxItemPreview` 潜影盒预览 | `record ShulkerBoxItemPreview(ItemStack containerStack, ItemStack previewStack)` + `create` |
| `TradeDefinition` / `TradeEnchantmentEntry` / `TradeOfferSnapshot` 交易数据（`TradeRegistry`） | `TradeDefinition#matches`、`TradeOfferSnapshot.fromOffer`、`OUT_OF_STOCK` |
| `ItemRegistryGroupMatcher` / `ItemRegistryJsonAdapter` 物品注册表/Gson 适配器 | `getGroupEntries`；适配器已注册进 `ClientAccess.gson` |
| `RegistryContainer<T>` / `CharacterConsumer` | `register`、`unregister`、`getRegistry`；`accept(char)` |
| `NoSlowInventoryMode` / `NoSlowInventoryNcpMode` NoSlow 背包模式 | 事件回调；**模块内部** |
| `NoOpVertexConsumer` | `INSTANCE` |
| `PalettedContainerAccess` / `PalettedContainerDataAccess` 区块调色板 mixin 接口 | 由 mixin 实现，**不要自己实现** |

## 8. utils/math

`src/main/java/evernight/client/utils/math/`

### `MathUtils`（`math/MathUtils.java:25`）通用数学

`distanceToCamera`、`distanceToBlockCenter`、`cameraRotationDifference`、`yawDifferenceToPlayer`、
`directionFromPitchYaw(float pitch, float yaw)`（单位 `Vec3`，**pitch 在前**）、`wrapAngle`（0~360）、
`interpolateFrame(double start, double end)` / `(float, float)`、`lerp(double start, double end, double delta)`、
`floorToDecimals`、`rangeProgress`（自动交换边界，0~1）、`nearestBound`、`randomFloat`、`randomInt`、
`passesRandomThreshold`、`faceCorners`、`getDistanceScale`、`getPositionDelta`。**坑**：`lerp` 顺序是
start/end/delta（不是 `Mth.lerp` 的 delta 在前）；`passesRandomThreshold` 的 `threshold = 0` 永远 `false`
（即「0 关闭」）；`floorToDecimals` 用 BigDecimal FLOOR，负数抛异常。

### `Vec3Utils`（`math/Vec3Utils.java:6`）Vec3 小工具

`setComponents(Vec3 vector, double x, double y, double z)`：**原地修改**传入 Vec3（依赖 `Vec3Accessor` mixin），
只能给自己新建的临时对象用；`horizontalDistance`、`lerp(float fraction, Vec3 start, Vec3 end)`。

| 类 | 关键 API |
| --- | --- |
| `MathConstants` 常量 | `FORTY_FIVE_DEGREES`、`NINETY_DEGREES`、`FULL_TURN_DEGREES`、`PI`、`ONE_HALF`/`HALF`、`ONE_QUARTER`、`RADIANS_PER_DEGREE`、`DEGREES_PER_RADIAN` |
| `FloatBounds` float 矩形（GUI 布局） | `record FloatBounds(float minX, float minY, float maxX, float maxY)`；`getWidth`/`getHeight`、`expand`、`union`、`intersects`、`contains`、`fromPositionAndSize` |

## 9. utils/movement

`src/main/java/evernight/client/utils/movement/`

### `MovementPhysics`（`movement/MovementPhysics.java:18`）移动物理

常量 `public static float baseMoveSpeed = 0.2873F`、`scaledBaseMoveSpeed`（**public 可变，别随手改**）；
`getHorizontalMotion(float yaw, ClientInput input, double speed)` → `double[]{x, z}`（已处理 45° 斜向）、
`getMovementYaw`、`isMoving`、`isLocalPlayerMoving`、`isMovingToward`、`applySpeedEffects`、`getBaseMoveSpeed`、
`getJumpBoost`（0 或 `(amplifier+1)*0.1`）、`findStepHeight`（仅 `onGround && horizontalCollision`）、
`getHorizontalSpeedKmh`、`accelerateTo`、`applyHorizontalMotion`。

| 类 | 关键 API |
| --- | --- |
| `MovementDirection` 8 方向枚举 | `getForwardInput`、`getSidewaysInput`、`createInput` |
| `MovementInputProcessor` 输入规范化 | `applyInputPreservingBobbing`、`normalizeInput`、`getRelativeDirection` |
| `MotionHistory` / `MotionSample` 运动采样环形历史 | `append`、`getLatest`、`weightedAverage`、`average`、`clear`；`record MotionSample(...)` |
| `MovementSpeedTracker` 速度统计（`ClientContext.movementSpeedTracker`） | `getCurrentSpeed`、`getAverageSpeed`、`getDisplaySpeed`、`calculateCurrentSpeed`（km/h，自订阅 tick） |
| `PlayerMotionPredictor` 运动预测盒 | `updatePredictions`、`getPredictedBoxes`、`reset`；**MovementPredictionManager 内部** |
| `ElytraFlyModeHandler` + `BoostElytraFlyHandler` / `BounceElytraFlyHandler` / `ControlElytraFlyHandler` / `NewGrimElytraFlyHandler` / `VanillaElytraFlyHandler` | `handleMovement`、`handlePlayerTick`…；**ElytraFlyModule 内部，不要复用** |
| `VelocityModeHandler` + `GrimVelocityHandler` / `WallsVelocityHandler` | `onExplosion`、`onEntityMotion`、`onSendPacket`…；**VelocityModule 内部** |
| `GrimSpeedHandler` / `StrafeSpeedHandler` / `StrictStrafeSpeedHandler` | `onMove`、`onSendPosition`；**SpeedModule 内部** |
| `EntityMotionAccess` 实体运动量 mixin 接口 | 由 mixin 实现，**不要自己实现** |

## 10. utils/network

`src/main/java/evernight/client/utils/network/`

### `PacketSender`（`network/PacketSender.java:27`）发包

`send(Packet)`（走 `Connection.sendPacket`，触发 `PacketEvent.Send`/`PacketEvent.PostSend`，可被取消）、
`sendDirect(Packet)`（`connection.channel.writeAndFlush`，**跳过 `Connection` 层与 `PacketEvent.Send/PostSend`**，
仍过 netty pipeline，PingSpoof 延迟 handler 照常生效）、`sendUseItemOn(InteractionHand, BlockHitResult)`、
`sendUseItem`（两种重载）、`sendSwing`、`sendAttack(int entityId)`（手工构造 `ServerboundInteractPacket`）、
`sendRotation`（两种）、`sendPosition`、`sendPositionRotation`、`sendPlayerAction(ServerboundPlayerActionPacket.
Action, BlockPos, Direction)`、`sendPlayerCommand`、`sendStartFallFlying`、`sendSwapOffhand`、`sendSelectedSlot`、
`nextPredictionSequence`、`markImportant`。**坑**：全部**真的发包**且**不等服务端响应**、不做预测/回滚（预测只在
`BlockInteractionUtils.placeBlock` 那条路径）；`sendAttack` **不触发本地挥手/攻击冷却**；`ABORT_DESTROY_BLOCK` 的
sequence 固定 0。

### `InteractPacketUtils`（`network/InteractPacketUtils.java:10`）解析 `ServerboundInteractPacket`

`getEntity`（查不到 `null`）、`getAction` → `InteractPacketUtils.Action {INTERACT, ATTACK, INTERACT_AT}`。
用于 `PacketEvent` 判断这包在打谁（例 `client/commands/misc/Fakeplayer/FakeplayerCommand.java:201`）。

### `HttpRequests`（`network/HttpRequests.java:18`）HTTP 客户端

`get(String url)` / `post(String url, BodyPublisher)` / `newRequest(String url)`（带 30s 超时）、
`send(HttpRequest)`（**阻塞**）、`send(HttpRequest, BodyHandler<T>)`、`sendAsync`（`CompletableFuture`）。
**坑**：**别放客户端线程**；失败抛 `CompletionException`/`UncheckedIOException`，参考
`client/commands/social/Namehistory/NamehistoryCommand.java:121`。

| 类 | 关键 API |
| --- | --- |
| `AutoPresetServerEntry` 自动预设服务器配置 | `getServer`、`getPresets`、`getKitName`、`hasKit`、`setKitName` |
| `ImportantPacket`「重要包」标记 mixin 接口 | `evernight$markImportant`；配合 `PacketSender.markImportant` |
| `PingSpoofDelayHandler` PingSpoof netty 延迟 handler | `NAME`；**PingSpoofModule 内部**，由 `ConnectionMixin` 安装 |
| `ServerSideDeathState` 服务端死亡状态 mixin 接口 | 由 mixin 实现，**不要自己实现** |

## 11. utils/world

`src/main/java/evernight/client/utils/world/`

### `BlockInteractionUtils`（`world/BlockInteractionUtils.java:82`）放置/交互/攻击方块

两条放置路径，返回值只表示「已发起」，**不等服务端确认**：

- `placeBlock(BlockPos, Vec3 hitPosition, Direction, boolean airPlace, InteractionHand)` /
  `placeBlock(BlockPos, boolean airPlace, InteractionHand)`：走 `MultiPlayerGameMode#useItemOn`，**带客户端预测**，
  先过 `canPlaceBlock`；需潜行交互时临时 `setSneaking(true)` 再还原；`hitPosition` 传 `null` 自动补「块心 + 面偏移」。
- `sendPlacement`、`placeBlockIgnoringCrystals`：**只发包 + 挥手，无本地预测**。

`public static boolean isPlacingBlock`（放置期间 `true`，`PlayerMixin` 靠它强制 `isSecondaryUseActive`）。
找面：`findSupportDirection(BlockPos[, boolean strictDirection[, boolean checkRaytrace]])`（顺序
DOWN,NORTH,EAST,SOUTH,WEST,UP，跳过顶界/世界边界/不可见面）、`getStrictDirections`（裸 `List`，实为
`List<Direction>`）、`getStrictDirectionsOrFallback`、`isStrictDirectionEnabled`。
找位：`findSupportPosition(BlockPos, int maxDistance, boolean checkPlayers, boolean strictDirection[,
Predicate<? super BlockPos>])`、`findSupportPath`（从远到近 `List<BlockPos>`）。
可行性：`canPlaceBlock(BlockPos, Block|null, boolean playersOnly[, boolean ignoreCrystals])`、
`canPlaceAt(BlockPos, boolean playersOnly[, boolean ignoreCrystals])`、`isOutsidePlacementBounds`、
`hasBlockingEntity(AABB, boolean playersOnly, boolean ignoreCrystals)`、`isCollidable`、`canPlayerInteract`。
水晶/实体：`canPlaceCrystal(BlockPos base, boolean requireTwoAirBlocks, boolean checkEntities, boolean checkCrystals,
boolean allowFire, boolean shortHeight, boolean expandPlayers)`、`attackCrystalAt(BlockPos, int minimumAge)`、
`attackEntity`、`interactWithEntity`（`interactAt`+`interact`+挥手，实际发包）。
杂项：`requiresSneakForInteraction`、`isInteractiveBlock`、`isThrowableItem`、`isGlass`、`getBlock`。
**坑**：`isInteractionCooldownActive`/`resetInteractionCooldown` 是 50ms 手动冷却闸门，**仓库里没有任何地方调
reset**，默认恒 `false`，要用得自己 arm；`canPlaceCrystal` 要求基座是黑曜石/基岩；`attackEntity` 成功后重置攻击冷却。

### `WorldUtils`（`world/WorldUtils.java:65`）世界查询与可见性

`getTopY`（`minY + level.getHeight()`）、`getDimension`（→ `evernight.client.settings.options.DimensionType`）、
`getBlock`、`isAir`、`isReplaceable`、`hasFluid`、`hasCollision`、`getHardness`、`isBedrockOrObsidian`、
`canMineBlock`（空气/流体/负硬度 false）、`getLoadedChunks`、`getLoadedBlockEntities`、`reloadRenderer`、
`canSee(Vec3 origin, Vec3 target)` / `canSee(Vec3, Vec3, BlockPos targetBlock, Direction targetFace)`、
`canSeeFromEyes`、`canSeeBlock`、`canSeeAny`（两种）、`findVisiblePoint`、`streamSphereBlocks(Vec3 center,
float radius)`、`collectSphereBlocks(Vec3 center, float radius, boolean excludeAir)`、`isTargetable`、`getEntityDistance`、
`getEntityRange`（→ `WorldUtils.EntityRange`）、`getBreakProgress`、`isInteractive`、`isStructureChest`、
`distanceToBounds`；常量 `BASTION_FLOOR_BLOCKS`/`ANCIENT_CITY_FLOOR_BLOCKS`/`TRIAL_CHAMBER_FLOOR_BLOCKS`。
**坑**：`canSee` 距离 > 128 直接 false（MISS 或命中点 == target 才算可见）；`reloadRenderer` 走
`levelRenderer.allChanged()`，**开销大**；`isTargetable` 已排除自己/好友/死亡。

### `BlockBreakTracker`（`world/BlockBreakTracker.java:36`，`ClientContext.blockBreakTracker`）挖掘进度

`getBreakProgress`（0~1；无记录/不可挖 0）、`getEntries`（`List<BlockBreakEntry>`）。**坑**：构造时自
`eventBus.subscribe(this)` 订阅 `LocalPlayerTickEvent` 与 `PacketEvent`，与 `SpeedMine` 联动，**不要自己 new**。
`BlockBreakEntry`：`getPosition`、`getProgress`、`getInterpolatedProgress`、`isBroken`、`isExtraBreak`、
`getEntityId`、`getAirTicks`。

| 类 | 关键 API |
| --- | --- |
| `BlockStateUtils` 挡路/可炸判定 | `isBlocking`（`BlockState`/`BlockPos`）、`isBlockingExceptCarpet`、`isBlastResistantUnbreakable`/`isBlastResistantBreakable`、`isWeakOrInstantlyBreakable` |
| `ChunkScanner` 异步分区块扫描 | 构造 `(int workerCount, ChunkScanner.Listener)` 或 3 个 `BiConsumer`；`start`、`stop`、`scanLoadedChunks`、`restartExecutor`、`getExecutor`；被 MultiTask/NewChunks/Search/Tunnels 复用 |
| `MapDataCache` 地图缓存（`ClientContext.mapDataCache`） | `load`、`save`、`getMapData`（`ItemStack, MapId` / `ItemStack, int`） |
| `Waypoint` 路径点（`NamedEntry`） | `new Waypoint(String name, double x, double y, double z, String dimension, String server)` / `(String, Vec3, String, String)`；`getPosition`、`distanceTo`、`getDimension`、`getServer`、`getDisplayComponent`、`matches`、`setToggled` |
| `HoleInfo` 洞数据（`HoleManager`） | `record HoleInfo(HoleInfo.Safety safety, BlockPos pos, AABB bounds, boolean trapped)` + `enum Safety {SAFE, UNSAFE}` |
| `NukerBlockQueue` Nuker 待挖队列 | `enqueue`、`getNextPositions`、`pruneInvalidPositions`、`getPositions`；**NukerModule 内部** |
| `BlockStateSnapshot` 方块状态快照（`WorldStateManager`） | `record BlockStateSnapshot(BlockState state, BlockPos position, long capturedAtMillis)`；`hasExpired`、静态 `captureState` |
| `BlockBreakingState` 破坏状态 mixin 接口（`MultiPlayerGameMode`） | `getCurrentBreakingBlock`、`setBreakingProgress` 等；由 mixin 实现 |
| `BlockRegistryGroupMatcher` 方块版 `RegistryGroupMatcher` | `getRegistryId`、`getGroupNames`、`getGroupEntries` |
| `Season` 季节枚举（`UIModule`/`ClientScreen` 主题） | `WINTER/SPRING/SUMMER/AUTUMN`；`fromMonth`、`containsMonth` |
| `ItemFrameContainerLocator` 展示框标记容器 | `findMarkedFrames`、`findNearestFrame(Predicate<ItemStack>, double)`、`findAllContainers`、`findNearestContainer(Predicate<ItemStack>\|Item, double)`、`resolveContainer(ItemFrame)`、`isContainer(BlockPos)`；按玩家距离升序，只认 `Container` 方块实体 |

## 10. utils/search

统一搜索（`utils/search/`）。配置在 `UI > 搜索`（位于「设置提示」与「元素」之间）：`搜索语言`（`SearchMode.effective()`
解析 `AUTO` → 当前 Evernight 语言；`CHINESE` 匹配中文名 + 智能拼音，`ENGLISH` 匹配英文名 + 注册名）与 `模糊拼音`
（可展开，含 7 个 PinIn 模糊音分项）。

| 类 | 关键 API |
| --- | --- |
| `SearchQuery` 一次查询（不可变） | `of(String)` / `of(String, SearchMode)`、`isBlank`、`text`、`mode`、`matchesEnglish(String...)`（大小写不敏感 `contains`）、`matchesChinese(String...)`（字面或拼音）；内置按候选串的结果缓存，**同一实例可每帧复用** |
| `SearchQueryHolder` 搜索框缓存 | `update(String rawText)`：文本、生效模式或模糊音开关变化才重建 `SearchQuery`，布局循环里直接调 |
| `SearchKey` 条目名字集合 | `of(Collection, Collection)`、`empty`、`matches(SearchQuery)`（按模式选英文/中文一侧）、`english()`、`chinese()`、`allTexts()` |
| `SearchNames` 条目 → `SearchKey` | `forItem`、`forBlock`、`forEntityType`、`forMobEffect`、`forPotion`、`forSound`、`forEnchantment(Enchantment, Identifier)`、`forItemStack`（含附魔/药水效果/自定义名）、`forModule`、`forRegistryEntry(Identifier, entry, label)`；注册表对象带缓存 |
| `LocalizedNames` 固定语言名 | `english` / `chinese`（资源管理器 `en_us` / `zh_cn` 表，含模组键）、`clientEnglish` / `clientChinese`（Evernight 自带语言文件） |
| `PinyinMatcher` 拼音匹配 | `contains(text, query)`（首次调用解析内置 PinIn 字典；模糊音按 `UI > 搜索` 的开关在匹配锁内应用）、`preloadAsync()`（客户端初始化已异步预热） |

**坑**：`PinyinMatcher` 的 PinIn 上下文只在客户端线程匹配；`LocalizedNames` 首次使用会在客户端线程解析语言表（一次性）。

