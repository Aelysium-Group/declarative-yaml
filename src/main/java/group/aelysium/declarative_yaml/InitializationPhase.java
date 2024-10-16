package group.aelysium.declarative_yaml;

import group.aelysium.declarative_yaml.annotations.*;
import group.aelysium.declarative_yaml.lib.Printer;
import group.aelysium.declarative_yaml.lib.YAMLNode;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles the loading of the config class members in preparation for (de)serialization.
 */
class InitializationPhase {
    public static YAMLNode nodesFromClass(Object instance, Printer printer) throws Exception {
        List<ConfigTarget> targets = generateConfigTargets(instance.getClass(), printer);
        return convertConfigTargetsToYAMLNodes(instance.getClass(), targets);
    }

    public static List<ConfigTarget> generateConfigTargets(@NotNull Object instance, Printer printer) {
        List<ConfigTarget> targets = new ArrayList<>();
        Arrays.stream(instance.getClass().getDeclaredFields())
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
                    if (key.isEmpty()) key = Deserializer.convertFieldNameToYAMLKey(f);

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
}
