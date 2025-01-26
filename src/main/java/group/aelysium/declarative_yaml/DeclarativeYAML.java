package group.aelysium.declarative_yaml;

import group.aelysium.declarative_yaml.annotations.*;
import group.aelysium.declarative_yaml.lib.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.CommentedConfigurationNode;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Used for declarative loading of a YAML configuration.
 */
public class DeclarativeYAML {
    private static final Map<String, GitOperator> operators = new ConcurrentHashMap<>();
    private static final Map<String, String> basePaths = new ConcurrentHashMap<>();

    public static void registerRepository(String namespace, GitOperator.Config config) {
        if(operators.containsKey(namespace)) throw new RuntimeException("The namespace "+namespace+" is already being used.");
        operators.put(namespace, config.build());
    }
    public static Optional<GitOperator> fetchRepository(@NotNull String namespace) {
        return Optional.ofNullable(operators.get(namespace));
    }
    public static void basePath(String namespace, String basePath) {
        if (!Pattern.compile("^[a-zA-Z0-9\\_\\-\\.\\/\\\\]+$").matcher(basePath).matches())
            throw new IllegalArgumentException("Invalid base path string: " + basePath);
        basePaths.put(namespace, basePath);
    }
    public static String basePath(String namespace) {
        return Optional.ofNullable(basePaths.get(namespace)).orElse("");
    }

    /**
     * Loads a configuration from a class declaration.
     * For details on how to properly set up a config declaration, check out the wiki.<br/>
     * <a href="https://wiki.aelysium.group/declarative-yaml/">Aelysium Wiki | Declarative YAML</a>
     * @param clazz   The class definition of the config.
     * @param printer The printer configuration to use.
     */
    public static <T> T From(@NotNull Class<T> clazz, @NotNull Printer printer) throws RuntimeException {
        try {
            if(!clazz.isAnnotationPresent(Config.class))
                throw new RuntimeException("Config class declarations must be annotated with @Config.");

            T instance = clazz.getConstructor().newInstance();

            YAMLNode node = InitializationPhase.nodesFromClass(instance, printer);
            CommentedConfigurationNode yaml = LoadingPhase.loadYAMLFile(instance, printer, node);
            InjectionPhase.injectConfigValueIntoClass(instance, printer, yaml);

            return instance;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a configuration from a class declaration.
     * For details on how to properly set up a config declaration, check out the wiki.<br/>
     * <a href="https://wiki.aelysium.group/declarative-yaml/">Aelysium Wiki | Declarative YAML</a>
     * @param clazz The class definition of the config.
     */
    public static <T> T From(@NotNull Class<T> clazz) throws RuntimeException {
        return From(clazz, new Printer());
    }

    /**
     * Reloads the specified instance from the config file.
     * This file does not print or store any data from the class onto the file system, it only reads from the specific file.<br/>
     * For details on how to properly set up a config declaration, check out the wiki.<br/>
     * <a href="https://wiki.aelysium.group/declarative-yaml/">Aelysium Wiki | Declarative YAML</a>
     * @param instance The config instance to reload values into.
     * @param printer The configuration to use during the loading.
     */
    @Contract(mutates = "param1")
    public static void ReadOnly(@NotNull Object instance, @NotNull Printer printer) {
        try {
            if(!instance.getClass().isAnnotationPresent(Config.class))
                throw new RuntimeException("Config class declarations must be annotated with @Config.");

            CommentedConfigurationNode yaml = LoadingPhase.reloadYAMLFile(instance, printer);
            InjectionPhase.injectConfigValueIntoClass(instance, printer, yaml);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Updates the file on the file system to mirror the config instance.
     * This method only serializes and stores the instance, it does not read from the file, nor update the instance at all.<br/>
     * For details on how to properly set up a config declaration, check out the wiki.<br/>
     * <a href="https://wiki.aelysium.group/declarative-yaml/">Aelysium Wiki | Declarative YAML</a>
     * @param instance The config instance to serialize into a config on the file system.
     * @param printer The configuration to use during the loading.
     */
    public static void WriteOnly(@NotNull Object instance, @NotNull Printer printer) {
        try {
            if(!instance.getClass().isAnnotationPresent(Config.class))
                throw new RuntimeException("Config class declarations must be annotated with @Config.");

            YAMLNode node = InitializationPhase.nodesFromClass(instance, printer);
            LoadingPhase.updateYAMLFile(instance, printer, node);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
