package kr.ac.korea.esel.rts.hash;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.objectweb.asm.ClassReader;

import kr.ac.korea.esel.rts.hash.encoder.BaseClassEncoder;
import kr.ac.korea.esel.rts.hash.encoder.ClassHashItem;
import kr.ac.korea.esel.rts.hash.encoder.Hasher;
import kr.ac.korea.esel.rts.hash.encoder.MethodHashItem;

public class ClassLevel extends BaseClassEncoder {
    private Map<String, Hasher> methods = new TreeMap<>();

    public static ClassHashItem create(byte[] in) {
        return create(new ClassReader(in));
    }

    public static ClassHashItem create(ClassReader in) {
        ClassLevel ret = new ClassLevel();
        in.accept(ret, 0);
        return ret;
    }

    @Override
    protected Hasher beforeVisitMethod(int access, String name,
            String descriptor, String signature, String[] exceptions) {
        Hasher ret = new Hasher();
        String key = name + descriptor;
        methods.put(key, ret);

        ret.update(access);
        ret.update(key);

        return ret;
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
        for (Map.Entry<String, Hasher> entry : methods.entrySet()) {
            classHeaderHash.update(entry.getKey());
            classHeaderHash.update(entry.getValue());
        }
    }

    @Override
    public Iterable<MethodHashItem> iterateMethods() {
        return Collections.emptyList();
    }

    @Override
    public MethodHashItem method(String name, String desc) {
        return null;
    }
}
