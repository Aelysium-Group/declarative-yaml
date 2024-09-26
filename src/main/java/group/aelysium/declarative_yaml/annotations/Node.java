package group.aelysium.declarative_yaml.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
/**
 * Programmatically pulls data from the {@link Config} and stores it in the associated field.
 * The whatever that type of data is will be inferred from the field type.
 */
public @interface Node {
    /**
     * The order of this entry.
     * This value manages how the elements will appear in the config.
     */
    int order() default 0;

    /**
     * The key to read.
     */
    String key();

    /**
     * The default value if the entry doesn't exist.
     * The value will attempt to be parsed to other data types if it's supported.<br/>
     * Supported values are:<br/>
     * - 'boolean' = "true", "false"
     * - 'int' = "1", "-230", etc.<br/>
     * - 'long' = "1L", "-230L", etc.<br/>
     * - `float` = "1.2", "-230.5", etc.<br/>
     * - `empty array` = "[]"<br/>
     * - `string` = If none of the above converts work, string is the default.
     */
    String defaultValue();
}
