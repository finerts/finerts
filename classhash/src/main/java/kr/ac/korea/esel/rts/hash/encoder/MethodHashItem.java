package kr.ac.korea.esel.rts.hash.encoder;

import org.objectweb.asm.Opcodes;

public class MethodHashItem {
    private final int access;
    private final String methodSignature;
    private final String name;
    private final String desc;
    private final int methodSignatureHash;
    private final Hasher hash = new Hasher();

    /**
     * @param isInstanceMethod
     * @param hash
     */
    public MethodHashItem(int access, String name, String desc) {
        this.methodSignature = name + desc;
        this.access = access;
        this.name = name;
        this.desc = desc;

        hash.update(name);
        hash.update(desc);
        this.methodSignatureHash = hash.get();
    }

    public int signatureHash() {
        return methodSignatureHash;
    }

    public String methodSignature() {
        return methodSignature;
    }

    public String desc() {
        return desc;
    }

    public String name() {
        return name;
    }

    public int methodHash() {
        return hash.get();
    }

    public boolean isPrivateMethod() {
        return !hasAccess(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED);
    }

    public boolean isFinalMethod() {
        if (hasAccess(Opcodes.ACC_STATIC)) return true;
        if (name.endsWith("init>")) return true;
        return !hasAccess(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED);
    }

    public boolean isOverridable() {
        return !(isPrivateMethod() || isFinalMethod());
    }

    public boolean hasAccess(int code) {
        return (access & (Opcodes.ACC_STATIC)) != 0;
    }

    public Hasher hasher() { return hash; }
}
