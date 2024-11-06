package junit.kr.ac.korea.agent.recorder;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;
import java.util.logging.Logger;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import junit.kr.ac.korea.agent.data.AgentDataset;
import junit.kr.ac.korea.agent.instrumentor.ProposedStrategy;
import junit.kr.ac.korea.agent.instrumentor.Strategy.Context;
import junit.kr.ac.korea.agent.instrumentor.Strategy.TraceParam;

public class AgentRecorder extends RecorderService {
    private static final Logger LOGGER = Logger.getLogger(AgentRecorder.class.getName());

    public static final class Extra {
        public final int methodHash;
        public final int classHash;
        public final int nameHash;
        public final int destClassNameHash;

        public Extra(int methodHash, int classHash, int nameHash, int destClassNameHash) {
            this.methodHash = methodHash;
            this.classHash = classHash;
            this.nameHash = nameHash;
            this.destClassNameHash = destClassNameHash;
        }
    }

    public static void visitClass(int hash) {
        data().addClass(hash);
    }

    public static void visitInstanceMethod(int methodHash) {
        data().addInstanceMethod(methodHash);
    }

    public static void visit(int m, int c) {
        data().add(m, c);
    }

    public static void visitOverridableMethod(Object src, long key, int extraId) {
        data().addOverriableMethod(src, key, extraId);
    }

    public static long computeProposedKey(Object obj, int nameHash) {
        Class<?> cls = obj.getClass();
        int identity = cls.hashCode();
        return (((long)nameHash) << Integer.SIZE) | identity;
    }

    public AgentRecorder(Path root, Set<String> classes, Function<Path, AgentDataset> dataset, boolean dryrun) {
        super(root, classes, dataset, dryrun);
    }

    public static final class CalleeSpec implements TraceCallee {
        private static final String RECORDER_NAME;
        private static final String[] TRACE_CLASS;
        private static final String[] TRACE_ALL;
        private static final String[] TRACE_INSTANCE_METHOD;
        private static final String[] TRACE_RUNTIME_METHOD;
        private static final String[] PROPOSED_KEY_COMPUTER;

        static {
            Class<?> recorder = AgentRecorder.class;
            RECORDER_NAME = recorder.getName().replace(".", "/"); 
            try {
                TRACE_CLASS = parse(recorder, "visitClass", int.class);
                TRACE_ALL = parse(recorder, "visit", int.class, int.class);
                TRACE_INSTANCE_METHOD = parse(recorder, "visitInstanceMethod", int.class);
                TRACE_RUNTIME_METHOD = parse(recorder, "visitOverridableMethod", Object.class, long.class, int.class);
                PROPOSED_KEY_COMPUTER = parse(recorder, "computeProposedKey", Object.class, int.class);
            } catch(NoSuchMethodException e) {
                throw new RuntimeException(e);   
            }
        }

        private static String[] parse(Class<?> cls, String name, Class<?>... params) throws NoSuchMethodException, SecurityException {
            Method method = cls.getDeclaredMethod(name, params);
            return new String[] {name, Type.getMethodDescriptor(method)};
        }
    
        private void call(Context context, String[] spec, Object... values) {
            for (Object o : values) {
                context.visitLdcInsn(o);
            }

            context.visitMethodInsn(Opcodes.INVOKESTATIC, RECORDER_NAME, spec[0], spec[1], false);
        }
    
        public void traceClassName(Context context) {
            call(context, TRACE_CLASS, context.value(TraceParam.ClassHash));
        }
    
        public void traceMethodAndClass(Context context) {
            call(context, TRACE_ALL, context.value(TraceParam.MethodHash), context.value(TraceParam.ClassHash));
        }
    
        public void traceInstanceMethod(Context context) {
            call(context, TRACE_INSTANCE_METHOD,  context.value(TraceParam.MethodHash));
        }
    
        public void traceRuntimeMethod(Context context) {
            int nameHash = context.value(TraceParam.NameHash);
            int dest = context.value(TraceParam.ClassNameHash);

            int contextIndex = CONTEXT_INDEX.incrementAndGet();
            Object extra = new ProposedStrategy.Extra(
                context.value(TraceParam.MethodHash),
                context.value(TraceParam.ClassHash),
                dest, nameHash);

            Lock l = CONTEXT_LOCK.writeLock();
            CONTEXT.put(contextIndex, extra);
            l.unlock();

            String fieldName = "___finerts_" + nameHash + "_key";
            context.addCacheField(fieldName);

            context.visitVarInsn(Opcodes.ALOAD, 0);
            context.visitInsn(Opcodes.DUP);
            context.visitFieldInsn(Opcodes.GETFIELD, context.internalClassName(), fieldName, "J");

            Label label = new Label();
            context.visitInsn(Opcodes.DUP2);
            context.visitLdcInsn(0L);
            context.visitInsn(Opcodes.LCMP);
            context.visitJumpInsn(Opcodes.IFNE, label);
            context.visitInsn(Opcodes.POP2);

            context.visitInsn(Opcodes.DUP);
            context.visitInsn(Opcodes.DUP);
            call(context, PROPOSED_KEY_COMPUTER, nameHash);
            context.visitInsn(Opcodes.DUP2_X1);
            context.visitFieldInsn(Opcodes.PUTFIELD, context.internalClassName(), fieldName, "J");

            context.visitLabel(label);
            call(context, TRACE_RUNTIME_METHOD, contextIndex);
        }
    }

    @Override
    public TraceCallee createCallee() {
        return new CalleeSpec();
    }
}
