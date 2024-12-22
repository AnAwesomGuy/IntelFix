package net.anawesomguy.intelfix;

public enum FMLType {
    NEW_FML, // supports runtime deobfuscation (6+)
    OLD_FML, // no runtime deobfuscation or launchwrapper (5-)
    NO_FML; // just no fml at all

    public boolean isFML() {
        return this != NO_FML; // is not not fml
    }

    public boolean cannotDeobfuscate() {
        return this != NEW_FML;
    }

    public static final FMLType CURRENT;

    static {
        // IClassTransformer -> old fml
        // IFMLLoadingPlugin -> "new" fml
        // neither -> no fml
        FMLType fmlType;
        try {
            Class.forName("cpw.mods.fml.relauncher.IClassTransformer");
            fmlType = FMLType.OLD_FML;
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("cpw.mods.fml.relauncher.IFMLLoadingPlugin");
                Class.forName("net.minecraft.launchwrapper.IClassTransformer");
                fmlType = FMLType.NEW_FML;
            } catch (ClassNotFoundException ex) {
                fmlType = FMLType.NO_FML;
            }
        }
        CURRENT = fmlType;
    }
}
