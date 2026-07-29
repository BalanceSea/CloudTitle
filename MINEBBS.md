# CloudTitle | 云称号

> 面向 Paper 1.21.11 的现代称号系统，提供称号仓库、称号商城、玩家自定义称号、原版药水 Buff、PlaceholderAPI 变量以及 SQLite/MySQL 跨服存储。

## 插件信息

| 项目 | 内容 |
| --- | --- |
| 插件名称 | CloudTitle |
| 中文名称 | 云称号 |
| 当前版本 | 1.0-SNAPSHOT |
| 支持服务端 | Paper 1.21.11 |
| Java 版本 | Java 21 |
| 数据存储 | SQLite / MySQL |
| 必需前置 | 无 |
| 作者 | MoutainSeaL |
| 作者 QQ | 3643203568 |

## 插件介绍

CloudTitle 是一套适用于生存服、会员服和群组服的完整称号系统。

玩家可以通过独立的称号仓库、商城与称号工坊获取和管理称号；管理员可以为每个称号配置名称、描述、图标、原版药水 Buff 以及不同的领取条件。

插件默认提供中文语言文件和经过排版的 GUI。所有 GUI 均采用类似 TrMenu 的字符布局，每个界面使用独立配置文件，无需修改源码即可调整标题、槽位、物品、Lore、点击方式与执行动作。

## 主要功能

- **称号仓库**：查看、佩戴、切换和卸下玩家已经拥有的称号。
- **称号商城**：支持金币、点券、权限、物品、PAPI 数值条件和免费领取。
- **称号工坊**：玩家可通过 GUI 和聊天输入创建自己的称号名称与描述。
- **自定义费用**：创建称号可消耗 Vault 金币或 PlayerPoints 点券。
- **费用豁免**：拥有指定权限的玩家可以免费创建自定义称号。
- **原版 Buff**：称号可附加多个药水效果，并配置等级、粒子和 HUD 图标。
- **中文 Buff 名称**：GUI 默认使用中文名称，也可以在语言文件中逐项修改。
- **默认称号**：玩家未佩戴称号时，PAPI 显示变量可回退到指定称号。
- **分段提交物品**：不要求一次携带全部物品，提交进度会保存到数据库。
- **CraftEngine 支持**：称号可要求提交指定的 CraftEngine 自定义物品。
- **PAPI 条件领取**：可判断 PlaceholderAPI 变量是否达到指定数值。
- **商城显示控制**：领取功能与商城展示可分别启用或关闭。
- **SQLite / MySQL**：单服可直接使用 SQLite，群组服可使用共享 MySQL。
- **自定义表名**：四张数据表均可修改名称。
- **跨服 Buff 处理**：记录 Buff 的负责服务器，切服时清理上一服对应效果。
- **GUI 点击冷却**：每个 GUI 可独立设置 0 至 5000 毫秒的点击间隔。
- **安全重载**：重载或卸载时关闭全部插件 GUI，并取消未完成的聊天输入。

## GUI 自定义

插件包含三个互相独立的 GUI 文件：

| 文件 | 界面 |
| --- | --- |
| `gui/warehouse.yml` | 称号仓库 |
| `gui/shop.yml` | 称号商城 |
| `gui/custom.yml` | 自定义称号工坊 |

每个 GUI 均支持：

- `Title`：MiniMessage 界面标题
- `Layout`：每行 9 个字符、最多 6 行的字符布局
- `Icons`：静态图标和动态称号槽
- `Material`、`Amount`、`Name`、`Lore`
- `Glow`、`Custom-Model-Data`、`Item-Flags`
- `all`、`left`、`right`、`shift-left`、`shift-right` 点击类型
- `Options.Click-Cooldown-Millis` 点击速度限制

内置动作：

~~~text
close
menu: previous
menu: next
menu: warehouse
menu: shop
menu: custom
title: select
title: buy
title: clear
custom: edit-name
custom: edit-description
custom: create
message: <MiniMessage>
player: <命令>
console: <命令>
sound: <音效> [音量] [音调]
~~~

## 商城获取方式

通过 `titles.yml` 中的 `shop.type` 设置：

| 类型 | 说明 | 需要的插件 |
| --- | --- | --- |
| `money` | 使用金币购买 | Vault + 经济插件 |
| `points` | 使用点券购买 | PlayerPoints |
| `permission` | 拥有指定权限后领取 | 权限插件 |
| `item` | 提交原版或 CraftEngine 物品 | CraftEngine 仅在使用其物品时需要 |
| `papi` | 判断 PAPI 变量数值 | PlaceholderAPI |
| `free` | 免费领取 | 无 |

`shop.enabled` 控制称号能否通过商城领取，`shop.display` 单独控制是否在商城 GUI 中显示。隐藏的称号仍然可以通过管理员命令发放。

`bypass-permission` 可以让指定权限跳过金币、点券、权限、物品或 PAPI 条件。

### 分段物品提交

默认的“泥土收藏家”要求累计提交 1000 个泥土：

