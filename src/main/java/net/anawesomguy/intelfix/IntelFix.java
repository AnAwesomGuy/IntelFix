package net.anawesomguy.intelfix;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import cpw.mods.fml.relauncher.FMLInjectionData;
import net.minecraft.launchwrapper.Launch;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class IntelFix {
    public static final Logger LOGGER = Logger.getLogger("IntelFix");

    static {
        // log level is either configured by fml or through the property `intelfix.loglevel`
        try {
            LOGGER.setParent(FMLLog.getLogger());
        } catch (LinkageError e) {
            Level level;
            try {
                level = Level.parse(System.getProperty("intelfix.loglevel"));
            } catch (RuntimeException ex) {
                level = Level.INFO;
            }
            LOGGER.setLevel(level);
        }
    }

    static File configFile;
    static boolean deobfEnv;
    static String modFile;

    static String injectedClass, injectedMethod, glHelperClass, setClientTexture;
    static boolean obfuscatedNames;
    static boolean useLegacy;

    private IntelFix() {
    }

    // properties: injected_class, injected_method, gl_helper_class, setClientActiveTexture, obfuscated_names, use_legacy
    static void loadConfig() {
        LOGGER.fine("Initializing IntelFixSetup from " + (modFile == null ? modFile = IntelFix.class.getProtectionDomain().getCodeSource().getLocation().getPath() : modFile));

        // read config (honestly even i kinda forgot how this works but if it works, it works. never touching this again lol.)
        Properties config = new Properties();
        String comment = " ONLY CHANGE THESE IF YOU KNOW WHAT YOU'RE DOING!\n\n" +
                         " injected_class: the class to inject the fix into\n" +
                         " injected_method: the name and descriptor of the method to inject the fix into\n" +
                         " gl_helper_class: the name of the `OpenGLHelper` class\n" +
                         " setClientActiveTexture: the name WITHOUT descriptor of the `OpenGlHelper.setClientActiveTexture(I)V` method\n" +
                         " obfuscated_names: if the above names are obfuscated and unmapped (always false if using FML < 5 or the Java agent)\n" +
                         " use_legacy: whether the legacy fix is used (inject into `OpenGlHelper.setActiveTexture` instead of `Tessellator#draw`) (boolean)\n";
        List<String> configVals = Arrays.asList(
            "injected_class", "injected_method", "gl_helper_class",
            "setClientActiveTexture", "obfuscated_names", "use_legacy");
        String injectedClass = "", injectedMethod = "", glHelperClass = "", setClientTexture = "";
        Boolean obfuscatedNames = null;
        boolean useLegacy = false;
        try {
            File file = configFile;
            if (file == null)
                file = configFile = findConfigFile();
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
                        config.put(val, val.equals("use_legacy") ? "false" : "");
                        modified = true;
                    }
                if (modified)
                    config.store(new FileWriter(file), comment);
            } else {
                LOGGER.info("Config does not exist! Creating from defaults.");
                config.setProperty("injected_class", "");
                config.setProperty("injected_method", "");
                config.setProperty("gl_helper_class", "");
                config.setProperty("setClientActiveTexture", "");
                config.setProperty("obfuscated_names", "");
                config.setProperty("use_legacy", "false");
                config.store(new FileWriter(file), comment);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load or write to config file at \"" + configFile.getAbsolutePath() +
                                     "\", will use defaults!", e);
        }

        // set config values (adjusting to the defaults)
        IntelFix.useLegacy = useLegacy;

        boolean obfNames;
        if (FMLType.CURRENT.cannotDeobfuscate()) {
            obfNames = true;
            if (Boolean.FALSE.equals(obfuscatedNames)) // null-safe !obfuscatedNames
                LOGGER.warning(
                    "Ignoring `obfuscated_names` set through config, runtime deobfuscation is not supported in this environment!");
        } else if (obfuscatedNames == null)
            obfNames = false;
        else
            obfNames = obfuscatedNames;
        IntelFix.obfuscatedNames = obfNames;

        if (glHelperClass.isEmpty())
            glHelperClass = maybeUnmap("net.minecraft.client.renderer.OpenGlHelper", obfNames);
        IntelFix.glHelperClass = glHelperClass != null ? glHelperClass.replace('.', '/') : glHelperClass;

        IntelFix.injectedClass = injectedClass.isEmpty() ?
            (useLegacy ? IntelFix.glHelperClass.replace('/', '.') : maybeUnmap("net.minecraft.client.renderer.Tessellator", obfNames)) :
            injectedClass;

        method:
        if (injectedMethod.isEmpty()) {
            if (obfNames) {
                if (FMLType.CURRENT.cannotDeobfuscate()) {
                    injectedMethod = useLegacy ? null : "a(I)V";
                    break method;
                }
                LOGGER.severe("Cannot set `obfuscated_names` without also specifying a method! This mod will not function!");
            }
            injectedMethod = useLegacy ?
                (deobfEnv ? "setActiveTexture(I)V" : "func_77473_a(I)V") :
                (deobfEnv ? "draw()I" : "func_78381_a()I");
        }
        IntelFix.injectedMethod = injectedMethod;

        clientTexture:
        if (setClientTexture.isEmpty()) {
            if (obfNames) {
                if (FMLType.CURRENT.cannotDeobfuscate()) {
                    setClientTexture = null;
                    break clientTexture;
                }
                LOGGER.severe("Cannot set `obfuscated_names` without also specifying `setClientActiveTexture`! This mod will not function!");
            }
            setClientTexture = deobfEnv ? "setClientActiveTexture" : "func_77472_b";
        }
        IntelFix.setClientTexture = setClientTexture;

        LOGGER.finer("Config values read, parsed, and stored!");
        LOGGER.log(Level.FINER,
                   "use_legacy: {0}, injected_class: {1}, injected_method: {2}, gl_helper_class: {3}, setClientActiveTexture: {4}, obfuscated_names: {5} (null means default and undetermined)",
                   new Object[]{useLegacy, IntelFix.injectedClass, IntelFix.injectedMethod, IntelFix.glHelperClass, IntelFix.setClientTexture, obfuscatedNames});
    }

    private static File findConfigFile() {
        /* gets config file location (wtf is this monstrosity) (i dont even think its necessary but who cares lmao)
         * flow: (only advances to next step if it fails)
         * get config dir from `Loader.instance().getConfigDir()`
         * get config dir from ModLoader cfgdir (private static final field)
         * get mc home from `Launch.minecraftHome` (only exists when using launchwrapper)
         * get mc home from `FMLInjectionData.data()`'s 7th element
         */
        File mcDir;
        try {
            return new File(Loader.instance().getConfigDir(), "INTELFIX.properties");
        } catch (LinkageError e) {
            try {
                @SuppressWarnings("JavaReflectionMemberAccess")
                Field f = Class.forName("ModLoader").getField("cfgdir");
                f.setAccessible(true);
                return new File((File)f.get(null), "INTELFIX.properties");
            } catch (ClassNotFoundException ex) {
            } catch (NoSuchFieldException ex) {
            } catch (IllegalAccessException ex) {
            } catch (RuntimeException ex) {
            }
            try {
                mcDir = Launch.minecraftHome;
            } catch (LinkageError er) {
                label:
                {
                    try {
                        Object data = FMLInjectionData.data()[6];
                        if (data instanceof File) {
                            mcDir = (File)data;
                            break label;
                        }
                    } catch (LinkageError err) {
                    } catch (RuntimeException ex) {
                    }
                    mcDir = new File(System.getProperty("user.dir", "."));
                }
            }
        }
        return new File(new File(mcDir, "config"), "INTELFIX.properties");
    }

    private static Boolean parseBool(String val) {
        if ("true".equalsIgnoreCase(val))
            return Boolean.TRUE;
        if (!val.isEmpty() && !"false".equalsIgnoreCase(val))
            LOGGER.log(Level.INFO, "Parsing weird boolean value \"{0}\" as false!", val);
        return Boolean.FALSE;
    }

    private static String maybeUnmap(String clazz, boolean obfuscatedNames) {
        if (!deobfEnv && obfuscatedNames) {
            if (FMLType.CURRENT.cannotDeobfuscate())
                return null;
            String unmapped = FMLDeobfuscatingRemapper.INSTANCE.unmap(clazz);
            LOGGER.log(Level.FINER, "Unmapped class \"{0}\" to \"{1}\"", new Object[]{clazz, unmapped});
            return unmapped;
        }

        return clazz;
    }

    //region // getters
    //@formatter:off
    public static String injectedClass() { return injectedClass; }
    public static String injectedMethod() { return injectedMethod; }
    public static String glHelperClass() { return glHelperClass; }
    public static String setClientTexture() { return setClientTexture; }
    public static boolean obfuscatedNames() { return obfuscatedNames; }
    public static boolean useLegacy() { return useLegacy; }
    public static File configFile() { return configFile; }
    //@formatter:on
    //endregion
}
