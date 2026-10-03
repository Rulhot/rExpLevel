package ru.rulhot.rexplevel.placeholder;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rexplevel.manager.AutoConvertManager;
import ru.rulhot.rexplevel.manager.ConfigManager;

public final class PlaceholderHook {

    private @Nullable RExpExpansion expansion;

    public void register(
            @NotNull JavaPlugin plugin,
            @NotNull AutoConvertManager autoConvert,
            @NotNull ConfigManager config
    ) {
        unregister();
        Plugin placeholderApi = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (placeholderApi == null || !placeholderApi.isEnabled()) {
            return;
        }
        expansion = new RExpExpansion(plugin.getPluginMeta().getVersion(), autoConvert, config);
        expansion.register();
    }

    public void unregister() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
    }
}
