package kr.ac.korea.esel.rts.hash;

import java.util.Map;
import java.util.TreeMap;

import org.objectweb.asm.ClassReader;

import kr.ac.korea.esel.rts.hash.encoder.BaseClassEncoder;
import kr.ac.korea.esel.rts.hash.encoder.Hasher;
import kr.ac.korea.esel.rts.hash.encoder.MethodHashItem;

public class MethodLevel extends BaseClassEncoder {
    private Map<String, MethodHashItem> methods = new TreeMap<>();

    public static MethodLevel create(byte[] in) {
        return create(new ClassReader(in));
    }

    public static MethodLevel create(ClassReader in) {
        MethodLevel ret = new MethodLevel();
        in.accept(ret, 0);
        return ret;
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
        String className = internalClassName();
        long classHash = classHeaderHash.get();
        for (MethodHashItem item : methods.values()) {
            // This is necessary to distinguish the same method bodies in
            // different classes.
            Hasher hash = item.hasher();
            hash.update(className);
            hash.update(classHash);
        }
    }

    @Override
    protected Hasher beforeVisitMethod(int access, String name,
            String descriptor, String signature, String[] exceptions) {
        MethodHashItem m = new MethodHashItem(access, name, descriptor);
        methods.put(m.methodSignature(), m);

        return m.hasher();
    }

    @Override
    public Iterable<MethodHashItem> iterateMethods() {
        return methods.values();
    }

    @Override
    public MethodHashItem method(String name, String desc) {
        return methods.get(name + desc);
    }
}
