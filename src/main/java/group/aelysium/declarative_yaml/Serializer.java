package group.aelysium.declarative_yaml;

import group.aelysium.declarative_yaml.annotations.Comment;
import group.aelysium.declarative_yaml.lib.Primitives;
import group.aelysium.declarative_yaml.lib.Printer;
import group.aelysium.declarative_yaml.lib.Serializable;
import group.aelysium.declarative_yaml.lib.YAMLNode;
import org.spongepowered.configurate.util.Strings;

import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class Serializer {
    public static void serialize(FileWriter writer, YAMLNode nodeTree, Printer printer) throws Exception {
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
                String name = Deserializer.convertFieldNameToYAMLKey(f);
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

                String name = Deserializer.convertFieldNameToYAMLKey(component.getName());
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
                    throw new RuntimeException("Declarative YAML requires that maps conform to one of the supported types: [" + Deserializer.supportedMaps + "]");
                if (!(Primitives.isPrimitive(clazz) || String.class.isAssignableFrom(valueClass) || Serializable.class.isAssignableFrom(valueClass)))
                    throw new RuntimeException("Declarative YAML requires that maps conform to one of the supported types: [" + Deserializer.supportedMaps + "]");

                tempNode.setGetChild(Deserializer.convertFieldNameToYAMLKey(key), new YAMLNode(Deserializer.convertFieldNameToYAMLKey(key), v, null));
            });

            if (current.name() != null) writer.append("\n");
            nodeToString(writer, tempNode, level + 1, printer);
        }
    }
}
