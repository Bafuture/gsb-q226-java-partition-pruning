package com.example.gsb.partition;

/**
 * 分区策略：负责把分区键映射到分区，并给出该分区覆盖的键区间（供裁剪使用）。
 */
public interface Partitioner {

    /** 返回分区键所属分区的标识。 */
    String partitionIdFor(long partitionKey);

    /** 该分区键所属分区覆盖的分区键下界（闭区间）。 */
    long keyMinOf(long partitionKey);

    /** 该分区键所属分区覆盖的分区键上界（闭区间）。 */
    long keyMaxOf(long partitionKey);
}
