package com.example.gsb.partition;

/**
 * 查询条件。分区键支持点查或范围（用于分区裁剪），索引键支持点查或闭区间范围；
 * 不设置的维度表示不加限制。
 */
public final class Query {

    private final Long partitionKeyPoint;
    private final Long partitionKeyFrom;
    private final Long partitionKeyTo;
    private final Long sortKeyFrom;
    private final Long sortKeyTo;

    private Query(Builder builder) {
        this.partitionKeyPoint = builder.partitionKeyPoint;
        this.partitionKeyFrom = builder.partitionKeyFrom;
        this.partitionKeyTo = builder.partitionKeyTo;
        this.sortKeyFrom = builder.sortKeyFrom;
        this.sortKeyTo = builder.sortKeyTo;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Query all() {
        return builder().build();
    }

    Long partitionKeyPoint() {
        return partitionKeyPoint;
    }

    Long partitionKeyFrom() {
        return partitionKeyFrom;
    }

    Long partitionKeyTo() {
        return partitionKeyTo;
    }

    long sortKeyFromOrMin() {
        return sortKeyFrom == null ? Long.MIN_VALUE : sortKeyFrom;
    }

    long sortKeyToOrMax() {
        return sortKeyTo == null ? Long.MAX_VALUE : sortKeyTo;
    }

    public static final class Builder {
        private Long partitionKeyPoint;
        private Long partitionKeyFrom;
        private Long partitionKeyTo;
        private Long sortKeyFrom;
        private Long sortKeyTo;

        /** 分区键点查：只会命中一个分区（范围、哈希策略均可裁剪）。 */
        public Builder partitionKeyPoint(long value) {
            this.partitionKeyPoint = value;
            return this;
        }

        /** 分区键闭区间范围：仅覆盖该范围的分区会被扫描。 */
        public Builder partitionKeyRange(long from, long to) {
            if (from > to) {
                throw new IllegalArgumentException("from > to: " + from + " > " + to);
            }
            this.partitionKeyFrom = from;
            this.partitionKeyTo = to;
            return this;
        }

        /** 索引键点查。 */
        public Builder sortKeyPoint(long value) {
            return sortKeyRange(value, value);
        }

        /** 索引键闭区间范围查询。 */
        public Builder sortKeyRange(long from, long to) {
            if (from > to) {
                throw new IllegalArgumentException("from > to: " + from + " > " + to);
            }
            this.sortKeyFrom = from;
            this.sortKeyTo = to;
            return this;
        }

        public Query build() {
            return new Query(this);
        }
    }
}
