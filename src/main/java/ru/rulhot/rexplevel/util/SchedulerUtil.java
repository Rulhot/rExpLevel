package ru.rulhot.rexplevel.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@RequiredArgsConstructor
public final class SchedulerUtil {

    private final @NotNull Plugin plugin;
    private final @NotNull Set<Task> tracked = ConcurrentHashMap.newKeySet();

    public void runAsync(@NotNull Runnable action) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> action.run());
    }

    public void runGlobal(@NotNull Runnable action) {
        plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> action.run());
    }

    public @NotNull Task runGlobalAtFixedRate(@NotNull Runnable action, long periodTicks) {
        long period = Math.max(1L, periodTicks);
        TrackedTask wrapped = new TrackedTask();
        ScheduledTask scheduled = plugin.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(plugin, task -> action.run(), period, period);
        wrapped.bind(scheduled);
        tracked.add(wrapped);
        return wrapped;
    }

    public void runFor(@NotNull Entity entity, @NotNull Runnable action) {
        entity.getScheduler().run(plugin, task -> action.run(), null);
    }

    public @Nullable Task runForLater(
            @NotNull Entity entity,
            @NotNull Runnable action,
            long delayTicks
    ) {
        TrackedTask wrapped = new TrackedTask();
        ScheduledTask scheduled = entity.getScheduler().runDelayed(
                plugin,
                task -> {
                    try {
                        action.run();
                    } finally {
                        tracked.remove(wrapped);
                    }
                },
                null,
                delayTicks
        );
        if (scheduled == null) {
            return null;
        }
        wrapped.bind(scheduled);
        tracked.add(wrapped);
        return wrapped;
    }

    public void cancelAll() {
        for (Task task : tracked) {
            task.cancel();
        }
        tracked.clear();
        plugin.getServer().getAsyncScheduler().cancelTasks(plugin);
        plugin.getServer().getGlobalRegionScheduler().cancelTasks(plugin);
    }

    @FunctionalInterface
    public interface Task {
        void cancel();
    }

    private final class TrackedTask implements Task {

        private final @NotNull AtomicReference<ScheduledTask> scheduled = new AtomicReference<>();

        private void bind(@NotNull ScheduledTask task) {
            scheduled.set(task);
        }

        @Override
        public void cancel() {
            ScheduledTask task = scheduled.getAndSet(null);
            if (task != null) {
                task.cancel();
            }
            tracked.remove(this);
        }
    }
}
