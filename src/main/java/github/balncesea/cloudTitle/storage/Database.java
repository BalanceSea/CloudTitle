package github.balncesea.cloudTitle.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import github.balncesea.cloudTitle.CloudTitle;
import github.balncesea.cloudTitle.model.PlayerTitleData;
import github.balncesea.cloudTitle.model.TitleDefinition;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 称号数据访问层。所有 SQL 都在专用执行器中运行，主线程只处理缓存和玩家界面。
 * 表名经过白名单校验后才会拼接进 SQL，值本身始终通过 PreparedStatement 传入。
 */
/** SQLite/MySQL JDBC 适配器，实现统一的称号存储契约。 */
public final class Database implements TitleRepository {
    private final CloudTitle plugin;
    private final HikariDataSource dataSource;
    private final ExecutorService executor;
    private final String playersTable;
    private final String ownedTable;
    private final String customTitlesTable;
    private final String itemProgressTable;
    private final String customOwnerIndex;
    private final boolean mysql;

    public Database(CloudTitle plugin, FileConfiguration config) throws SQLException {
        this.plugin = plugin;
        playersTable = tableName(config, "tables.players", "ct_players");
        ownedTable = tableName(config, "tables.owned", "ct_owned");
        customTitlesTable = tableName(config, "tables.custom-titles", "ct_custom_titles");
        itemProgressTable = tableName(config, "tables.item-progress", "ct_item_progress");
        if (new HashSet<>(List.of(playersTable, ownedTable, customTitlesTable, itemProgressTable)).size() != 4) {
            throw new IllegalArgumentException("storage.yml 中的四个表必须使用不同名称");
        }
        customOwnerIndex = generatedIndexName(customTitlesTable);
        HikariConfig hikari = new HikariConfig();
        String type = config.getString("type", "sqlite").toLowerCase(Locale.ROOT);
        mysql = type.equals("mysql");
        if (mysql) {
            String parameters = config.getString("mysql.parameters", "");
            String suffix = parameters.isBlank() ? "" : "?" + parameters;
            hikari.setJdbcUrl("jdbc:mysql://" + config.getString("mysql.host", "127.0.0.1") + ":" +
                    config.getInt("mysql.port", 3306) + "/" + config.getString("mysql.database", "minecraft") + suffix);
            hikari.setUsername(config.getString("mysql.username", "root"));
            hikari.setPassword(config.getString("mysql.password", ""));
            hikari.setMaximumPoolSize(Math.max(2, config.getInt("mysql.pool-size", 10)));
            hikari.setConnectionTimeout(config.getLong("mysql.connection-timeout-ms", 10000));
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
        } else {
            File file = new File(plugin.getDataFolder(), config.getString("sqlite.file", "data.db"));
            hikari.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            hikari.setMaximumPoolSize(1);
            hikari.setConnectionTestQuery("SELECT 1");
            hikari.setDriverClassName("org.sqlite.JDBC");
        }
        hikari.setPoolName("CloudTitle-Database");
        dataSource = new HikariDataSource(hikari);
        executor = Executors.newFixedThreadPool(type.equals("sqlite") ? 1 : Math.max(2, config.getInt("mysql.pool-size", 10) / 2), r -> {
            Thread thread = new Thread(r, "CloudTitle-Storage"); thread.setDaemon(true); return thread;
        });
        initialize();
    }

