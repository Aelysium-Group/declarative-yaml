package group.aelysium.declarative_yaml;

import io.leangen.geantyref.TypeToken;
import org.jetbrains.annotations.NotNull;
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
     * An entry is defined as a non-static, final member, annotated with both {@link Comment} and {@link Node} or just a single Comment/Node annotation.<br/>
     * Comments and Nodes will be printed to the configuration in the order that you define in the annotations.
     * If a Comment and Node have the same order, the comment will be printed first.
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
     * Just like entries, the member must be non-static and final. The member must also be a byte[].
     * <h3>Path Parameters</h3>
     * Via the {@link PathParameter} annotation, you can extract specific values from the config path into a member.
     * Just like entries, the member used must be non-static and final. The member can be any type you want, the value in the path will attempt to be serialized into the type you specify.
     * <pre><code>{@code "/config/{identifier}.yml" }</code></pre>
     * Any value specified in place of {identifier} will be loaded into the defined member annotated with {@link PathParameter @PathParameter("identifier")}
     * In order to define what values to replace in the path, you can use the pathReplacements parameter in this method.
     * @param clazz The class definition of the config.
     * @param pathReplacements If the {@link Config#value()} has curly braces covered paths (i.e. "some/{dynamic}/path.yml", those will be replaced with the provided replacements.
     * @throws IOException If the config filepath contains invalid characters.
     * @throws ArrayIndexOutOfBoundsException If you don't provide the same number of pathReplacements as {path_parameters} in the @Config path.
     */
    public static <T> T load(Class<T> clazz, Map<String, String> pathReplacements) throws IOException, ArrayIndexOutOfBoundsException {
        if(!clazz.isAnnotationPresent(Config.class)) throw new RuntimeException("Configs must be annotated with @Config");

        Config config = clazz.getAnnotation(Config.class);

        String path;
        {
            AtomicInteger index = new AtomicInteger(0);
            List<String> splitPath = Arrays.stream(config.value().split("/")).map(v -> {
                if(!v.startsWith("{")) return v;
                String key = v.replaceAll("^.*\\{([a-zA-Z0-9\\_\\-\\.\\/\\\\]+)\\}.*","$1");

                String replacement = pathReplacements.get(key);
                if(replacement == null) throw new IllegalArgumentException("No value for the path key '"+key+"' exists!");
                System.out.println(replacement);
                index.incrementAndGet();
                return v.replaceAll("^\\{[a-zA-Z0-9\\_\\-\\.\\/\\\\]+\\}(\\.[a-zA-Z0-9\\_\\-]*)?",replacement+"$1");
            }).toList();
            path = String.join("/", splitPath);
            System.out.println(path);
        }
        if(!Pattern.compile("^[a-zA-Z0-9\\_\\-\\.\\/\\\\]+$").matcher(path).matches())
            throw new IOException("Invalid file path defined for config: "+path);

        Map<Integer, List<ConfigEntry>> entries = new HashMap<>();
        try {
            Comment comment = clazz.getAnnotation(Comment.class);
            enterValue(entries, new ConfigComment(Integer.MIN_VALUE, "", comment.value()));
        } catch (Exception ignore) {}

        List<Field> allContentsFields = new ArrayList<>();
        List<ConfigNode> pathParameters = new ArrayList<>();
        // Load all Comment and Entry annotations
        Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers())).toList()
                .forEach(f -> {
                    boolean hasComment = f.isAnnotationPresent(Comment.class);
                    boolean hasEntry = f.isAnnotationPresent(Node.class);
                    if(f.isAnnotationPresent(PathParameter.class)) {
                        PathParameter pathParameter = f.getAnnotation(PathParameter.class);
                        pathParameters.add(new ConfigNode(0, pathParameter.value(), pathReplacements.get(pathParameter.value()), f));
                        return;
                    }
                    if(f.isAnnotationPresent(AllContents.class)) {
                        allContentsFields.add(f);
                        return;
                    }
                    if(!(hasComment || hasEntry)) return;

                    if(hasEntry) {
                        Node node = f.getAnnotation(Node.class);
                        enterValue(entries, new ConfigNode(node.order(), node.key(), node.defaultValue(), f));

                        if(hasComment) {
                            Comment comment = f.getAnnotation(Comment.class);
                            enterValue(entries, new ConfigComment(node.order(), node.key(), comment.value()));
                        }
                    }
                });

        // Compile Comment and Entry annotations for file printing.
        List<ConfigEntry> sortedEntries = new ArrayList<>();
        {
            List<Map.Entry<Integer, List<ConfigEntry>>> list = new ArrayList<>(entries.entrySet());
            list.sort((entry1, entry2) -> entry2.getKey().compareTo(entry1.getKey()));

            list.forEach(entry -> sortedEntries.addAll(entry.getValue()));
        }

        // Construct the Java object with all the provided data
        List<ConfigNode> nodes = (List<ConfigNode>) (Object) sortedEntries.stream().filter(v -> v instanceof ConfigNode).toList();

        // Populate the object instance with the data.
        try {
            T instance = clazz.getConstructor().newInstance();

            // Generates the config if it doesn't exist then loads the contents of the config.
            CommentedConfigurationNode yaml = loadOrGenerate(path, sortedEntries);
            byte[] allContents = loadBytes(path);
            allContentsFields.forEach(f -> {
                try {
                    if (!f.getType().equals(byte[].class)) throw new ClassCastException("Fields annotated with @AllContents must be of type byte[]!");

                    f.setAccessible(true);
                    f.set(instance, allContents);
                    f.setAccessible(false);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            pathParameters.forEach(n -> {
                try {
                    n.field().setAccessible(true);
                    n.field().set(instance, n.value());
                    n.field().setAccessible(false);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            for (ConfigNode node : nodes) {
                Type type = node.field.getGenericType();
                node.field.setAccessible(true);
                node.field.set(instance, getValue(yaml, node.key(), TypeToken.get(type)));
                node.field.setAccessible(false);
            }
            return instance;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * Loads a configuration from a class definition.
     * Config class must be annotated with the {@link Config} annotation, and members must be marked with {@link Comment} and/or {@link Node} annotations.
     * If a method has the {@link Node} annotation, the value of the entry will be loaded into the actual method.
     * If you want to add naked comments, you can add the {@link Comment} annotation to an empty method.
     * @param clazz The class definition.
     * @throws IOException If the config filepath contains invalid characters.
     * @throws ArrayIndexOutOfBoundsException If you don't provide the same number of pathReplacements as {path_parameters} in the @Config path.
     */
    public static <T> T load(Class<T> clazz) throws IOException, ArrayIndexOutOfBoundsException {
        return load(clazz, Map.of());
    }

    /**
     * Retrieve data from a specific configuration node.
     * @param data The configuration data to search for a specific node.
     * @param node The node to search for.
     * @param type The type to convert the retrieved data to.
     * @return Data with a type matching `type`
     * @throws IllegalStateException If there was an issue while retrieving the data or converting it to `type`.
     */
    protected static <T> T getValue(CommentedConfigurationNode data, String node, TypeToken<T> type) throws IllegalStateException {
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

    protected static CommentedConfigurationNode loadOrGenerate(@NotNull String path, @NotNull List<ConfigEntry> contents) {
        File configPointer = new File(path);

        try {
            if (!configPointer.exists()) {
                File parent = configPointer.getParentFile();
                if(parent != null) if (!parent.exists()) parent.mkdirs();

                YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                        .file(configPointer)
                        .build();

                CommentedConfigurationNode root = loader.load(ConfigurationOptions.defaults());

                for (ConfigEntry entry : contents) {
                    if(entry instanceof ConfigNode node)
                        root.node(Arrays.stream(node.key().split("\\.")).toList()).set(node.value);
                    if(entry instanceof ConfigComment comment)
                        root.node(Arrays.stream(comment.key().split("\\.")).toList()).comment(String.join("\n", comment.value()));
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
    protected static byte[] loadBytes(@NotNull String path) {
        File configPointer = new File(path);

        try {
            return Files.readAllBytes(configPointer.toPath());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static void enterValue(Map<Integer, List<ConfigEntry>> entries, ConfigEntry entry) {
        entries.computeIfAbsent(entry.order(), k -> new ArrayList<>()).add(entry);
    }

    protected static abstract class ConfigEntry {
        private final int order;
        public ConfigEntry(int order) {
            this.order = order;
        }

        public int order() {
            return this.order;
        }
    }
    protected static class ConfigComment extends ConfigEntry {
        private final String key;
        private final List<String> value;
        public ConfigComment(int order, String key, String[] value) {
            super(order);
            this.key = key;
            this.value = Arrays.stream(value).map(s -> {
                if(s.startsWith("#") || s.isEmpty()) return s;
                if(s.startsWith(" ")) return "#"+s;
                return "# "+s;
            }).toList();
        }
        public String key() {
            return this.key;
        }
        public List<String> value() {
            return this.value;
        }
    }
    protected static class ConfigNode extends ConfigEntry {
        protected static Object convertValue(String value) {
            if(value.equals("true") || value.equals("false")) return value.equals("true");
            if(value.equals("[]")) return new ArrayList<>();
            try {
                return Integer.valueOf(value);
            } catch (Exception ignore) {}
            try {
                return Long.valueOf(value);
            } catch (Exception ignore) {}
            try {
                return Float.valueOf(value);
            } catch (Exception ignore) {}
            return value;
        }

        private final String key;
        private final Object value;
        private final Field field;
        public ConfigNode(int order, String key, String value, Field field) {
            super(order);
            this.key = key;
            this.value = convertValue(value);
            this.field = field;
        }

        public String key() {
            return this.key;
        }

        public Object value() {
            return this.value;
        }

        public Field field() {
            return this.field;
        }
    }
}
