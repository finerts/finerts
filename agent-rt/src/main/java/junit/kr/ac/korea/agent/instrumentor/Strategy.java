package junit.kr.ac.korea.agent.instrumentor;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import kr.ac.korea.esel.rts.hash.encoder.ClassHashItem;
import kr.ac.korea.esel.rts.hash.encoder.Hasher;
import kr.ac.korea.esel.rts.hash.encoder.MethodHashItem;

public abstract class Strategy {
    public enum TraceParam {
        MethodHash,
        ClassHash,
        NameHash,
        ClassNameHash
    }

    public static abstract class Context extends MethodVisitor {
        private final ClassVisitor cv;
        private final ClassHashItem classHash;
        private final MethodHashItem methodHash;

        public Context(ClassVisitor cv, MethodVisitor visitor, ClassHashItem hash, int access, String methodName, String methodDesc) {
            super(Opcodes.ASM9, visitor);

            this.cv = cv;
            this.classHash = hash;
            this.methodHash = classHash.method(methodName, methodDesc);
        }

        public String internalClassName() {return classHash.internalClassName(); }

        public boolean isOverridable() {
            return methodHash.isOverridable();
        }

        public int value(TraceParam param) {
            switch(param) {
                case MethodHash: return methodHash.methodHash();
                case ClassHash: return classHash.classHash();
                case NameHash: return methodHash.signatureHash();
                case ClassNameHash: return classNameHash();
                default: throw new RuntimeException();
            }
        }

        private int classNameHash() {
            Hasher crc = new Hasher();
            crc.clear();
            crc.update(internalClassName().replace("/", "."));
            return crc.get();
        }

        public String methodName() {
            return methodHash.name();
        }

        public String methodDesc() {
            return methodHash.desc();
        }

        public boolean isPrivateMethod() {
            return methodHash.isPrivateMethod();
        }

        public boolean isFinalMethod() {
            return methodHash.isFinalMethod();
        }

        public void addCacheField(String name) {
            FieldVisitor fv = cv.visitField(Opcodes.ACC_PRIVATE, name, "J", null, 0);
            fv.visitEnd();
        }
    }

    public ClassVisitor create(ClassReader reader, ClassWriter cw) {
        ClassHashItem hash = createHash(reader);
        
        return new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(final int access, final String name,
                    final String desc, final String signature,
                    final String[] exceptions) {
                final MethodVisitor mv = super.visitMethod(access, name, desc,
                        signature, exceptions);
                if (name.equals("<clinit>"))
                    return mv;
                if ((access & Opcodes.ACC_ABSTRACT) != 0)
                    return mv;

                return createTracer(cv, mv, hash, access, name, desc);
            }
        };
    }

    abstract ClassHashItem createHash(ClassReader reader);

    protected abstract MethodVisitor createTracer(ClassVisitor cv, MethodVisitor visitor, ClassHashItem hash, int access, String methodName, String methodDesc);
}
