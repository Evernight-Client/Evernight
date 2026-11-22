# Evernight 渲染层 —— 绘制、字体、颜色与 HUD 组件渲染

适用范围：要在 Evernight 里画 2D 界面、HUD 组件、世界空间方框/线段，或需要量文字宽度、取色板、读写渲染目标时。
源码路径省略 `src/main/java/evernight/` 前缀（下文只写 `client/render/Xxx.java`），包根 `evernight`。
渲染类基本都 `implements ClientAccess`，可直接用 `ClientAccess.evernightMinecraft`（`client/ClientAccess.java:19`）与
`ClientAccess.eventBus`（同文件 `:23`）。

## 目录

- [1. 开工前的三条约定](#1-开工前的三条约定)
- [2. GuiDrawUtils 绘制工具箱](#2-guidrawutils-绘制工具箱)
- [3. 字体渲染](#3-字体渲染)
- [4. 颜色 Catppuccin 与 ColorUtils](#4-颜色-catppuccin-与-colorutils)
- [5. GUI 缩放与投影](#5-gui-缩放与投影)
- [6. HUD 组件渲染](#6-hud-组件渲染)
- [7. 世界空间渲染](#7-世界空间渲染)
- [8. 渲染状态与渲染目标](#8-渲染状态与渲染目标)
- [9. 着色器体系一览](#9-着色器体系一览)
- [10. 本机渲染期覆盖（可见性 / 皮肤层）](#10-本机渲染期覆盖可见性--皮肤层)
- [常见任务速查](#常见任务速查)

## 1. 开工前的三条约定

**① PoseStack 从哪拿**：

| 场景 | 拿 PoseStack | 参考 |
| --- | --- | --- |
| 自绘界面（ClickGUI、编辑器） | `GuiProjectionUtils.toPoseStack(graphics)` | `gui/screens/ClientScreen.java:291` |
| 2D 覆盖层（2D ESP、准星、HUD） | `HudRenderEvent#getPoseStack()` | `events/HudRenderEvent.java:12` |
| 世界空间 | `WorldRenderEvent#getPoseStack()` | `events/WorldRenderEvent.java:9` |

**② 颜色怎么传**：GUI 工具类同时接受 `java.awt.Color` 与 ARGB `int`（`0xAARRGGBB`，`-1` = 不透明白）；`Color` 重载内部取
`color.hashCode()` 当 ARGB（`GuiDrawUtils.java:208`），alpha 为 0 的矩形会被跳过（`:192`）。

**③ 什么时候 flush**：`GuiDrawUtils` 的四边形/线段写进静态缓存（`:28-29`），必须 `GuiDrawUtils.flush()` 才提交；`flush()` 还会
`FontRenderer.INSTANCE.flush()` 并 `renderBuffers().bufferSource().endBatch()`（`:222-238`）。换投影、`popPose()`、结束面板前都要
flush，只画背景可用 `flush(false)`（`gui/screens/ClientScreen.java:461`）。

## 2. GuiDrawUtils 绘制工具箱

源码：`client/render/gui/GuiDrawUtils.java`。坐标是屏幕逻辑坐标（受 GUI scale 影响），z 由 PoseStack 决定。

| 方法（行号） | 说明 |
| --- | --- |
| `fillRect(poseStack, left, top, right, bottom, int color)` :190 / `…, Color)` :206 | 实心矩形。**参数是边界**（right/bottom 为终点），不是宽高 |
| `fillVerticalGradient(poseStack, x, y, width, height, topColor, bottomColor)` :200 | 竖直渐变。**参数是左上角 + 宽高** |
| `fillHorizontalGradient(poseStack, x, y, width, height, leftColor, rightColor)` :216 | 水平渐变（左上角 + 宽高） |
| `fillGradientRect(matrix, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft)` :126 | 四角独立颜色的渐变（左上角 + 宽高） |
| `fillHorizontalGradient(matrix, x, y, width, height, int... colors)` :135 | 多段横向渐变，至少 2 色 |
| `drawRectOutline(poseStack, left, top, right, bottom, Color)` :171 | 1px 实心描边（内部 4 个 `fillRect`），跟随 Color 的 alpha |
| `drawLineRect(poseStack, left, top, right, bottom, Color)` :160 | 用线缓冲描边，线宽固定 `lineWidth = 1.0F`（`:30`） |
| `drawTexture(poseStack, Identifier, int x, int y, float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color)` :70 | 画贴图/图标/头像；`region*` 是取图区域，`texture*` 是整张图尺寸；11 参重载省掉 region（:78） |
| `drawItem(poseStack, ItemStack, float x, float y, float z)` :66 / `…, boolean flushItemBuffers)` :85 | 16×16 物品模型（内部平移到 `(x+8, y+8, 150+z)` 再 scale 16）；需与后续文字保持层序时传 `true`（内部 `flushEntities()`） |
| `drawDurabilityBar(poseStack, ItemStack, float x, float y)` :114 / `drawItemCooldown(...)` :179 | 在 `(x, y)` 处画物品耐久条 / 冷却遮罩 |
| `drawText(poseStack, String, int x, int y, int color, boolean shadow)` :152 | **原版字体**，不受 Fonts 模块影响；中文/排版请用 `FontRenderer`（§3） |
| `getLineVertices()` :36 / `flush()` :32 / `flush(boolean flushText)` :222 / `flush(RenderType, boolean)` :226 | 直接拿 `lineBuffer` 写线段 / 提交缓存（第三版可换渲染类型） |

- 立即提交版 `RenderUtils.INSTANCE.fillRect(poses, left, top, right, bottom[, z], color/Color)`（`RenderUtils.java:187`/`:206`/`:247`）
  不进缓存、立刻用 `ClientRenderTypes.gui()` 提交，需要严格控制层序时用它（`ClientScreen.java:365` 的 tooltip 背景）。
- 仓库里**没有**圆角/圆形 helper（全仓 grep 不到 `drawRoundedRect`/`drawCircle`）。现有界面都是 `fillRect` + `drawRectOutline` 拼的
  （`gui/panels/Panel.java:285-289`、`ClientScreen.java:362-368`）；需要圆角请自己按象限画，不要臆造 API。

```java
// 面板底 + 顶部渐变 + 描边 + 标题（写法取自 gui/panels/Panel.java:285-289、ClientScreen.java:362-374）
GuiDrawUtils.fillRect(poseStack, x, y, x + width, y + height, ColorUtils.withAlpha(Catppuccin.BASE, 180));
GuiDrawUtils.fillVerticalGradient(poseStack, x, y, width, 10.0F, ColorUtils.toArgb(Catppuccin.MAUVE, 0.35F), ColorUtils.toArgb(Catppuccin.MAUVE, 0.0F));
GuiDrawUtils.drawRectOutline(poseStack, x - 1.0F, y - 1.0F, x + width, y + height,
        ColorUtils.withAlpha(Catppuccin.SURFACE2, 200));
FontRenderer.INSTANCE.drawWithShadow(poseStack, title, x + 2.0F, y + 2.0F, Catppuccin.TEXT);
GuiDrawUtils.flush();
```

## 3. 字体渲染

入口唯一：`FontRenderer.INSTANCE`（`client/render/font/FontRenderer.java:18`）。**不要**直接用 `ClientAccess.evernightMinecraft.font`
—— 那会绕过 MiSans 与 NameProtect。

### 3.1 FontRenderer 常用 API

| 方法（行号） | 说明 |
| --- | --- |
| `getWidth(String)` :74 / `getWidth(Component)` :159 / `getWidth(FormattedCharSequence)` :109 | 当前生效字体的像素宽度 |
| `getHeight()` :26 | 行高。原版字体恒为 9；MiSans 为图集高 `-1` |
| `drawWithShadow(poseStack, String, float x, float y, Color)` :191 | 最常用：带阴影一行文字 |
| `drawWithShadow(poseStack, Component, float x, float y, int argb)` :118 / `…, DisplayMode)` :122 | 传 ARGB，或用 `DisplayMode`（如 `SEE_THROUGH`） |
| `drawWithShadow(poseStack, String/Component, float x, float y, float scale, Color)` :163/:173 | 按比例缩放绘制 |
| `drawWithoutShadow(poseStack, String, x, y, Color)` :90 / `(…, int argb, DisplayMode)` :140 | 无阴影 |
| `flush()` :38 / `isFontLoading()` :44 / `getRenderer()` :48 / `createRenderer()` :21 / `setRenderer(AtlasTextRenderer)` :94 | 提交图集字形（`GuiDrawUtils.flush()` 已包含）/ 字体重载冷却中（约 3 tick）/ 取、重建、替换底层图集渲染器 |

`FontsModule`（`modules/client/FontsModule.java`）的设置会改变这些结果：`sizeSetting`（字号 ×2）、`styleSetting`、
`antiAliasSetting`、`shadowSetting`（阴影偏移，配合 `getShadowOffset()` :67）、`translateSetting`/`shiftSetting`（全局偏移 :59/:63）。

### 3.2 什么时候走 MiSans、什么时候回退原版

判据只有一条：`FontsModule.INSTANCE.isToggled() && renderer != null`（`FontRenderer.java:78`、`:127`、`:150`）。

- 成立 → 走 `AtlasTextRenderer`（内置 MiSans 图集字，渲染器惰性创建，字号 = `sizeSetting * 2`，`:21-24`）；`FontsModule` 在客户端初始化时
  默认开启（`modules/client/FontsModule.java:99-102`），所以默认就是 MiSans。不成立 → 调 `evernightMinecraft.font.drawInBatch(...)` 用原版字体
  （`FontRenderer.java:130-133`、`:153-156`）。
- 字体资源固定内置：`FontSupport` 只读 `assets/evernight/font/MiSans-Regular.ttf`（`client/render/font/FontSupport.java:16`），读不到才退逻辑字体
  `SansSerif`（`:53-59`）；没有系统字体查找或用户覆盖（类注释 `:8-13`）。
- 排版必须用 `getWidth/getHeight` 而不是硬编码像素：MiSans 与原版行高不同（§3.1）。NameProtect 开启时 `getWidth` 与绘制都会先替换玩家名
  （`FontRenderer.java:75-77`），直接量当前字符串即可。
- 图集字符集在创建时烘好：ASCII `32..126` + `∞♫♪?` + 语言表全部字符（`AtlasTextRenderer.java:135-143`）；新增字符要等字体重载后才进图集。
  `GuiDrawUtils.drawText` 走原版 `font.drawInBatch`（`GuiDrawUtils.java:152-158`），**不**受 Fonts 模块影响。

```java
// 一行文字水平/垂直居中（真实 API 组合）
float textWidth = FontRenderer.INSTANCE.getWidth(text);
FontRenderer.INSTANCE.drawWithShadow(poseStack, text, x + (width - textWidth) / 2.0F,
        y + (height - FontRenderer.INSTANCE.getHeight()) / 2.0F, Catppuccin.TEXT);
```

### 3.3 AtlasTextRenderer / FormattedTextRenderer / FontAtlas

- `AtlasTextRenderer`（`client/render/font/AtlasTextRenderer.java`）：`create(int fontSize, FontsStyleChoice style)` :127、`draw(...)`
  :94/:98/:102/:118、`drawCentered(poses, String, double x, double y, int argb)` :81、`drawWithoutShadow(...)` :220、
  `getWidth(String)` :228、`getHeight()` :85、`getCharacterWidth(char)` :199、`flush()` :49。一般经 `FontRenderer.INSTANCE.getRenderer()` 取。
- `FormattedTextRenderer`（`client/render/font/FormattedTextRenderer.java:12`）：`FormattedCharSink` 实现，由
  `AtlasTextRenderer.draw(FormattedCharSequence, …)` 内部创建（`AtlasTextRenderer.java:102-116`），逐段按 `Style#getColor()` 改色、
  阴影用 0.25 亮度；外部只传 `Component#getVisualOrderText()`，不要自己 new。`§` 颜色码走图集时由
  `AtlasTextRenderer.renderString` :168 解析（解析循环在 :178-193），走原版时由原版解析。
- `FontAtlas`（`client/render/font/FontAtlas.java:27`）：字形图集本体，`ATLAS_TEXTURE = evernight:textures/font.png`；`ensureGlyph(char)` :98、
  `getGlyphWidth(char)` :351、`getHeight()` :359、`uploadDirty()` :312、`release()` :363。只有做自定义图集渲染器时才碰。

### 3.4 TextUtils 与 i18n 的边界

`client/render/font/TextUtils.java` 是纯字符串工具，**不碰语言表**；玩家可见文本的翻译入口是 `Translations`
（`client/render/text/Translations.java`），键格式见 `i18n.md`。常用方法：`normalizeKeyboardLayout(String)` :16（俄文键盘布局映射回英文）、
`containsIgnoreCase(String, String)` :88、`splitQuotedArguments(String)` :92、`wrapToWidth(String, double maximumWidth)` :29（按当前字体
宽度折行，返回 `List`，暂无调用点）、`splitOnDelimiter(String)` :59 / `getLastFormattingCode(String)` :75（暂无调用点）。

## 4. 颜色 Catppuccin 与 ColorUtils

### 4.1 色板 `client/render/color/Catppuccin.java`（全部 `java.awt.Color`、不透明）

| 分组 | 常量 |
| --- | --- |
| 强调色 :15-28 | `ROSEWATER` `FLAMINGO` `PINK` `MAUVE` `RED` `MAROON` `PEACH` `YELLOW` `GREEN` `TEAL` `SKY` `SAPPHIRE` `BLUE` `LAVENDER` |
| 文字 :30-32 | `TEXT` `SUBTEXT1` `SUBTEXT0` |
| 覆盖层 :34-36 | `OVERLAY2` `OVERLAY1` `OVERLAY0` |
| 表面 :38-43 | `SURFACE2` `SURFACE1` `SURFACE0` `BASE` `MANTLE` `CRUST` |

- `Catppuccin.withAlpha(Color, int alpha)` :48（clamp 0..255）—— 给不透明色加透明度用。
- **主题色不是常量**：用 `ColorUtils.getThemeColor()`（= `UIModule.INSTANCE.colorSetting.getValue()`，`ColorUtils.java:41-43`；默认
  `withAlpha(MAUVE, 130)`，见 `modules/client/UIModule.java:243`）。玩家可改，别写死 `Catppuccin.MAUVE` 当主题色。

### 4.2 `client/render/color/ColorUtils.java`

| 方法（行号） | 说明 |
| --- | --- |
| `withAlpha(Color, int)` :188 / `withAlpha(Color, float)` :142 / `toArgb(Color, int)` :213 / `toArgb(Color, float)` :192 | 两个重载各一：0..255 与 0..1（给 `fillRect` 等用 ARGB int） |
| `packArgb(int r,g,b,a)` :126 / `fromArgb(int)` :37 / `toHex(Color, boolean)` :179 / `parseHexColor(String)` :45 | ARGB 打包解包；十六进制读写（`#rgb`/`#rrggbb`/`#aarrggbb`） |
| `getThemeColor()` :41 | 当前主题色 |
| `blend(Color first, Color second, float firstWeight)` :130 | 线性插值（含 alpha）；`firstWeight` 是第一个颜色的权重 |
| `pulse(Color a, Color b, double period, double offset)` :95 | 双色呼吸；`period`/`offset` 单位毫秒（内部用 `System.currentTimeMillis()`） |
| `rainbow(int offset, float s, float b, int a)` :102 / `rainbow(int period, int offset, float s, float b, int a)` :119 | 彩虹；后者自定义周期（相位 = `ceil(now/20) % period`） |
| `getHealthColor(double)` :21 / `getLatencyColor(int)` :16 / `redToGreen(float)` :175 / `darken(Color, float)` :77 / `brighten(Color, float)` :153 / `modulateWithShaderColor(int)` :33 | 血量/延迟/进度取色 / 变暗变亮 / 乘 `RenderUtils` 全局颜色 |

```java
// 常用组合（每个调用在真实源码里都出现过）
Color theme = ColorUtils.getThemeColor();                               // 主题色（UIModule 设置）
Color background = ColorUtils.withAlpha(Catppuccin.BASE, 160);          // 0..255 透明度
int packed = ColorUtils.toArgb(theme, 0.8F);                            // 0..1 透明度 -> ARGB
Color hint = ColorUtils.pulse(Catppuccin.OVERLAY1, Catppuccin.TEXT, 2000.0D, 250.0D); // ClickGuiScreen.java:252
```

HUD 组件取色用 `HudWidgetModule#getHudColor(float position)`（`modules/HudWidgetModule.java:120`）：按 HUD 的渐变/彩虹设置返回颜色并随
玩家设置变化 —— HUD 里不要写死 `Catppuccin.TEXT`。

## 5. GUI 缩放与投影

### 5.1 `client/render/gui/GuiScaleUtils.java`

| 方法（行号） | 说明 |
| --- | --- |
| `getScaledWidth()` :22 / `getScaledHeight()` :18 | 应用 `scaleMultiplier` 后的逻辑尺寸（自绘界面用这个，别用 `window.getGuiScaledWidth`） |
| `getBaseScaledHeight()` :13 / `calculateGuiScale()` :47 | 不受乘数影响的基准高度 / 当前窗口应使用的 GUI scale 整数 |
| `applyGuiScale(float)` :52 / `applyDefaultGuiScale()` :43 / `restoreGuiScale()` :26 | 成对切换：apply 保存原值，restore 还原（`ClientScreen.render` 已配对，:304-305、:383） |
| `calculateScaleMultiplier(float)` :64 / `getScaleMultiplier()` :39 / `isAdvancedUiScaleLoaded()` :35 | 鼠标坐标换算 / 当前乘数；装了 `advanced-ui-scale` 模组时部分逻辑跳过 |

### 5.2 `client/render/gui/GuiProjectionUtils.java`

| 方法（行号） | 说明 |
| --- | --- |
| `beginGuiProjection()` :33 / `restoreProjection()` :22 | **成对**：保存 projection+modelView，切 GUI 正交投影并 `translate(0,0,-11000)`；有重入保护（`:34`、`:23`） |
| `toPoseStack(GuiGraphics)` :58 / `toPoseStack(Matrix3x2fStack)` :49 | 把 `GuiGraphics` 的 2D 变换转成 `PoseStack`，让 `GuiDrawUtils` 可用 |

`ClientScreen.render` 与 `mixin/GameRendererMixin.java:297`/`:334` 已经成对调用过，**不要**在普通 `Screen` 或 HUD 事件回调里重复
begin/restore。只有自己搭渲染上下文（如重放一批延迟绘制）才需要手动配对：

```java
// modules/client/FontsModule.java:83-88 的真实写法
GuiProjectionUtils.beginGuiProjection();
try {
    this.flushQueuedDraws(new PoseStack());
} finally {
    GuiProjectionUtils.restoreProjection();
}
```

## 6. HUD 组件渲染

### 6.1 三个角色与渲染链路

| 层 | 类（源码） | 职责 |
| --- | --- | --- |
| 数据 + 内容 | `HudWidgetModule`（`modules/HudWidgetModule.java:23`） | 模块本体：`calculateDimensions()` 返回 `{width, height}`、`renderContent(PoseStack)` 画内容、构造器里 `setRenderer(...)` |
| 布局 + 编辑器外壳 | `HudWidgetRenderer`（`client/render/hud/HudWidgetRenderer.java:20`） | 位置（0..1 相对坐标）、尺寸、锚点、选中/淡入动画；在 HUD 编辑器里画背景/描边/悬浮名 |
| 分组排版 | `HudAnchorGroup`（`client/render/hud/HudAnchorGroup.java:15`） | 把同一锚点的组件竖排，算边距 |

链路：`HUDModule.onHudRender`（`modules/client/HUDModule.java:124`）→ `HudWidgetRenderer.renderingHud = true`（:175）→
`HudEditorScreen.renderWidgets`（`gui/screens/HudEditorScreen.java:223`）→ 每个 `HudAnchorGroup.render`（:52）。`renderingHud == true`
表示正常 HUD（只画内容），`false` 表示在 HUD 编辑器里（额外画背景、选中框、缩放柄）。编辑器里悬停 300ms 的模块名由
`HudHoverLabel`（`client/render/hud/HudHoverLabel.java:3`）提供：`HudWidgetRenderer.render` 调它的 `show(text, x, y, direction)` :38
（绘制在 `HudEditorScreen.java:349-356`）—— 它是编辑器专用状态（`HudEditorScreen.hoverLabel` :41），普通模块不要自己 new。

### 6.2 新增一个 HUD 组件（最小流程）

1. 在 `modules/hud/` 建 `XxxModule extends HudWidgetModule`，构造器 `super("Xxx", new String[0])`（英文名，永不翻译）。
2. 构造器末尾 `this.setRenderer(new ResizableHudWidgetRenderer(this))`；`setRenderer` 是 protected（`HudWidgetModule.java:116`）。
3. 覆写 `calculateDimensions()` 返回 `float[]{宽度, 高度}`（父类默认实现直接抛 `RuntimeException`，`HudWidgetModule.java:101-103`）。
4. 覆写 `renderContent(PoseStack)`，原点为组件左上角（对齐用 `getContentX(width)`/`getContentY(height)`）；最后注册模块（见
   `module-settings.md`）并在两份语言表加 `evernight.names.modules.Xxx`/`evernight.descriptions.modules.Xxx`（见 `i18n.md`）。

```java
public XxxModule() {
    super("Xxx", new String[0]);
    this.setRenderer(new ResizableHudWidgetRenderer(this));
}
@Override
public float[] calculateDimensions() {
    return new float[]{FontRenderer.INSTANCE.getWidth(this.text), FontRenderer.INSTANCE.getHeight()};
}
@Override
public void renderContent(PoseStack poses) {
    FontRenderer.INSTANCE.drawWithShadow(poses, this.text, 0.0F, 0.0F, Catppuccin.TEXT);
}
```

### 6.3 HudWidgetRenderer 常用 API

| 方法（行号） | 说明 |
| --- | --- |
| `getX()` :66 / `getY()` :57 | 屏幕坐标（相对坐标 × 窗口尺寸，并被夹在屏幕内） |
| `getDimensions()` :62 / `setDimensions(float[])` :160 | 当前尺寸，由 `HudAnchorGroup` 每帧写入 |
| `getContentX(float width)` :189 / `getContentY(float height)` :184 | 内容对齐起点（受锚点/方向影响） |
| `getHorizontalDirection()` :241 / `getVerticalDirection()` :225 | `1`/`-1`：组件在屏幕左/右、上/下，决定悬浮名与展开方向 |
| `setX(float, boolean screenCoordinates)` :172 / `setY(...)` :198 | `true` = 屏幕坐标，`false` = 0..1 相对坐标 |
| `getBounds()` :206 / `isMouseOver(x, y)` :165 / `updateSnapping(w, h, 5.0F)` :79 | 命中测试（`FloatBounds`）/ 拖拽吸附到边缘或中心 |
| `setAnchor(HudAnchor)` :148 / `getAnchor()` :213 | 锚点；真正换组请用 `HudWidgetModule#setAnchor`（`modules/HudWidgetModule.java:108-114`） |
| `getEnabledProgress()` :71 / `updateEnabledAnimation()` :259 / `render(poses, mouseX, mouseY)` :110 | 开关淡入淡出进度（0..1，250ms）/ 画编辑器外壳并处理悬停；**子类重写时先 `super.render(...)`**（`modules/hud/HudTextListRenderer.java:59`） |

### 6.4 ResizableHudWidgetRenderer

`client/render/hud/ResizableHudWidgetRenderer.java`：给组件加整体缩放，内容在自己的缩放坐标系里绘制。

| 方法（行号） | 说明 |
| --- | --- |
| `getScale()` :31 / `calculateDimensions()` :45 | 内部 `Size` 设置（0.5..2.0，:24-26）× `HUDModule.getTextScale()`（仅启用 hud scale 时）；`calculateDimensions` 会把结果乘 scale |
| `render(...)` :53 | 先 `super.render(...)`，再 scale/translate 后调 `widget.renderContent(poses)` → 内容里用**未缩放**坐标 |
| `enableResizing(HudWidgetModule widget)` :107 | 把内部 `Size` 设置注册进模块（出现在 ClickGUI）并允许拖拽右下角缩放；用法 `renderer.enableResizing(this)`（`modules/hud/InventoryModule.java:304`） |
| `enableHudScale()` :112 / `isResizeHandleHovered(mouseX, mouseY)` :97 | 额外乘 HUD 全局文字缩放（`modules/hud/EntityListModule.java:188`）/ 右下角 5×5 缩放柄命中 |

### 6.5 锚点分组与边距

`HudAnchor`（`client/settings/options/HudAnchor.java:13`）：`TOP_LEFT`、`TOP_RIGHT`、`BOTTOM_LEFT`、`BOTTOM_RIGHT`、`TOP_CENTER`、
`NONE`（自由摆放）；枚举自带对齐方式与命中区域（`getBounds()` :61），显示名走 `NamedEntry`（键见 `i18n.md`）。对齐枚举是
`HudHorizontalAlignment`（`FREE/LEFT/CENTER/RIGHT`，`client/render/hud/HudHorizontalAlignment.java:3`）与 `HudVerticalAlignment`
（`FREE/TOP/CENTER/BOTTOM`，`client/render/hud/HudVerticalAlignment.java:3`）。

`HudAnchorGroup`（`client/render/hud/HudAnchorGroup.java`）排版规则：

- 锚点 != `NONE`：x 由锚点水平对齐 + `HUDModule.getSafeX()` 算（`calculateX` :113）；第一个组件的 y 由 `calculateY` :39 算
  （`TOP` 加 `getSafeY()`、`BOTTOM` 减高度与底部边距、`CENTER` 居中）；`BOTTOM_*` 向上堆叠（:103-111），其余向下堆叠（:84-87）。
- 锚点 == `NONE`：只有明确设了非 `FREE` 的 `HudHorizontalAlignment`/`HudVerticalAlignment` 才对齐（`layoutFreeWidget` :92-101）；分组平移由
  `applyHudTranslation` :122 加 `HUDModule.getParallaxOffset()`；组件不可见（未启用且淡出进度为 0）时按 `{0, 0}` 算尺寸（:61-67）。

边距全部来自 `HUDModule`（`modules/client/HUDModule.java`）：`getSafeX()` :82 / `getSafeY()` :97（四边留白）、
`getBottomHudOffset()` :107（底部最大占位）、`getChatInputOffset()` :191 / `getEffectIconOffset()` :113（聊天框/药水图标占位）、
`getTextScale()` :93（全局文字缩放）、`getParallaxOffset()` :195（视差）。

## 7. 世界空间渲染

### 7.1 入口、坐标系与深度

| 需求 | 用哪个入口 | 做法 |
| --- | --- | --- |
| 穿墙方框（默认捷径） | `WorldRenderEvent.Main`（`events/WorldRenderEvent.java:50`） | `WorldBoxRenderer.fillBox/outlineBox`；管线是 `NO_DEPTH_TEST`（`ClientRenderPipelines.WORLD_FILL` :81、`WORLD_LINES` :96） |
| 受深度遮挡的方框 | `WorldRenderEvent.BeforeBufferSource` :17 | 自己的 `MeshBuffer` + `ClientRenderTypes.worldFillDepth()`/`worldLinesDepth()`（模板 `modules/render/HighlightModule.java:226-265`） |
| 线段/追踪线、世界文字 | `WorldRenderEvent.Main` | `LineMeshRenderer`；`RenderUtils.INSTANCE.drawWorldText/drawWorldRect` |

`WorldBoxRenderer` 收**世界坐标 `AABB`**，内部减相机位置（`VertexGeometry.java:19-25`），不要自己再 `translateToCamera`；它同时以 `priority = -9999`
订阅 `WorldRenderEvent.Main`（`WorldBoxRenderer.java:25-29`），事件末尾自动 `flush()` + `GuiDrawUtils.flush(true)` —— 模块里**不用**自己 flush。

### 7.2 WorldBoxRenderer（`client/render/mesh/WorldBoxRenderer.java`）

| 方法（行号） | 说明 |
| --- | --- |
| `fillBox(PoseStack, BlockPos, Color)` :31 / `(PoseStack, AABB, Color)` :35 / `(PoseStack, AABB, int)` :39 | 实心长方体 |
| `fillFadingBox(PoseStack, AABB, Color)` :46 / `outlineFadingBox(PoseStack, AABB, int, float)` :94 | 底面到顶面 alpha 渐隐的实心体 / 竖直棱淡出的描边 |
| `outlineBox(PoseStack, BlockPos, Color, float lineWidth)` :80 / `(PoseStack, AABB, Color, float)` :84 | 方框描边（12 条棱） |
| `applyRenderColor(int)` :111 / `modulateColor(int, int[])` :115 / `flush()` :119 | 乘上 `RenderUtils` 的全局颜色 / 手动提交（一般不需要） |

```java
// 模块里的标准写法（模式取自 modules/render/HighlightModule.java:207-224）
@EventHandler
private void onWorldRender(WorldRenderEvent.Main event) {
    PoseStack poseStack = event.getPoseStack();
    AABB box = new AABB(blockPos);
    WorldBoxRenderer.fillBox(poseStack, box, this.fillColorSetting.getValue());
    WorldBoxRenderer.outlineBox(poseStack, box, this.colorSetting.getValue(), 1.0F);
}
```

### 7.3 FeatureBoxRenderer（带淡出、自动过期）

实例在 `ClientContext.featureBoxRenderer`（`client/ClientContext.java:66`，由 `bootstrap/ClientInitializer.java:84` 创建）。`register(RenderConfiguration)` :44
在构造器里注册一次；`addBox(NamedFeature, BlockPos)` :36 / `(NamedFeature, AABB)` :64 每次要画就调用（`Module` 是 `NamedFeature`，直接传 `this`）。
`RenderConfiguration`（record，:116-120）参数依次为 `feature, fillColor, outlineColor, lineWidth, fadeDuration, retainPreviousBoxes, fadeEnabled,
fadeTimeScale`，都是 `ValueProvider`（可直接传 `Setting` 或 lambda `() -> 1.0F`）。`unregister` 抛 `UnsupportedOperationException`，**不支持运行时
注销**；过期框在 `LocalPlayerTickEvent` 里清理（:49-62），绘制在 `WorldRenderEvent.Main`（:96-114）自动完成，无需 flush。

```java
// 注册（modules/combat/PistonCrystalModule.java:90-92）与标记（modules/player/ScaffoldModule.java:220-225）
ClientContext.featureBoxRenderer.register(new FeatureBoxRenderer.RenderConfiguration(
        this, this.fillSetting, this.outlineSetting, () -> 1.0F, this.fadeTimeSetting,
        () -> true, this.fadeSetting, 1000));
if (this.renderSetting.getValue()) {
    ClientContext.featureBoxRenderer.addBox(this, target.position());
}
```

### 7.4 LineMeshRenderer（`client/render/mesh/LineMeshRenderer.java`）

| 方法（行号） | 说明 |
| --- | --- |
| `begin(WorldRenderEvent, float width)` :36 / `begin(PoseStack, float width)` :40 | 开始一批线段；内部 `updateProjection(poses)` :31 |
| `addVertex(double x, double y, double z, Color)` :48 / `(…, int color)` :52 | 顶点**成对**才成线段（1、2 组成一条，3、4 组成下一条…） |
| `addVertex(x, y, z, int color, boolean relativeToCamera, boolean clamp)` :56 | `relativeToCamera` 传世界坐标；`clamp = true` 把视口外端点夹到屏幕边缘 |
| `addLine(Vec3 from, Vec3 to, Color)` :98 / `addTracer(Vec3 position, Color)` :83 / `draw()` :107 | 世界坐标两点连线 / 屏幕中心到目标投影点（目标在身后时夹边）/ 提交整批 |

```java
// modules/render/TrailsModule.java:128-141 的写法
LineMeshRenderer.begin(poseStack, lineWidth);
for (int pointIndex = 1; pointIndex < points.size(); pointIndex++) {
    LineMeshRenderer.addVertex(previous.position.x, previous.position.y, previous.position.z,
            color.hashCode(), true, false);
    LineMeshRenderer.addVertex(point.position.x, point.position.y, point.position.z,
            color.hashCode(), true, false);
}
LineMeshRenderer.draw();
```

### 7.5 MeshBuffer + VertexGeometry（底层顶点批）

| 类 / 方法（行号） | 说明 |
| --- | --- |
| `MeshBuffer.create()` :44 / `create(int capacity)` :17 / `setFormat(Mode, VertexFormat)` :81 | 新建缓冲（默认 786432 字节）；实心面 `Mode.QUADS + DefaultVertexFormat.POSITION_COLOR`、线段 `Mode.LINES + POSITION_COLOR_NORMAL_LINE_WIDTH`（`GuiDrawUtils.java:44-45`） |
| `begin(Mode, VertexFormat)` :103 / `isBuilding()` :31 / `draw(RenderType)` :108 / `draw()` :133 | 开始写入 / 判断 / 提交；`draw()` 用 `MeshDrawContext` 的当前渲染类型 |
| `VertexGeometry.appendFilledBox(VertexConsumer, Matrix4f, AABB, int)` :17 / `appendBoxOutline(VertexConsumer, Pose, AABB, float, int)` :53 / `appendLine(VertexConsumer, Pose, 6 个坐标, width, color)` :79（另有 `startColor,endColor` 重载 :84） | 世界坐标实心方盒 / 方盒描边（自动减相机）/ 单条线段（自动算法线） |
| `VertexGeometry.forEachCircleSegment(float radius, int segments, CircleSegmentConsumer)` :102 | 遍历水平圆环线段（<2 段抛 `IllegalArgumentException`） |
| `MeshDrawContext.setRenderType(RenderType)` :21 / `getBuffer()` :29 / `draw(MeshData)` :16 | 决定 `MeshBuffer.draw()` 的 RenderType；`getBuffer()` 是共享实例（`modules/hud/GraphModule.java:54`） |

```java
// modules/hud/GraphModule.java:54-64 的写法
MeshBuffer buffer = MeshDrawContext.getBuffer();
buffer.begin(Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
for (int index = 1; index < samples.size(); index++) {
    VertexGeometry.appendLine(buffer, poses.last(), index - 1, previousY, 0.0F,
            index, currentY, 0.0F, 1.0F, color);
}
buffer.draw(ClientRenderTypes.worldLines());
```

### 7.6 RenderUtils 世界侧辅助（`client/render/util/RenderUtils.java`）

| 方法（行号） | 说明 |
| --- | --- |
| `translateToCamera(PoseStack, Vec3)` :96 / `applyCameraRotation(PoseStack)` :287 | 世界坐标 → 相机相对坐标并 translate / 让面片 billboard 面向相机 |
| `drawWorldText(poses, String, Vec3, double scale, Color, boolean shadow)` :154 / 带 offset 重载 :210 | 世界空间文字（内部 0.025 缩放） |
| `drawWorldRect(...)` :174 / `drawWorldTexture(...)` :159 / `addWorldLine(poses, BufferBuilder, Vec3, Vec3, float, int, int)` :234 | 世界空间实心片 / 贴图 / 世界坐标线段（写进传入的 Builder） |
| `renderEntity(Entity, partialTick, PoseStack, MultiBufferSource)` :74 / `flushEntities()` :271 / `getEntityBuffers()` :47 | 在 GUI/HUD 里渲染实体模型 / 提交实体缓冲 |
| `WORLD_PROJECTION_MATRIX` :38 / `getPartialTick()` :66 / `setPartialTick(float)` :59 / `setRenderColor(r,g,b,a)` :251 / `getRenderColor()` :276 | 世界投影矩阵 / 当前帧插值 / 全局着色乘子（HUD 淡入淡出、Shader 模块同步） |

## 8. 渲染状态与渲染目标

`client/render/shader/RenderState.java:3`：`apply(boolean entering)`（进入 true、退出 false），`then(next)` :4 串联、`combine(states...)` :11 组合。
`client/managers/render/RenderStateManager.java` 预置：`BLENDED_SMOOTH_LINES` :15、`THROUGH_WALLS` :24（关深度写 + `glDepthRange(0, 0.1)`）、
`POLYGON_OFFSET` :35、`POLYGON_OFFSET_THROUGH_WALLS` :48（前两个 `then` 组合）；入口 `runWithStates(Runnable, RenderState...)` :57、
`addStates(...)` :63、`reset()` :52。

```java
// modules/render/LogoutSpotsModule.java:433-436 的写法：提交后再叠一层穿墙
LineMeshRenderer.draw();
RenderStateManager.addStates(RenderStateManager.POLYGON_OFFSET_THROUGH_WALLS);
GuiDrawUtils.flush();
RenderStateManager.reset();
```

渲染目标与缓冲源（`client/render/shader/RenderTargetUtils.java`、`client/render/mesh/BufferSourceUtils.java`）：

| 方法（行号） | 说明 |
| --- | --- |
| `pushFramebuffer(RenderTarget)` :37 / `popFramebuffer()` :21 | 压/弹 level renderer 的 framebuffer 栈 |
| `overrideRenderTargets(RenderTarget)` :42 / `restoreRenderTargets()` :25 | **成对**：保存主目标并切到目标，恢复时弹栈 + 还原 override（不配对会画到错误目标） |
| `setMainRenderTarget(RenderTarget)` :56 | 只换主目标（`MaskShaderRenderer.captureMask` 用，:67） |
| `readShaderSource(String resourcePath)` :60 | 读 `/assets/evernight/shaders/<resourcePath>` |
| `BufferSourceUtils.cleanup(MultiBufferSource)` :9（`client/render/mesh/BufferSourceUtils.java`） | 只有实现 `BufferSourceCleanup` 的缓冲源才会被清理 |

整屏/目标辅助（`RenderUtils`）：`clearColor(RenderTarget[, Color])` :87/:92、`clearDepth(RenderTarget)` :258、
`blitToMain(RenderTarget[, RenderPipeline[, FilterMode]])` :299/:140/:144、`drawFullscreenQuad()` :307（往目标画 `-1..1` 全屏 quad）。

## 9. 着色器体系一览

不深入 GLSL，只讲"新增一个效果要动哪里"。

| 类（源码） | 作用 | 新增时要做什么 |
| --- | --- | --- |
| `managers/ShaderRegistry.java` | pass 注册表：私有 `register(id, pass)` 只在构造器调用（:64-76）；`getPass(id)` :82、`render(id, Runnable)` :98、`reloadAll()` :86、`getBufferSource()` :57；静态 `setRenderColor(int)` :110、`finishFeatureRendering()` :103 | 在构造器里加一行 `this.register("my_id", new MyPass());` |
| `client/render/shader/ShaderPass.java` / `BaseShaderPass.java` | 最小接口 `getProgram()` / `updateUniforms()` / `render(Runnable draw)`（:3-11）；基类编译 vertex/fragment 资源、`glUseProgram` 后 draw（构造 `(String vertexResource, String fragmentResource)`，`initializeProgram()` :20） | 单 pass 效果 |
| `client/render/shader/PostProcessShaderPass.java` / `PreprocessedShaderPass.java` | 后期处理基类：渲进 `POST_TARGET`、设 `u_Proj/u_ModelView/u_Size/u_Texture/u_Time`（:24-37）、`renderToTarget(draw)` :78；Preprocessed 版多一道 pre-pass（coverage/distance，:11-12） | **新增后期效果首选基类**（复杂描边用 Preprocessed） |
| `client/render/shader/ShaderPipeline.java` | 串联多个 `ShaderStage`，最后 `blitToMain`（:46-56） | 多级级联（blur/bloom/dof） |
| `client/render/shader/ClientRenderPipelines.java` | `RenderPipeline`/`Snippet` 常量：`GUI_LINES` :68、`GUI_TRIANGLES` :71、`WORLD_FILL` :81、`WORLD_LINES` :96、`WORLD_LINES_DEPTH` :104、`ENTITY_SHADER` :115、`BLIT_TRANSLUCENT`/`BLIT_NO_BLEND` :122/:146 | 新管线写成 `public static final RenderPipeline` |
| `client/render/util/ClientRenderTypes.java` | pipeline → `RenderType`：`gui()` :140、`guiTriangles()` :192、`guiLines()` :128、`worldFill()` :208、`worldLines()` :212、`font(Identifier, boolean)` :168、`image(Identifier)` :200 | 新 RenderType 加这里，模块里别自己 `RenderType.create` |
| `client/render/shader/ShaderUniforms.java` | uniform 写入：`set(name, Matrix4f)` :42、`set(name, float)` :50、`set(name, float, float)` :55、`set(name, int)` :60、`set(name, boolean)` :65、`set(name, Color)` :71、`set(name, int unit, RenderTarget)` :31 | 在你的 pass 的 `updateUniforms()` 里调用 |
| `client/render/shader/ReloadableShaderPass.java` / `MaskShaderRenderer.java` | 重载时重新编译（`ShaderRegistry.reloadAll` 遍历调用）；`MaskShaderRenderer` 是 GUI 模糊/发光入口：`renderBlur(Runnable drawMask, float width)` :51、`captureMask(...)` :57、`renderEffect(String id)` :73 | 想让 F3+T 生效就实现 Reloadable；模糊/发光直接调 `MaskShaderRenderer`，不用写 pass |

调用方式：`ClientContext.shaderRegistry.render("id", draw)`（真实用例 `MaskShaderRenderer.java:74`）；shader 资源路径由
`RenderTargetUtils.readShaderSource(resourcePath)` 拼成 `/assets/evernight/shaders/<resourcePath>`（`RenderTargetUtils.java:62`），所以
`BaseShaderPass` 的构造参数写相对该目录的路径。

## 10. 本机渲染期覆盖（可见性 / 皮肤层）

有些模块只想改「自己看到的」，不想动服务器同步状态（例：`SkinFlickerModule` 的可见性模式）。这类需求全部在渲染期判断。

| 关注点 | 入口 |
| --- | --- |
| 皮肤层是否显示（帽子/夹克/袖子/裤腿/披风） | `Avatar#isModelPartShown(PlayerModelPart)`（`net.minecraft.world.entity.Avatar`，`Player` 继承它） |
| 第三人称渲染状态 | `AvatarRenderer#extractRenderState` 把结果写进 `AvatarRenderState.showHat / showJacket / showLeftSleeve / showRightSleeve / showLeftPants / showRightPants / showCape` |
| 第一人称手部 | `ItemInHandRenderer#renderPlayerArm` / `renderMapHand` 把 `isModelPartShown(LEFT_SLEEVE/RIGHT_SLEEVE)` 当 `AvatarRenderer#renderRightHand / renderLeftHand(..., boolean sleeveVisible)` 的参数传下去 |
| 玩家本地选项 | `Minecraft#options` 的 `isModelPartEnabled` / `setModelPart`；`Options#broadcastOptions()` 会把 `ClientInformation` 发给服务器，**别人就能看到** |

写法：mixin 方法**声明所在的类**（`Avatar`），注入里用 `(Object) this == Minecraft.getInstance().player` 区分本机与远端，
返回模块缓存的「改动前」值。参考 `mixin/AvatarMixin.java` + `modules/misc/SkinFlickerModule#shouldSuppressLocalFlicker`。

Shader 模块会把同一批对象二次渲染进自己的目标，两个标志位区分主渲染与着色器 pass：

| 标志 | 何时为 true |
| --- | --- |
| `ClientContext.shaderRegistry.renderingIntoTarget` | `PostProcessShaderPass#renderToTarget` 期间（实体/描边/发光等 shader 目标渲染） |
| `ClientContext.shaderRegistry.renderingHands` | `ShaderModule` 的手部 pass 期间 |

要「皮肤照常闪、但着色器里不闪」，就在这两个标志下返回稳定值；`shaderRegistry` 由 `GameRendererMixin` 在游戏渲染器初始化时创建，读之前判空。
判定覆盖的是**所有 `PlayerModelPart`**（不只袖子），所以着色器 pass 里第三人称/freecam 的整个模型、披风、各皮肤层都稳定；
用 Iris 光影时再叠加 `IrisShaderBridge.isHandRendererActive()`（影子 pass 的手部）。

## 常见任务速查

- 在 GUI 上画实心矩形 → `GuiDrawUtils.fillRect(poseStack, left, top, right, bottom, colorArgb)`
- 提交本帧 GUI 绘制 → `GuiDrawUtils.flush()`（换投影或 `popPose` 前必须）；画文字 → `FontRenderer.INSTANCE.drawWithShadow(poseStack, text, x, y, Color)`
- 量文字宽度/行高、做居中 → `FontRenderer.INSTANCE.getWidth(text)`/`getHeight()`；`x + (width - getWidth(text)) / 2.0F`
- 取主题色/改透明度/插值/动画色 → `ColorUtils.getThemeColor()` / `withAlpha` / `blend` / `pulse` / `rainbow`
- 按血量/延迟取色 → `ColorUtils.getHealthColor(ratio)` / `getLatencyColor(ping)`
- 取缩放后的界面尺寸 → `GuiScaleUtils.getScaledWidth()` / `getScaledHeight()`
- 新增 HUD 组件 → `extends HudWidgetModule` + 覆写 `calculateDimensions`/`renderContent` + `setRenderer(new ResizableHudWidgetRenderer(this))`
- 让 HUD 组件可拖拽缩放/跟随文字缩放 → `enableResizing(this)` / `enableHudScale()`（ResizableHudWidgetRenderer）
- HUD 组件取渐变色 → `this.getHudColor(y)`（`HudWidgetModule`，随 HUD 设置变化）
- 画世界方框（穿墙） → `WorldBoxRenderer.fillBox(poseStack, aabb, color)` / `outlineBox(poseStack, aabb, color, lineWidth)`
- 画带淡出、会自动过期的方框 → `FeatureBoxRenderer`：构造器 `register(RenderConfiguration)` + 每次 `addBox(this, pos)`
- 画世界线段/追踪线 → `LineMeshRenderer.begin(poseStack, width)` + `addVertex/addLine` + `draw()`
- 在世界上画文字/小片 → `RenderUtils.INSTANCE.drawWorldText(...)` / `drawWorldRect(...)`
- 世界绘制颜色跟随全局着色 → `WorldBoxRenderer.applyRenderColor(color)` 或 `ColorUtils.modulateWithShaderColor(argb)`
- 叠一层穿墙/多边形偏移状态 → `RenderStateManager.addStates(RenderStateManager.POLYGON_OFFSET_THROUGH_WALLS)` + `reset()`
- 加一个后期着色器 → `extends PostProcessShaderPass` + `ShaderRegistry` 构造器 `register("id", new MyPass())` + `ClientContext.shaderRegistry.render("id", draw)`
- 只在本机隐藏/改变某个渲染状态 → mixin 方法声明类 + `(Object) this == Minecraft.getInstance().player` 判断（见第 10 节）
- 只在着色器 pass 里换值 → 判断 `ClientContext.shaderRegistry.renderingIntoTarget` / `renderingHands`