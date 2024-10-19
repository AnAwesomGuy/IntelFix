package net.anawesomguy.intelfix;

import com.google.common.collect.ImmutableSet;
import cpw.mods.fml.relauncher.IFMLCallHook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;

import static net.anawesomguy.intelfix.IntelFixPlugin.LOGGER;

public final class IntelFixSetup implements IFMLCallHook {
    @Override
    public void injectData(Map<String, Object> map) {
    }

    // properties: injected_class, injected_method, use_legacy
    @Override
    public Void call() {
        LOGGER.fine("Initializing IntelFixSetup from " + IntelFixPlugin.modFile);

        // config section below
        Properties config = new Properties();
        String comment = " ONLY CHANGE THESE IF YOU KNOW WHAT YOU'RE DOING!\n\n" +
                         " injected_class: the class to inject the fix into\n" +
                         " injected_method: the name and descriptor of the method to inject the fix into\n" +
                         " gl_helper_class: the name of the `OpenGLHelper class\n" +
                         " set_client_texture: the name withOUT descriptor of the `OpenGlHelper.setClientActiveTexture(I)V` method\n" +
                         " obfuscated_names: if the above names are obfuscated and unmapped\n" +
                         " use_legacy: whether the legacy fix is used (inject into `OpenGlHelper.setActiveTexture` instead of `Tessellator#draw`)\n";
        List<String> configVals = Arrays.asList("injected_class", "injected_method", "gl_helper_class", "set_client_texture", "obfuscated_names", "use_legacy");
        String injectedClass = "", injectedMethod = "", glHelperClass = "", setClientTexture = "";
        boolean obfuscatedNames = false, useLegacy = false;
        try {
            File file = IntelFixPlugin.configFile;
            if (file.exists()) {
                config.load(new FileInputStream(file));
                for (Entry<Object, Object> entry : ImmutableSet.copyOf(config.entrySet())) {
                    Object key = entry.getKey();
                    int index;
                    if (key instanceof String && (index = configVals.indexOf(key)) > -1) {
                        configVals.set(index, null);
                        Object value = entry.getValue();
                        if (value instanceof String) {
                            String val = ((String)value).trim();
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
                for (String val : configVals) {
                    if (val != null) {
                        LOGGER.log(Level.WARNING, "Missing config value \"{0}\", replacing with defaults!", val);
                        config.put(val, val.equals("use_legacy") || val.equals("obfuscated_names") ? "false" : "");
                        modified = true;
                    }
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
            LOGGER.log(Level.SEVERE, "Could not load or write to config file INTELFIX.properties, will use defaults!", e);
        }

        IntelFixPlugin.setConfigVals(useLegacy, injectedClass, injectedMethod, glHelperClass, setClientTexture, obfuscatedNames);
        return null;
    }

    private boolean parseBool(String val) {
        if ("true".equalsIgnoreCase(val))
            return true;

        if (!val.isEmpty() && !"false".equalsIgnoreCase(val))
            LOGGER.log(Level.INFO, "Parsing weird boolean value \"{0}\" as false!", val);
        return false;
    }
}
