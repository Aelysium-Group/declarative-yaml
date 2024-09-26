package group.aelysium.declarative_yaml.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Config {
    /**
     * The location of the config.
     * Points to the config's physical path on the machine.
     * If the config exists, it'll load the details.
     * If it doesn't exist, it'll be created.
     * @return The path of this config.
     */
    String value();

    /**
     * The separator to use when separating entries.
     * @return The entry separator.
     */
    String entrySeparator() default "\n\n";
}
