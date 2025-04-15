package net.anawesomguy.intelfix;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import net.minecraft.launchwrapper.IClassTransformer;
import org.lwjgl.opengl.GL13;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.logging.Level;

import static net.anawesomguy.intelfix.IntelFix.*;

public final class LaunchWrapperPatch implements IClassTransformer {
    @Override
    public byte[] transform(String clsName, String deobfName, byte[] bytes) {
        final boolean obfNames = obfuscatedNames;
        final String cls = injectedClass;
        if (bytes == null || !(obfNames ? clsName : deobfName).equals(cls))
            return bytes;

        LOGGER.log(Level.FINE, "Found class to patch: '{0}' ({1})", new Object[]{clsName, deobfName});
        final boolean[] patched = {false};

        String method = injectedMethod;
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM4, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                                             String[] exceptions) {
                MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
                if (patched[0] || "<init>".equals(name))
                    return visitor;

                final String method;
                if (obfNames)
                    method = name.concat(desc);
                else {
                    method = FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(cls, name, desc).concat(desc);
                    IntelFix.LOGGER.log(Level.FINER, "Remapped '{0}{1}' in '{2}' to '{3}'",
                                        new Object[]{name, desc, cls, method});
                }

                if (method.equals(injectedMethod))
                    return new MethodVisitor(Opcodes.ASM4, visitor) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            IntelFix.LOGGER.log(Level.FINE, "Injecting patch into '{0}'", injectedMethod);
                            patched[0] = true;
                            if (IntelFix.useLegacy)
                                mv.visitVarInsn(Opcodes.ILOAD, 0); // load the param (legacy)
                            else
                                mv.visitLdcInsn(GL13.GL_TEXTURE0); // 9 levels of indents lol
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, IntelFix.glHelperClass, IntelFix.setClientTexture, "(I)V");
                        }
                    };

                return visitor;
            }
        }, 0);

        if (!patched[0]) {
            LOGGER.log(Level.WARNING, "Did not apply patch, method '{0}' not found in class!", method);
            return bytes;
        }

        return writer.toByteArray();
    }
}
