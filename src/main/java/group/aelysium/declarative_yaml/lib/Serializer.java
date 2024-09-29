package group.aelysium.declarative_yaml.lib;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

class Serializer {
    public static void checkFieldTypes(Class<?> clazz) throws Exception {
        for (Field field : clazz.getDeclaredFields()) {
            Class<?> fieldType = field.getType();

            if (isPrimitiveOrWrapper(fieldType)) {
                continue;
            } else if (Map.class.isAssignableFrom(fieldType)) {
                checkMapField(field);
            } else if (List.class.isAssignableFrom(fieldType) || fieldType.isArray()) {
                checkCollectionField(field);
            } else if (YAMLSerializable.class.isAssignableFrom(fieldType)) {
                continue;
            } else {
                throw new Exception("Unsupported field type: " + fieldType.getName());
            }
        }
    }

    private static boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive() ||
                clazz == Boolean.class || clazz == Byte.class || clazz == Character.class ||
                clazz == Double.class || clazz == Float.class || clazz == Integer.class ||
                clazz == Long.class || clazz == Short.class;
    }

    private static void checkMapField(Field field) throws Exception {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericType;
            Type[] typeArguments = parameterizedType.getActualTypeArguments();
            if (typeArguments.length == 2 && typeArguments[0] == String.class) {
                Class<?> valueType = (Class<?>) typeArguments[1];
                if (isPrimitiveOrWrapper(valueType) || YAMLSerializable.class.isAssignableFrom(valueType)) {
                    return;
                }
            }
        }
        throw new Exception("Unsupported Map field: " + field.getName());
    }

    private static void checkCollectionField(Field field) throws Exception {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericType;
            Type[] typeArguments = parameterizedType.getActualTypeArguments();
            if (typeArguments.length == 1) {
                Class<?> elementType = (Class<?>) typeArguments[0];
                if (isPrimitiveOrWrapper(elementType) || YAMLSerializable.class.isAssignableFrom(elementType)) {
                    return;
                }
            }
        } else if (field.getType().isArray()) {
            Class<?> elementType = field.getType().getComponentType();
            if (isPrimitiveOrWrapper(elementType) || YAMLSerializable.class.isAssignableFrom(elementType)) {
                return;
            }
        }
        throw new Exception("Unsupported Collection field: " + field.getName());
    }
}
