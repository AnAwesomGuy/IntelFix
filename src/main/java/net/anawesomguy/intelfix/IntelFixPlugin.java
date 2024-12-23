package net.anawesomguy.intelfix;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.io.File;
import java.util.Map;

import static net.anawesomguy.intelfix.IntelFix.*;

public final class IntelFixPlugin implements IFMLLoadingPlugin {
    static {
        @SuppressWarnings("unused")
        Class<?> stupidClassLoadingIssueSoIHaveToInitializeTheClassEarly = IntelFix.class;
    }

    @Deprecated
    @Override
    public String[] getLibraryRequestClass() {
        return null;
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{
            FMLType.CURRENT == FMLType.OLD_FML ?
                "net.anawesomguy.intelfix.IntelFixPatcher$Old" :
                "net.anawesomguy.intelfix.IntelFixPatcher$LW"};
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
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
