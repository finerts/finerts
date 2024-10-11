package junit.kr.ac.korea.agent.data;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public interface AgentResult {
    static String COV_SUFFIX = "";
    static Path ROOT = Paths.get(".");

    void dump(Path out) throws IOException;
}
