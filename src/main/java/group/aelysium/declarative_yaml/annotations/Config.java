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
     * If it doesn't exist, it'll be created.<br/><br/>
     * If this config is using Git (e.g. has the {@link Git @Git} annotation), this path should be the location of the config within the git repository.
     * Where "/" is the root directory in the git repository.
     * @return The path of this config.
     */
    String value();
}
