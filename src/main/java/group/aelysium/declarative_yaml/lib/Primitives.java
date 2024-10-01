package group.aelysium.declarative_yaml.lib;

public interface Primitives {
    /**
     * Checks if the class is of type primitive or a primitive wrapper such as {@link Integer} or {@link Long}.
     * @param clazz The class to check.
     * @return `true` if the class is a primitive or primitive wrapper. `false` otherwise.
     */
    static boolean isPrimitive(Class<?> clazz) {
        return clazz.isPrimitive() ||
                Boolean.class.isAssignableFrom(clazz) ||
                Character.class.isAssignableFrom(clazz) ||
                Byte.class.isAssignableFrom(clazz) ||
                Short.class.isAssignableFrom(clazz) ||
                Integer.class.isAssignableFrom(clazz) ||
                Long.class.isAssignableFrom(clazz) ||
                Float.class.isAssignableFrom(clazz) ||
                Double.class.isAssignableFrom(clazz) ||
                Void.class.isAssignableFrom(clazz);
    }
}