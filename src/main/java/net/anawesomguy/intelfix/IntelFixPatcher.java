package net.anawesomguy.intelfix;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.logging.Level;

import static net.anawesomguy.intelfix.IntelFixPlugin.*;

// https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/1294926-themastercavers-world?page=13#c294
public final class IntelFixPatcher {
    public static byte[] patch(String name, byte[] bytes) {
        ClassWriter writer = new ClassWriter(0);
        boolean[] patched = {false};
        ClassVisitor visitor = new PatchCV(writer, patched, name, obfuscatedNames);

        new ClassReader(bytes).accept(visitor, 0);

        if (!patched[0]) {
            LOGGER.log(Level.WARNING, "Did not apply patch, method \"{0}\" not found in class!", injectedMethod);
            return bytes;
        }

        return writer.toByteArray();
    }

    // launchwrapper
    public static class LW implements IClassTransformer {
        @Override
        public byte[] transform(String clsName, String deobfName, byte[] bytes) {
            if (bytes == null || !(obfuscatedNames ? clsName : deobfName).equals(injectedClass))
                return bytes;
            return patch(injectedClass, bytes);
        }
    }

    // pre-launchwrapper
    public static class Old implements cpw.mods.fml.relauncher.IClassTransformer {
        @Override
        public byte[] transform(String name, byte[] bytes) {
            return new byte[0];
        }
    }

    // javaagent
    public static class JA implements ClassFileTransformer {
        public static void premain(String args, Instrumentation inst) {
            inst.addTransformer(new JA());
        }

        @Override
        public byte[] transform(ClassLoader cl, String name, Class<?> cls, ProtectionDomain pd, byte[] bytes) {
            return ;
        }
    }

    private IntelFixPatcher() {
    }
}
