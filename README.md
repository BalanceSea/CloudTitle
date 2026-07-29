# 云称号

适用于 Paper 1.21.11 的称号插件，支持 SQLite/MySQL、TrMenu 风格可配置 GUI、自定义语言、称号商城、自定义称号、PlaceholderAPI 与原版药水 Buff。

## 安装

1. 执行 `./gradlew build`，成品位于 `build/libs/CloudTitle-1.0.jar`。
2. 将 JAR 放入服务端 `plugins` 目录。
3. HikariCP、SQLite JDBC 与 MySQL Connector/J 由 Spigot/Paper LibraryLoader 在首次启动时从 Maven Central 加载，不会打包进插件 JAR。金币需要 Vault 与 Vault 经济插件，点券需要 PlayerPoints，变量需要 PlaceholderAPI；后三者均为软依赖。
4. 首次启动后编辑 `plugins/CloudTitle` 下的配置，使用 `/cloudtitle reload` 重载。存储类型切换需要重启服务端。

## 配置文件

- `config.yml`：服务器标识、自定义称号价格、币种、豁免权限和 Buff 刷新间隔。
- `storage.yml`：SQLite 或 MySQL 连接参数。
- `titles.yml`：称号名称、描述、图标、药水 Buff 与商城条件。
- `gui/warehouse.yml`：称号仓库界面。
- `gui/shop.yml`：称号商城界面。
- `gui/custom.yml`：称号工坊界面。
- `lang/zh_CN.yml`：玩家消息，可复制为其他语言并修改 `default-language`。

## TrMenu 风格 GUI

每个 GUI 文件均由 `Title`、`Layout`、`Icons` 组成。`Layout` 每行固定 9 个字符，共 1 至 6 行，字符会读取 `Icons` 中同名节点：

```yaml
Title: "<aqua>示例菜单"
Layout:
  - "#########"
  - "#TTTTTTT#"
  - "#########"
Icons:
  '#':
    Display:
      Material: BLACK_STAINED_GLASS_PANE
      Name: " "
  'T':
    Type: title
    Display:
      Material: "%title_material%"
      Name: "%title_name%"
      Lore: ["%title_description%"]
    Actions:
      all: ["title: select"]
```

每个 GUI 可通过 `Options.Click-Cooldown-Millis` 单独设置按钮点击冷却，范围为 `0-5000` 毫秒，`0` 表示关闭限制。插件重载或卸载时会自动关闭所有由本插件打开的 GUI，并取消未完成的自定义称号输入。

显示项支持 `Material`、`Amount`、`Name`、`Lore`、`Glow`、`Custom-Model-Data` 和 `Item-Flags`。动作支持 `all`、`left`、`right`、`shift-left`、`shift-right`。

内置动作包括：

- `menu: previous`、`menu: next`、`menu: warehouse`、`menu: shop`、`menu: custom`
- `title: select`、`title: buy`、`title: clear`
- `custom: edit-name`、`custom: edit-description`、`custom: create`
- `close`、`message: <MiniMessage>`、`player: <命令>`、`console: <命令>`、`sound: <音效> [音量] [音调]`

常用变量包括 `%player%`、`%page%`、`%max_page%`、`%owned_count%`、`%title_id%`、`%title_name%`、`%title_material%`、`%title_description%`、`%title_buffs%`、`%title_cost%`、`%title_status%`、`%custom_name%`、`%custom_description%` 与 `%custom_cost%`。

## 存储与跨服

单服默认使用 SQLite。跨服网络应让所有子服连接同一个 MySQL 数据库，并给每台子服设置唯一的 `server-id`。

四张数据表可在 `storage.yml -> tables` 中分别修改：`players`、`owned`、`custom-titles`、`item-progress`。表名必须以字母开头，只能包含字母、数字和下划线。修改表名会创建新表，不会自动迁移旧表中的数据。

