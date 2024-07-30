package net.anawesomguy.intelfix;

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
    private String modFile;
    private File mcDir;

    @Override
    public void injectData(Map<String, Object> map) {
        mcDir = (File)map.get("mcLocation");
        Object file = map.get("coremodLocation");
        if (file instanceof File) // null check too
            modFile = ((File)file).getPath();
        else
            modFile = IntelFixSetup.class.getProtectionDomain().getCodeSource().getLocation().getPath();
    }

    // properties: injected_class, injected_method, use_legacy
    @Override
    public Void call() {
        LOGGER.fine("Initializing IntelFixSetup from " + modFile);

        Properties config = new Properties();
        File file = new File(new File(mcDir, "config"), "INTELFIX.properties");
        String comment = "injected_class: the class to inject the fix into.\n" +
                         "injected_method: the method to inject the fix into, must contain descriptor.\n" +
                         "use_legacy: whether the legacy fix is used (inject into OpenGlHelper.setActiveTexture instead of Tessellator#draw) (boolean)";
        List<String> configVals = Arrays.asList("injected_class", "injected_method", "use_legacy");
        String injectedClass = "", injectedMethod = "";
        boolean useLegacy = false;
        try {
            if (file.exists()) {
                config.load(new FileInputStream(file));
                for (Entry<Object, Object> entry : config.entrySet()) {
                    Object key = entry.getKey();
                    int index;
                    if (key instanceof String && (index = configVals.indexOf(key)) > -1) {
                        configVals.set(index, null);
                        Object value = entry.getValue();
                        if (value instanceof String) {
                            String val = (String)value;
                            if (index == 0)
                                injectedClass = val;
                            else if (index == 1)
                                injectedMethod = val;
                            else if (index == 2)
                                if ("true".equalsIgnoreCase(val))
                                    useLegacy = true;
                                else {
                                    if (!val.isEmpty() && !"false".equalsIgnoreCase(val))
                                        LOGGER.info("Parsing weird value '" + val + "' as false!");
                                    useLegacy = false;
                                }
                        }
                    } else {
                        LOGGER.info("Found bad config value '" + key + "'!");
                        config.remove(key);
                    }
                }

                boolean modified = false;
                //noinspection ForLoopReplaceableByForEach
                for (int i = 0; i < configVals.size(); i++) {
                    String val = configVals.get(i);
                    if (val != null) {
                        LOGGER.warning("Missing config value '" + val + "', replacing with defaults!");
                        config.put(val, val.equals("use_legacy") ? "false" : "");
                        modified = true;
                    }
                }
                if (modified)
                    config.store(new FileWriter(file), comment);
            } else {
                config.setProperty("injected_class", "");
                config.setProperty("injected_method", "");
                config.setProperty("use_legacy", "false");
                config.store(new FileWriter(file), comment);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load or write to config file INTELFIX.properties, will use defaults!", e);
        }

        IntelFixPlugin.setInjected(useLegacy, injectedClass, injectedMethod);
        return null;
    }
}
