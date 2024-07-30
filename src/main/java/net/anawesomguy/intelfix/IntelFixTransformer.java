package net.anawesomguy.intelfix;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

public final class IntelFixTransformer implements IClassTransformer {
    @Override
    public byte[] transform(final String name, final String transformedName, final byte[] bytes) {
        if (bytes == null || !IntelFixPlugin.injectedClass.equals(name))
            return bytes;
        IntelFixPlugin.LOGGER.fine("Found target class '" + name + "', attempting to patch!");
        final boolean[] patched = {false};
        final ClassWriter writer = new ClassWriter(0);
        final ClassVisitor visitor = new ClassVisitor(ASM4, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                                             String[] exceptions) {
                final MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
                final String method = name + desc;
                if (IntelFixPlugin.injectedMethod.equals(method))
                    return new MethodVisitor(ASM4, visitor) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            IntelFixPlugin.LOGGER.fine("Injecting patch into '" + method + "'");
                            patched[0] = false;
                            mv.visitLabel(new Label());
                            if (IntelFixPlugin.useLegacy)
                                mv.visitVarInsn(ILOAD, 0);
                            else
                                mv.visitFieldInsn(GETSTATIC, "net/minecraft/client/renderer/OpenGlHelper", "defaultTexUnit", "I");
                            mv.visitMethodInsn(INVOKESTATIC, "net/minecraft/client/renderer/OpenGlHelper", "setClientActiveTexture", "(I)V");
                        }
                    };
                return visitor;
            }
        };

        new ClassReader(bytes).accept(visitor, ClassReader.EXPAND_FRAMES);

        if (!patched[0]) {
            IntelFixPlugin.LOGGER.warning("Did not apply patch, method '" + IntelFixPlugin.injectedMethod + "' not found in class!");
            return bytes;
        }

        return writer.toByteArray();
    }
}
