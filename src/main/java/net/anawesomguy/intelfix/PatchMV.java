package net.anawesomguy.intelfix;

import org.objectweb.asm.MethodVisitor;
import org.lwjgl.opengl.GL13;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;

import java.util.logging.Level;

import static net.anawesomguy.intelfix.IntelFixPlugin.*;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

class PatchMV extends MethodVisitor {
    final boolean[] patch;
    final String mth;

    PatchMV(MethodVisitor mv, boolean[] patch, String mth) {
        super(Opcodes.ASM4, mv);
        this.patch = patch;
        this.mth = mth;
    }

    @Override
    public void visitCode() {
        super.visitCode();
        LOGGER.log(Level.FINE, "Injecting patch into \"{0}\"", mth);
        patch[0] = true;
        mv.visitLabel(new Label());
        if (useLegacy)
            mv.visitVarInsn(ILOAD, 0);
        else
            mv.visitLdcInsn(GL13.GL_TEXTURE0);
        mv.visitMethodInsn(INVOKESTATIC, glHelperClass, setClientTexture, "(I)V");
    }
}
