package net.anawesomguy.intelfix;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import net.minecraft.launchwrapper.IClassTransformer;
import org.lwjgl.opengl.GL13;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.logging.Level;

import static net.anawesomguy.intelfix.IntelFixPlugin.*;
import static org.objectweb.asm.Opcodes.*;

public final class IntelFixTransformer implements IClassTransformer {
    @Override
    public byte[] transform(final String clsName, String deobfName, byte[] bytes) {
        if (bytes == null || !(obfuscatedNames ? clsName : deobfName).equals(injectedClass))
            return bytes;
        LOGGER.log(Level.FINE, "Found target class \"{0}\" ({1}), attempting to patch!",
                   new Object[]{deobfName, clsName});
        final boolean[] patched = {false};
        ClassWriter writer = new ClassWriter(0);
        ClassVisitor visitor = new ClassVisitor(ASM4, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                                             String[] exceptions) {
                MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
                if (!patched[0]) {
                    final String method;
                    if (obfuscatedNames)
                        method = name.concat(desc);
                    else {
                        method = FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(clsName, name, desc).concat(desc);
                        LOGGER.log(Level.FINER, "Remapped \"{0}{1}\" in \"{2}\" to \"{3}\"",
                                   new Object[]{name, desc, clsName, method});
                    }

                    if (method.equals(injectedMethod))
                        return new MethodVisitor(ASM4, visitor) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                LOGGER.log(Level.FINE, "Injecting patch into \"{0}\"", method);
                                patched[0] = true;
                                mv.visitLabel(new Label());
                                if (useLegacy)
                                    mv.visitVarInsn(ILOAD, 0);
                                else
                                    mv.visitLdcInsn(GL13.GL_TEXTURE0);
                                mv.visitMethodInsn(INVOKESTATIC, glHelperClass, setClientTexture, "(I)V");
                            }
                        };
                }

                return visitor;
            }
        };

        new ClassReader(bytes).accept(visitor, 0);

        if (!patched[0]) {
            LOGGER.log(Level.WARNING, "Did not apply patch, method \"{0}\" not found in class!", injectedMethod);
            return bytes;
        }

        return writer.toByteArray();
    }
}
