# Evernight 渲染层 —— 绘制、字体、颜色与 HUD 组件渲染

范围：画 2D 界面、HUD 组件、世界空间方框/线段，量文字宽度、取色板、读写渲染目标。源码省略
`src/main/java/evernight/` 前缀（下文写 `client/render/Xxx.java`），包根 `evernight`；渲染类基本都
`implements ClientAccess`，可直接用 `ClientAccess.evernightMinecraft` 与 `ClientAccess.eventBus`。

## 1. 开工前的三条约定

**① PoseStack**：自绘界面用 `GuiProjectionUtils.toPoseStack(graphics)`；2D 覆盖层（2D ESP、准星、HUD）用
`HudRenderEvent#getPoseStack()`；世界空间用 `WorldRenderEvent#getPoseStack()`。

**② 颜色**：GUI 工具类同时接受 `java.awt.Color` 与 ARGB `int`（`0xAARRGGBB`，`-1` = 不透明白）；`Color` 重载内部取
`color.hashCode()` 当 ARGB，alpha 为 0 的矩形会被**跳过**。

**③ flush**：`GuiDrawUtils` 的四边形/线段先进静态缓存，必须 `GuiDrawUtils.flush()` 才提交（还会 flush `FontRenderer`
并 `endBatch()`）；换投影、`popPose()`、结束面板前都要 flush，只画背景可用 `flush(false)`。

## 2. GuiDrawUtils 绘制工具箱

坐标为屏幕逻辑坐标，z 由 PoseStack 决定。

- `fillRect(poseStack, left, top, right, bottom, int color)` / `…, Color)`：**参数是边界不是宽高**。
- `fillVerticalGradient(poseStack, x, y, width, height, topColor, bottomColor)` / `fillHorizontalGradient(poseStack, x, y, width, height, leftColor, rightColor)`：**左上角 + 宽高**。
- `fillGradientRect(matrix, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft)` / `fillHorizontalGradient(matrix, x, y, width, height, int... colors)`：四角独立 / 多段（≥2 色）。
- `drawRectOutline(poseStack, left, top, right, bottom, Color)`：1px 描边，跟随 alpha。
- `drawLineRect(poseStack, left, top, right, bottom, Color)`：线缓冲，线宽 `1.0F`。
- `drawTexture(poseStack, Identifier, int x, int y, float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color)`：`region*` 取图区域、`texture*` 整图尺寸；11 参重载省 region。
- `drawItem(poseStack, ItemStack, float x, float y, float z)` / `…, boolean flushItemBuffers)`：16×16（内部 `(x+8, y+8, 150+z)` + scale 16）；保层序传 `true`。
- `drawDurabilityBar(poseStack, ItemStack, float x, float y)` / `drawItemCooldown(...)`。
- `drawText(poseStack, String, int x, int y, int color, boolean shadow)`：**原版字体**，不受 Fonts 影响。
- `getLineVertices()` / `flush()` / `flush(boolean flushText)` / `flush(RenderType, boolean)`。
- `RenderUtils.INSTANCE.fillRect(poses, left, top, right, bottom[, z], color/Color)`：立即提交版（`ClientRenderTypes.gui()`），需严控层序时用。
- 仓库**没有**圆角/圆形 helper（grep 不到 `drawRoundedRect`/`drawCircle`），别臆造；现有界面是 `fillRect` + `drawRectOutline` 拼的。

## 3. 字体渲染

入口唯一：`FontRenderer.INSTANCE`（`client/render/font/`）。**不要**直接用 `ClientAccess.evernightMinecraft.font`
—— 会绕过 MiSans 与 NameProtect。

### 3.1 FontRenderer 常用 API

- `getWidth(String)` / `getWidth(Component)` / `getWidth(FormattedCharSequence)` / `getHeight()`：行高原版 9、MiSans 为图集高 `-1`。
- `drawWithShadow(poseStack, String, float x, float y, Color)`；`(poseStack, Component, float x, float y, int argb)` / `…, DisplayMode)`（如 `SEE_THROUGH`）。
- `drawWithShadow(poseStack, String/Component, float x, float y, float scale, Color)`。
- `drawWithoutShadow(poseStack, String, x, y, Color)` / `(…, int argb, DisplayMode)`。
- `flush()`（`GuiDrawUtils.flush()` 已含）/ `isFontLoading()`（重载约 3 tick）/ `getRenderer()` / `createRenderer()` / `setRenderer(AtlasTextRenderer)`。

