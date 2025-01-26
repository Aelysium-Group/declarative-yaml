package group.aelysium.declarative_yaml.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sets the namespace for this configuration.
 * dYAML settings such as git and basepath are bound to namespace so this lets you target specific ones.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Namespace {
    String value();
}
