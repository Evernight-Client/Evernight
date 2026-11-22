# 完整示例插件

以下文件构成一个可编译的最小插件（包名 `com.example.exampleaddon`，模组 id `exampleaddon`）。与 `docs/addons.md` 的片段一致。

## 工程结构

```
example-addon/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── libs/evernight-4.0.0-dev.jar
└── src/main/
    ├── java/com/example/exampleaddon/
    │   ├── ExampleAddon.java
    │   ├── ExampleModule.java
    │   └── ExampleCommand.java
    └── resources/
        ├── fabric.mod.json
        └── assets/exampleaddon/lang/{en_us,zh_cn}.json
```

## settings.gradle

```gradle
pluginManagement {
    repositories {
        maven { url = 'https://maven.fabricmc.net/' }
        mavenCentral()
        gradlePluginPortal()
    }
}
rootProject.name = 'example-addon'
```

## build.gradle

```gradle
plugins {
    id 'net.fabricmc.fabric-loom-remap' version '1.18.4'
}

version = '1.0.0'
group = 'com.example'

base { archivesName = 'example-addon' }

repositories {
    mavenCentral()
}

dependencies {
    minecraft "com.mojang:minecraft:1.21.11"
    mappings loom.officialMojangMappings()
    modImplementation "net.fabricmc:fabric-loader:0.20.1"
    modImplementation "net.fabricmc.fabric-api:fabric-api:0.141.6+1.21.11"

    // 由 Evernight 的 gradlew build 生成
    modImplementation files("libs/evernight-4.0.0-dev.jar")
}

java {
    withSourcesJar()
}

tasks.withType(JavaCompile).configureEach {
    options.release = 21
    options.encoding = 'UTF-8'
}
```

> 若 Loom 报 `Plugin [id: 'net.fabricmc.fabric-loom-remap'] was not found`，把 Evernight 的 `settings.gradle` 中 `pluginManagement.resolutionStrategy.eachPlugin` 段复制过来。Gradle 需运行在 JDK 25 上（Loom 1.18.4 要求），`options.release = 21` 保持 Java 21 目标。

## gradle.properties

```properties
org.gradle.jvmargs=-Xmx2G
```

## fabric.mod.json

```json
{
  "schemaVersion": 1,
  "id": "exampleaddon",
  "version": "1.0.0",
  "name": "Example Addon",
  "description": "An Evernight addon example.",
  "authors": ["Your Name"],
  "environment": "client",
  "entrypoints": {
    "evernight-addon": ["com.example.exampleaddon.ExampleAddon"]
  },
  "depends": {
    "fabricloader": ">=0.19.5",
    "minecraft": "1.21.11",
    "java": ">=21",
    "fabric-api": "*",
    "evernight": ">=4.0.0"
  }
}
```

## ExampleAddon.java

```java
package com.example.exampleaddon;

import evernight.addon.AddonContext;
import evernight.addon.EvernightAddon;

public final class ExampleAddon extends EvernightAddon {
    @Override
    public void onInitialize(AddonContext context) {
        context.registerModule(new ExampleModule());
        context.registerCommand(new ExampleCommand());
    }

    @Override
    public void onShutdown() {
        // 停止插件线程/连接（本例没有）
    }
}
```

## ExampleModule.java

```java
package com.example.exampleaddon;

import evernight.addon.AddonModule;
import evernight.client.events.core.EventHandler;
import evernight.client.events.ui.MinecraftTickEvent;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.text.Translations;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;

public final class ExampleModule extends AddonModule {
    private final Setting<Boolean> announceSetting;
    private final Setting<Integer> intervalSetting;
    private int ticks;

    public ExampleModule() {
        super("exampleaddon", "ExampleAddon", "Demonstrates the addon API.",
                ModuleCategory.MISC, "exampleaddon", "exaddon");
        this.announceSetting = this.add(new BooleanSetting("Announce", false));
        this.intervalSetting = this.add(
                new NumberSetting<>("Interval", 20, 1, 200).dependsOn(this.announceSetting));
    }

    @EventHandler
    private void onTick(MinecraftTickEvent event) {
        if (!this.announceSetting.getValue()) {
            return;
        }
        if (++this.ticks < this.intervalSetting.getValue()) {
            return;
        }
        this.ticks = 0;
        ChatUtils.printChat(
                Component.literal(Translations.get("addons.exampleaddon.tick")),
                ChatUtils.moduleSignature(this));
    }
}
```

## ExampleCommand.java

```java
package com.example.exampleaddon;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import evernight.client.commands.Command;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;

public final class ExampleCommand extends Command {
    public ExampleCommand() {
        super("exampleaddon");
        this.setAliases("exaddon");
    }

    @Override
    public void exec(LiteralArgumentBuilder root) {
        com.mojang.brigadier.Command<Object> action = context -> {
            ChatUtils.printChat(Component.literal("Hello from Example Addon."), ChatUtils.DEFAULT_SIGNATURE);
            return 1;
        };
        root.executes(action);
    }
}
```

## assets/exampleaddon/lang/en_us.json

```json
{
  "addons.exampleaddon.names.modules.ExampleAddon": "Example Addon",
  "addons.exampleaddon.names.settings.Announce": "Announce",
  "addons.exampleaddon.names.settings.Interval": "Interval",
  "addons.exampleaddon.descriptions.modules.ExampleAddon": "Demonstrates the addon API.",
  "addons.exampleaddon.tick": "Example addon ticked."
}
```

## assets/exampleaddon/lang/zh_cn.json

```json
{
  "addons.exampleaddon.names.modules.ExampleAddon": "示例插件",
  "addons.exampleaddon.names.settings.Announce": "播报",
  "addons.exampleaddon.names.settings.Interval": "间隔",
  "addons.exampleaddon.descriptions.modules.ExampleAddon": "演示插件 API。",
  "addons.exampleaddon.tick": "示例插件 tick。"
}
```

## 构建

```bash
# 先在 Evernight 仓库生成 dev jar
gradlew.bat build

# 再在插件工程
gradlew.bat build
```

把 `build/libs/example-addon-1.0.0.jar` 放进 Evernight 的 `run/mods/`（或正式游戏的 `.minecraft/mods`），启动后在日志里确认 `Loaded addon Example Addon 1.0.0 (exampleaddon)`，并在游戏内执行 `;addons`。
