package com.walmartlabs.concord.common.format;

public enum DefinitionType {

    CONCORD_YAML("concord/yaml", "yml", ".*\\.(yml|yaml)$");

    private final String type;
    private final String extension;
    private final String mask;

    DefinitionType(String type, String extension, String mask) {
        this.type = type;
        this.extension = extension;
        this.mask = mask;
    }

    public String getType() {
        return type;
    }

    public String getExtension() {
        return extension;
    }

    public String getMask() {
        return mask;
    }

    public static DefinitionType getByType(String type) {
        for (DefinitionType t : values()) {
            if (type.equals(t.type)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown type: " + type);
    }
}
