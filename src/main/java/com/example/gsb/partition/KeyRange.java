package com.example.gsb.partition;

/**
 * 左闭右开的长整型键范围 {@code [minInclusive, maxExclusive)}。
 */
public record KeyRange(long minInclusive, long maxExclusive) {

    public KeyRange {
        if (minInclusive > maxExclusive) {
            throw new IllegalArgumentException(
                    "minInclusive %d > maxExclusive %d".formatted(minInclusive, maxExclusive));
        }
    }

    public static KeyRange all() {
        return new KeyRange(Long.MIN_VALUE, Long.MAX_VALUE);
    }

    public boolean contains(long value) {
        return value >= minInclusive && value < maxExclusive;
    }

    /** 两个左闭右开区间是否相交（相切但不重叠不算）。 */
    public boolean overlaps(KeyRange other) {
        return this.minInclusive < other.maxExclusive && other.minInclusive < this.maxExclusive;
    }

    @Override
    public String toString() {
        return "[" + minInclusive + ", " + maxExclusive + ")";
    }
}
