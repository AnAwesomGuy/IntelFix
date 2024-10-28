package net.anawesomguy.intelfix;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.FMLInjectionData;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import net.minecraft.launchwrapper.Launch;

import java.io.File;
import java.util.Map;

import static net.anawesomguy.intelfix.IntelFix.*;

public final class IntelFixPlugin implements IFMLLoadingPlugin {
    @Deprecated
    @Override
    public String[] getLibraryRequestClass() {
        return null;
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{"net.anawesomguy.intelfix.IntelFixPatcher$LW"};
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
        if (obfEnv instanceof Boolean)
            deobfEnv = !(Boolean)obfEnv;

        Object loc = map.get("coremodLocation");
        if (loc instanceof File) // null check too
            modFile = ((File)loc).getPath();
        else
            modFile = IntelFix.class.getProtectionDomain().getCodeSource().getLocation().getPath();

        /* gets config file location (wtf is this monstrosity) (i dont even think its necessary but who cares lmao)
         * flow: (only advances to next step if it fails)
         * get `mcLocation` from map
         * get config dir from `Loader.instance().getConfigDir()`
         * get mc home from `Launch.minecraftHome` (only exists when using launchwrapper)
         * get mc home from `FMLInjectionData.data()`'s 7th element
         */
        File mcDir;
        Object mcLoc = map.get("mcLocation");
        if (mcLoc instanceof File)
            mcDir = (File)mcLoc;
        else {
            try {
                configFile = new File(Loader.instance().getConfigDir(), "INTELFIX.properties");
                return;
            } catch (NoClassDefFoundError e) {
                try {
                    mcDir = Launch.minecraftHome;
                } catch (NoClassDefFoundError er) {
                    label:
                    {
                        try {
                            Object data = FMLInjectionData.data()[6];
                            if (data instanceof File) {
                                mcDir = (File)data;
                                break label;
                            }
                        } catch (NoClassDefFoundError err) {
                        } catch (RuntimeException ex) {
                        }
                        mcDir = new File(System.getProperty("user.dir", "."));
                    }
                }
            }
        }
        configFile = new File(new File(mcDir, "config"), "INTELFIX.properties");
    }
}
