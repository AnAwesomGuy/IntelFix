package net.anawesomguy.intelfix;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import cpw.mods.fml.relauncher.FMLInjectionData;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import net.minecraft.launchwrapper.Launch;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class IntelFixPlugin implements IFMLLoadingPlugin {
    public static final Logger LOGGER = Logger.getLogger("IntelFix");
    static File mcDir;
    static boolean deobfEnv;

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
        return new String[]{"net.anawesomguy.intelfix.IntelFixPatcher"};
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

        //wtf is this monstrosity (i dont even think its necessary but who cares lmao)
        File mcDir = null;
        Object mcLoc = map.get("mcLocation");
        if (mcLoc instanceof File)
            mcDir = (File)mcLoc;
        else {
            try {
                mcDir = Launch.minecraftHome;
            } catch (NoClassDefFoundError e) {
                try {
                    Object data = FMLInjectionData.data()[6];
                    if (data instanceof File) {
                        mcDir = (File)data;
                    }
                } catch (NoClassDefFoundError er) {
                } catch (RuntimeException ex) {
                }
            }

            if (mcDir == null) {
                label:
                {
                    try {
                        Field f = Loader.class.getDeclaredField("minecraftDir");
                        f.setAccessible(true);
                        mcDir = (File)f.get(null);
                        break label;
                    } catch (NoClassDefFoundError e) {
                    } catch (Exception e) {
                    }
                    try {
                        Field f = FMLRelaunchLog.class.getDeclaredField("minecraftHome");
                        f.setAccessible(true);
                        mcDir = (File)f.get(null);
                        break label;
                    } catch (NoClassDefFoundError e) {
                    } catch (Exception e) {
                    }
                    Class<?> c;
                    try {
                        c = Class.forName("ModLoader");
                    } catch (ClassNotFoundException e) {
                        c = null;
                    }
                    if (c != null) {
                        Field f;
                        try {
                            f = c.getDeclaredField("modDir");
                            mcDir = ((File)f.get(null)).getParentFile();
                            break label;
                        } catch (Exception e) {
                            try {
                                f = c.getDeclaredField("cfgdir");
                                mcDir = ((File)f.get(null)).getParentFile();
                                break label;
                            } catch (Exception ex) {
                            }
                        }
                    }
                    mcDir = new File(".");
                }
            }
        }
        IntelFixPlugin.mcDir = mcDir;
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
                    "Cannot set `obfuscated_names` without also specifying a method! This mod will not work properly!");
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
                LOGGER.severe(
                    "Cannot set `obfuscated_names` without also specifying `set_client_active`! This mod will not work properly!");
            IntelFixPlugin.setClientTexture = deobfEnv ? "setClientActiveTexture" : "func_77472_b";
        }

        LOGGER.finer("Config values read, parsed, and stored!");
        LOGGER.log(Level.FINER,
                   "use_legacy: {0}, injected_class: {1}, injected_method: {2}, gl_helper_class: {3}, set_client_texture: {4}, obfuscated_names: {5}",
                   new Object[]{useLegacy, IntelFixPlugin.injectedClass, IntelFixPlugin.injectedMethod, IntelFixPlugin.glHelperClass, IntelFixPlugin.setClientTexture, obfuscatedNames}
        );
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
