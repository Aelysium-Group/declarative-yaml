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
     * This value should be an index defining the descending order of how you want nodes printed to your file.
     */
    int value() default 0;

    /**
     * The key to read.
     * You can climb up the yaml hierarchy by separating your keys with periods.
     */
    String key();
}
