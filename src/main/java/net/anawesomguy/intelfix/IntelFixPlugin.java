package net.anawesomguy.intelfix;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.io.File;
import java.util.Map;

import static net.anawesomguy.intelfix.IntelFix.*;

public final class IntelFixPlugin implements IFMLLoadingPlugin {
    static {
        @SuppressWarnings("unused")
        Class<?> stupidClassLoadingIssueSoIHaveToInitializeTheClassEarly = IntelFix.class;

        try {
            Class.forName("net.minecraft.launchwrapper.IClassTransformer");
            FMLType.CURRENT = FMLType.NEW_FML;
        } catch (ClassNotFoundException e) {
            FMLType.CURRENT = FMLType.OLD_FML;
        }
    }

    @Deprecated
    @Override
    public String[] getLibraryRequestClass() {
        return null;
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{
            FMLType.CURRENT == FMLType.NEW_FML ?
                "net.anawesomguy.intelfix.LaunchWrapperPatch" :
                "net.anawesomguy.intelfix.OldFMLPatch"};
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return "net.anawesomguy.intelfix.IntelFixSetup";
    }

    @Override
    public void injectData(Map<String, Object> map) {
        Object obfEnv = map.get("runtimeDeobfuscationEnabled");
        if (obfEnv instanceof Boolean) // null check too
            deobfEnv = !(Boolean)obfEnv;

        Object loc = map.get("coremodLocation");
        if (loc instanceof File) // null check too
            modFile = ((File)loc).getPath();

        Object mcLoc = map.get("mcLocation");
        if (mcLoc instanceof File) // null check too
            configFile = new File(new File((File)mcLoc, "config"), "INTELFIX.properties");
    }
}