~~~yaml
shop:
  enabled: true
  display: true
  type: item
  bypass-permission: "cloudtitle.shop.dirt_collector.bypass"
  items:
    - source: vanilla
      id: DIRT
      amount: 1000
      display: "<white>泥土</white>"
~~~

玩家每次点击对应称号时，插件会提交背包中当前可用的目标物品。进度会写入数据库，因此重启或切换服务器后仍然保留。

CraftEngine 物品示例：

~~~yaml
items:
  - source: craftengine
    id: your_namespace:your_item
    amount: 100
    display: "<aqua>自定义物品</aqua>"
~~~

CraftEngine 物品必须使用完整的 `namespace:id`。

### PAPI 数值条件

默认的“资深冒险家”要求玩家等级达到 30：

~~~yaml
shop:
  enabled: true
  display: true
  type: papi
  bypass-permission: "cloudtitle.shop.experienced_adventurer.bypass"
  papi-conditions:
    - placeholder: "%player_level%"
      operator: ">="
      value: 30
      display: "<white>玩家等级达到 30 级</white>"
~~~

支持 `>`、`>=`、`<`、`<=`、`==` 和 `!=`。配置多条条件时必须全部满足。变量没有成功展开、返回非数值内容或 PlaceholderAPI 不可用时，插件不会发放称号。

## 内置称号

| ID | 称号 | 获取方式 | Buff |
| --- | --- | --- | --- |
| `resident` | 云世界居民 | 默认显示称号，不在商城展示 | 无 |
| `newcomer` | 初来乍到 | 500 金币 | 速度 I |
| `dirt_collector` | 泥土收藏家 | 累计提交 1000 个泥土 | 幸运 I |
| `experienced_adventurer` | 资深冒险家 | 玩家等级达到 30 | 无 |
| `vip` | VIP 会员 | 权限 `group.vip` | 急迫 I |
| `pro` | PRO 会员 | 权限 `group.pro` | 速度 I、急迫 I |
| `mvp` | MVP 会员 | 权限 `group.mvp` | 速度 I、急迫 II |
| `elite` | ELITE 精英会员 | 权限 `group.elite` | 速度 II、急迫 II、抗性提升 I |

默认称号只会在玩家没有佩戴称号时用于 PAPI 显示，不会自动授予称号、写入佩戴状态或施加 Buff。

## PlaceholderAPI 变量

| 变量 | 返回内容 |
| --- | --- |
| `%cloudtitle_title%` / `%cloudtitle_name%` | 当前显示称号，Legacy 颜色格式 |
| `%cloudtitle_title_minimessage%` / `%cloudtitle_minimessage%` | 当前显示称号的 MiniMessage 原文 |
| `%cloudtitle_title_plain%` / `%cloudtitle_plain%` | 当前显示称号的纯文本 |
| `%cloudtitle_description%` | 当前显示称号的纯文本描述 |
| `%cloudtitle_selected_id%` | 玩家真实佩戴的称号 ID |
| `%cloudtitle_displayed_id%` | 应用默认称号回退后的显示 ID |
| `%cloudtitle_owned_count%` | 玩家仓库中的称号数量 |

PAPI 查询只读取玩家登录时异步加载的内存缓存，不会在聊天、TAB 或计分板刷新时同步访问数据库。

## 命令

