package group.aelysium.declarative_yaml.annotations;

import group.aelysium.declarative_yaml.GitOperator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the config as live.<br/>
 * Any time the physical file is updated, the corresponding config instance will update as well.<br/><br/>
 * If this config is using Git (e.g. has the {@link Git @Git} annotation), this option will automatically pull from the repository every n number of seconds and then update the config instance (the amount of time between pulls is configurable via the {@link GitOperator.Config})
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Live {
    /**
     * If the config reloading causes an exception to throw,
     * should the exception just be ignored and instead just keep
     * the config how it was before the update was made.
     */
    boolean rollback() default false;
}
