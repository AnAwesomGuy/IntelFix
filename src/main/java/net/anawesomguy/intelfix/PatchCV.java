package net.anawesomguy.intelfix;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.logging.Level;

import static net.anawesomguy.intelfix.IntelFixPlugin.*;

public class PatchCV extends ClassVisitor {
    final boolean[] patch;
    final String cls;
    final boolean isObf;

    public PatchCV(ClassVisitor cv, boolean[] patch, String cls, boolean isObf) {
        super(Opcodes.ASM4, cv);
        this.patch = patch;
        this.cls = cls;
        this.isObf = isObf;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                                     String[] exceptions) {
        MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
        if (!patch[0]) {
            final String method;
            if (isObf)
                method = name.concat(desc);
            else {
                method = FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(cls, name, desc).concat(desc);
                LOGGER.log(Level.FINER, "Remapped \"{0}{1}\" in \"{2}\" to \"{3}\"",
                           new Object[]{name, desc, cls, method});
            }

            if (method.equals(injectedMethod))
                return new PatchMV(visitor, patch, method);
        }

        return visitor;
    }
}
