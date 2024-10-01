package group.aelysium.declarative_yaml.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Pulls the value of a {@link Config} path parameter into the field.
 * The field must be of type {@link String}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface PathParameter {
    /**
     * The name of the path parameter to remote.
     */
    String value();
}
