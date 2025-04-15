package net.anawesomguy.intelfix;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public final class IntelFixAgent implements ClassFileTransformer {
    public static void premain(String args, Instrumentation inst) {
        FMLType.CURRENT = FMLType.NO_FML;
        IntelFix.loadConfig();
        inst.addTransformer(new IntelFixAgent());
    }

    @Override
    public byte[] transform(ClassLoader loader, String name, Class<?> cls, ProtectionDomain pd, byte[] bytes) {
        return IntelFix.findAndPatch(name, bytes);
    }
}
