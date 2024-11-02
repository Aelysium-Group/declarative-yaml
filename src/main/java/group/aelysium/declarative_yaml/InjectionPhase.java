package group.aelysium.declarative_yaml;

import group.aelysium.declarative_yaml.annotations.AllContents;
import group.aelysium.declarative_yaml.annotations.PathParameter;
import group.aelysium.declarative_yaml.lib.Printer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.CommentedConfigurationNode;

import java.io.File;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

class InjectionPhase {
    public static void injectConfigValueIntoClass(
            @NotNull Object instance,
            Printer printer,
            CommentedConfigurationNode yaml
    ) throws Exception {
        pathParameters(instance, printer);

        handleAllContents(instance, LoadingPhase.resolveFile(instance, printer));
        for (ConfigTarget target : InitializationPhase.generateConfigTargets(instance, printer)) {
            if(target.field() == null) continue;

            target.field().setAccessible(true);
            target.field().set(instance, getValueFromYAML(yaml, target));
            target.field().setAccessible(false);
        }
    }

    private static void pathParameters(@NotNull Object instance, @NotNull Printer printer) {
        Arrays.stream(instance.getClass().getDeclaredFields())
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

    private static Object getValueFromYAML(CommentedConfigurationNode root, ConfigTarget target) throws Exception {
        CommentedConfigurationNode node = getNodeFromYAML(root, target.key());

        return Deserializer.deserialize(node, target.field().getType(), target.field().getGenericType());
    }

    public static CommentedConfigurationNode getNodeFromYAML(CommentedConfigurationNode node, String route) throws Exception {
        String[] steps = route.split("\\.");

        AtomicReference<CommentedConfigurationNode> currentNode = new AtomicReference<>(node);
        Arrays.stream(steps).forEach(step -> currentNode.set(currentNode.get().node(step)));
        if (currentNode.get() == null) throw new NullPointerException("The node " + route + " is null.");

        return currentNode.get();
    }

    private static void handleAllContents(@NotNull Object instance, @NotNull File file) throws Exception {
        byte[] allContents = Files.readAllBytes(file.toPath());

        Arrays.stream(instance.getClass().getDeclaredFields())
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
}
