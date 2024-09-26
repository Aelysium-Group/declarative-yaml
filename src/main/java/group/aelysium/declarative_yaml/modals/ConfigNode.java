package group.aelysium.declarative_yaml.modals;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ConfigNode {
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

    private final int order;
    private final List<String> comment;
    private final String key;
    private final Object value;
    private final Field field;
    public ConfigNode(int order, String key, String value, Field field, List<String> comment) {
        this.order = order;
        this.key = key;
        this.value = convertValue(value);
        this.field = field;
        this.comment = comment;
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

    public List<String> comment() {
        return this.comment;
    }
    public int order() {
        return this.order;
    }
}
