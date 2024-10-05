package group.aelysium.declarative_yaml;

import group.aelysium.declarative_yaml.annotations.*;
import group.aelysium.declarative_yaml.lib.Primitives;
import group.aelysium.declarative_yaml.lib.Printer;
import group.aelysium.declarative_yaml.lib.Serializable;
import group.aelysium.declarative_yaml.lib.YAMLNode;
import io.leangen.geantyref.TypeVariableImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.util.Strings;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Used for declarative loading of a YAML configuration.
 */
public class DeclarativeYAML {

    /**
     * Loads a configuration from a class definition.
     * <h3>Creating a Config</h3>
     * Config classes must be annotated with the {@link Config} annotation.
     * The path provided to the annotation will generate a config relative to the classloader's root.
     * <h3>Adding Entries</h3>
     * An entry is defined as a non-static member, annotated with both {@link Comment} and {@link Node} or just a single Node annotation.<br/>
     * Comments and Nodes will be printed to the configuration in the order that you define in the annotations.
     * If a Comment is present, the comment will always be printed first.
     * <h3>Entry Serialization</h3>
     * When a member is annotated with {@link Node} the value of that entry in the config will be loaded into that member.
     * The type itself can be Primitive, {@link Serializable}, String.
     * Additionally, you can also specify one of: List(?), Set(?), Map(String, ?). Where the question marks represent one of the already mentioned types.
     * <h3>Configuration Injection</h3>
     * Declarative YAML supports config injection. What this means is that, two config classes can point to the same config.
     * If they have differing entries, those entries will be injected into the correct location in the config, and will also be loaded as such.
     * <h3>Header Comment</h3>
     * If you want to add a comment that always appears at the top of the config;
     * simply use the {@link Config} annotation on the class declaration, just below the {@link Config} annotation.
     * <h3>All Contents</h3>
     * The {@link AllContents} annotation will load all bytes from the configuration into the member it's assigned to.
     * Just like entries, the member must be non-static. The member must also be a byte[].
     * <h3>Path Parameters</h3>
     * Via the {@link PathParameter} annotation, you can extract specific values from the config path into a member.
     * Just like entries, the member used must be non-static. The member can be any type you want, the value in the path will attempt to be serialized into the type you specify.
     * <pre><code>{@code "/config/{identifier}.yml" }</code></pre>
     * Any value specified in place of {identifier} will be loaded into the defined member annotated with {@link PathParameter @PathParameter("identifier")}
     * In order to define what values to replace in the path, you can use the pathReplacements parameter in this method.
     * <h3>Comment Replacement</h3>
     * When adding comments using the {@link Comment} annotation, you can define targets as {some_key} which will be replaced when the config is generated.
     * <pre><code>{@code "This is an example comment with an inserted value that says: {say_something_here}" }</code></pre>
     * To replace a target, you can define its name inside the commentReplacements parameter on {@link Printer}.
     *
     * @param clazz   The class definition of the config.
     * @param printer The printer configuration to use.
     */
    public static <T> T load(@NotNull Class<T> clazz, @NotNull Printer printer) throws RuntimeException {
        try {
            printer.injecting(clazz.isAnnotationPresent(Inject.class));

            if(!(clazz.isAnnotationPresent(Config.class) || !printer.injecting()))
                throw new RuntimeException("Config class declarations must be annotated with either @Config or @Inject.");

            String configPath;
            {
                if(printer.injecting()) configPath = clazz.getAnnotation(Inject.class).value();
                else configPath = clazz.getAnnotation(Config.class).value();
            }

            String path = parsePath(configPath, printer);

            T instance = clazz.getConstructor().newInstance();

            pathParameters(clazz, instance, printer);

            List<ConfigTarget> targets = generateConfigTargets(clazz, instance, printer);
            YAMLNode nodeTree = convertConfigTargetsToYAMLNodes(clazz, targets);

            CommentedConfigurationNode yaml = loadOrGenerateFile(path, nodeTree, printer);

            handleAllContents(clazz, instance, path);
            for (ConfigTarget target : targets) {
                if (target.field() == null) continue;
                target.field().setAccessible(true);
                target.field().set(instance, getValueFromYAML(yaml, target));
                target.field().setAccessible(false);
            }
            return instance;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T load(@NotNull Class<T> clazz) throws RuntimeException {
        return load(clazz, new Printer());
    }

    public static void reload(@NotNull Object instance, @NotNull Printer printer) throws RuntimeException {
        try {
            printer.injecting(instance.getClass().isAnnotationPresent(Inject.class));

            if(!(instance.getClass().isAnnotationPresent(Config.class) || !printer.injecting()))
                throw new RuntimeException("Config class declarations must be annotated with either @Config or @Inject.");

            String configPath;
            {
                if(printer.injecting()) configPath = instance.getClass().getAnnotation(Inject.class).value();
                else configPath = instance.getClass().getAnnotation(Config.class).value();
            }

            String path = parsePath(configPath, printer);

            pathParameters(instance.getClass(), instance, printer);

            List<ConfigTarget> targets = generateConfigTargets(instance.getClass(), instance, printer);
            YAMLNode nodeTree = convertConfigTargetsToYAMLNodes(instance.getClass(), targets);

            CommentedConfigurationNode yaml = loadOrGenerateFile(path, nodeTree, printer);

            handleAllContents(instance.getClass(), instance, path);
            for (ConfigTarget target : targets) {
                if(target.field() == null) continue;
                target.field().setAccessible(true);
                target.field().set(instance, getValueFromYAML(yaml, target));
                target.field().setAccessible(false);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void reload(@NotNull Object instance) throws RuntimeException {
        reload(instance, new Printer());
    }

    public static void update(@NotNull Object instance, @NotNull Printer printer) throws RuntimeException {
        try {
            printer.injecting(instance.getClass().isAnnotationPresent(Inject.class));

            if(!(instance.getClass().isAnnotationPresent(Config.class) || !printer.injecting()))
                throw new RuntimeException("Config class declarations must be annotated with either @Config or @Inject.");

            String configPath;
            {
                if(printer.injecting()) configPath = instance.getClass().getAnnotation(Inject.class).value();
                else configPath = instance.getClass().getAnnotation(Config.class).value();
            }

            String path = parsePath(configPath, printer);

            pathParameters(instance.getClass(), instance, printer);

            List<ConfigTarget> targets = generateConfigTargets(instance.getClass(), instance, printer);
            YAMLNode nodeTree = convertConfigTargetsToYAMLNodes(instance.getClass(), targets);

            CommentedConfigurationNode yaml = loadOrGenerateFile(path, nodeTree, printer);

            handleAllContents(instance.getClass(), instance, path);
            for (ConfigTarget target : targets) {
                if(target.field() == null) continue;
                target.field().setAccessible(true);
                target.field().set(instance, getValueFromYAML(yaml, target));
                target.field().setAccessible(false);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void update(@NotNull Object instance) throws RuntimeException {
        update(instance, new Printer());
    }

    private static List<ConfigTarget> generateConfigTargets(@NotNull Class<?> clazz, @NotNull Object instance, Printer printer) {
        List<ConfigTarget> targets = new ArrayList<>();
        Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers())).toList()
                .forEach(f -> {
                    boolean hasComment = f.isAnnotationPresent(Comment.class);
                    boolean hasEntry = f.isAnnotationPresent(Node.class);
                    if (!(hasComment || hasEntry)) return;

                    Node node = f.getAnnotation(Node.class);

                    List<String> comment = null;
                    if (hasComment) {
                        Comment c = f.getAnnotation(Comment.class);
                        comment = new ArrayList<>();
                        for (String s : c.value()) {
                            AtomicReference<String> correctedString = new AtomicReference<>(s);
                            printer.commentReplacements().forEach((k, v) -> correctedString.set(correctedString.get().replace("{" + k + "}", v)));
                            comment.add(correctedString.get());
                        }
                    }

                    String key = node.key();
                    if (key.isEmpty()) key = FieldAssigner.convertFieldNameToYAMLKey(f);

                    Object defaultValue = null;
                    try {
                        f.setAccessible(true);
                        defaultValue = f.get(instance);
                        f.setAccessible(false);
                    } catch (Exception ignore) {
                    }
                    if (defaultValue == null)
                        throw new NullPointerException("You must define a default value on fields annotated with @Node. Issue was caused by " + f.getName());
                    targets.add(new ConfigTarget(node.value(), key, defaultValue, f, comment));
                });

        targets.sort((target1, target2) -> {
            int compare = Integer.compare(target1.order(), target2.order());
            if (compare == 0) return target1.key().compareTo(target2.key());
            return compare;
        });

        return targets;
    }

    private static YAMLNode convertConfigTargetsToYAMLNodes(@NotNull Class<?> clazz, List<ConfigTarget> targets) {
        YAMLNode root;

        try {
            Comment comment = clazz.getAnnotation(Comment.class);
            root = new YAMLNode(null, Arrays.asList(comment.value()));
        } catch (Exception ignore) {
            root = new YAMLNode(null, null);
        }

        for (ConfigTarget parsingTarget : targets) {
            String[] keys = parsingTarget.key().split("\\.");
            AtomicReference<YAMLNode> currentNode = new AtomicReference<>(root);
            for (int i = 0; i < keys.length; i++) {
                String key = keys[i];
                boolean lastKey = i == keys.length - 1;

                YAMLNode newCurrent = currentNode.get().setGetChild(
                        key,
                        lastKey ? new YAMLNode(key, parsingTarget.value(), parsingTarget.comment())
                                : new YAMLNode(key, null)
                );
                if (Set.class.isAssignableFrom(parsingTarget.value().getClass())) newCurrent.isArray(true);
                if (List.class.isAssignableFrom(parsingTarget.value().getClass())) newCurrent.isArray(true);

                currentNode.set(newCurrent);
            }
        }

        return root;
    }

    private static void pathParameters(@NotNull Class<?> clazz, @NotNull Object instance, @NotNull Printer printer) {
        Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers())).toList()
                .forEach(f -> {
                    if (!f.isAnnotationPresent(PathParameter.class)) return;

                    PathParameter pathParameter = f.getAnnotation(PathParameter.class);
                    try {
                        f.setAccessible(true);
                        f.set(instance, printer.pathReplacements().get(pathParameter.value()));
                        f.setAccessible(false);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private static void handleAllContents(@NotNull Class<?> clazz, @NotNull Object instance, @NotNull String path) throws Exception {
        File configPointer = new File(path);
        byte[] allContents = Files.readAllBytes(configPointer.toPath());

        Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers())).toList()
                .forEach(f -> {
                    if (!f.isAnnotationPresent(AllContents.class)) return;

                    try {
                        if (!f.getType().equals(byte[].class))
                            throw new ClassCastException("Fields annotated with @AllContents must be of type byte[]!");

                        f.setAccessible(true);
                        f.set(instance, allContents);
                        f.setAccessible(false);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * Retrieve data from a specific configuration node.
     *
     * @param target The target to search for.
     * @return Data with a type matching `type`
     * @throws IllegalStateException If there was an issue while retrieving the data or converting it to `type`.
     */
    private static Object getValueFromYAML(CommentedConfigurationNode root, ConfigTarget target) throws Exception {
        CommentedConfigurationNode node = getNodeFromYAML(root, target.key());

        return FieldAssigner.serialize(node, target.field().getType(), target.field().getGenericType());
    }

    public static CommentedConfigurationNode getNodeFromYAML(CommentedConfigurationNode node, String route) throws Exception {
        String[] steps = route.split("\\.");

        AtomicReference<CommentedConfigurationNode> currentNode = new AtomicReference<>(node);
        Arrays.stream(steps).forEach(step -> currentNode.set(currentNode.get().node(step)));
        if (currentNode.get() == null) throw new NullPointerException("The node " + route + " is null.");

        return currentNode.get();
    }

    private static CommentedConfigurationNode loadOrGenerateFile(@NotNull String path, @NotNull YAMLNode nodeTree, Printer printer) {
        File configPointer = new File(path);

        try {
            if (!configPointer.exists()) {
                if (printer.injecting())
                    throw new IOException("Attempted to inject into a config that doesn't exist! " + path);

                File parent = configPointer.getParentFile();
                if (parent != null) if (!parent.exists()) parent.mkdirs();
                configPointer.createNewFile();

                try (FileWriter writer = new FileWriter(configPointer)) {
                    YAMLPrinter.deserialize(writer, nodeTree, printer);
                }
            }

            if (printer.injecting()) {
                try (FileWriter writer = new FileWriter(configPointer)) {
                    YAMLPrinter.deserialize(writer, nodeTree, printer);
                }
            }

            return YamlConfigurationLoader.builder()
                    .indent(2)
                    .path(configPointer.toPath())
                    .build().load();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
}

class YAMLPrinter {
    public static void deserialize(FileWriter writer, YAMLNode nodeTree, Printer printer) throws Exception {
        nodeToString(writer, nodeTree, 0, printer);
    }

    public static void nodeToString(FileWriter writer, YAMLNode current, int level, Printer printer) throws Exception {
        String indent = Strings.repeat(" ", level * printer.indentSpaces());

        // Climb hierarchy
        if (current.children().isPresent()) {
            if (current.name() == null) {
                for (YAMLNode node : current.children().orElseThrow())
                    nodeToString(writer, node, level, printer);
                return;
            }

            writer.append(indent).append(current.name()).append(":\n");
            for (YAMLNode node : current.children().orElseThrow())
                nodeToString(writer, node, level + 1, printer);
            return;
        }

        // Print comments
        for (String s : current.comment().orElse(List.of())) {
            if (printer.indentComments()) writer.append(indent);

            // Add comment hashtag if one wasn't given.
            if (!s.startsWith("#")) writer.append("# ");

            writer.append(s);
            writer.append("\n");
        }

        // Print Node
        if (current.value().isEmpty()) return;
        Object value = current.value().orElseThrow();
        Class<?> clazz = value.getClass();

        if (current.name() != null) writer.append(indent).append(current.name()).append(": ");

        if (Primitives.isPrimitive(clazz) || value instanceof String) {
            writer.append(current.stringifiedValue().orElse("")).append("\n");
            writer.append(printer.lineSeparator());
        }

        if (Serializable.class.isAssignableFrom(clazz)) {
            List<Field> fields = Arrays.stream(clazz.getFields()).filter(f -> !Modifier.isStatic(f.getModifiers())).toList();

            YAMLNode extraNode = new YAMLNode(null, null);
            boolean hasEntries = false;
            for (Field f : fields) {
                f.setAccessible(true);
                if (f.get(value) == null) {
                    f.setAccessible(false);
                    continue;
                }
                String name = FieldAssigner.convertFieldNameToYAMLKey(f);
                extraNode.setGetChild(name, new YAMLNode(name, f.get(value), null));
                hasEntries = true;
                f.setAccessible(false);
            }
            if (!hasEntries) {
                writer.append("{}\n");
                writer.append(printer.lineSeparator());
                return;
            }
            if (current.name() != null) writer.append("\n");
            nodeToString(writer, extraNode, level + 1, printer);

            writer.append(printer.lineSeparator());
        }

        if (clazz.isEnum()) {
            writer.append(value.toString());
            writer.append(printer.lineSeparator());
        }

        if (Record.class.isAssignableFrom(clazz)) {
            List<RecordComponent> components = Arrays.stream(clazz.getRecordComponents()).toList();
            YAMLNode extraNode = new YAMLNode(null, null);
            boolean hasEntries = false;
            for (RecordComponent component : components) {
                Method accessor = component.getAccessor();
                Object componentValue = accessor.invoke(value);
                if (componentValue == null) {
                    continue;
                }

                List<String> comment = null;
                boolean hasComment = component.isAnnotationPresent(Comment.class);
                if (hasComment) {
                    Comment c = component.getAnnotation(Comment.class);
                    comment = new ArrayList<>();
                    for (String s : c.value()) {
                        AtomicReference<String> correctedString = new AtomicReference<>(s);
                        printer.commentReplacements().forEach((k, v) -> correctedString.set(correctedString.get().replace("{" + k + "}", v)));
                        comment.add(correctedString.get());
                    }
                }

                String name = FieldAssigner.convertFieldNameToYAMLKey(component.getName());
                extraNode.setGetChild(name, new YAMLNode(name, componentValue, comment));
                hasEntries = true;
            }

            if (!hasEntries) {
                writer.append("{}\n");
                writer.append(printer.lineSeparator());
                return;
            }

            if (current.name() != null) writer.append("\n");
            nodeToString(writer, extraNode, level + 1, printer);

            writer.append(printer.lineSeparator());
        }

        if (Collection.class.isAssignableFrom(clazz)) {
            if (((Collection<?>) value).isEmpty()) {
                writer.append("[]\n");
                writer.append(printer.lineSeparator());
                return;
            }

            writer.append("\n");
            for (Object e : (Collection<?>) value) {
                if (Primitives.isPrimitive(e.getClass())) {
                    writer.append("\n");
                    writer.append(indent).append(Strings.repeat(" ", printer.indentSpaces())).append("- ").append(e.toString());
                } else if (e instanceof String) {
                    writer.append("\n");
                    writer.append(indent).append(Strings.repeat(" ", printer.indentSpaces())).append("- \"").append(String.valueOf(e)).append("\"");
                } else if (e instanceof Serializable) {
                    writer.append(indent).append(Strings.repeat(" ", printer.indentSpaces())).append("- ");
                    String tempLineSeparator = printer.lineSeparator();
                    printer.lineSeparator("");
                    nodeToString(writer, new YAMLNode(null, e, null), level, printer);
                    printer.lineSeparator(tempLineSeparator);
                } else continue;
                writer.append("\n");
            }
            writer.append(printer.lineSeparator());
        }

        if (Map.class.isAssignableFrom(value.getClass())) {
            if (((Map<?, ?>) value).isEmpty()) {
                writer.append("{}\n");
                writer.append(printer.lineSeparator());
                return;
            }

            YAMLNode tempNode = new YAMLNode(null, null);
            ((Map<?, ?>) value).forEach((k, v) -> {
                Class<?> valueClass = v.getClass();

                if (!(k instanceof String key))
                    throw new RuntimeException("Declarative YAML requires that maps conform to one of the supported types: [" + FieldAssigner.supportedMaps + "]");
                if (!(Primitives.isPrimitive(clazz) || String.class.isAssignableFrom(valueClass) || Serializable.class.isAssignableFrom(valueClass)))
                    throw new RuntimeException("Declarative YAML requires that maps conform to one of the supported types: [" + FieldAssigner.supportedMaps + "]");

                tempNode.setGetChild(FieldAssigner.convertFieldNameToYAMLKey(key), new YAMLNode(FieldAssigner.convertFieldNameToYAMLKey(key), v, null));
            });

            if (current.name() != null) writer.append("\n");
            nodeToString(writer, tempNode, level + 1, printer);
        }
    }
}

record ConfigTarget(int order, String key, Object value, Field field, List<String> comment) {
    ConfigTarget(int order, @NotNull String key, @NotNull Object value, @Nullable Field field, @Nullable List<String> comment) {
        this.order = order;
        this.key = key;
        this.value = value;
        this.field = field;
        this.comment = comment;
    }
}