    /** 创建基础表和自定义称号所有者索引；索引使用元数据检查以兼容 SQLite/MySQL。 */
    private void initialize() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + quoted(playersTable) + " (uuid VARCHAR(36) PRIMARY KEY, selected_title VARCHAR(96), applied_buffs TEXT NOT NULL, applied_server VARCHAR(128) NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + quoted(ownedTable) + " (uuid VARCHAR(36) NOT NULL, title_id VARCHAR(96) NOT NULL, PRIMARY KEY (uuid, title_id))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + quoted(customTitlesTable) + " (id VARCHAR(96) PRIMARY KEY, owner_uuid VARCHAR(36) NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL, created_at BIGINT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + quoted(itemProgressTable) + " (uuid VARCHAR(36) NOT NULL, title_id VARCHAR(96) NOT NULL, item_key VARCHAR(255) NOT NULL, submitted_amount INT NOT NULL, PRIMARY KEY (uuid, title_id, item_key))");
        }
        createIndexIfMissing(customTitlesTable, customOwnerIndex, "owner_uuid");
    }

    private void createIndexIfMissing(String table, String index, String column) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (indexExists(connection, table, index)) return;
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE INDEX " + quoted(index) + " ON " + quoted(table) + "(" + quoted(column) + ")");
            } catch (SQLException exception) {
                // Multiple backend servers may initialize the shared MySQL database simultaneously.
                if (!indexExists(connection, table, index)) throw exception;
            }
        }
    }

    private boolean indexExists(Connection connection, String table, String index) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        LinkedHashSet<String> tableNames = new LinkedHashSet<>(List.of(
                table,
                table.toUpperCase(Locale.ROOT),
                table.toLowerCase(Locale.ROOT)
        ));
        for (String tableName : tableNames) {
            try (ResultSet result = metadata.getIndexInfo(
                    connection.getCatalog(),
                    null,
                    tableName,
                    false,
                    false
            )) {
                while (result.next()) {
                    if (index.equalsIgnoreCase(result.getString("INDEX_NAME"))) return true;
                }
            }
        }
        return false;
    }

    private static String tableName(FileConfiguration config, String path, String defaultName) {
        String value = config.getString(path, defaultName).trim();
        if (!value.matches("[A-Za-z][A-Za-z0-9_]{0,47}")) {
            throw new IllegalArgumentException(path + " 必须以字母开头，只能包含字母、数字、下划线，且最长 48 个字符");
        }
        return value;
    }

    private static String generatedIndexName(String table) {
        String value = "idx_" + table + "_owner";
        return value.length() <= 64 ? value : "idx_" + Integer.toUnsignedString(table.hashCode(), 36) + "_owner";
    }

    private static String quoted(String identifier) {
        return "`" + identifier + "`";
    }

    public CompletableFuture<PlayerTitleData> load(UUID uuid) { return supply(() -> {
        ensurePlayer(uuid);
        Set<String> owned = new HashSet<>();
        Map<String, TitleDefinition> custom = new HashMap<>();
        Map<String, Map<String, Integer>> itemProgress = new HashMap<>();
        String selected = null, applied = "", appliedServer = "";
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement p = c.prepareStatement("SELECT selected_title, applied_buffs, applied_server FROM " + quoted(playersTable) + " WHERE uuid=?")) {
                p.setString(1, uuid.toString()); try (ResultSet r = p.executeQuery()) { if (r.next()) { selected = r.getString(1); applied = r.getString(2); appliedServer = r.getString(3); } }
            }
            try (PreparedStatement p = c.prepareStatement("SELECT title_id FROM " + quoted(ownedTable) + " WHERE uuid=?")) {
                p.setString(1, uuid.toString()); try (ResultSet r = p.executeQuery()) { while (r.next()) owned.add(r.getString(1)); }
            }
            try (PreparedStatement p = c.prepareStatement("SELECT id,name,description FROM " + quoted(customTitlesTable) + " WHERE owner_uuid=?")) {
                p.setString(1, uuid.toString()); try (ResultSet r = p.executeQuery()) { while (r.next()) {
                    String id = r.getString(1);
                    custom.put(id, new TitleDefinition(id, r.getString(2), List.of(r.getString(3)), Material.NAME_TAG,
                            List.of(), TitleDefinition.Attributes.empty(), TitleDefinition.Shop.hidden(), true));
                }}
            }
            try (PreparedStatement p = c.prepareStatement("SELECT title_id,item_key,submitted_amount FROM " + quoted(itemProgressTable) + " WHERE uuid=?")) {
                p.setString(1, uuid.toString());
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        itemProgress.computeIfAbsent(r.getString(1), ignored -> new HashMap<>())
                                .put(r.getString(2), r.getInt(3));
                    }
                }
            }
        }
        return new PlayerTitleData(uuid, owned, selected, custom, itemProgress, applied, appliedServer);
    }); }

    public CompletableFuture<Void> grant(UUID uuid, String titleId) { return run(() -> {
        ensurePlayer(uuid);
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                insertOwned(c, uuid, titleId);
                deleteItemProgress(c, uuid, titleId);
                c.commit();
            } catch (SQLException exception) {
                c.rollback();
                throw exception;
            }
        }
    }); }

    public CompletableFuture<Void> revoke(UUID uuid, String titleId) { return run(() -> {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement("DELETE FROM " + quoted(ownedTable) + " WHERE uuid=? AND title_id=?")) { p.setString(1, uuid.toString()); p.setString(2, titleId); p.executeUpdate(); }
            try (PreparedStatement p = c.prepareStatement("DELETE FROM " + quoted(customTitlesTable) + " WHERE id=? AND owner_uuid=?")) { p.setString(1, titleId); p.setString(2, uuid.toString()); p.executeUpdate(); }
            deleteItemProgress(c, uuid, titleId);
            try (PreparedStatement p = c.prepareStatement("UPDATE " + quoted(playersTable) + " SET selected_title=NULL WHERE uuid=? AND selected_title=?")) { p.setString(1, uuid.toString()); p.setString(2, titleId); p.executeUpdate(); }
            c.commit();
        }
    }); }

    public CompletableFuture<Void> setSelected(UUID uuid, String titleId) { return run(() -> {
        ensurePlayer(uuid);
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement("UPDATE " + quoted(playersTable) + " SET selected_title=? WHERE uuid=?")) {
            if (titleId == null) p.setNull(1, Types.VARCHAR); else p.setString(1, titleId);
            p.setString(2, uuid.toString()); p.executeUpdate();
        }
    }); }

    public CompletableFuture<Void> setAppliedBuffs(UUID uuid, String value, String serverId, boolean requireOwnership) { return run(() -> {
        ensurePlayer(uuid);
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement("UPDATE " + quoted(playersTable) + " SET applied_buffs=?, applied_server=? WHERE uuid=?" + (requireOwnership ? " AND applied_server=?" : ""))) {
            p.setString(1, value == null ? "" : value); p.setString(2, serverId); p.setString(3, uuid.toString()); if (requireOwnership) p.setString(4, serverId); p.executeUpdate();
        }
    }); }

    public CompletableFuture<Void> createCustom(UUID uuid, TitleDefinition title) { return run(() -> {
        ensurePlayer(uuid);
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement p = c.prepareStatement("INSERT INTO " + quoted(customTitlesTable) + "(id,owner_uuid,name,description,created_at) VALUES(?,?,?,?,?)")) {
                    p.setString(1, title.id()); p.setString(2, uuid.toString()); p.setString(3, title.name());
                    p.setString(4, title.description().isEmpty() ? "" : title.description().get(0)); p.setLong(5, System.currentTimeMillis()); p.executeUpdate();
                }
                insertOwned(c, uuid, title.id()); c.commit();
            } catch (SQLException ex) { c.rollback(); throw ex; }
        }
    }); }

    @Override
    public CompletableFuture<TitleRepository.ItemSubmissionResult> submitItems(
            UUID uuid,
            String titleId,
            List<TitleDefinition.ItemRequirement> requirements,
            Map<String, Integer> offered) {
        return supply(() -> {
            ensurePlayer(uuid);
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    Map<String, Integer> progress = loadItemProgress(connection, uuid, titleId, true);
                    Map<String, Integer> accepted = new HashMap<>();
                    for (TitleDefinition.ItemRequirement requirement : requirements) {
                        int current = Math.max(0, progress.getOrDefault(requirement.key(), 0));
                        int submitted = Math.max(0, offered.getOrDefault(requirement.key(), 0));
                        int acceptedAmount = Math.min(submitted, Math.max(0, requirement.amount() - current));
                        if (acceptedAmount <= 0) continue;
                        int updated = current + acceptedAmount;
                        saveItemProgress(connection, uuid, titleId, requirement.key(), current > 0, updated);
                        progress.put(requirement.key(), updated);
                        accepted.put(requirement.key(), acceptedAmount);
                    }

                    boolean completed = !requirements.isEmpty() && requirements.stream()
                            .allMatch(requirement -> progress.getOrDefault(requirement.key(), 0) >= requirement.amount());
                    if (completed) {
                        insertOwned(connection, uuid, titleId);
                        deleteItemProgress(connection, uuid, titleId);
                    }
                    connection.commit();
                    return new TitleRepository.ItemSubmissionResult(
                            Map.copyOf(progress), Map.copyOf(accepted), completed);
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                }
            }
        });
    }

    private Map<String, Integer> loadItemProgress(
            Connection connection,
            UUID uuid,
            String titleId,
            boolean lock) throws SQLException {
        Map<String, Integer> progress = new HashMap<>();
        String sql = "SELECT item_key,submitted_amount FROM " + quoted(itemProgressTable)
                + " WHERE uuid=? AND title_id=?" + (mysql && lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, titleId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) progress.put(result.getString(1), result.getInt(2));
            }
        }
        return progress;
    }

    private void saveItemProgress(
            Connection connection,
            UUID uuid,
            String titleId,
            String itemKey,
            boolean exists,
            int amount) throws SQLException {
        String sql = exists
                ? "UPDATE " + quoted(itemProgressTable) + " SET submitted_amount=? WHERE uuid=? AND title_id=? AND item_key=?"
                : "INSERT INTO " + quoted(itemProgressTable) + "(submitted_amount,uuid,title_id,item_key) VALUES(?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, amount);
            statement.setString(2, uuid.toString());
            statement.setString(3, titleId);
            statement.setString(4, itemKey);
            statement.executeUpdate();
        }
    }

    private void deleteItemProgress(Connection connection, UUID uuid, String titleId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + quoted(itemProgressTable) + " WHERE uuid=? AND title_id=?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, titleId);
            statement.executeUpdate();
        }
    }

    private void ensurePlayer(UUID uuid) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement p = c.prepareStatement("SELECT uuid FROM " + quoted(playersTable) + " WHERE uuid=?")) {
                p.setString(1, uuid.toString()); try (ResultSet r = p.executeQuery()) { if (r.next()) return; }
            }
            try (PreparedStatement p = c.prepareStatement("INSERT INTO " + quoted(playersTable) + "(uuid,selected_title,applied_buffs,applied_server) VALUES(?,NULL,'','')")) {
                p.setString(1, uuid.toString()); try { p.executeUpdate(); } catch (SQLException duplicate) {
                    try (PreparedStatement check = c.prepareStatement("SELECT uuid FROM " + quoted(playersTable) + " WHERE uuid=?")) { check.setString(1, uuid.toString()); try (ResultSet r = check.executeQuery()) { if (!r.next()) throw duplicate; } }
                }
            }
        }
    }

    private void insertOwned(Connection c, UUID uuid, String titleId) throws SQLException {
        try (PreparedStatement check = c.prepareStatement("SELECT title_id FROM " + quoted(ownedTable) + " WHERE uuid=? AND title_id=?")) {
            check.setString(1, uuid.toString()); check.setString(2, titleId); try (ResultSet r = check.executeQuery()) { if (r.next()) return; }
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO " + quoted(ownedTable) + "(uuid,title_id) VALUES(?,?)")) { p.setString(1, uuid.toString()); p.setString(2, titleId); p.executeUpdate(); }
    }

    private CompletableFuture<Void> run(SqlRunnable task) { return CompletableFuture.runAsync(() -> { try { task.run(); } catch (SQLException e) { throw new CompletionException(e); } }, executor); }
    private <T> CompletableFuture<T> supply(SqlSupplier<T> task) { return CompletableFuture.supplyAsync(() -> { try { return task.get(); } catch (SQLException e) { throw new CompletionException(e); } }, executor); }
    @Override public void close() { executor.shutdown(); try { if (!executor.awaitTermination(5, TimeUnit.SECONDS)) plugin.getLogger().warning("等待数据库任务结束超时"); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } dataSource.close(); }
    @FunctionalInterface private interface SqlRunnable { void run() throws SQLException; }
    @FunctionalInterface private interface SqlSupplier<T> { T get() throws SQLException; }
}
