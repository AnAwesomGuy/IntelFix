package net.anawesomguy.intelfix;

import cpw.mods.fml.relauncher.IFMLCallHook;

import java.util.Map;
import java.util.logging.Level;

public final class IntelFixSetup implements IFMLCallHook {
    @Override
    public void injectData(Map<String, Object> map) {
    }

    @Override
    public Void call() {
        IntelFix.isFML = true;
        IntelFix.loadConfig();
        return null;
    }

    private boolean parseBool(String val) {
        if ("true".equalsIgnoreCase(val))
            return true;

        if (!val.isEmpty() && !"false".equalsIgnoreCase(val))
            IntelFix.LOGGER.log(Level.INFO, "Parsing weird boolean value \"{0}\" as false!", val);
        return false;
    }
}
