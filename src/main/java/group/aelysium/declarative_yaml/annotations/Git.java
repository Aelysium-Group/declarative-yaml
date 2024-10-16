package group.aelysium.declarative_yaml.annotations;

import group.aelysium.declarative_yaml.GitOperator;
import group.aelysium.declarative_yaml.DeclarativeYAML;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Should the config be backed by GitOps instead of a physical file.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Git {
    /**
     * The url of the git repository to target.
     * This should be the same URL as the one used to create the {@link GitOperator.Config Git Config}.
     * Make sure that before you attempt to load any configs using this annotation, that you first properly register the Git Config using {@link DeclarativeYAML#registerRepository(GitOperator.Config)}.
     */
    String value();

    /**
     * Is Git Ops required for this config?
     * If enabled, the config will not load if a Git Operator wasn't registered with the same name as value.
     * If disabled, the path of {@link Config} will instead be used to generate the config file, and this annotation will simply be ignored.
     */
    boolean required() default true;
}
