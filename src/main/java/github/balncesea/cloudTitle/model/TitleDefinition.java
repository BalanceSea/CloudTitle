package github.balncesea.cloudTitle.model;

import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;
import java.util.List;
import java.util.Locale;
import java.math.BigDecimal;
import java.util.Objects;

/** 不可变的称号定义，配置称号和玩家自定义称号共用此模型。 */
public record TitleDefinition(
        String id, String name, List<String> description, Material icon,
        List<Buff> buffs, Attributes attributes, Shop shop, boolean custom) {
    public TitleDefinition {
        id = Objects.requireNonNull(id, "id").trim();
        name = name == null ? id : name;
        description = description == null ? List.of() : List.copyOf(description);
        icon = icon == null ? Material.NAME_TAG : icon;
        buffs = buffs == null ? List.of() : List.copyOf(buffs);
        attributes = attributes == null ? Attributes.empty() : attributes;
        shop = shop == null ? Shop.hidden() : shop;
    }

    /** 称号使用的原版药水效果及显示选项。 */
    public record Buff(PotionEffectType type, int amplifier, boolean particles, boolean icon) {
        public Buff {
            type = Objects.requireNonNull(type, "type");
            amplifier = Math.max(0, amplifier);
        }
    }

    public record Attributes(List<String> attributePlus, List<String> sxAttribute) {
        public Attributes {
            attributePlus = attributePlus == null ? List.of() : List.copyOf(attributePlus);
            sxAttribute = sxAttribute == null ? List.of() : List.copyOf(sxAttribute);
        }

        public static Attributes empty() {
            return new Attributes(List.of(), List.of());
        }

        public boolean isEmpty() {
            return attributePlus.isEmpty() && sxAttribute.isEmpty();
        }
    }
    public record Shop(
            boolean enabled,
            boolean displayed,
            CostType type,
            double price,
            String permission,
            String bypassPermission,
            String requirementDisplay,
            List<ItemRequirement> items,
            List<PapiCondition> papiConditions) {
        public Shop {
            type = type == null ? CostType.FREE : type;
            permission = permission == null ? "" : permission;
            bypassPermission = bypassPermission == null ? "" : bypassPermission;
            requirementDisplay = requirementDisplay == null ? "" : requirementDisplay;
            items = items == null ? List.of() : List.copyOf(items);
            papiConditions = papiConditions == null ? List.of() : List.copyOf(papiConditions);
        }

        public static Shop hidden() {
            return new Shop(false, false, CostType.FREE, 0, "", "", "", List.of(), List.of());
        }
    }

    public record PapiCondition(
            String placeholder,
            NumericOperator operator,
            BigDecimal expected,
            String display) {
        public PapiCondition {
            placeholder = placeholder == null ? "" : placeholder;
            operator = operator == null ? NumericOperator.EQUAL : operator;
            expected = expected == null ? BigDecimal.ZERO : expected;
            display = display == null || display.isBlank() ? placeholder : display;
        }
    }
    public enum NumericOperator {
        GREATER_THAN(">"),
        GREATER_OR_EQUAL(">="),
        LESS_THAN("<"),
        LESS_OR_EQUAL("<="),
        EQUAL("=="),
        NOT_EQUAL("!=");

        private final String symbol;

        NumericOperator(String symbol) { this.symbol = symbol; }
        public String symbol() { return symbol; }

        public boolean test(BigDecimal actual, BigDecimal expected) {
            int comparison = actual.compareTo(expected);
            return switch (this) {
                case GREATER_THAN -> comparison > 0;
                case GREATER_OR_EQUAL -> comparison >= 0;
                case LESS_THAN -> comparison < 0;
                case LESS_OR_EQUAL -> comparison <= 0;
                case EQUAL -> comparison == 0;
                case NOT_EQUAL -> comparison != 0;
            };
        }

        public static NumericOperator parse(String value) {
            return switch (value == null ? "" : value.trim()) {
                case ">" -> GREATER_THAN;
                case ">=", "≥" -> GREATER_OR_EQUAL;
                case "<" -> LESS_THAN;
                case "<=", "≤" -> LESS_OR_EQUAL;
                case "==", "=" -> EQUAL;
                case "!=", "≠" -> NOT_EQUAL;
                default -> null;
            };
        }
    }
    public record ItemRequirement(ItemSource source, String id, int amount, String display) {
        public String key() {
            return source.name().toLowerCase(Locale.ROOT) + ":" + id.toLowerCase(Locale.ROOT);
        }
    }
    public enum ItemSource {
        VANILLA, CRAFTENGINE;

        public static ItemSource parse(String value) {
            if (value == null) return VANILLA;
            return switch (value.toLowerCase()) {
                case "craftengine", "ce" -> CRAFTENGINE;
                default -> VANILLA;
            };
        }
    }
    public enum CostType { MONEY, POINTS, PERMISSION, ITEM, PAPI, FREE;
        public static CostType parse(String value) {
            try { return valueOf(value.toUpperCase()); } catch (Exception ignored) { return FREE; }
        }
    }
}
