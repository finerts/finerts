package kr.ac.korea.esel.rts.hash.encoder;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;

import java.util.Map;
import java.util.TreeMap;

class AnnotationEncoder extends AnnotationVisitor {

    private final Hasher digest;
    private final Map<String, Hasher> children = new TreeMap<>();

    AnnotationEncoder(int typeRef, TypePath typePath, Hasher parent) {
        this(parent);
        this.digest.update(typeRef);
        if (typePath != null) {
            this.digest.update(typePath.toString());
        }
    }

    AnnotationEncoder(Hasher digest) {
        super(Opcodes.ASM9);
        this.digest = digest;
    }

    @Override
    public void visit(String name, Object value) {
        getDigest(name).update(value);
    }

    private Hasher getDigest(String name) {
        if (name == null) {
            name = "<null>";
        }
        return children.computeIfAbsent(name, s -> new Hasher());
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
        getDigest(name).update(descriptor);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
        Hasher child = getDigest(name);
        child.update(descriptor);
        return new AnnotationEncoder(child);
    }

    @Override
    public void visitEnd() {
        for (Hasher child : children.values()) {
            digest.update(child);
        }
    }
}
