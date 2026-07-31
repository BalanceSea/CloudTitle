# 云称号 CloudTitle 2.0

一款基于 Spigot API 1.20.1、面向 Spigot 1.20+ 的现代称号插件，提供称号仓库、称号商城、玩家自定义称号、原版药水增益、AttributePlus/SX-Attribute 属性和跨服数据同步。插件支持 SQLite 与 MySQL，GUI 和语言文件均可自由配置，并提供 PlaceholderAPI 变量用于聊天、TAB、计分板等展示场景。

## 功能亮点

- 称号仓库：集中展示玩家已拥有的称号，支持佩戴、切换与卸下。
- 称号商城：支持 Vault 金币、PlayerPoints 点券、权限、物品、PAPI 数值条件和免费领取。
- 自定义称号：玩家可在 GUI 中编辑名称与描述，支持金币或点券定价及权限豁免。
- TrMenu 风格 GUI：仓库、商城、工坊分别使用独立配置文件，支持字符布局、图标、Lore、点击动作和点击冷却。
- 称号 Buff：每个称号可配置多个原版药水效果，GUI 默认使用中文效果名称。
- 第三方属性：每个称号可独立配置 AttributePlus 与 SX-Attribute Lore 属性词条，切换称号不会叠加旧来源。
- 物品兑换：支持原版物品和 CraftEngine 自定义物品，可分段提交并持久化进度。
- PAPI 条件领取：可以根据 PlaceholderAPI 变量的数值进行比较，多项条件需全部满足。
- 默认称号：玩家未佩戴称号时，可为显示变量配置一个回退称号。
- 跨服支持：MySQL 可在多个子服间共享拥有状态、当前称号和物品提交进度，并处理跨服 Buff 清理。
- 自定义数据表：数据库表名可以分别配置，方便接入现有数据库规范。
- 完整中文配置：消息、GUI、获取条件和 Buff 显示均可自定义。

## 运行环境

| 项目 | 要求 |
| --- | --- |
| 服务端 | Spigot 1.20+，兼容同版本 Paper |
| 插件字节码 | Java 17 |
| 存储 | SQLite 或 MySQL |
| 必需前置 | 无 |

## 可选依赖

| 插件 | 用途 |
| --- | --- |
| PlaceholderAPI | 提供称号显示变量以及 PAPI 数值领取条件 |
| Vault + 经济插件 | 金币购买称号及金币创建自定义称号 |
| PlayerPoints | 点券购买称号及点券创建自定义称号 |
| CraftEngine | 使用 CraftEngine 自定义物品兑换称号 |
| AttributePlus | 为佩戴中的称号应用 AP 属性词条 |
| SX-Attribute | 为佩戴中的称号应用 SX 属性词条，兼容新旧来源 API |

MiniMessage、HikariCP、SQLite JDBC 和 MySQL Connector/J 通过 Spigot LibraryLoader 在运行时加载，不会打包进插件 JAR。首次启动时请确保服务端能够访问 Maven Central。

## 安装方法

1. 将 `CloudTitle-2.0.jar` 放入服务端的 `plugins` 目录。
2. 启动服务端，等待插件生成默认配置文件。
3. 根据需要修改 `plugins/CloudTitle` 下的配置。
4. 普通配置可使用 `/cloudtitle reload` 重载；切换 SQLite/MySQL 或修改数据库连接参数后建议完整重启服务端。

## 配置文件

| 文件 | 用途 |
| --- | --- |
| `config.yml` | 服务器标识、默认称号、自定义称号、属性联动及 Buff 设置 |
| `storage.yml` | SQLite/MySQL 连接和数据表名称 |
| `titles.yml` | 称号、描述、图标、Buff、AP/SX 属性和获取条件 |
| `gui/warehouse.yml` | 称号仓库 GUI |
| `gui/shop.yml` | 称号商城 GUI |
| `gui/custom.yml` | 自定义称号工坊 GUI |
| `lang/zh_CN.yml` | 消息前缀、提示文本、获取条件和药水效果名称 |

每个 GUI 都可以独立设置 `Title`、`Layout`、`Icons` 和 `Options.Click-Cooldown-Millis`。插件重载或卸载时会主动关闭本插件打开的全部 GUI，并取消尚未完成的聊天输入。

## AP / SX 属性配置

```yaml
attributes:
  attribute-plus:
    - "物理伤害:5"
  sx-attribute:
    - "攻击力: 5"
```

AP/SX 为服务端软依赖，不会被打包进 CloudTitle，也不会由 LibraryLoader 下载。只安装其中一个时仅应用对应列表；两者同时安装时会分别创建独立来源。卸下称号、退出、回收、重载和停服都会清理 CloudTitle 创建的来源。

