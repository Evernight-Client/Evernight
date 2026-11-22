package evernight.client.config.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import evernight.client.settings.core.NamedEntry;
import evernight.client.utils.core.ExperimentalFeatures;

public final class EnumJsonConverter<E extends Enum<E>> {
    private final Class<E> enumClass;

    public EnumJsonConverter(Class<E> enumClass) {
        this.enumClass = enumClass;
    }

    public static int findSelectableIndex(Enum value) {
        int index = 0;
        while (true) {
            Class classForLength = value.getDeclaringClass();
            if (index >= classForLength.getEnumConstants().length) {
                return -1;
            }
            Class classForRead = value.getDeclaringClass();
            Enum candidate = ((Enum[]) classForRead.getEnumConstants())[index];
            if (!ExperimentalFeatures.isEnumConstantHidden(candidate)) {
                String candidateName = candidate.name();
                String valueName = value.name();
                if (candidateName.equalsIgnoreCase(valueName)) {
                    return index;
                }
            }
            index++;
        }
    }

    public static Enum nextValue(Enum value) {
        int currentIndex = findSelectableIndex(value);
        int index = 0;
        while (true) {
            Class classForLength = value.getDeclaringClass();
            if (index >= classForLength.getEnumConstants().length) {
                Class classForFallback = value.getDeclaringClass();
                return (Enum) classForFallback.getEnumConstants()[0];
            }
            Class classForRead = value.getDeclaringClass();
            Enum candidate = (Enum) classForRead.getEnumConstants()[index];
            if (!ExperimentalFeatures.isEnumConstantHidden(candidate) && index > currentIndex) {
                return candidate;
            }
            index++;
        }
    }

    public static String getName(Enum value) {
        if (value instanceof NamedEntry namedValue) {
            return namedValue.getName();
        } else {
            throw new IllegalArgumentException();
        }
    }

    public JsonElement toJson(Enum value) {
        if (value instanceof NamedEntry namedValue) {
            return new JsonPrimitive(namedValue.getName());
        } else {
            throw new IllegalArgumentException();
        }
    }

    public E fromJson(JsonElement json) {
        for (E candidate : this.enumClass.getEnumConstants()) {
            if (!(candidate instanceof NamedEntry namedCandidate)) {
                throw new IllegalArgumentException();
            }
            if (!ExperimentalFeatures.isEnumConstantHidden(candidate)) {
                String jsonName = json.getAsString();
                String candidateName = namedCandidate.getName();
                if (jsonName.equalsIgnoreCase(candidateName)) {
                    return candidate;
                }
            }
        }
        return this.enumClass.getEnumConstants()[0];
    }
}