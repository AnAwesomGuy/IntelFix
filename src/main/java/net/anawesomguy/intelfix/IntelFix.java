package net.anawesomguy.intelfix;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import cpw.mods.fml.relauncher.FMLInjectionData;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import net.minecraft.launchwrapper.Launch;
import org.lwjgl.opengl.GL13;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.objectweb.asm.Opcodes.*;

public final class IntelFix {
    public static final Logger LOGGER = Logger.getLogger("IntelFix");

    static {
        // log level is either configured by fml or through the property `intelfix.loglevel`
        try {
            LOGGER.setParent(FMLRelaunchLog.log.getLogger());
        } catch (LinkageError e) {
            Level level;
            try {
                level = Level.parse(System.getProperty("intelfix.loglevel", "INFO"));
            } catch (IllegalArgumentException ex) {
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

    // properties: injected_class, injected_method, gl_helper_class, setClientActiveTexture, obfuscated_names, use_legacy
    static void loadConfig() {
        LOGGER.log(Level.FINER,
                   "Initializing {0} from '{1}'",
                   new Object[]{
                       IntelFix.class.getName(),
                       (modFile == null ?
                            modFile = IntelFix.class.getProtectionDomain().getCodeSource().getLocation().getPath() :
                            modFile)});

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
                        LOGGER.log(Level.INFO, "Found bad config value '{0}'", key);
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
                //noinspection ResultOfMethodCallIgnored
                file.getParentFile().mkdirs();
                config.store(new FileWriter(file), comment);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load or write to config file at \"" + configFile.getAbsolutePath() +
                                     "\", will use defaults!", e);
        }

        FMLType fmlType = FMLType.getCurrent();

        // set config values (adjusting to the defaults)
        IntelFix.useLegacy = useLegacy;

        boolean obfNames;
        if (fmlType.cannotDeobfuscate()) {
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
            (useLegacy ?
                 IntelFix.glHelperClass.replace('/', '.') :
                 maybeUnmap("net.minecraft.client.renderer.Tessellator", obfNames)) :
            injectedClass;

        method:
        if (injectedMethod.isEmpty()) {
            if (obfNames) {
                if (fmlType.cannotDeobfuscate()) {
                    injectedMethod = null;
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
                if (fmlType.cannotDeobfuscate()) {
                    setClientTexture = null;
                    break clientTexture;
                }
                LOGGER.severe(
                    "Cannot set `obfuscated_names` without also specifying `setClientActiveTexture`! This mod will not function!");
            }
            setClientTexture = deobfEnv ? "setClientActiveTexture" : "func_77472_b";
        }
        IntelFix.setClientTexture = setClientTexture;

        LOGGER.fine("Config values read, parsed, and stored!");
        LOGGER.log(Level.FINE,
                   "use_legacy: {0}, injected_class: {1}, injected_method: {2}, gl_helper_class: {3}, setClientActiveTexture: {4}, obfuscated_names: {5} (null means default and undetermined)",
                   new Object[]{useLegacy, IntelFix.injectedClass, IntelFix.injectedMethod, IntelFix.glHelperClass, IntelFix.setClientTexture, obfNames});
    }

    private static File findConfigFile() {
        /* gets config file location (wtf is this monstrosity) (i dont even think its necessary but who cares lmao)
         * flow: (only advances to next step if it fails)
         * get config dir from `Loader.instance().getConfigDir()`
         * get config dir from ModLoader cfgdir (private static final field)
         * get mc home from `Launch.minecraftHome` (only exists when using launchwrapper)
         * get mc home from `FMLInjectionData.data()`'s 7th element
         */
        try {
            return new File(Loader.instance().getConfigDir(), "INTELFIX.properties");
        } catch (LinkageError e) {
        } catch (NullPointerException e) { // occurs when run too early
        }

        File mcDir;
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
            if (FMLType.getCurrent().cannotDeobfuscate())
                return null;
            String unmapped = FMLDeobfuscatingRemapper.INSTANCE.unmap(clazz);
            LOGGER.log(Level.FINER, "Unmapped class '{0}' to '{1}'", new Object[]{clazz, unmapped});
            return unmapped;
        }

        return clazz;
    }

    private static final String ILLEGAL_STATE_EX = "java/lang/IllegalStateException";

    public static byte[] findAndPatch(String name, byte[] bytes) {
        if (injectedClass != null && !injectedClass.equals(name))
            return bytes;

        ClassNode clazz = new ClassNode();
        ClassReader reader = new ClassReader(bytes);
        reader.accept(clazz, 0);

        if (!"java/lang/Object".equals(clazz.superName) || clazz.access != 0x0021 /*ACC_PUBLIC and ACC_SUPER*/)
            return bytes;

        MethodNode drawNode = null;
        String exceptionString = "Not tesselating!";
        List<MethodNode> methods = clazz.methods;
        for (MethodNode method : methods) {
            if (!method.exceptions.isEmpty() || !Modifier.isPublic(method.access) || Modifier.isStatic(method.access) ||
                "<init>".equals(method.name))
                continue; // skip all methods with exceptions || non-public || static || constructors
            if (drawNode == null) {
                if (!"()V".equals(method.desc) && !"()I".equals(method.desc))
                    break; // draw ("Not tesselating!") is always the first public instance method with no args and returns int or void
            } else if (!"(I)V".equals(method.desc))
                continue; // startDrawing ("Already tesselating!") always take an int and returns void
            // check for:
            // NEW java/lang/IllegalStateException
            // DUP
            // LDC "Not tesselating!" and then "Already tesselating!" (the check runs twice to be extra sure it's the right class)
            // INVOKESPECIAL java/lang/IllegalStateException <init> (Ljava/lang/String;)V
            // ATHROW
            AbstractInsnNode[] nodes = method.instructions.toArray();
            for (AbstractInsnNode node : nodes) {
                AbstractInsnNode insn = node;
                if ( // i tried my best to make it readable please dont kill me
                    (insn instanceof TypeInsnNode &&
                     insn.getOpcode() == NEW &&
                     ILLEGAL_STATE_EX.equals(((TypeInsnNode)insn).desc)) &&
                    ((insn = insn.getNext()) instanceof InsnNode &&
                     insn.getOpcode() == DUP) &&
                    ((insn = insn.getNext()) instanceof LdcInsnNode &&
                     exceptionString.equals(((LdcInsnNode)insn).cst)) &&
                    ((insn = insn.getNext()) instanceof MethodInsnNode &&
                     insn.getOpcode() == INVOKESPECIAL &&
                     ILLEGAL_STATE_EX.equals(((MethodInsnNode)insn).name) &&
                     "<init>".equals(((MethodInsnNode)insn).owner) &&
                     "(Ljava/lang/String;)V".equals(((MethodInsnNode)insn).desc)) &&
                    ((insn = insn.getNext()) instanceof InsnNode &&
                     insn.getOpcode() == ATHROW)
                ) {
                    if (drawNode == null) {
                        drawNode = method;
                        exceptionString = "Already tesselating!";
                    } else {
                        LOGGER.log(Level.FINE, "Injecting patch into '{0}' in '{1}'", new Object[]{drawNode.name, name});
                        drawNode.instructions.insert(new LdcInsnNode(GL13.GL_TEXTURE0));
                        drawNode.instructions.insert(new LdcInsnNode(GL13.GL_TEXTURE0));
                        ClassWriter writer = new ClassWriter(reader, 0);
                        clazz.accept(writer);
                        return writer.toByteArray();
                    }
                }
            }
        }
        return bytes;
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

    private IntelFix() {}
    //@formatter:on
    //endregion
}
