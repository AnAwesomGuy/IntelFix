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

    static FMLType CURRENT;

    public static FMLType getCurrent() {
        if (CURRENT != null)
            return CURRENT;
        // IClassTransformer -> old fml
        // FMLDeobfuscatingRemapper -> "new" fml
        // neither -> no fml
        FMLType fmlType;
        try {
            Class.forName("cpw.mods.fml.relauncher.IClassTransformer");
            fmlType = FMLType.OLD_FML;
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper",
                              false, FMLType.class.getClassLoader()); // dont init and break stuff
                Class.forName("net.minecraft.launchwrapper.IClassTransformer");
                fmlType = FMLType.NEW_FML;
            } catch (ClassNotFoundException ex) {
                fmlType = FMLType.NO_FML;
            }
        }
        return CURRENT = fmlType;
    }
}