`FontsModule`（`modules/client/FontsModule.java`）设置会改变结果：`sizeSetting`（字号 ×2）、`styleSetting`、
`antiAliasSetting`、`shadowSetting`（配合 `getShadowOffset()`）、`translateSetting`/`shiftSetting`（全局偏移）。

### 3.2 什么时候走 MiSans、什么时候回退原版

判据：`FontsModule.INSTANCE.isToggled() && renderer != null`。

- 成立 → `AtlasTextRenderer`（内置 MiSans，字号 = `sizeSetting * 2`，`FontsModule` 默认开启）；不成立 →
  `evernightMinecraft.font.drawInBatch(...)`。
- 字体固定内置：`FontSupport` 只读 `assets/evernight/font/MiSans-Regular.ttf`，读不到才退 `SansSerif`；无系统字体查找。
- 排版用 `getWidth/getHeight`，别硬编码像素；NameProtect 开启时二者都先替换玩家名。
- 字符集创建时烘好（ASCII `32..126` + `∞♫♪?` + 语言表字符），**新增字符要等字体重载**；`GuiDrawUtils.drawText` 走原版，**不**受 Fonts 影响。

### 3.3 AtlasTextRenderer / FormattedTextRenderer / FontAtlas

- `AtlasTextRenderer`：`create(int fontSize, FontsStyleChoice style)`、`draw(...)`、`drawCentered(poses, String, double x, double y, int argb)`、`drawWithoutShadow(...)`、`getWidth(String)`、`getHeight()`、`getCharacterWidth(char)`、`flush()`；`§` 颜色码由 `renderString` 解析。
- `FormattedTextRenderer`：`FormattedCharSink` 实现，由 `AtlasTextRenderer.draw(FormattedCharSequence, …)` 内部创建；外部只传 `Component#getVisualOrderText()`，**不要自己 new**。
- `FontAtlas`：`ATLAS_TEXTURE = evernight:textures/font.png`；`ensureGlyph(char)`、`getGlyphWidth(char)`、`getHeight()`、`uploadDirty()`、`release()`。

### 3.4 TextUtils 与 i18n 的边界

`TextUtils`（`client/render/font/`）纯字符串工具，**不碰语言表**；翻译入口 `Translations` 见 `i18n.md`。常用：
`normalizeKeyboardLayout(String)`、`containsIgnoreCase(String, String)`、`splitQuotedArguments(String)`、
`wrapToWidth(String, double maximumWidth)`、`splitOnDelimiter(String)`、`getLastFormattingCode(String)`。

## 4. 颜色 Catppuccin 与 ColorUtils

### 4.1 色板 `client/render/color/Catppuccin.java`（`java.awt.Color`、不透明）

- 强调色：`ROSEWATER` `FLAMINGO` `PINK` `MAUVE` `RED` `MAROON` `PEACH` `YELLOW` `GREEN` `TEAL` `SKY` `SAPPHIRE` `BLUE` `LAVENDER`
- 文字：`TEXT` `SUBTEXT1` `SUBTEXT0`；覆盖层：`OVERLAY2` `OVERLAY1` `OVERLAY0`；表面：`SURFACE2` `SURFACE1` `SURFACE0` `BASE` `MANTLE` `CRUST`
- `Catppuccin.withAlpha(Color, int alpha)`（clamp 0..255）。
- **主题色不是常量**：`ColorUtils.getThemeColor()`（= `UIModule.INSTANCE.colorSetting.getValue()`，默认 `withAlpha(MAUVE, 130)`）；**别写死 `Catppuccin.MAUVE`**。

### 4.2 `client/render/color/ColorUtils.java`

- `withAlpha(Color, int)` / `withAlpha(Color, float)` / `toArgb(Color, int)` / `toArgb(Color, float)`：0..255 与 0..1 两套。
- `packArgb(int r,g,b,a)` / `fromArgb(int)` / `toHex(Color, boolean)` / `parseHexColor(String)`（`#rgb`/`#rrggbb`/`#aarrggbb`）。
- `getThemeColor()`。
- `blend(Color first, Color second, float firstWeight)`：`firstWeight` 是第一个颜色的权重。
- `pulse(Color a, Color b, double period, double offset)`：单位毫秒。
- `rainbow(int offset, float s, float b, int a)` / `rainbow(int period, int offset, float s, float b, int a)`：后者相位 = `ceil(now/20) % period`。
- `getHealthColor(double)` / `getLatencyColor(int)` / `redToGreen(float)` / `darken(Color, float)` / `brighten(Color, float)` / `modulateWithShaderColor(int)`。

