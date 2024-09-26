package group.aelysium.declarative_yaml;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Loads all bytes from the {@link Config}.
 * The field this annotation is attached to must be of type byte[] otherwise this will throw a {@link RuntimeException}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AllContents {
}
