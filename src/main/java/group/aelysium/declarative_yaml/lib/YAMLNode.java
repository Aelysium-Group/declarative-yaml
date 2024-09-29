package group.aelysium.declarative_yaml.lib;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class YAMLNode {
    private final String name;
    private final Object value;
    private final List<YAMLNode> children;
    private List<String> comment;

    public YAMLNode(@NotNull String name, @Nullable Object value, @Nullable List<String> comment) {
        this.name = name;
        this.value = value;
        this.children = null;
        this.comment = comment;
    }

    public YAMLNode(@NotNull String name, @Nullable List<String> comment) {
        this.name = name;
        this.value = null;
        this.children = new ArrayList<>();
        this.comment = comment;
    }

    public String name() {
        return this.name;
    }

    public Optional<Object> value() {
        return Optional.ofNullable(this.value);
    }
    public Optional<String> stringifiedValue() {
        if(this.value == null) return Optional.empty();

        if(this.value instanceof String) return Optional.of("\""+this.value+"\"");

        return Optional.of(this.value.toString());
    }

    public Optional<List<YAMLNode>> children() {
        return Optional.ofNullable(this.children);
    }

    /**
     * Gets the child if it exists.
     * If it doesn't exist it's created and then returned.
     * The only exception is if this Node is a value node, in which case it can't hold children.
     *
     * @param key  The key to look for.
     * @param node The node to add if the key doesn't exist.
     * @return A Node, no matter what.
     * @throws NullPointerException If this Node has a value, in which case it won't have any children.
     */
    public @NotNull YAMLNode setGetChild(@NotNull String key, @NotNull YAMLNode node) throws NullPointerException {
        if (this.children == null)
            throw new NullPointerException("This node contains a value and isn't allowed to have children!");

        Optional<YAMLNode> fetched = this.child(key);
        if (fetched.isPresent()) return fetched.orElseThrow();

        this.children.add(node);
        return node;
    }

    public Optional<YAMLNode> child(String name) {
        if (this.children == null) return Optional.empty();
        return this.children.stream().filter(n -> n.name.equals(name)).findFirst();
    }

    public void child(YAMLNode node) {
        if (this.children == null) return;
        this.children.add(node);
    }

    public Optional<List<String>> comment() {
        return Optional.ofNullable(this.comment);
    }

    public void comment(List<String> comment) {
        this.comment = comment;
    }
}