HUD 取色用 `HudWidgetModule#getHudColor(float position)` —— HUD 里**不要写死 `Catppuccin.TEXT`**。

## 5. GUI 缩放与投影

### 5.1 `client/render/gui/GuiScaleUtils.java`

- `getScaledWidth()` / `getScaledHeight()`：应用 `scaleMultiplier` 后的逻辑尺寸（**别用 `window.getGuiScaledWidth`**）。
- `getBaseScaledHeight()` / `calculateGuiScale()`。
- `applyGuiScale(float)` / `applyDefaultGuiScale()` / `restoreGuiScale()`：成对，apply 存原值、restore 还原（`ClientScreen.render` 已配对）。
- `calculateScaleMultiplier(float)` / `getScaleMultiplier()` / `isAdvancedUiScaleLoaded()`：装 `advanced-ui-scale` 时部分逻辑跳过。

### 5.2 `client/render/gui/GuiProjectionUtils.java`

- `beginGuiProjection()` / `restoreProjection()`：**成对**，切 GUI 正交并 `translate(0,0,-11000)`，有重入保护。
- `toPoseStack(GuiGraphics)` / `toPoseStack(Matrix3x2fStack)`。

`ClientScreen.render` 与 `mixin/GameRendererMixin` 已成对调用，**不要**在普通 `Screen` 或 HUD 回调里重复
begin/restore；只有自己搭渲染上下文才手动配对。

## 6. HUD 组件渲染

### 6.1 三个角色与渲染链路

- `HudWidgetModule`（`modules/HudWidgetModule.java`）：`calculateDimensions()` 返回 `{width, height}`、`renderContent(PoseStack)`、构造器里 `setRenderer(...)`。
- `HudWidgetRenderer`（`client/render/hud/`）：位置（0..1）、尺寸、锚点、选中/淡入动画、编辑器外壳。
- `HudAnchorGroup`：同锚点组件竖排，算边距。

链路：`HUDModule.onHudRender` → `HudWidgetRenderer.renderingHud = true` → `HudEditorScreen.renderWidgets` →
`HudAnchorGroup.render`。`renderingHud` 为 `true` 是正常 HUD、`false` 是在编辑器里。
编辑器悬停名走 `HudHoverLabel`（`show(text, x, y, direction)`，状态在 `HudEditorScreen.hoverLabel`），普通模块**不要自己 new**。

### 6.2 新增一个 HUD 组件（最小流程）

1. 在 `modules/hud/` 建 `XxxModule extends HudWidgetModule`，构造器 `super("Xxx", new String[0])`（英文名，永不翻译）。
2. 构造器末尾 `this.setRenderer(new ResizableHudWidgetRenderer(this))`；`setRenderer` 是 protected。
3. 覆写 `calculateDimensions()` 返回 `float[]{宽度, 高度}`（父类默认直接抛 `RuntimeException`）。
4. 覆写 `renderContent(PoseStack)`，原点为左上角（对齐用 `getContentX(width)`/`getContentY(height)`）；再注册模块
   （`module-settings.md` 第 9 节）、补两份语言表（`i18n.md` 第 5 节）。文本型另有 `HudTextListRenderer` + `HudTextRow`
   （`module-settings.md` 第 3.3 节）。

### 6.3 HudWidgetRenderer 常用 API

- `getX()` / `getY()`（屏幕坐标，已夹进屏幕）；`getDimensions()` / `setDimensions(float[])`（每帧由 `HudAnchorGroup` 写入）。
- `getContentX(float width)` / `getContentY(float height)`；`getHorizontalDirection()` / `getVerticalDirection()`（`1`/`-1`）。
- `setX(float, boolean screenCoordinates)` / `setY(...)`：`true` = 屏幕坐标，`false` = 0..1 相对坐标。
- `getBounds()`（`FloatBounds`）/ `isMouseOver(x, y)` / `updateSnapping(w, h, 5.0F)`。
- `setAnchor(HudAnchor)` / `getAnchor()`：换组用 `HudWidgetModule#setAnchor`。
- `getEnabledProgress()` / `updateEnabledAnimation()`（0..1，250ms）/ `render(poses, mouseX, mouseY)`：**子类重写先 `super.render(...)`**。

