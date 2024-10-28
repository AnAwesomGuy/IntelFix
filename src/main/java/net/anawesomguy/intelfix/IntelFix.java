package net.anawesomguy.intelfix;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class IntelFix {
    public static final Logger LOGGER = Logger.getLogger("IntelFix");

    static {
        init:
        {
            try {
                LOGGER.setParent(FMLLog.getLogger());
                break init;
            } catch (NoClassDefFoundError e) {
            } catch (NoSuchMethodError e) {
            }
            Level level;
            try {
                level = Level.parse(System.getProperty("intelfix.loglevel"));
            } catch (Exception e) {
                level = Level.INFO;
            }
            LOGGER.setLevel(level);
        }
    }

    static File configFile;
    public static boolean isFML;
    static boolean deobfEnv;
    static String modFile;

    static String injectedClass, injectedMethod, glHelperClass, setClientTexture;
    static boolean obfuscatedNames;
    static boolean useLegacy;

    private IntelFix() {
    }

    // properties: injected_class, injected_method, gl_helper_class, set_client_texture, obfuscated_names, use_legacy
    static void loadConfig() {
        LOGGER.fine("Initializing IntelFixSetup from " + modFile);

        // read config
        Properties config = new Properties();
        String comment = " ONLY CHANGE THESE IF YOU KNOW WHAT YOU'RE DOING!\n\n" +
                         " injected_class: the class to inject the fix into\n" +
                         " injected_method: the name and descriptor of the method to inject the fix into\n" +
                         " gl_helper_class: the name of the `OpenGLHelper class\n" +
                         " set_client_texture: the name WITHOUT descriptor of the `OpenGlHelper.setClientActiveTexture(I)V` method\n" +
                         " obfuscated_names: if the above names are obfuscated and unmapped\n" +
                         " use_legacy: whether the legacy fix is used (inject into `OpenGlHelper.setActiveTexture` instead of `Tessellator#draw`)\n";
        List<String> configVals = Arrays.asList(
            "injected_class", "injected_method", "gl_helper_class",
            "set_client_texture", "obfuscated_names", "use_legacy");
        String injectedClass = "", injectedMethod = "", glHelperClass = "", setClientTexture = "";
        boolean obfuscatedNames = false, useLegacy = false;
        try {
            File file = configFile;
            if (file.exists()) {
                config.load(new FileInputStream(file));
                for (Entry<?, ?> entry : config.entrySet().toArray(new Entry<?, ?>[0])) {
                    Object key = entry.getKey();
                    int index;
                    if (key instanceof String && (index = configVals.indexOf(key)) > -1) {
                        configVals.set(index, null);
                        Object value = entry.getValue();
                        if (value instanceof String) {
                            String val = ((String)value).trim();
                            if (!val.isEmpty())
                                switch (index) {
                                    case 0:
                                        injectedClass = val;
                                        break;
                                    case 1:
                                        injectedMethod = val;
                                        break;
                                    case 2:
                                        glHelperClass = val;
                                        break;
                                    case 3:
                                        setClientTexture = val;
                                        break;
                                    case 4:
                                        obfuscatedNames = parseBool(val);
                                        break;
                                    case 5:
                                        useLegacy = parseBool(val);
                                        break;
                                }
                        }
                    } else {
                        LOGGER.log(Level.INFO, "Found bad config value \"{0}\"", key);
                        config.remove(key);
                    }
                }

                boolean modified = false;
                for (String val : configVals)
                    if (val != null) {
                        LOGGER.log(Level.WARNING, "Missing config value \"{0}\", replacing with defaults!", val);
                        config.put(val, val.equals("use_legacy") || val.equals("obfuscated_names") ? "false" : "");
                        modified = true;
                    }
                if (modified)
                    config.store(new FileWriter(file), comment);
            } else {
                LOGGER.info("Config does not exist! Creating from defaults.");
                config.setProperty("injected_class", "");
                config.setProperty("injected_method", "");
                config.setProperty("gl_helper_class", "");
                config.setProperty("set_client_texture", "");
                config.setProperty("use_legacy", "false");
                config.setProperty("obfuscated_names", "false");
                config.store(new FileWriter(file), comment);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load or write to config file at \"" + configFile.getAbsolutePath() +
                                     "\", will use defaults!", e);
        }

        // set config values
        IntelFix.useLegacy = useLegacy;
        IntelFix.obfuscatedNames = obfuscatedNames;
        IntelFix.injectedClass = !injectedClass.isEmpty() ?
            injectedClass :
            maybeUnmap(
                useLegacy ? "net.minecraft.client.renderer.OpenGlHelper" : "net.minecraft.client.renderer.Tessellator",
                obfuscatedNames);

        if (!injectedMethod.isEmpty())
            IntelFix.injectedMethod = injectedMethod;
        else {
            if (obfuscatedNames)
                LOGGER.severe(
                    "Cannot set `obfuscated_names` without also specifying a method! This mod will not function!");
            if (useLegacy)
                IntelFix.injectedMethod = deobfEnv ? "setActiveTexture(I)V" : "func_77473_a(I)V";
            else
                IntelFix.injectedMethod = deobfEnv ? "draw()I" : "func_78381_a()I";
        }

        IntelFix.glHelperClass = (
            !glHelperClass.isEmpty() ?
                glHelperClass :
                maybeUnmap("net.minecraft.client.renderer.OpenGlHelper", obfuscatedNames)
        ).replace('.', '/');

        if (!setClientTexture.isEmpty())
            IntelFix.setClientTexture = setClientTexture;
        else {
            if (obfuscatedNames)
                LOGGER.severe(
                    "Cannot set `obfuscated_names` without also specifying `set_client_texture`! This mod will not function!");
            IntelFix.setClientTexture = deobfEnv ? "setClientActiveTexture" : "func_77472_b";
        }

        LOGGER.finer("Config values read, parsed, and stored!");
        LOGGER.log(Level.FINER,
                   "use_legacy: {0}, injected_class: {1}, injected_method: {2}, gl_helper_class: {3}, set_client_texture: {4}, obfuscated_names: {5}",
                   new Object[]{useLegacy, IntelFix.injectedClass, IntelFix.injectedMethod, IntelFix.glHelperClass, IntelFix.setClientTexture, obfuscatedNames});
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

    public static String setClientTexture() {
        return setClientTexture;
    }

    public static boolean obfuscatedNames() {
        return obfuscatedNames;
    }

    public static boolean useLegacy() {
        return useLegacy;
    }

    private static boolean parseBool(String val) {
        if ("true".equalsIgnoreCase(val))
            return true;

        if (!val.isEmpty() && !"false".equalsIgnoreCase(val))
            LOGGER.log(Level.INFO, "Parsing weird boolean value \"{0}\" as false!", val);
        return false;
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
