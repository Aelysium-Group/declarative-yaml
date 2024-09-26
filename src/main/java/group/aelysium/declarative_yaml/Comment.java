package group.aelysium.declarative_yaml;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows you to programmatically add YAML comments to your {@link Config}.
 * Comments only work if they're added to an already existing {@link Node}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.TYPE })
public @interface Comment {
    /**
     * The comment to show.
     * Each new array element will be put on a newline.
     * If an entry is provided without a leading # character, one will be added.
     */
    String[] value();
}
