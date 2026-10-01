package com.example.gsb.partition;

import java.util.Objects;

/**
 * 表中的一行数据。
 *
 * <p>{@code id} 是主键（用于分区内点查、范围查询与跨分区去重）；{@code ts} 是事件时间
 * （epoch day，长整型，作为日期分区键）；{@code payload} 为业务内容。
 */
public record Row(String id, long ts, String payload) {

    public Row {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
    }
}
