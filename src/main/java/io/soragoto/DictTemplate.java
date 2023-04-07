package io.soragoto;

public enum DictTemplate {

    PRIMARY("openfly.primary", "首选字词"),
    SECONDARY("openfly.secondary", "次选字词"),
    VOID("openfly.void", "填空码"),
    OFF_TABLE("openfly.off-table", "表外字"),
    WHIMSICALITY("openfly.whimsicality", "随心所欲"),
    SECONDARY_SHORT_CODE("openfly.secondary.short.code", "二重简码"),
    REVERSE("openfly-reverse", "反查"),


    SYMBOLS("openfly.symbols", "符号编码"),
    UNCOMMON("openfly.uncommon", "生僻字"),
    USER_TOP("openfly.user.top", "用户置顶"),
    USER("openfly.user", "用户置底");

    private final String _name;
    private final String _description;
    private static final String _version = "v1.0.0";

    DictTemplate(String name, String description) {
        _name = name;
        _description = description;
    }

    public String getFileName() {
        return _name + ".dict.yaml";
    }

    public String getTemplate() {
        return String.format("""
                # Rime dictionary
                # encoding: utf-8
                #
                # %s
                                    
                ---
                name: %s
                version: "%s"
                sort: by_weight
                use_preset_vocabulary: false
                ...
                    
                """, _description, _name, _version);
    }

}