## 商城获取方式

在 `titles.yml` 中通过 `shop.type` 设置获取方式：

| 类型 | 说明 |
| --- | --- |
| `money` | 使用 Vault 金币购买 |
| `points` | 使用 PlayerPoints 点券购买 |
| `permission` | 拥有指定权限后领取 |
| `item` | 提交原版或 CraftEngine 物品 |
| `papi` | 判断 PlaceholderAPI 变量数值 |
| `free` | 免费领取 |

`shop.enabled` 控制称号是否能够通过商城领取，`shop.display` 控制是否显示在商城 GUI 中。`bypass-permission` 可以让指定权限跳过费用或领取条件。

### PAPI 数值条件示例

```yaml
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
```

支持 `>`、`>=`、`<`、`<=`、`==` 和 `!=`。配置多条条件时必须全部满足；变量未展开或结果不是数值时不会发放称号。商城 GUI 会实时显示当前值和判断结果。

### 分段物品提交示例

```yaml
shop:
  enabled: true
  display: true
  type: item
  items:
    - source: vanilla
      id: DIRT
      amount: 1000
      display: "<white>泥土</white>"
```

玩家每次点击称号都会提交背包中当前可用的目标物品，不要求一次携带全部数量。提交进度保存在数据库中，重启和跨服后仍会保留。CraftEngine 物品将 `source` 改为 `craftengine`，并使用完整的 `namespace:id`。

## 内置示例称号

- 默认称号：`resident`
- 金币称号：`newcomer`
- 物品兑换称号：`dirt_collector`，累计提交 1000 个泥土
- PAPI 条件称号：`experienced_adventurer`，玩家等级达到 30
- 会员称号：`vip`、`pro`、`mvp`、`elite`

会员称号对应权限为 `group.vip`、`group.pro`、`group.mvp` 和 `group.elite`。

## 命令

| 命令 | 说明 |
| --- | --- |
| `/cloudtitle` | 打开称号仓库 |
| `/cloudtitle shop` | 打开称号商城 |
| `/cloudtitle custom` | 打开自定义称号工坊 |
| `/cloudtitle set <id>` | 佩戴已拥有的称号 |
| `/cloudtitle clear` | 卸下当前称号 |
| `/cloudtitle grant <玩家> <id>` | 管理员发放称号 |
| `/cloudtitle revoke <玩家> <id>` | 管理员回收称号 |
| `/cloudtitle reload` | 重载插件配置 |

命令别名：`/ct`、`/title`、`/称号`。

## 权限

| 权限 | 默认 | 说明 |
| --- | --- | --- |
| `cloudtitle.use` | 所有玩家 | 使用基础命令和 GUI |
| `cloudtitle.admin` | OP | 发放、回收及重载配置 |
| `cloudtitle.custom` | 所有玩家 | 使用自定义称号功能 |
| `cloudtitle.custom.bypass` | OP | 豁免自定义称号创建费用 |

称号自身的权限要求和豁免权限可在 `titles.yml` 中逐个配置。

## PlaceholderAPI 变量

| 变量 | 返回内容 |
| --- | --- |
| `%cloudtitle_title%` | 当前显示称号，传统颜色代码格式 |
| `%cloudtitle_title_minimessage%` | 当前显示称号的 MiniMessage 原文 |
| `%cloudtitle_title_plain%` | 当前显示称号纯文本 |
| `%cloudtitle_description%` | 当前显示称号描述 |
| `%cloudtitle_selected_id%` | 玩家真实佩戴的称号 ID |
| `%cloudtitle_displayed_id%` | 应用默认称号回退后的显示 ID |
| `%cloudtitle_owned_count%` | 玩家仓库中的称号数量 |

PAPI 查询只读取玩家登录时异步加载的缓存，不会在聊天、TAB 或计分板刷新时同步访问数据库。

## 跨服部署

跨服网络中的所有子服应连接同一个 MySQL 数据库，并在 `config.yml` 中为每台子服配置唯一的 `server-id`。插件会记录自己施加的 Buff 及负责服务器，在玩家切换服务器时清理上一服对应效果，再应用当前称号效果。

请勿让多台子服使用相同的 `server-id`。修改数据表名称会创建新表，不会自动迁移旧表数据，生产环境修改前请先备份数据库。

## 构建

源码构建需要 Java 17 或更高版本：

```bash
./gradlew build
```

Windows：

```powershell
.\gradlew.bat build
```

构建产物位于 `build/libs/CloudTitle-2.0.jar`。

插件本身以 Java 17 编译；Minecraft 1.20.5 及更高版本的服务端仍需按照 Mojang/Spigot 要求使用 Java 21。
