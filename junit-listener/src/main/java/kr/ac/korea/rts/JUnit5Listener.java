package kr.ac.korea.rts;

import java.util.logging.Logger;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import junit.kr.ac.korea.agent.AgentService;

public class JUnit5Listener implements TestExecutionListener {
    private static Logger log = Logger.getLogger("experiment");
    private static final AgentService service = new AgentService();

    private boolean hasFailure = false;

    public boolean hasFailure() {
        return hasFailure;
    }
    
    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        TestSource source = testIdentifier.getSource().orElse(null);

        if (source instanceof ClassSource) {
            // log.info(((ClassSource)source).getClassName());
            ClassSource current = (ClassSource)source;
            service.accept(current.getJavaClass());
        } else if (source instanceof MethodSource) {
            MethodSource current = (MethodSource)source;
            service.accept(current.getJavaClass());
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        service.terminate();
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (testExecutionResult.getStatus() == TestExecutionResult.Status.FAILED) {
            hasFailure = true;
            log.severe("########## Test failed. ###############");
            log.severe(testIdentifier.toString());
            testExecutionResult.getThrowable().ifPresent(e -> {
                log.severe(e.toString());
                e.printStackTrace();
            });
        }

        service.pause();
	}
}
