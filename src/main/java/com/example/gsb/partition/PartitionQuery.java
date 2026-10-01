package com.example.gsb.partition;

/**
 * 查询条件：对分区键做等值或范围过滤，并可选地附带主键区间用于分区内范围查询。
 */
public final class PartitionQuery {

    enum Kind {
        EQUAL,
        RANGE
    }

    private final Kind kind;
    private final long key;
    private final KeyRange range;
    private final String fromIdInclusive;
    private final String toIdExclusive;

    private PartitionQuery(Kind kind, long key, KeyRange range,
                           String fromIdInclusive, String toIdExclusive) {
        this.kind = kind;
        this.key = key;
        this.range = range;
        this.fromIdInclusive = fromIdInclusive;
        this.toIdExclusive = toIdExclusive;
    }

    /** 分区键等值查询。 */
    public static PartitionQuery equalTo(long partitionKey) {
        return new PartitionQuery(Kind.EQUAL, partitionKey, null, null, null);
    }

    /** 分区键范围查询 {@code [minInclusive, maxExclusive)}。 */
    public static PartitionQuery between(long minInclusive, long maxExclusive) {
        return new PartitionQuery(Kind.RANGE, 0L,
                new KeyRange(minInclusive, maxExclusive), null, null);
    }

    /** 不限制分区键（全表扫描，用于对照与全量合并）。 */
    public static PartitionQuery all() {
        return between(Long.MIN_VALUE, Long.MAX_VALUE);
    }

    /** 附带主键区间过滤（用于分区内/跨分区的主键范围查询），返回新的查询对象。 */
    public PartitionQuery withIdRange(String fromIdInclusive, String toIdExclusive) {
        return new PartitionQuery(kind, key, range, fromIdInclusive, toIdExclusive);
    }

    Kind kind() {
        return kind;
    }

    long equalKey() {
        return key;
    }

    KeyRange range() {
        return range;
    }

    public String fromIdInclusive() {
        return fromIdInclusive;
    }

    public String toIdExclusive() {
        return toIdExclusive;
    }

    @Override
    public String toString() {
        String base = switch (kind) {
            case EQUAL -> "pk=" + key;
            case RANGE -> "pk" + range;
        };
        String idFilter = (fromIdInclusive == null ? "" : ", id>=" + fromIdInclusive)
                + (toIdExclusive == null ? "" : ", id<" + toIdExclusive);
        return "PartitionQuery(" + base + idFilter + ")";
    }
}
