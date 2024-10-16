package group.aelysium.declarative_yaml;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;

record ConfigTarget(int order, String key, Object value, Field field, List<String> comment) {
    ConfigTarget(int order, @NotNull String key, @NotNull Object value, @Nullable Field field, @Nullable List<String> comment) {
        this.order = order;
        this.key = key;
        this.value = value;
        this.field = field;
        this.comment = comment;
    }
}