### 6.4 ResizableHudWidgetRenderer

- `getScale()` / `calculateDimensions()`：`Size`（0.5..2.0）× `HUDModule.getTextScale()`（启 hud scale 时）。
- `render(...)`：先 `super.render(...)`，再 scale/translate 调 `widget.renderContent(poses)` → 内容用**未缩放**坐标。
- `enableResizing(HudWidgetModule widget)`：`Size` 进模块（出现在 ClickGUI），允许拖右下角缩放。
- `enableHudScale()` / `isResizeHandleHovered(mouseX, mouseY)`。

### 6.5 锚点分组与边距

`HudAnchor`（`client/settings/options/HudAnchor.java`）：`TOP_LEFT`、`TOP_RIGHT`、`BOTTOM_LEFT`、`BOTTOM_RIGHT`、
`TOP_CENTER`、`NONE`（自由摆放）；自带对齐与命中区域（`getBounds()`），显示名走 `NamedEntry`（键见 `i18n.md`）。
对齐枚举：`HudHorizontalAlignment`（`FREE/LEFT/CENTER/RIGHT`）、`HudVerticalAlignment`（`FREE/TOP/CENTER/BOTTOM`）。

- 锚点 != `NONE`：x 由水平对齐 + `HUDModule.getSafeX()` 算（`calculateX`）；y 由 `calculateY` 算；`BOTTOM_*` 向上堆叠。
- 锚点 == `NONE`：只有非 `FREE` 对齐才对（`layoutFreeWidget`）；平移由 `applyHudTranslation` 加 `getParallaxOffset()`；
  组件不可见时按 `{0, 0}` 算尺寸。
- 边距全来自 `HUDModule`：`getSafeX()` / `getSafeY()`、`getBottomHudOffset()`、`getChatInputOffset()` / `getEffectIconOffset()`、`getTextScale()`、`getParallaxOffset()`。

## 7. 世界空间渲染

### 7.1 入口、坐标系与深度

- 穿墙方框（默认）：`WorldRenderEvent.Main` + `WorldBoxRenderer.fillBox/outlineBox`；管线 `NO_DEPTH_TEST`（`ClientRenderPipelines.WORLD_FILL`、`WORLD_LINES`）。
- 受深度遮挡：`WorldRenderEvent.BeforeBufferSource` + 自己的 `MeshBuffer` + `ClientRenderTypes.worldFillDepth()`/`worldLinesDepth()`。
- 线段/追踪线、世界文字：`WorldRenderEvent.Main` + `LineMeshRenderer`；`RenderUtils.INSTANCE.drawWorldText/drawWorldRect`。

`WorldBoxRenderer` 收**世界坐标 `AABB`**（内部减相机，**不要自己 `translateToCamera`**），以 `priority = -9999` 订阅
`WorldRenderEvent.Main`，末尾自动 flush —— 模块里**不用**自己 flush。

### 7.2 WorldBoxRenderer（`client/render/mesh/`）

- `fillBox(PoseStack, BlockPos, Color)` / `(PoseStack, AABB, Color)` / `(PoseStack, AABB, int)`。
- `fillFadingBox(PoseStack, AABB, Color)` / `outlineFadingBox(PoseStack, AABB, int, float)`：渐隐体 / 竖直棱淡出描边。
- `outlineBox(PoseStack, BlockPos, Color, float lineWidth)` / `(PoseStack, AABB, Color, float)`：12 条棱。
- `applyRenderColor(int)` / `modulateColor(int, int[])` / `flush()`（手动提交一般不需要）。

### 7.3 FeatureBoxRenderer（带淡出、自动过期）

实例在 `ClientContext.featureBoxRenderer`。`register(RenderConfiguration)` 在构造器注册一次；
`addBox(NamedFeature, BlockPos)` / `(NamedFeature, AABB)` 每次要画就调用（`Module` 是 `NamedFeature`，传 `this`）。
`RenderConfiguration`（record）参数依次 `feature, fillColor, outlineColor, lineWidth, fadeDuration, retainPreviousBoxes,
fadeEnabled, fadeTimeScale`，都是 `ValueProvider`（可传 `Setting` 或 lambda）。**`unregister` 抛
`UnsupportedOperationException`，不支持运行时注销**；过期框在 `LocalPlayerTickEvent` 清理、`WorldRenderEvent.Main`
自动绘制，无需 flush。

