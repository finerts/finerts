package junit.kr.ac.korea.agent.data;

import java.io.IOException;

public interface AgentResult {
    static String COV_SUFFIX = ".cov";
    
    void dump() throws IOException;
}
