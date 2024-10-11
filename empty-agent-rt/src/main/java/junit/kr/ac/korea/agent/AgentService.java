package junit.kr.ac.korea.agent;

import java.lang.instrument.Instrumentation;

public class AgentService {
    public void accept(Class<?> cls) {
    }

    public void accept(String name) {
        
    }

    public void terminate() {

    }

    public void pause() {
        
    }

    static void premain(String options, Instrumentation inst) {
    }
}