package com.himacasino.server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;

/** サーバー tick で動く簡易スケジューラ (Bukkit の runTaskLater / runTaskTimer 相当)。 */
public final class Scheduler {

    public interface Handle {
        void cancel();
    }

    private static final class Task implements Handle {
        int delay;
        final int period;
        final Runnable once;
        final BooleanSupplier repeating;
        boolean cancelled;

        Task(int delay, int period, Runnable once, BooleanSupplier repeating) {
            this.delay = delay;
            this.period = period;
            this.once = once;
            this.repeating = repeating;
        }

        @Override public void cancel() { cancelled = true; }
    }

    private final List<Task> tasks = new ArrayList<>();
    private final List<Task> incoming = new ArrayList<>();

    /** ticks 後に 1 回実行 */
    public Handle later(int ticks, Runnable task) {
        Task t = new Task(Math.max(0, ticks), 0, task, null);
        incoming.add(t);
        return t;
    }

    /** period tick ごとに実行。false を返すと停止。 */
    public Handle repeat(int delay, int period, BooleanSupplier task) {
        Task t = new Task(Math.max(0, delay), Math.max(1, period), null, task);
        incoming.add(t);
        return t;
    }

    /** サーバーの END_SERVER_TICK から毎 tick 呼ぶ */
    public void tick() {
        if (!incoming.isEmpty()) {
            tasks.addAll(incoming);
            incoming.clear();
        }
        for (Iterator<Task> it = tasks.iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (t.cancelled) { it.remove(); continue; }
            if (t.delay > 0) { t.delay--; continue; }
            try {
                if (t.once != null) {
                    it.remove();
                    t.once.run();
                } else if (t.repeating.getAsBoolean()) {
                    t.delay = t.period - 1;
                } else {
                    it.remove();
                }
            } catch (RuntimeException e) {
                it.remove();
                System.err.println("[HimaCasino] タスクで例外が発生しました: " + e);
                e.printStackTrace();
            }
        }
    }

    public void clear() {
        tasks.clear();
        incoming.clear();
    }
}