### 7.4 LineMeshRenderer（`client/render/mesh/`）

- `begin(WorldRenderEvent, float width)` / `begin(PoseStack, float width)`。
- `addVertex(double x, double y, double z, Color)` / `(…, int color)`：顶点**成对**才成线段。
- `addVertex(x, y, z, int color, boolean relativeToCamera, boolean clamp)`：`clamp = true` 把视口外端点夹到屏幕边缘。
- `addLine(Vec3 from, Vec3 to, Color)` / `addTracer(Vec3 position, Color)` / `draw()`。

### 7.5 MeshBuffer + VertexGeometry（底层顶点批）

- `MeshBuffer.create()` / `create(int capacity)` / `setFormat(Mode, VertexFormat)`：实心面 `Mode.QUADS + DefaultVertexFormat.POSITION_COLOR`、线段 `Mode.LINES + POSITION_COLOR_NORMAL_LINE_WIDTH`。
- `begin(Mode, VertexFormat)` / `isBuilding()` / `draw(RenderType)` / `draw()`（用 `MeshDrawContext` 当前类型）。
- `VertexGeometry.appendFilledBox(VertexConsumer, Matrix4f, AABB, int)` / `appendBoxOutline(VertexConsumer, Pose, AABB, float, int)` / `appendLine(VertexConsumer, Pose, 6 个坐标, width, color)`（另有 `startColor,endColor` 重载）。
- `VertexGeometry.forEachCircleSegment(float radius, int segments, CircleSegmentConsumer)`（<2 段抛 `IllegalArgumentException`）。
- `MeshDrawContext.setRenderType(RenderType)` / `getBuffer()`（共享）/ `draw(MeshData)`。

### 7.6 RenderUtils 世界侧辅助（`client/render/util/`）

- `translateToCamera(PoseStack, Vec3)` / `applyCameraRotation(PoseStack)`。
- `drawWorldText(poses, String, Vec3, double scale, Color, boolean shadow)` / 带 offset 重载（内部 0.025 缩放）。
- `drawWorldRect(...)` / `drawWorldTexture(...)` / `addWorldLine(poses, BufferBuilder, Vec3, Vec3, float, int, int)`。
- `renderEntity(Entity, partialTick, PoseStack, MultiBufferSource)` / `flushEntities()` / `getEntityBuffers()`。
- `WORLD_PROJECTION_MATRIX` / `getPartialTick()` / `setPartialTick(float)` / `setRenderColor(r,g,b,a)` / `getRenderColor()`。

## 8. 渲染状态与渲染目标

`RenderState`（`client/render/shader/`）：`apply(boolean entering)`（进入 true、退出 false）、`then(next)`、`combine(states...)`。
`RenderStateManager`（`client/managers/render/`）预置 `BLENDED_SMOOTH_LINES`、`THROUGH_WALLS`（关深度写 +
`glDepthRange(0, 0.1)`）、`POLYGON_OFFSET`、`POLYGON_OFFSET_THROUGH_WALLS`；入口 `runWithStates(Runnable, RenderState...)`、
`addStates(...)`、`reset()`。叠穿墙：`addStates(POLYGON_OFFSET_THROUGH_WALLS)` → 绘制 → `GuiDrawUtils.flush()` → `reset()`。

- `RenderTargetUtils.pushFramebuffer(RenderTarget)` / `popFramebuffer()`。
- `overrideRenderTargets(RenderTarget)` / `restoreRenderTargets()`：**成对**，恢复时弹栈 + 还原 override（**不配对会画到错误目标**）。
- `setMainRenderTarget(RenderTarget)`（`MaskShaderRenderer.captureMask` 用）；`readShaderSource(String resourcePath)` 读 `/assets/evernight/shaders/<resourcePath>`。
- `BufferSourceUtils.cleanup(MultiBufferSource)`：只有实现 `BufferSourceCleanup` 的缓冲源才会被清理。
- 整屏辅助（`RenderUtils`）：`clearColor(RenderTarget[, Color])`、`clearDepth(RenderTarget)`、`blitToMain(RenderTarget[, RenderPipeline[, FilterMode]])`、`drawFullscreenQuad()`。

## 9. 着色器体系一览

- `managers/ShaderRegistry.java`：私有 `register(id, pass)` 只在构造器调用；`getPass(id)`、`render(id, Runnable)`、
  `reloadAll()`、`getBufferSource()`；静态 `setRenderColor(int)`、`finishFeatureRendering()`。新增即加
  `this.register("my_id", new MyPass());`
