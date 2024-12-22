package net.anawesomguy.intelfix;

import cpw.mods.fml.relauncher.IFMLCallHook;

import java.util.Map;

public final class IntelFixSetup implements IFMLCallHook {
    @Override
    public void injectData(Map<String, Object> map) {
    }

    @Override
    public Void call() {
        IntelFix.loadConfig();
        return null;
    }
}
