package group.aelysium.declarative_yaml;

import group.aelysium.declarative_yaml.annotations.Config;
import group.aelysium.declarative_yaml.lib.Printer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

public class LiveConfig {
    private final Object instance;
    private final Printer printer;
    private final Consumer<Exception> onFail;

    public LiveConfig(@NotNull Object instance, @NotNull Printer printer, @Nullable Consumer<Exception> onFail) {
        if(!instance.getClass().isAnnotationPresent(Config.class))
            throw new RuntimeException("Config class declarations must be annotated with @Config.");
        this.instance = instance;
        this.printer = printer;
        this.onFail = onFail;
    }

    public Object instance() {
        return this.instance;
    }

    public Printer printer() {
        return this.printer;
    }

    public Consumer<Exception> onFail() {
        return this.onFail;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        LiveConfig that = (LiveConfig) object;
        return Objects.equals(instance, that.instance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instance);
    }
}