- `ShaderPass` / `BaseShaderPass`：接口 `getProgram()` / `updateUniforms()` / `render(Runnable draw)`；基类编译资源、
  `glUseProgram` 后 draw（构造 `(String vertexResource, String fragmentResource)`，`initializeProgram()`）。
- `PostProcessShaderPass` / `PreprocessedShaderPass`：渲进 `POST_TARGET`、设 `u_Proj/u_ModelView/u_Size/u_Texture/u_Time`、
  `renderToTarget(draw)`；Preprocessed 多一道 pre-pass。**新增后期效果首选基类**。
- `ShaderPipeline`：串联 `ShaderStage`，最后 `blitToMain`。
- `ClientRenderPipelines`：`RenderPipeline`/`Snippet` 常量 `GUI_LINES`、`GUI_TRIANGLES`、`WORLD_FILL`、`WORLD_LINES`、`WORLD_LINES_DEPTH`、
  `ENTITY_SHADER`、`BLIT_TRANSLUCENT`/`BLIT_NO_BLEND`；新管线写成 `public static final RenderPipeline`。
- `ClientRenderTypes`（`client/render/util/`）：`gui()`、`guiTriangles()`、`guiLines()`、`worldFill()`、`worldLines()`、
  `font(Identifier, boolean)`、`image(Identifier)`；**别自己 `RenderType.create`**。
- `ShaderUniforms`：`set(name, Matrix4f)`、`set(name, float)`、`set(name, float, float)`、`set(name, int)`、
  `set(name, boolean)`、`set(name, Color)`、`set(name, int unit, RenderTarget)`；在 `updateUniforms()` 里调用。
- `ReloadableShaderPass` / `MaskShaderRenderer`：重载时重新编译；`MaskShaderRenderer` 是 GUI 模糊/发光入口：
  `renderBlur(Runnable drawMask, float width)`、`captureMask(...)`、`renderEffect(String id)`。F3+T 生效就实现 Reloadable。

调用：`ClientContext.shaderRegistry.render("id", draw)`；资源路径由 `readShaderSource` 拼成
`/assets/evernight/shaders/<resourcePath>`，`BaseShaderPass` 构造参数写相对该目录的路径。

## 10. 本机渲染期覆盖（可见性 / 皮肤层）

只想改「自己看到的」、不动服务器同步状态（例：`SkinFlickerModule` 的可见性）时，全部在渲染期判断。

- 皮肤层：`Avatar#isModelPartShown(PlayerModelPart)`（`net.minecraft.world.entity.Avatar`，`Player` 继承它）。
- 第三人称：`AvatarRenderer#extractRenderState` 写进 `AvatarRenderState.showHat / showJacket / showLeftSleeve / showRightSleeve / showLeftPants / showRightPants / showCape`。
- 第一人称手部：`ItemInHandRenderer#renderPlayerArm` / `renderMapHand` 把 `isModelPartShown(LEFT_SLEEVE/RIGHT_SLEEVE)` 当 `AvatarRenderer#renderRightHand / renderLeftHand(..., boolean sleeveVisible)` 的参数传下去。
- 玩家本地选项：`Minecraft#options` 的 `isModelPartEnabled` / `setModelPart`；`Options#broadcastOptions()` 会发给服务器，**别人就能看到**。

写法：mixin 方法**声明所在的类**（`Avatar`），注入里用 `(Object) this == Minecraft.getInstance().player` 区分本机与远端，
返回模块缓存的「改动前」值（参考 `mixin/AvatarMixin.java` + `SkinFlickerModule#shouldSuppressLocalFlicker`）。

Shader 模块会把同一批对象二次渲染进自己的目标：

- `ClientContext.shaderRegistry.renderingIntoTarget`：`PostProcessShaderPass#renderToTarget` 期间。
- `ClientContext.shaderRegistry.renderingHands`：`ShaderModule` 的手部 pass 期间。

要「皮肤照常闪、但着色器里不闪」，就在这两个标志下返回稳定值；`shaderRegistry` 由 `GameRendererMixin` 创建，
**读之前判空**。判定覆盖**所有 `PlayerModelPart`**（不只袖子）；用 Iris 光影时再叠加
`IrisShaderBridge.isHandRendererActive()`（影子 pass 的手部）。
