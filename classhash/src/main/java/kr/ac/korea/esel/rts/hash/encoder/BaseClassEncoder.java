package kr.ac.korea.esel.rts.hash.encoder;

import org.objectweb.asm.*;

import java.util.*;

public abstract class BaseClassEncoder extends ClassVisitor implements ClassHashItem {
    protected static final int ACC_OVERRIDING_EXTERNAL = Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC;
    protected BaseClassEncoder() {
        super(Opcodes.ASM9);
    }

    @Override
    public String internalClassName() {
        return className;
    }

    @Override
    public int classHash() {
        return classHeaderHash.get();
    }

    public final Hasher classHeaderHash = new Hasher();
    private final Map<String, Hasher> fields = new TreeMap<>();
    private String className;

    private static class MethodEncoder extends MethodVisitor {

        private Hasher crc;

        MethodEncoder(Hasher crc) {
            super(Opcodes.ASM9);
            this.crc = crc;
        }

        @Override
        public void visitInsn(int opcode) {
            crc.update(opcode);
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            crc.update(opcode);
            crc.update(operand);
        }

        @Override
        public void visitVarInsn(int opcode, int varCode) {
            crc.update(opcode);
            crc.update(varCode);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            crc.update(opcode);
            crc.update(type);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name,
                String descriptor) {
            crc.update(opcode);
            crc.update(owner);
            crc.update(name);
            crc.update(descriptor);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                String descriptor, boolean isInterface) {
            crc.update(opcode);
            crc.update(owner);
            crc.update(name);
            crc.update(descriptor);
            crc.update(isInterface);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor,
                Handle bootstrapMethodHandle,
                Object... bootstrapMethodArguments) {
            crc.update(name);
            crc.update(descriptor);
            crc.update(bootstrapMethodHandle);
            for (Object o : bootstrapMethodArguments) {
                crc.update(o);
            }
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            crc.update(opcode);
        }

        @Override
        public void visitLdcInsn(Object value) {
            crc.update(value);
        }

        @Override
        public void visitIincInsn(int varCode, int increment) {
            crc.update(varCode);
            crc.update(increment);
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt,
                Label... labels) {
            crc.update(min);
            crc.update(max);
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys,
                Label[] labels) {
            for (int key : keys) {
                crc.update(key);
            }
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor,
                int numDimensions) {
            crc.update(descriptor);
            crc.update(numDimensions);
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler,
                String type) {
            if (type == null) {
                type = "finally";
            }

            crc.update(type);
        }

        private AnnotationVisitor createAnnotationVisitor(boolean isVisible, String desc) {
            if (isVisible) {
                crc.update(desc);
                return new AnnotationEncoder(crc);
            } else {
                return null;
            }
        }

        private AnnotationVisitor createAnnotationVisitor(boolean isVisible, int typeRef, TypePath typePath, String desc) {
            if (isVisible) {
                crc.update(desc);
                return new AnnotationEncoder(typeRef, typePath, crc);
            } else {
                return null;
            }
        }

        @Override
        public AnnotationVisitor visitAnnotationDefault() {
            return createAnnotationVisitor(true, "default");
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor,
                boolean visible) {
            return createAnnotationVisitor(visible, descriptor);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef,
                TypePath typePath, String descriptor, boolean visible) {
            return createAnnotationVisitor(visible, typeRef, typePath, descriptor);
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter,
                String descriptor, boolean visible) {
            return createAnnotationVisitor(visible, descriptor);
        }

        @Override
        public AnnotationVisitor visitInsnAnnotation(int typeRef,
                TypePath typePath, String descriptor, boolean visible) {
            return createAnnotationVisitor(visible, typeRef, typePath, descriptor);
        }

        @Override
        public AnnotationVisitor visitTryCatchAnnotation(int typeRef,
                TypePath typePath, String descriptor, boolean visible) {
            return createAnnotationVisitor(visible, typeRef, typePath, descriptor);
        }
    }

    private static class FieldEncoder extends FieldVisitor {

        private final Hasher digest;

        FieldEncoder(Hasher digest) {
            super(Opcodes.ASM9);
            this.digest = digest;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor,
                boolean visible) {
            if (visible) {
                return new AnnotationEncoder(digest);
            }

            return null;
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef,
                TypePath typePath, String descriptor, boolean visible) {
            if (visible) {
                return new AnnotationEncoder(typeRef, typePath, digest);
            }

            return null;
        }

        @Override
        public void visitAttribute(Attribute attribute) {
            throw new ClassEncoderException("Ignore attributes");
        }
    }

    @Override
    public void visit(int version, int access, String name, String signature,
            String superName, String[] interfaces) {
        classHeaderHash.update(version);
        classHeaderHash.update(name);
        classHeaderHash.update(superName);

        this.className = name;

        if (interfaces != null) {
            Arrays.sort(interfaces);
            for (String itf : interfaces) {
                classHeaderHash.update(itf);
            }
        }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor,
            boolean visible) {
        if (visible) {
            classHeaderHash.update(descriptor);
            return new AnnotationEncoder(classHeaderHash);
        }

        return null;
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath,
            String descriptor, boolean visible) {
        if (visible) {
            classHeaderHash.update(descriptor);
            return new AnnotationEncoder(typeRef, typePath, classHeaderHash);
        }

        return null;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor,
            String signature, Object value) {
        if (value != null) {
            Hasher field = new Hasher();
            if (fields.put(name, field) != null) {
                throw new ClassEncoderException("Duplicated field");
            }

            field.update(name);
            field.update(descriptor);
            field.update(value);

            return new FieldEncoder(field);
        }

        return null;
    }

    @Override
    public void visitEnd() {
        for (Hasher field : fields.values()) {
            classHeaderHash.update(field);
        }
    }

    protected abstract Hasher beforeVisitMethod(int access, String name,
            String descriptor, String signature, String[] exceptions);

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
            String signature, String[] exceptions) {
        if ((access & Opcodes.ACC_ABSTRACT) != 0)
            return null;
        if (name.equals("<clinit>")) {
            classHeaderHash.update(name);
            return new MethodEncoder(classHeaderHash);
        }

        return new MethodEncoder(beforeVisitMethod(access, name, descriptor,
                signature, exceptions));
    }

    public static final class ClassEncoderException extends RuntimeException {
        ClassEncoderException(String msg) {
            super(msg);
        }
    }
}
