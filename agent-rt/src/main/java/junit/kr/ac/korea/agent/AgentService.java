package junit.kr.ac.korea.agent;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import junit.kr.ac.korea.agent.data.AgentResult;
import junit.kr.ac.korea.agent.recorder.RecorderService;

public class AgentService {
    private static final Logger LOGGER = Logger.getLogger(AgentService.class.getName());
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    static RecorderService recorder;

    private static void dump(AgentResult result) {
        if (result != null) {
            executor.submit(() -> {
                try {
                    result.dump();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    public void accept(Class<?> cls) {
        dump(recorder.accept(cls));
    }

    public void pause() {
        recorder.pause();
    }

    public void terminate() {
        dump(recorder.terminate());
        
        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.warning(() -> e.toString());
        }
    }

    public void accept(String className) {
        try {
            accept(Class.forName(className));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}