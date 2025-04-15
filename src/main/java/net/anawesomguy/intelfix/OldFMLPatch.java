package net.anawesomguy.intelfix;

public final class OldFMLPatch implements cpw.mods.fml.relauncher.IClassTransformer {
    @Override
    public byte[] transform(String name, byte[] bytes) {
        return IntelFix.findAndPatch(name, bytes);
    }
}
