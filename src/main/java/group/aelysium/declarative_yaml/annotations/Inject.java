package group.aelysium.declarative_yaml.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the following class into an already existing config.
 * This annotation should be used in-place of the {@link Config} annotation when you have
 * an already existing config that you want to add or read values from.
 * This is especially helpful for when you're working with other software's that add their own configs.</br></br>
 * The main difference between Injections and {@link Config} is that Injections will only work if the config exists already.
 * Otherwise, loading the class annotated with Inject will throw an exception.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Inject {
    /**
     * The location of the config.
     * Points to the config's physical path on the machine.
     * If the config exists, it'll load the details.
     * If it doesn't exist, it'll be created.
     * @return The path of this config.
     */
    String value();
}
