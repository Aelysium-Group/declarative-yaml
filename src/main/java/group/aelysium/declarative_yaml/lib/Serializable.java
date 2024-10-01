package group.aelysium.declarative_yaml.lib;

import java.lang.reflect.Constructor;

public abstract class Serializable {
    protected Serializable() {
        boolean hasNoParamConstructor = false;
        Constructor<?>[] constructors = this.getClass().getDeclaredConstructors();
        for (Constructor<?> constructor : constructors) {
            if (constructor.getParameterCount() == 0) {
                hasNoParamConstructor = true;
                break;
            }
        }
        if (!hasNoParamConstructor) throw new IllegalStateException("Serializable classes must have a no-parameter constructor.");
    }
}
