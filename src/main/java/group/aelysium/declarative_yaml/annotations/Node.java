package group.aelysium.declarative_yaml.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Programmatically pulls data from the {@link Config} and stores it in the associated field.
 * The whatever that type of data is will be inferred from the field type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Node {
    /**
     * The order of this entry.
     * This value should be an index defining the descending order of how you want nodes printed to your file.
     */
    int value() default 0;

    /**
     * The key to read.
     * You can climb up the yaml hierarchy by separating your keys with periods.
     * If you leave this empty, the key will be parsed from the class field's name.
     * In order to successfully use the class field name as the YAML key.
     * Make sure you format the name using a blend of camelCase (node names) and snakeCase (node nesting):<br/>
     * <code>nodeOne_nodeTwo_nodeThree</code><br/>
     * Will be parsed into:<br/>
     * <code>node-one.node-two.node-three</code><br/>
     * Or rather<br/>
     * <pre><code>
     * node-one:
     *     node-two:
     *          node-three:
     * </code></pre>
     */
    String key() default "";
}
