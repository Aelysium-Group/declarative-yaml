package group.aelysium.declarative_yaml;

import group.aelysium.declarative_yaml.annotations.*;
import group.aelysium.declarative_yaml.lib.Printer;
import group.aelysium.declarative_yaml.lib.YAMLNode;
import io.leangen.geantyref.TypeToken;
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
     * The value itself will be serialized using {@link TypeToken} into whatever the member is.
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
     * @param clazz The class definition of the config.
     * @param printer The printer configuration to use.
     * @throws IOException If the config filepath contains invalid characters.
     * @throws ArrayIndexOutOfBoundsException If you don't provide the same number of pathReplacements as {path_parameters} in the @Config path.
     */
    public static <T> T load(@NotNull Class<T> clazz, @NotNull Printer printer) throws IOException, ArrayIndexOutOfBoundsException {
        printer.injecting(clazz.isAnnotationPresent(Inject.class));

        if(!(clazz.isAnnotationPresent(Config.class) || !printer.injecting()))
            throw new RuntimeException("Config class declarations must be annotated with either @Config or @Inject.");

        String configPath;
        {
            if(printer.injecting()) configPath = clazz.getAnnotation(Inject.class).value();
            else configPath = clazz.getAnnotation(Config.class).value();
        }

        String path = parsePath(configPath, printer);
        try {
            T instance = clazz.getConstructor().newInstance();

            pathParameters(clazz, instance, printer);

            List<ConfigTarget> targets = generateConfigTargets(clazz, printer);
            YAMLNode nodeTree = convertConfigTargetsToYAMLNodes(clazz, targets);

            CommentedConfigurationNode yaml = loadOrGenerateFile(path, nodeTree, printer);

            handleAllContents(clazz, instance, path);
            for (ConfigTarget node : targets) {
                if(node.field() == null) continue;
                Type type = node.field().getGenericType();
                node.field().setAccessible(true);
                node.field().set(instance, getValueFromYAML(yaml, node.key(), TypeToken.get(type)));
                node.field().setAccessible(false);
            }
            return instance;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T load(@NotNull Class<T> clazz) throws IOException, ArrayIndexOutOfBoundsException {
        return load(clazz, new Printer());
    }

    private static List<ConfigTarget> generateConfigTargets(@NotNull Class<?> clazz, Printer printer) {
        Map<Integer, List<ConfigTarget>> toBeSorted = new HashMap<>();
        Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers())).toList()
                .forEach(f -> {
                    boolean hasComment = f.isAnnotationPresent(Comment.class);
                    boolean hasEntry = f.isAnnotationPresent(Node.class);
                    if(!(hasComment || hasEntry)) return;

                    Node node = f.getAnnotation(Node.class);

                    List<String> comment = null;
                    if(hasComment) {
                        Comment c = f.getAnnotation(Comment.class);
                        comment = new ArrayList<>();
                        for (String s : c.value()) {
                            AtomicReference<String> correctedString = new AtomicReference<>(s);
                            printer.commentReplacements().forEach((k, v) -> correctedString.set(correctedString.get().replace("{"+k+"}", v)));
                            comment.add(correctedString.get());
                        }
                    }

                    Object defaultValue = null;
                    try {
                        defaultValue = f.get(f.getType());
                    } catch (Exception ignore) {}
                    if(defaultValue == null) throw new NullPointerException("You must define a default value on entries annotated with @Node");
                    toBeSorted.computeIfAbsent(node.value(), k -> new ArrayList<>()).add(new ConfigTarget(node.value(), node.key(), defaultValue, f, comment));
                });

        List<ConfigTarget> sorted = new ArrayList<>();
        {
            List<Map.Entry<Integer, List<ConfigTarget>>> list = new ArrayList<>(toBeSorted.entrySet());
            list.sort((entry1, entry2) -> entry2.getKey().compareTo(entry1.getKey()));

            list.forEach(entry -> sorted.addAll(entry.getValue()));
            Collections.reverse(sorted);
        }

        return sorted;
    }

    private static YAMLNode convertConfigTargetsToYAMLNodes(@NotNull Class<?> clazz, List<ConfigTarget> targets) {
        YAMLNode root;

        try {
            Comment comment = clazz.getAnnotation(Comment.class);
            root = new YAMLNode("", Arrays.asList(comment.value()));
        } catch (Exception ignore) {
            root = new YAMLNode("", null);
        }

        for (ConfigTarget parsingTarget : targets) {
            String[] keys = parsingTarget.key().split("\\.");
            AtomicReference<YAMLNode> currentNode = new AtomicReference<>(root);
            for (int i = 0; i < keys.length; i++) {
                String key = keys[i];
                boolean lastKey = i == keys.length - 1;

                YAMLNode newCurrent = currentNode.get().setGetChild(
                        key,
                        lastKey ? new YAMLNode(key, parsingTarget.value(), parsingTarget.comment()) : new YAMLNode(key, null)
                );
                currentNode.set(newCurrent);
            }
        }

        return root;
    }

    private static void pathParameters(@NotNull Class<?> clazz, @NotNull Object instance, @NotNull Printer printer) {
        Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers())).toList()
                .forEach(f -> {
                    if(!f.isAnnotationPresent(PathParameter.class)) return;

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
                    if(!f.isAnnotationPresent(AllContents.class)) return;

                    try {
                        if (!f.getType().equals(byte[].class)) throw new ClassCastException("Fields annotated with @AllContents must be of type byte[]!");

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
     * @param data The configuration data to search for a specific node.
     * @param node The node to search for.
     * @param type The type to convert the retrieved data to.
     * @return Data with a type matching `type`
     * @throws IllegalStateException If there was an issue while retrieving the data or converting it to `type`.
     */
    private static <T> T getValueFromYAML(CommentedConfigurationNode data, String node, TypeToken<T> type) throws IllegalStateException {
        try {
            String[] steps = node.split("\\.");

            final CommentedConfigurationNode[] currentNode = {data};
            Arrays.stream(steps).forEach(step -> {
                currentNode[0] = currentNode[0].node(step);
            });

            if(currentNode[0] == null) throw new NullPointerException();

            return currentNode[0].get(type);
        } catch (NullPointerException e) {
            throw new IllegalStateException("The node ["+node+"] doesn't exist!");
        } catch (ClassCastException e) {
            throw new IllegalStateException("The node ["+node+"] is of the wrong data type! Make sure you are using the correct type of data!");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static CommentedConfigurationNode loadOrGenerateFile(@NotNull String path, @NotNull YAMLNode nodeTree, Printer printer) {
        File configPointer = new File(path);

        try {
            if (!configPointer.exists()) {
                if(printer.injecting()) throw new IOException("Attempted to inject into a config that doesn't exist! "+path);

                File parent = configPointer.getParentFile();
                if(parent != null) if (!parent.exists()) parent.mkdirs();
                configPointer.createNewFile();

                try(FileWriter writer = new FileWriter(configPointer)) {
                    YAMLPrinter.format(writer, nodeTree, printer);
                }
            }

            if(printer.injecting()) {
                try(FileWriter writer = new FileWriter(configPointer)) {
                    YAMLPrinter.format(writer, nodeTree, printer);
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
                if(!v.startsWith("{")) return v;
                String key = v.replaceAll("^.*\\{([a-zA-Z0-9\\_\\-\\.\\/\\\\]+)\\}.*","$1");

                String replacement = printer.pathReplacements().get(key);
                if(replacement == null) throw new IllegalArgumentException("No value for the path key '"+key+"' exists!");
                index.incrementAndGet();
                return v.replaceAll("^\\{[a-zA-Z0-9\\_\\-\\.\\/\\\\]+\\}(\\.[a-zA-Z0-9\\_\\-]*)?",replacement+"$1");
            }).toList();
            path = String.join("/", splitPath);
        }
        if(!Pattern.compile("^[a-zA-Z0-9\\_\\-\\.\\/\\\\]+$").matcher(path).matches())
            throw new IOException("Invalid file path defined for config: "+path);

        return path;
    }
}

class YAMLPrinter {
    public static void format(FileWriter writer, YAMLNode nodeTree, Printer printer) throws Exception {
        nodeToYAML(writer, nodeTree, -1, printer);
    }

    private static void nodeToYAML(FileWriter writer, YAMLNode current, int level, Printer printer) throws Exception {
        String indent = Strings.repeat(" ", level < 0 ? 0 : level * printer.indentSpaces());

        if (current.children().isPresent()) {
            if(level >= 0) writer.append(indent).append(current.name()).append(":\n");
            for (YAMLNode node : current.children().orElseThrow())
                nodeToYAML(writer, node, level + 1, printer);
            return;
        }

        for (String s : current.comment().orElse(List.of())) {
            if (printer.indentComments()) writer.append(indent);

            System.out.println(s);
            // Add comment hashtag if one wasn't given.
            if(!s.startsWith("#")) writer.append("# ");

            writer.append(s);
            writer.append("\n");
        }
        if(!current.name().isEmpty())
            writer.append(indent)
                .append(current.name())
                .append(": ")
                .append(current.stringifiedValue().orElse(""))
                .append("\n");

        writer.append(printer.lineSeparator());
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