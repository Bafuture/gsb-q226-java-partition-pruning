package com.example.gsb.partition;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * K 路归并：把多个已按主键排序的分区扫描结果合并成一个按主键有序的结果，
 * 并按主键去重（同一主键出现在多个分区时，保留 {@link Row#ts()} 最大的行，
 * 即最新版本）。
 */
final class KWayMerger {

    private KWayMerger() {
    }

    /**
     * @param sortedInputs 每个列表都必须已按 {@link Row#id()} 升序排列
     * @return 按主键升序、按主键去重后的合并结果
     */
    static List<Row> mergeByIdDedup(List<List<Row>> sortedInputs) {
        PriorityQueue<Cursor> cursors = new PriorityQueue<>(
                (left, right) -> left.current.id().compareTo(right.current.id()));
        for (List<Row> input : sortedInputs) {
            if (!input.isEmpty()) {
                cursors.offer(new Cursor(input));
            }
        }

        List<Row> merged = new ArrayList<>();
        while (!cursors.isEmpty()) {
            String smallestId = cursors.peek().current.id();
            Row winner = null;
            List<Cursor> advanceable = new ArrayList<>();
            while (!cursors.isEmpty() && cursors.peek().current.id().equals(smallestId)) {
                Cursor cursor = cursors.poll();
                if (winner == null || cursor.current.ts() > winner.ts()) {
                    winner = cursor.current;
                }
                if (cursor.advance()) {
                    advanceable.add(cursor);
                }
            }
            merged.add(winner);
            cursors.addAll(advanceable);
        }
        return merged;
    }

    private static final class Cursor {
        private final Iterator<Row> iterator;
        private Row current;

        Cursor(List<Row> rows) {
            this.iterator = rows.iterator();
            this.current = iterator.next();
        }

        boolean advance() {
            if (iterator.hasNext()) {
                current = iterator.next();
                return true;
            }
            return false;
        }
    }
}
