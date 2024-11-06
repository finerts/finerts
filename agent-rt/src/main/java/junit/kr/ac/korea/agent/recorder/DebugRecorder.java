package junit.kr.ac.korea.agent.recorder;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

import junit.kr.ac.korea.agent.data.AgentDataset;
import junit.kr.ac.korea.agent.data.EmptyDataset;
import junit.kr.ac.korea.agent.instrumentor.Strategy.Context;

public class DebugRecorder extends RecorderService {
    private static final Logger LOGGER = Logger.getLogger(DebugRecorder.class.getName());

    public DebugRecorder(Path root, Set<String> classes, Function<Path, AgentDataset> dataset, boolean dryrun) {
        super(root, classes, dataset, dryrun);
    }

    public static void visit(String className) {
        if (data() != EmptyDataset.EMPTY) LOGGER.info(className);
    }

    public static void visit(String className, String methodName) {
        if (data() != EmptyDataset.EMPTY) LOGGER.info(className + "." + methodName);
    }

    @Override
    public TraceCallee createCallee() {
        return new TraceCallee() {

            @Override
            public void traceClassName(Context context) {
                // TODO Auto-generated method stub
                throw new UnsupportedOperationException("Unimplemented method 'traceClassName'");
            }

            @Override
            public void traceMethodAndClass(Context context) {
                // TODO Auto-generated method stub
                throw new UnsupportedOperationException("Unimplemented method 'traceMethodAndClass'");
            }

            @Override
            public void traceInstanceMethod(Context context) {
                // TODO Auto-generated method stub
                throw new UnsupportedOperationException("Unimplemented method 'traceInstanceMethod'");
            }

            @Override
            public void traceRuntimeMethod(Context context) {
                // TODO Auto-generated method stub
                throw new UnsupportedOperationException("Unimplemented method 'traceRuntimeMethod'");
            }
            
        };
    }
}
