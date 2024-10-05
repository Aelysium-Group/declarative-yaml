package group.aelysium.declarative_yaml;

import group.aelysium.declarative_yaml.annotations.Node;
import group.aelysium.declarative_yaml.lib.Primitives;
import group.aelysium.declarative_yaml.lib.Serializable;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;

import java.lang.reflect.*;
import java.util.*;

class FieldAssigner {
    public static final String supportedMaps = String.join(", ", List.of(
            "Map<String, Primitive>",
            "Map<String, String>",
            "Map<String, Serializable>"
    ));
    public static final String supportedTypes = String.join(", ", List.of(
            "Primitive",
            "String",
            "Serializable",
            "Enums",
            "Java Records",
            "List<Primitive>",
            "List<String>",
            "List<Serializable",
            "Set<Primitive>",
            "Set<String>",
            "Set<Serializable>",
            supportedMaps
    ));

    public static Object serialize(CommentedConfigurationNode node, Class<?> clazz, Type type) throws Exception {
        if (Primitives.isPrimitive(clazz)) return FieldAssigner.serializePrimitive(node, clazz);
        if (String.class.isAssignableFrom(clazz)) return FieldAssigner.serializeString(node);
        if (Serializable.class.isAssignableFrom(clazz)) return FieldAssigner.serializeObject(node, clazz);
        if (clazz.isEnum()) {
            @SuppressWarnings("unchecked") final var enumType = (Class<? extends Enum<?>>) clazz;
            return FieldAssigner.serializeEnum(node, enumType);
        }
        if (Record.class.isAssignableFrom(clazz)) return FieldAssigner.serializeRecord(node, clazz);

        if (!(type instanceof ParameterizedType parameterizedType))
            throw new RuntimeException(clazz.getSimpleName() + " is not a type that's supported by Declarative YAML. Supported types are: " + supportedTypes);

        if (List.class.isAssignableFrom(clazz) && node.isList())
            return FieldAssigner.serializeList(node, clazz, parameterizedType);
        if (Set.class.isAssignableFrom(clazz) && node.isList())
            return FieldAssigner.serializeSet(node, clazz, parameterizedType);
        if (Map.class.isAssignableFrom(clazz) && node.isMap())
            return FieldAssigner.serializeMap(node, clazz, parameterizedType);

        throw new RuntimeException(clazz.getSimpleName() + " is not a type that's supported by Declarative YAML. Supported types are: " + supportedTypes);
    }


    private static <T> T serializePrimitive(CommentedConfigurationNode node, Class<T> clazz) throws Exception {
        return node.get(clazz);
    }

    private static String serializeString(CommentedConfigurationNode node) {
        return node.getString();
    }

    private static Object serializeObject(CommentedConfigurationNode objectRoot, Class<?> clazz) throws Exception {
        try {
            List<Field> fields = Arrays.stream(clazz.getFields()).filter(f -> Modifier.isFinal(f.getModifiers()) && f.isAnnotationPresent(Node.class)).toList();
            clazz.getConstructor().setAccessible(true);
            Object instance = clazz.getConstructor().newInstance();
            clazz.getConstructor().setAccessible(false);

            for (Field f : fields) {
                boolean isOptional = f.isAnnotationPresent(Nullable.class);

                f.setAccessible(true);

                try {
                    String key = convertFieldNameToYAMLKey(f);
                    CommentedConfigurationNode node = DeclarativeYAML.getNodeFromYAML(objectRoot, key);
                    f.set(instance, serialize(node, f.getType(), f.getGenericType()));
                } catch (Exception e) {
                    if (isOptional) {
                        f.set(instance, null);
                        f.setAccessible(false);
                        continue;
                    }

                    f.setAccessible(false);
                    throw e;
                }
                f.setAccessible(false);
            }

            return instance;
        } catch (Exception e) {
            throw new Exception(e);
        }
    }

    private static Object serializeEnum(CommentedConfigurationNode node, Class<? extends Enum<?>> clazz) {
        String value = node.getString();

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Enum value is missing for " + clazz.getSimpleName());
        }

