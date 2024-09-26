package group.aelysium.declarative_yaml;

import group.aelysium.declarative_yaml.annotations.*;
import group.aelysium.declarative_yaml.modals.ConfigNode;
import io.leangen.geantyref.TypeToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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
     * To replace a target, you can define its name inside the commentReplacements parameter.
     * @param clazz The class definition of the config.
     * @param commentReplacements A list of keys to replace with values for any comments that contain a target matching key.
     * @param pathReplacements If the {@link Config#value()} has curly braces covered paths (i.e. "some/{dynamic}/path.yml", those will be replaced with the provided replacements.
     * @throws IOException If the config filepath contains invalid characters.
     * @throws ArrayIndexOutOfBoundsException If you don't provide the same number of pathReplacements as {path_parameters} in the @Config path.
     */
    public static <T> T load(@NotNull Class<T> clazz, @Nullable Map<String, String> commentReplacements, @Nullable Map<String, String> pathReplacements) throws IOException, ArrayIndexOutOfBoundsException {
        if(!clazz.isAnnotationPresent(Config.class)) throw new RuntimeException("Configs must be annotated with @Config");

        Config config = clazz.getAnnotation(Config.class);

        String path = parsePath(config.value(), pathReplacements);
        try {
            T instance = clazz.getConstructor().newInstance();

            List<ConfigNode> nodes = generateEntries(clazz);
            pathParameters(clazz, instance, pathReplacements == null ? Map.of() : pathReplacements);
            handleAllContents(clazz, instance, path);

            // Generates the config if it doesn't exist then loads the contents of the config.
            CommentedConfigurationNode yaml = loadOrGenerateFile(path, nodes);

            for (ConfigNode node : nodes) {
                Type type = node.field().getGenericType();
                node.field().setAccessible(true);
                node.field().set(instance, getValueFromNode(yaml, node.key(), TypeToken.get(type)));
                node.field().setAccessible(false);
            }
            return instance;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static List<ConfigNode> generateEntries(@NotNull Class<?> clazz) {
        Map<Integer, List<ConfigNode>> entries = new HashMap<>();
        try {
            Comment comment = clazz.getAnnotation(Comment.class);
            enterValue(entries, new ConfigNode(Integer.MIN_VALUE, "", "", null, Arrays.asList(comment.value())));
        } catch (Exception ignore) {}

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
                        comment = Arrays.asList(c.value());
                    }

                    enterValue(entries, new ConfigNode(node.order(), node.key(), node.defaultValue(), f, comment));
                });

        List<ConfigNode> sortedEntries = new ArrayList<>();
        {
            List<Map.Entry<Integer, List<ConfigNode>>> list = new ArrayList<>(entries.entrySet());
            list.sort((entry1, entry2) -> entry2.getKey().compareTo(entry1.getKey()));

            list.forEach(entry -> sortedEntries.addAll(entry.getValue()));
        }

        return sortedEntries;
    }

    protected static void pathParameters(@NotNull Class<?> clazz, @NotNull Object instance, @NotNull Map<String, String> pathReplacements) {
        Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers())).toList()
                .forEach(f -> {
                    if(!f.isAnnotationPresent(PathParameter.class)) return;

                    PathParameter pathParameter = f.getAnnotation(PathParameter.class);
                    try {
                        f.setAccessible(true);
                        f.set(instance, pathReplacements.get(pathParameter.value()));
                        f.setAccessible(false);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    protected static void handleAllContents(@NotNull Class<?> clazz, @NotNull Object instance, @NotNull String path) throws Exception {
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
    protected static <T> T getValueFromNode(CommentedConfigurationNode data, String node, TypeToken<T> type) throws IllegalStateException {
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
            //throw new IllegalStateException("Unable to register the node: "+node);
        }
    }

    protected static CommentedConfigurationNode loadOrGenerateFile(@NotNull String path, @NotNull List<ConfigNode> nodes) {
        File configPointer = new File(path);

        try {
            if (!configPointer.exists()) {
                File parent = configPointer.getParentFile();
                if(parent != null) if (!parent.exists()) parent.mkdirs();

                YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                        .file(configPointer)
                        .build();

                CommentedConfigurationNode root = loader.load(ConfigurationOptions.defaults());

                for (ConfigNode node : nodes) {
                    if(node.comment() != null)
                        root.node(Arrays.stream(node.key().split("\\.")).toList()).comment(String.join("\n", node.comment()));

                    root.node(Arrays.stream(node.key().split("\\.")).toList()).set(node.value());
                }
                loader.save(root);
            }

            return YamlConfigurationLoader.builder()
                    .indent(2)
                    .path(configPointer.toPath())
                    .build().load();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static void enterValue(Map<Integer, List<ConfigNode>> entries, ConfigNode entry) {
        entries.computeIfAbsent(entry.order(), k -> new ArrayList<>()).add(entry);
    }

    protected static String parsePath(String originalPath, Map<String, String> pathReplacements) throws IOException {
        String path;
        {
            AtomicInteger index = new AtomicInteger(0);
            List<String> splitPath = Arrays.stream(originalPath.split("/")).map(v -> {
                if(!v.startsWith("{")) return v;
                String key = v.replaceAll("^.*\\{([a-zA-Z0-9\\_\\-\\.\\/\\\\]+)\\}.*","$1");

                String replacement = pathReplacements.get(key);
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
