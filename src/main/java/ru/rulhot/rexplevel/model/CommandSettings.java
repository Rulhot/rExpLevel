package ru.rulhot.rexplevel.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public record CommandSettings(
        @NotNull String main,
        @NotNull List<String> aliases
) {
}
