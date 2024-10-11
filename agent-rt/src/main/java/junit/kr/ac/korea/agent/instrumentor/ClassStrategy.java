package junit.kr.ac.korea.agent.instrumentor;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import junit.kr.ac.korea.agent.recorder.TraceCallee;
import kr.ac.korea.esel.rts.hash.ClassLevel;
import kr.ac.korea.esel.rts.hash.encoder.ClassHashItem;

/**
 * Adapter that instruments a class for coverage tracing.
 */
public class ClassStrategy extends Strategy {
    private final TraceCallee callee;

    public ClassStrategy(final TraceCallee callee) {
        this.callee = callee;
    }

    @Override
    protected MethodVisitor createTracer(ClassVisitor cv, MethodVisitor visitor, ClassHashItem hash, int access, String methodName, String methodDesc) {
        return new Context(cv, visitor, hash, access, methodName, methodDesc) {
            @Override
            public void visitCode() {
                super.visitCode();
                callee.traceClassName(this);
            }
        };
    }

    @Override
    ClassHashItem createHash(ClassReader reader) {
        return ClassLevel.create(reader);
    }
}