主命令别名：`/ct`、`/title`、`/称号`

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/cloudtitle` | 打开称号仓库 | `cloudtitle.use` |
| `/cloudtitle shop` | 打开称号商城 | `cloudtitle.use` |
| `/cloudtitle custom` | 打开自定义称号工坊 | `cloudtitle.use` + 工坊权限 |
| `/cloudtitle set <id>` | 佩戴已拥有的称号 | `cloudtitle.use` |
| `/cloudtitle clear` | 卸下当前称号 | `cloudtitle.use` |
| `/cloudtitle grant <玩家> <id>` | 发放静态称号 | `cloudtitle.admin` |
| `/cloudtitle revoke <玩家> <id>` | 回收玩家称号 | `cloudtitle.admin` |
| `/cloudtitle reload` | 重载配置并关闭插件 GUI | `cloudtitle.admin` |

## 权限

| 权限 | 默认 | 说明 |
| --- | --- | --- |
| `cloudtitle.use` | 所有玩家 | 使用基础命令和 GUI |
| `cloudtitle.admin` | OP | 发放、回收称号以及重载配置 |
| `cloudtitle.custom` | 所有玩家 | 使用自定义称号工坊 |
| `cloudtitle.custom.bypass` | OP | 豁免自定义称号创建费用 |

商城称号的领取权限和豁免权限可以在 `titles.yml` 中单独配置。

## 依赖说明

| 依赖 | 类型 | 用途 |
| --- | --- | --- |
| HikariCP 6.3.0 | LibraryLoader 运行库 | MySQL 连接池 |
| SQLite JDBC 3.50.3.0 | LibraryLoader 运行库 | SQLite 驱动 |
| MySQL Connector/J 9.4.0 | LibraryLoader 运行库 | MySQL 驱动 |
| Vault | 软依赖 | 金币商城和金币创建自定义称号 |
| PlayerPoints | 软依赖 | 点券商城和点券创建自定义称号 |
| PlaceholderAPI 2.11.7+ | 软依赖 | 称号变量和 PAPI 数值条件 |
| CraftEngine | 软依赖 | CraftEngine 自定义物品兑换 |

三个数据库运行库由 Spigot/Paper LibraryLoader 在启动时从 Maven Central 下载，不会被打包进插件 JAR。首次启动时请确保服务器可以访问 Maven Central。

没有安装某个软依赖时，仅对应功能不可用，其他称号功能仍可正常运行。

## 配置文件

首次运行后会在 `plugins/CloudTitle/` 生成：

~~~text
CloudTitle/
├─ config.yml
├─ storage.yml
├─ titles.yml
├─ gui/
│  ├─ warehouse.yml
│  ├─ shop.yml
│  └─ custom.yml
└─ lang/
   └─ zh_CN.yml
~~~

| 文件 | 用途 |
| --- | --- |
| `config.yml` | 服务器 ID、默认称号、自定义称号和 Buff 设置 |
| `storage.yml` | SQLite/MySQL 连接参数与数据表名称 |
| `titles.yml` | 称号名称、描述、图标、Buff 和领取条件 |
| `gui/warehouse.yml` | 称号仓库 GUI |
| `gui/shop.yml` | 称号商城 GUI |
| `gui/custom.yml` | 自定义称号工坊 GUI |
| `lang/zh_CN.yml` | 消息、获取条件模板和药水效果中文名称 |

普通 YAML 修改后可以执行 `/cloudtitle reload`。切换存储类型、修改数据库连接、修改 `server-id` 或 Buff 定时任务周期后应完整重启服务器。

## MySQL 与跨服

单服默认使用 SQLite，无需额外配置。

群组服部署时：

1. 所有子服连接同一个 MySQL 数据库。
2. 每台子服设置唯一的 `server-id`。
3. 各子服使用相同的数据表名称。
4. 修改数据库配置后完整重启服务器。

插件会保存玩家拥有的称号、真实佩戴状态、自定义称号、物品提交进度以及自己施加的 Buff 记录。

玩家进入新服务器时，插件会尝试清理上一台服务器记录的同等级药水效果，再应用当前称号 Buff。其他插件后来施加的更高等级效果不会被 CloudTitle 删除。

四张数据表均可在 `storage.yml` 中修改名称。修改表名会创建新的空表，不会自动迁移旧表数据，请在生产环境操作前备份数据库。

## 安装方法

1. 确认服务端为 Paper 1.21.11，并使用 Java 21。
2. 将 `CloudTitle-1.0.jar` 放入服务端 `plugins/` 目录。
3. 启动服务器并等待 LibraryLoader 下载数据库运行库。
4. 根据需要安装 Vault、PlayerPoints、PlaceholderAPI 或 CraftEngine。
5. 修改自动生成的配置文件。
6. 重启服务器，或对支持重载的配置执行 `/cloudtitle reload`。

## 常见问题

### 为什么金币商城无法使用？

需要同时安装 Vault 和一个兼容 Vault 的经济插件，并确保经济服务已经成功注册。

### 为什么点券商城无法使用？

需要安装 PlayerPoints。未安装时只会禁用点券相关功能。

### 为什么 PAPI 变量没有返回内容？

请确认已经安装 PlaceholderAPI，并且玩家数据已经完成异步加载。玩家离线或数据仍在加载时，变量可能返回空字符串。

### 默认称号会自动给玩家 Buff 吗？

不会。默认称号只用于玩家未佩戴称号时的显示回退，不会授予拥有权、修改数据库佩戴状态或施加 Buff。

### 物品必须一次提交完成吗？

不需要。玩家可以多次提交，进度会保存在数据库中。

### 为什么修改表名后原有数据不见了？

新表名会创建新的空表，插件不会自动搬迁旧表数据。请恢复原表名，或在备份后手动迁移数据。

### 重载插件时 GUI 会怎样？

执行 `/cloudtitle reload` 或卸载插件时，CloudTitle 会关闭所有由本插件打开的 GUI，并取消尚未完成的自定义称号聊天输入。

## 源码构建

构建环境需要 Java 21：

~~~bash
./gradlew build
~~~

Windows：

~~~powershell
.\gradlew.bat build
~~~

构建产物：

~~~text
build/libs/CloudTitle-1.0.jar
~~~

## 作者与支持

- 作者：**MoutainSeaL**
- QQ：**3643203568**

反馈问题时建议同时提供 Paper 版本、Java 版本、CloudTitle 版本、完整报错日志、复现步骤以及相关软依赖版本。

---

**推荐标签：** Paper、称号、GUI、MySQL、SQLite、PlaceholderAPI、Vault、PlayerPoints、CraftEngine、Buff、跨服