        try {
            return Enum.valueOf(clazz.asSubclass(Enum.class), value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid enum value '" + value + "' for enum " + clazz.getSimpleName() + ". Supported values are: " +
                    Arrays.toString(clazz.getEnumConstants()), e);
        }
    }

    private static Object serializeRecord(CommentedConfigurationNode node, Class<?> clazz) throws Exception {
        RecordComponent[] components = clazz.getRecordComponents();
        Map<String, Object> values = new HashMap<>();

        for (RecordComponent component : components) {
            String key = FieldAssigner.convertFieldNameToYAMLKey(component.getName());
            CommentedConfigurationNode componentNode = node.node(key);
            values.put(component.getName(), FieldAssigner.serialize(componentNode, component.getType(), component.getGenericType()));
        }

        Object[] valuesArray = Arrays.stream(components)
                .map(component -> values.get(component.getName()))
                .toArray();

        Constructor<?> constructor = clazz.getDeclaredConstructor(
                Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new)
        );
        constructor.setAccessible(true);
        Object instance = constructor.newInstance(valuesArray);
        constructor.setAccessible(false);

        return instance;
    }

    private static List<Object> serializeList(CommentedConfigurationNode node, Class<?> clazz, ParameterizedType type) throws Exception {
        Type entryType = type.getActualTypeArguments()[0];
        Class<?> entryClass = (Class<?>) type.getActualTypeArguments()[0];

        if (!(Primitives.isPrimitive(entryClass) || String.class.isAssignableFrom(entryClass) || Serializable.class.isAssignableFrom(entryClass)))
            throw new ClassCastException(clazz.getSimpleName() + " isn't a supported type in Declarative YAML. The supported types are: " + supportedTypes);

        List<Object> list = new ArrayList<>();
        for (CommentedConfigurationNode entry : node.childrenList())
            list.add(serialize(entry, entryClass, entryType));
        return Collections.unmodifiableList(list);
    }

    private static Set<Object> serializeSet(CommentedConfigurationNode node, Class<?> clazz, ParameterizedType type) throws Exception {
        Type entryType = type.getActualTypeArguments()[0];
        Class<?> entryClass = (Class<?>) type.getActualTypeArguments()[0];

        if (!(Primitives.isPrimitive(entryClass) || String.class.isAssignableFrom(entryClass) || Serializable.class.isAssignableFrom(entryClass)))
            throw new ClassCastException(clazz.getSimpleName() + " isn't a supported type in Declarative YAML. The supported types are: " + supportedTypes);

        Set<Object> set = new HashSet<>();
        for (CommentedConfigurationNode entry : node.childrenList())
            set.add(serialize(entry, entryClass, entryType));
        return Collections.unmodifiableSet(set);
    }

    private static Map<String, ?> serializeMap(CommentedConfigurationNode node, Class<?> clazz, ParameterizedType type) throws Exception {
        Type keyType = type.getActualTypeArguments()[0];
        Type valueType = type.getActualTypeArguments()[1];
        Class<?> keyClass = (Class<?>) type.getActualTypeArguments()[0];
        Class<?> valueClass = (Class<?>) type.getActualTypeArguments()[1];

        if (!(String.class.isAssignableFrom(keyClass)))
            throw new ClassCastException(clazz.getSimpleName() + " isn't a supported type in Declarative YAML. The supported types are: " + supportedTypes);
        if (!(Primitives.isPrimitive(valueClass) || String.class.isAssignableFrom(valueClass) || Serializable.class.isAssignableFrom(valueClass)))
            throw new ClassCastException(clazz.getSimpleName() + " isn't a supported type in Declarative YAML. The supported types are: " + supportedTypes);

        Map<String, Object> map = new HashMap<>();

        for (Map.Entry<Object, CommentedConfigurationNode> entry : node.childrenMap().entrySet()) {
            if (!(entry.getKey() instanceof String key))
                throw new IllegalArgumentException("Declarative YAML requires that maps loaded from YAML must be of the type [" + supportedMaps + "].");

            map.put(key, serialize(entry.getValue(), valueClass, valueType));
        }

        return Collections.unmodifiableMap(map);
    }

    public static String convertFieldNameToYAMLKey(String name) {
        return String.join(".", Arrays.stream(name.split("_")).map(s -> s.replaceAll("([a-z])([A-Z]+)", "$1-$2").toLowerCase()).toList());
    }

    public static String convertFieldNameToYAMLKey(Field field) {
        return convertFieldNameToYAMLKey(field.getName());
    }
}
