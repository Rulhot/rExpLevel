package ru.rulhot.rexplevel.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rexplevel.util.logger.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public final class UpdateChecker {

    private static final @NotNull URI LATEST_RELEASE =
            URI.create("https://api.github.com/repos/Rulhot/rExpLevel/releases/latest");
    private static final @NotNull String DOWNLOAD_PAGE = "https://github.com/Rulhot/rExpLevel/releases/latest";
    private static final @NotNull String USER_AGENT = "rExpLevel/";
    private static final @NotNull String TAG = "tag_name";
    private static final @NotNull Duration TIMEOUT = Duration.ofSeconds(10L);
    private static final int HTTP_OK = 200;
    private static final @NotNull Pattern NOT_VERSION = Pattern.compile("[^0-9.]");
    private static final @NotNull Pattern SEPARATOR = Pattern.compile("\\.");

    private final @NotNull String currentVersion;

    public void check() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE)
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT + currentVersion)
                .GET()
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            client.shutdown();
            if (error != null) {
                Logger.warn("Не удалось проверить обновления: " + error.getMessage());
                return;
            }
            report(response);
        });
    }

    private void report(@NotNull HttpResponse<String> response) {
        if (response.statusCode() != HTTP_OK) {
            Logger.warn("Не удалось проверить обновления: GitHub ответил кодом " + response.statusCode());
            return;
        }
        String latestVersion = parseTag(response.body());
        if (latestVersion == null) {
            Logger.warn("Не удалось проверить обновления: в ответе GitHub нет версии");
            return;
        }
        if (!isNewer(latestVersion, currentVersion)) {
            return;
        }
        Logger.warn("Доступна новая версия " + latestVersion + " (установлена " + currentVersion + "): "
                + DOWNLOAD_PAGE);
    }

    private static @Nullable String parseTag(@NotNull String body) {
        try {
            JsonElement tag = JsonParser.parseString(body).getAsJsonObject().get(TAG);
            return tag == null || tag.isJsonNull() ? null : tag.getAsString();
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException exception) {
            return null;
        }
    }

    static boolean isNewer(@NotNull String latest, @NotNull String current) {
        String[] latestParts = SEPARATOR.split(NOT_VERSION.matcher(latest).replaceAll(""));
        String[] currentParts = SEPARATOR.split(NOT_VERSION.matcher(current).replaceAll(""));
        for (int i = 0; i < Math.max(latestParts.length, currentParts.length); i++) {
            int difference = Integer.compare(part(latestParts, i), part(currentParts, i));
            if (difference != 0) {
                return difference > 0;
            }
        }
        return false;
    }

    private static int part(@NotNull String @NotNull [] parts, int index) {
        if (index >= parts.length || parts[index].isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
