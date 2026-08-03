package github.balncesea.cloudTitle.storage;

import github.balncesea.cloudTitle.model.PlayerTitleData;
import github.balncesea.cloudTitle.model.TitleDefinition;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 称号持久化契约。
 *
 * <p>业务层只依赖这个接口，不需要知道当前使用的是 SQLite 还是 MySQL。
 * 具体 JDBC、连接池和表名处理由 {@link Database} 负责。</p>
 */
public interface TitleRepository extends AutoCloseable {
    CompletableFuture<PlayerTitleData> load(UUID uuid);

    CompletableFuture<Void> grant(UUID uuid, String titleId);

    CompletableFuture<Void> revoke(UUID uuid, String titleId);

    CompletableFuture<Void> setSelected(UUID uuid, String titleId);

    CompletableFuture<Void> setAppliedBuffs(UUID uuid, String value, String serverId, boolean requireOwnership);

    CompletableFuture<Void> createCustom(UUID uuid, TitleDefinition title);

    CompletableFuture<ItemSubmissionResult> submitItems(
            UUID uuid,
            String titleId,
            List<TitleDefinition.ItemRequirement> requirements,
            Map<String, Integer> offered);

    /** 分段提交物品的原子处理结果。 */
    record ItemSubmissionResult(
            Map<String, Integer> progress,
            Map<String, Integer> accepted,
            boolean completed) {
    }

    @Override
    void close();
}
