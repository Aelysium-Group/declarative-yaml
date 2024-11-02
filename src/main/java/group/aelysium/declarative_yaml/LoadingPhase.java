package group.aelysium.declarative_yaml;

import group.aelysium.declarative_yaml.annotations.Config;
import group.aelysium.declarative_yaml.annotations.Git;
import group.aelysium.declarative_yaml.lib.Printer;
import group.aelysium.declarative_yaml.lib.YAMLNode;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

class LoadingPhase {
    public static CommentedConfigurationNode loadYAMLFile(Object instance, Printer printer, YAMLNode tree) throws Exception {
        File file = resolveFile(instance, printer);

        if(!file.exists()) generateFile(file, tree, printer);
        return loadFile(file);
    }
    public static CommentedConfigurationNode reloadYAMLFile(Object instance, Printer printer) throws Exception {
        File file = resolveFile(instance, printer);

        return loadFile(file);
    }
    public static void updateYAMLFile(Object instance, Printer printer, YAMLNode tree) throws Exception {
        File file = resolveFile(instance, printer);

        generateFile(file, tree, printer);
    }

    public static File resolveFile(Object instance, Printer printer) throws Exception {
        String configPath = instance.getClass().getAnnotation(Config.class).value();

        File file = new File(parsePath(configPath, printer));
        if(instance.getClass().isAnnotationPresent(Git.class)) {
            Git annotation = instance.getClass().getAnnotation(Git.class);
            GitOperator git = DeclarativeYAML.fetchRepository(annotation.value()).orElse(null);
            if(git == null)
                if(annotation.required()) throw new IllegalStateException(annotation.value()+" was called before it was registered. Git repositories must be registered using DynamicYAML.registerRepository.");
                else return file;

            git.sync();
            file = git.fetch(Path.of(configPath));
        }

        return file;
    }

    private static String parsePath(String originalPath, Printer printer) throws IOException {
        String path;
        {
            AtomicInteger index = new AtomicInteger(0);
            List<String> splitPath = Arrays.stream(originalPath.split("/")).map(v -> {
                if (!v.startsWith("{")) return v;
                String key = v.replaceAll("^.*\\{([a-zA-Z0-9\\_\\-\\.\\/\\\\]+)\\}.*", "$1");

                String replacement = printer.pathReplacements().get(key);
                if (replacement == null)
                    throw new IllegalArgumentException("No value for the path key '" + key + "' exists!");
                index.incrementAndGet();
                return v.replaceAll("^\\{[a-zA-Z0-9\\_\\-\\.\\/\\\\]+\\}(\\.[a-zA-Z0-9\\_\\-]*)?", replacement + "$1");
            }).toList();
            path = String.join("/", splitPath);
        }
        if (!Pattern.compile("^[a-zA-Z0-9\\_\\-\\.\\/\\\\]+$").matcher(path).matches())
            throw new IOException("Invalid file path defined for config: " + path);

        return path;
    }

    private static CommentedConfigurationNode loadFile(@NotNull File file) throws Exception {
        return YamlConfigurationLoader.builder()
                .indent(2)
                .path(file.toPath())
                .build().load();
    }

    private static void generateFile(@NotNull File file, @NotNull YAMLNode nodeTree, Printer printer) throws Exception {
        File parent = file.getParentFile();
        if(parent != null && !parent.exists()) parent.mkdirs();
        file.createNewFile();

        try (FileWriter writer = new FileWriter(file)) {
            Serializer.serialize(writer, nodeTree, printer);
        }
    }
}
