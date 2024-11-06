package junit.kr.ac.korea.agent.instrumentor;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import junit.kr.ac.korea.agent.recorder.TraceCallee;
import kr.ac.korea.esel.rts.hash.MethodLevel;
import kr.ac.korea.esel.rts.hash.encoder.ClassHashItem;

/**
 * Adapter that instruments a class for coverage tracing.
 */
public class ProposedStrategy extends Strategy {
    private final TraceCallee callee;

    public ProposedStrategy(final TraceCallee callee) {
        this.callee = callee;
    }

    public static final class Extra {
        public final int methodHash;
        public final int classHash;
        public final int declaringClassNameHash;
        public final int signatureHash;

        public Extra(int methodHash, int classHash, int declaringClassNameHash, int signatureHash) {
            this.methodHash = methodHash;
            this.classHash = classHash;
            this.declaringClassNameHash = declaringClassNameHash;
            this.signatureHash = signatureHash;
        }
    }

    @Override
    ClassHashItem createHash(ClassReader reader) {
        return MethodLevel.create(reader);
    }

    @Override
    protected MethodVisitor createTracer(ClassVisitor cv, MethodVisitor visitor, ClassHashItem hash, int access, String methodName, String methodDesc) {
        return new Context(cv, visitor, hash, access, methodName, methodDesc) {
            @Override
            public void visitCode() {
                super.visitCode();
                if (isFinalMethod()) callee.traceMethodAndClass(this);
                else callee.traceRuntimeMethod(this);
            }
        };
    }
}
