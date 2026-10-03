package ru.rulhot.rexplevel.manager;

import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rexplevel.model.AutoSettings;
import ru.rulhot.rexplevel.model.AutoSettings.ModeSettings;
import ru.rulhot.rexplevel.model.AutoSettings.Trigger;
import ru.rulhot.rexplevel.model.BottleSettings;
import ru.rulhot.rexplevel.model.BottleSettings.BottleMode;
import ru.rulhot.rexplevel.model.BottleSettings.ExpGive;
import ru.rulhot.rexplevel.model.BottleSettings.RedeemMode;
import ru.rulhot.rexplevel.model.CommandSettings;
import ru.rulhot.rexplevel.util.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public final class ConfigManager {

    private static final Pattern COMMAND_NAME = Pattern.compile("^[a-z0-9_]{1,32}$");
    private static final Map<String, Integer> VERSIONS = Map.of(
            "config.yml", 1,
            "bottle.yml", 2,
            "auto.yml", 1,
            "messages.yml", 2
    );
    private static final Set<String> IGNORED_ROOTS = Set.of();

    private final @NotNull JavaPlugin plugin;

    private volatile @NotNull Snapshot snapshot = Snapshot.defaults();
    private volatile @NotNull CommandSettings registeredCommands = defaultsCommands();

    public void loadForStartup() throws IOException, InvalidConfigurationException {
        Files.createDirectories(plugin.getDataFolder().toPath());
        saveResource("config.yml");
        YamlConfiguration loadedConfig = load("config.yml");
        CommandSettings loadedCommands = readCommands(loadedConfig);
        Snapshot current = snapshot;
        snapshot = new Snapshot(
                loadedConfig,
                current.messages(),
                current.bottle(),
                current.xpPerExperienceBottle(),
                current.auto(),
                loadedCommands
        );
        registeredCommands = loadedCommands;
    }

    public void reload() throws IOException, InvalidConfigurationException {
        Files.createDirectories(plugin.getDataFolder().toPath());
        saveResource("config.yml");
        saveResource("bottle.yml");
        saveResource("auto.yml");
        saveResource("messages.yml");

        YamlConfiguration loadedConfig = load("config.yml");
        YamlConfiguration loadedBottleConfig = load("bottle.yml");
        YamlConfiguration loadedAutoConfig = load("auto.yml");
        YamlConfiguration loadedMessages = load("messages.yml");
        BottleSettings loadedBottle = readBottle(loadedBottleConfig);
        int loadedXpPerExperience = Math.clamp(
                loadedBottleConfig.contains("experience.xp-per-bottle")
                        ? loadedBottleConfig.getInt("experience.xp-per-bottle")
                        : loadedBottleConfig.getInt("vanilla.xp-per-bottle", 7),
                1,
                1000
        );
        AutoSettings loadedAuto = readAuto(loadedAutoConfig);
        CommandSettings loadedCommands = readCommands(loadedConfig);

        snapshot = new Snapshot(
                loadedConfig,
                loadedMessages,
                loadedBottle,
                loadedXpPerExperience,
                loadedAuto,
                loadedCommands
        );
        if (!loadedCommands.equals(registeredCommands)) {
            Logger.warn("Имена команд изменены. Перезапустите сервер, чтобы применить "
                    + loadedCommands.main() + " / " + loadedCommands.aliases());
        }
    }

    public boolean isDebug() {
        YamlConfiguration config = snapshot.config();
        if (config.contains("settings.debug")) {
            return config.getBoolean("settings.debug");
        }
        return config.getBoolean("debug");
    }

    public boolean isMetrics() {
        return snapshot.config().getBoolean("settings.metrics", true);
    }

    public @NotNull BottleSettings bottle() {
        return snapshot.bottle();
    }

    public int xpPerExperienceBottle() {
        return snapshot.xpPerExperienceBottle();
    }

    public @NotNull AutoSettings auto() {
        return snapshot.auto();
    }

    public @NotNull CommandSettings commands() {
        return snapshot.commands();
    }

    public @NotNull String placeholder(@NotNull String key) {
        return snapshot.messages().getString("placeholder." + key, "");
    }

    public @NotNull String getCommandMessage(@NotNull String key) {
        Snapshot current = snapshot;
        return withPrefix(current, current.messages().getString("command." + key, ""));
    }

    public @NotNull String formatCommandMessage(
            @NotNull String key,
            @NotNull Map<String, ?> placeholders
    ) {
        return format(getCommandMessage(key), placeholders);
    }

    public @NotNull List<String> getCommandMessages(@NotNull String key) {
        Snapshot current = snapshot;
        return current.messages().getStringList("command." + key).stream()
                .map(line -> withPrefix(current, line))
                .toList();
    }

    public @NotNull String format(@NotNull String text, @NotNull Map<String, ?> placeholders) {
        String formatted = text;
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            formatted = formatted.replace(
                    "{" + entry.getKey() + "}",
                    String.valueOf(entry.getValue())
            );
        }
        return formatted;
    }

    private void saveResource(@NotNull String name) {
        File target = new File(plugin.getDataFolder(), name);
        if (!target.exists()) {
            plugin.saveResource(name, false);
        }
    }

    private @NotNull YamlConfiguration load(@NotNull String name)
            throws IOException, InvalidConfigurationException {
        File file = new File(plugin.getDataFolder(), name);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        yaml.load(file);

        try (InputStream stream = plugin.getResource(name)) {
            if (stream == null) {
                return yaml;
            }
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.options().parseComments(true);
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            mergeDefaults(name, file, yaml, defaults);
            yaml.setDefaults(defaults);
        }
        return yaml;
    }

    private void mergeDefaults(
            @NotNull String name,
            @NotNull File file,
            @NotNull YamlConfiguration yaml,
            @NotNull YamlConfiguration defaults
    ) throws IOException, InvalidConfigurationException {
        int latest = VERSIONS.getOrDefault(name, 1);
        int current = yaml.getInt("config-version", 0);
        if (current >= latest) {
            return;
        }

        YamlConfiguration ordered = new YamlConfiguration();
        ordered.options().parseComments(true);

        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) || "config-version".equals(key)) {
                continue;
            }
            String root = key.split("\\.", 2)[0];
            if (IGNORED_ROOTS.contains(root)) {
                continue;
            }
            if (yaml.contains(key)) {
                ordered.set(key, yaml.get(key));
                continue;
            }
            ordered.set(key, defaults.get(key));
        }

        for (String key : yaml.getKeys(true)) {
            if (yaml.isConfigurationSection(key) || "config-version".equals(key)) {
                continue;
            }
            if (ordered.contains(key)) {
                continue;
            }
            ordered.set(key, yaml.get(key));
        }

        ordered.set("config-version", latest);
        copyComments(defaults, ordered);
        copyComments(yaml, ordered);

        ordered.save(file);
        yaml.load(file);
    }

    private void copyComments(@NotNull YamlConfiguration source, @NotNull YamlConfiguration target) {
        for (String key : source.getKeys(true)) {
            if (!target.contains(key)) {
                continue;
            }
            List<String> comments = source.getComments(key);
            if (!comments.isEmpty() && target.getComments(key).isEmpty()) {
                target.setComments(key, comments);
            }
            List<String> inline = source.getInlineComments(key);
            if (!inline.isEmpty() && target.getInlineComments(key).isEmpty()) {
                target.setInlineComments(key, inline);
            }
        }
    }

    private @NotNull String withPrefix(@NotNull Snapshot current, @NotNull String text) {
        return text
                .replace("{prefix}", current.messages().getString("prefix", ""))
                .replace("{command}", current.commands().main());
    }

    private @NotNull CommandSettings readCommands(@NotNull YamlConfiguration yaml) {
        String main = sanitizeCommand(yaml.getString("commands.main", "rexp"), "rexp");
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        for (String raw : yaml.getStringList("commands.aliases")) {
            String alias = sanitizeCommand(raw, null);
            if (alias != null && !alias.equals(main)) {
                aliases.add(alias);
            }
        }
        if (aliases.isEmpty() && !"rx".equals(main)) {
            aliases.add("rx");
        }
        return new CommandSettings(main, List.copyOf(aliases));
    }

    private @Nullable String sanitizeCommand(@Nullable String raw, @Nullable String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (!COMMAND_NAME.matcher(value).matches()) {
            Logger.warn("Некорректное имя команды: " + raw);
            return fallback;
        }
        return value;
    }

    private @NotNull BottleSettings readBottle(@NotNull YamlConfiguration yaml) {
        Material material = Material.matchMaterial(yaml.getString("bottle.material", "EXPERIENCE_BOTTLE"));
        if (material == null || !material.isItem()) {
            Logger.warn("Некорректный материал бутылочки, используется EXPERIENCE_BOTTLE");
            material = Material.EXPERIENCE_BOTTLE;
        }

        return new BottleSettings(
                material,
                yaml.getString("bottle.name", ""),
                List.copyOf(yaml.getStringList("bottle.lore")),
                yaml.getBoolean("bottle.glint", true),
                Math.max(0, yaml.getInt("bottle.custom-model-data")),
                Math.clamp(yaml.getInt("bottle.max-levels", 500), 1, 1000),
                yaml.getBoolean("bottle.require-glass-bottle", true),
                readBottleRedeem(yaml.getString("bottle.redeem")),
                readExpGive(yaml.getString("bottle.exp-give"))
        );
    }

    private @NotNull ExpGive readExpGive(@Nullable String raw) {
        ExpGive mode = ExpGive.find(raw);
        if (mode != null) {
            return mode;
        }
        if (raw != null && !raw.isBlank()) {
            Logger.warn("Неизвестный bottle.exp-give: " + raw + ", используется FOLD");
        }
        return ExpGive.FOLD;
    }

    private @NotNull RedeemMode readBottleRedeem(@Nullable String raw) {
        RedeemMode mode = RedeemMode.find(raw);
        if (mode != null) {
            return mode;
        }
        if (raw != null && !raw.isBlank()) {
            Logger.warn("Неизвестный bottle.redeem: " + raw + ", используется DRINK");
        }
        return RedeemMode.DRINK;
    }

    private @NotNull AutoSettings readAuto(@NotNull YamlConfiguration yaml) {
        return new AutoSettings(
                yaml.getBoolean("auto-convert.enabled-by-default"),
                readBottleMode(yaml.getString("auto-convert.default-mode")),
                readAutoTrigger(yaml.getString("auto-convert.trigger")),
                Math.clamp(yaml.getInt("auto-convert.interval-seconds", 3), 1, 30),
                readAutoMode(yaml, "auto-convert.bottle", true, true),
                readAutoMode(yaml, "auto-convert.experience", false, false)
        );
    }

    private @NotNull Trigger readAutoTrigger(@Nullable String raw) {
        Trigger trigger = Trigger.find(raw);
        if (trigger != null) {
            return trigger;
        }
        if (raw != null && !raw.isBlank()) {
            Logger.warn("Неизвестный auto-convert.trigger: " + raw + ", используется EVENT");
        }
        return Trigger.EVENT;
    }

    private @NotNull ModeSettings readAutoMode(
            @NotNull YamlConfiguration yaml,
            @NotNull String path,
            boolean withBottleLevels,
            boolean allowRequireGlass
    ) {
        int bottleLevels = withBottleLevels
                ? Math.clamp(yaml.getInt(path + ".bottle-levels", 15), 1, 1000)
                : 1;
        return new ModeSettings(
                Math.clamp(yaml.getInt(path + ".keep-levels", 30), 0, 1000),
                bottleLevels,
                Math.clamp(yaml.getInt(path + ".max-bottles-per-cycle", 64), 1, 2304),
                allowRequireGlass && yaml.getBoolean(path + ".require-glass-bottle", true),
                readHungerPerBottle(yaml, path + ".hunger-per-bottle")
        );
    }

    private double readHungerPerBottle(@NotNull YamlConfiguration yaml, @NotNull String path) {
        Object raw = yaml.get(path, 0);
        if (raw instanceof Number number) {
            return Math.clamp(number.doubleValue(), 0.0D, 20.0D);
        }
        if (raw instanceof String text) {
            String normalized = text.trim().replace(',', '.');
            if (!normalized.isEmpty()) {
                try {
                    return Math.clamp(Double.parseDouble(normalized), 0.0D, 20.0D);
                } catch (NumberFormatException exception) {
                    Logger.warn("Некорректный " + path + ": " + text + ", используется 0");
                }
            }
        }
        return 0.0D;
    }

    private @NotNull BottleMode readBottleMode(@Nullable String raw) {
        BottleMode mode = BottleMode.find(raw);
        if (mode != null) {
            return mode;
        }
        if (raw != null && !raw.isBlank()) {
            Logger.warn("Неизвестный auto-convert.default-mode: " + raw + ", используется BOTTLE");
        }
        return BottleMode.BOTTLE;
    }

    private static @NotNull CommandSettings defaultsCommands() {
        return new CommandSettings("rexp", List.of("rx"));
    }

    private static @NotNull AutoSettings defaultsAuto() {
        ModeSettings bottle = new ModeSettings(30, 15, 64, true, 0);
        ModeSettings experience = new ModeSettings(30, 1, 64, false, 0);
        return new AutoSettings(
                false,
                BottleMode.BOTTLE,
                Trigger.EVENT,
                3,
                bottle,
                experience
        );
    }

    private static @NotNull BottleSettings defaultsBottle() {
        return new BottleSettings(
                Material.EXPERIENCE_BOTTLE,
                "",
                List.of(),
                true,
                0,
                500,
                true,
                RedeemMode.DRINK,
                ExpGive.FOLD
        );
    }

    private record Snapshot(
            @NotNull YamlConfiguration config,
            @NotNull YamlConfiguration messages,
            @NotNull BottleSettings bottle,
            int xpPerExperienceBottle,
            @NotNull AutoSettings auto,
            @NotNull CommandSettings commands
    ) {

        private static @NotNull Snapshot defaults() {
            return new Snapshot(
                    new YamlConfiguration(),
                    new YamlConfiguration(),
                    defaultsBottle(),
                    7,
                    defaultsAuto(),
                    defaultsCommands()
            );
        }
    }
}