插件会持久化自己施加的药水类型、等级和负责服务器。玩家进入新服时先清除上一服记录的同等级效果，再施加当前称号效果；旧服延迟到达的退出事件不会覆盖新服状态。插件不会删除其他系统后来施加的更高等级效果。

## 商城类型

`titles.yml` 中的 `shop.type` 支持：

- `money`：Vault 金币
- `points`：PlayerPoints 点券
- `permission`：拥有指定权限即可领取
- `item`：提交原版或 CraftEngine 物品，支持跨重启、跨服分段提交
- `papi`：读取 PlaceholderAPI 变量并进行数值比较
- `free`：免费领取

物品兑换示例：

```yaml
shop:
  type: item
  items:
    - source: vanilla
      id: DIRT
      amount: 1000
      display: "<white>泥土</white>"
    # CraftEngine 物品：
    # - source: craftengine
    #   id: your_namespace:your_item
    #   amount: 100
    #   display: "<aqua>自定义物品</aqua>"
```

玩家每次点击商城称号时会提交背包中当前可用的物品，进度持久化在 `item-progress` 表。原版物品按 `Material` 精确识别，并排除底层材质相同的 CraftEngine 自定义物品；CraftEngine 物品按完整 `namespace:id` 识别。

PAPI 数值条件示例：

```yaml
shop:
  type: papi
  papi-conditions:
    - placeholder: "%player_level%"
      operator: ">="
      value: 30
      display: "<white>玩家等级达到 30 级</white>"
```

支持的运算符为 `>`、`>=`、`<`、`<=`、`==`、`!=`。配置多条 `papi-conditions` 时必须全部满足。变量结果必须能解析为数值，支持整数、小数和带千分位逗号的数值。商城 GUI 会实时显示当前值、目标值和判断结果；`requirement-display` 中可使用 `%papi_conditions%`。

`shop.enabled` 控制该称号能否通过商城购买，`shop.display` 单独控制是否在商城 GUI 中显示。隐藏的称号仍可由管理员发放。`requirement-display` 可覆盖 GUI 中的获取条件文案，留空则按照商城类型自动生成中文文案。

`bypass-permission` 可让指定权限跳过费用或领取条件。Buff 名称使用 Paper 原版药水效果名称，`amplifier: 0` 表示 I 级；GUI 中文名称和显示格式可在 `lang/zh_CN.yml` 的 `potion-effects` 与 `buff-display` 中修改。

## 命令

- `/cloudtitle`：打开称号仓库
- `/cloudtitle shop`：打开称号商城
- `/cloudtitle custom`：打开称号工坊
- `/cloudtitle set <id>`、`/cloudtitle clear`：佩戴或卸下
- `/cloudtitle grant|revoke <玩家> <id>`：管理员发放或回收
- `/cloudtitle reload`：重载配置

基础权限为 `cloudtitle.use`，管理权限为 `cloudtitle.admin`，自定义权限为 `cloudtitle.custom`，默认自定义费用豁免权限为 `cloudtitle.custom.bypass`。

## PlaceholderAPI

- `%cloudtitle_title%`：当前称号，传统颜色代码格式
- `%cloudtitle_title_minimessage%`：当前称号 MiniMessage 原文
- `%cloudtitle_title_plain%`：当前称号纯文本
- `%cloudtitle_description%`：当前称号描述
- `%cloudtitle_selected_id%`：当前称号 ID
- `%cloudtitle_owned_count%`：仓库称号数量

PAPI 查询只读取登录时异步加载的缓存，不会在聊天或计分板刷新时访问数据库。

玩家未佩戴称号时，显示变量会回退到 `config.yml -> default-title.id` 指定的称号。默认示例为 `resident`（云世界居民）。该回退只影响显示，不会授予称号、写入数据库或施加 Buff。`%cloudtitle_selected_id%` 返回真实佩戴 ID，`%cloudtitle_displayed_id%` 返回最终显示 ID。
