package kr.ac.korea.esel.rts.hash;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

import kr.ac.korea.esel.rts.hash.encoder.BaseClassEncoder;
import kr.ac.korea.esel.rts.hash.encoder.Hasher;
import kr.ac.korea.esel.rts.hash.encoder.MethodHashItem;

public class hyRTSf extends BaseClassEncoder {
    private Map<String, MethodHashItem> methods = new TreeMap<>();

    public static hyRTSf create(byte[] in) {
        return create(new ClassReader(in));
    }

    public static hyRTSf create(ClassReader in) {
        hyRTSf ret = new hyRTSf();
        in.accept(ret, 0);
        return ret;
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
        String className = internalClassName();
        for (Map.Entry<String, MethodHashItem> entry : methods.entrySet()) {
            String key = entry.getKey();
            MethodHashItem item = entry.getValue();
            if (item.isOverridable()) {
                // This helps to detect instance method addition and deletion at
                // the class level.
                classHeaderHash.update(key);
            }

            Hasher hash = item.hasher();
            hash.update(className);
            hash.update(classHeaderHash);
        }
    }

    @Override
    protected Hasher beforeVisitMethod(int access, String name,
            String descriptor, String signature, String[] exceptions) {
        MethodHashItem item = new MethodHashItem(access, name, descriptor);
        methods.put(item.methodSignature(), item);

        return item.hasher();
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
