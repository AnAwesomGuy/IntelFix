package net.anawesomguy.intelfix;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import cpw.mods.fml.relauncher.FMLInjectionData;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import net.minecraft.launchwrapper.Launch;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class IntelFixPlugin implements IFMLLoadingPlugin {
    public static final Logger LOGGER = Logger.getLogger("IntelFix");
    static boolean deobfEnv;
    static String modFile;
    static File configFile;

    static {
        LOGGER.setParent(FMLLog.getLogger());
    }

    static String injectedClass, injectedMethod, glHelperClass, setClientTexture;
    static boolean obfuscatedNames;
    static boolean useLegacy;

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
    @SuppressWarnings("JavaReflectionMemberAccess")
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
         * get `cfgdir` from `ModLoader` class using reflection (it's private)
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
                        try {
                            Class<?> c = Class.forName("ModLoader");
                            Field f = c.getDeclaredField("cfgdir");
                            configFile = new File((File)f.get(null), "INTELFIX.properties");
                            return;
                        } catch (Exception ex) {
                            mcDir = new File(System.getProperty("user.dir", "."));
                        }
                    }
                }
            }
        }
        IntelFixPlugin.configFile = new File(new File(mcDir, "config"), "INTELFIX.properties");
    }

    public static boolean useLegacy() {
        return useLegacy;
    }

    public static String injectedClass() {
        return injectedClass;
    }

    public static String injectedMethod() {
        return injectedMethod;
    }

    public static String glHelperClass() {
        return glHelperClass;
    }

    public static String setClientTextureMethod() {
        return setClientTexture;
    }

    public static boolean obfuscatedNames() {
        return obfuscatedNames;
    }

    static void setConfigVals(boolean useLegacy, String injectedClass, String injectedMethod, String glHelperClass,
                              String setClientTexture, boolean obfuscatedNames) {
        IntelFixPlugin.useLegacy = useLegacy;
        IntelFixPlugin.obfuscatedNames = obfuscatedNames;

        IntelFixPlugin.injectedClass = injectedClass != null && !injectedClass.isEmpty() ?
            injectedClass :
            maybeUnmap(
                useLegacy ? "net.minecraft.client.renderer.OpenGlHelper" : "net.minecraft.client.renderer.Tessellator",
                obfuscatedNames);

        if (injectedMethod != null && !injectedMethod.isEmpty())
            IntelFixPlugin.injectedMethod = injectedMethod;
        else {
            if (obfuscatedNames)
                LOGGER.severe(
                    "Cannot set `obfuscated_names` without also specifying a method! This mod will not function!");
            if (useLegacy)
                IntelFixPlugin.injectedMethod = deobfEnv ? "setActiveTexture(I)V" : "func_77473_a(I)V";
            else
                IntelFixPlugin.injectedMethod = deobfEnv ? "draw()I" : "func_78381_a()I";
        }

        IntelFixPlugin.glHelperClass = (glHelperClass != null && !glHelperClass.isEmpty() ?
                                            glHelperClass :
                                            maybeUnmap("net.minecraft.client.renderer.OpenGlHelper",
                                                       obfuscatedNames)).replace('.', '/');

        if (setClientTexture != null && !setClientTexture.isEmpty())
            IntelFixPlugin.setClientTexture = setClientTexture;
        else {
            if (obfuscatedNames)
                LOGGER.severe("Cannot set `obfuscated_names` without also specifying `set_client_texture`! This mod will not function!");
            IntelFixPlugin.setClientTexture = deobfEnv ? "setClientActiveTexture" : "func_77472_b";
        }

        LOGGER.finer("Config values read, parsed, and stored!");
        LOGGER.log(Level.FINER,
                   "use_legacy: {0}, injected_class: {1}, injected_method: {2}, gl_helper_class: {3}, set_client_texture: {4}, obfuscated_names: {5}",
                   new Object[]{useLegacy, IntelFixPlugin.injectedClass, IntelFixPlugin.injectedMethod, IntelFixPlugin.glHelperClass, IntelFixPlugin.setClientTexture, obfuscatedNames});
    }

    private static String maybeUnmap(String clazz, boolean obfuscatedNames) {
        if (!deobfEnv && obfuscatedNames) {
            String unmapped = FMLDeobfuscatingRemapper.INSTANCE.unmap(clazz);
            LOGGER.log(Level.FINER, "Unmapped class \"{0}\" to \"{1}\"", new Object[]{clazz, unmapped});
            return unmapped;
        }

        return clazz;
    }
}
