package net.anawesomguy.intelfix;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;
import java.util.logging.Logger;

public final class IntelFixPlugin implements IFMLLoadingPlugin {
    public static final Logger LOGGER = Logger.getLogger("IntelFix");

    static {
        LOGGER.setParent(FMLLog.getLogger());
    }

    static String injectedClass = "net.minecraft.client.renderer.Tessellator";
    static String injectedMethod = "draw()I";

    static boolean useLegacy;

    @Deprecated
    @Override
    public String[] getLibraryRequestClass() {
        return null;
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{"net.anawesomguy.intelfix.IntelFixTransformer"};
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

    static void setInjected(boolean useLegacy, String injectedClass, String injectedMethod) {
        if (useLegacy == IntelFixPlugin.useLegacy) {
            if (injectedClass != null && !injectedClass.isEmpty())
                IntelFixPlugin.injectedClass = injectedClass.trim();
            if (injectedMethod != null && !injectedMethod.isEmpty())
                IntelFixPlugin.injectedMethod = injectedMethod.trim();
        } else {
            IntelFixPlugin.useLegacy = useLegacy;

            if (injectedClass != null && !injectedClass.isEmpty())
                IntelFixPlugin.injectedClass = injectedClass.trim();
            else
                IntelFixPlugin.injectedClass =
                    useLegacy ?
                        "net.minecraft.client.renderer.OpenGlHelper" :
                        "net.minecraft.client.renderer.Tessellator";

            if (injectedMethod != null && !injectedMethod.isEmpty())
                IntelFixPlugin.injectedMethod = injectedMethod.trim();
            else
                IntelFixPlugin.injectedMethod = useLegacy ? "setActiveTexture(I)V" : "draw()I";
        }
    }
}
