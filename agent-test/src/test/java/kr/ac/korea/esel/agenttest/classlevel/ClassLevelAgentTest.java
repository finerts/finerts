//package kr.ac.korea.esel.agenttest.classlevel;
//
//import junit.kr.ac.korea.esel.agent.InternalAgent.MonitorLevel;
//import kr.ac.korea.esel.agenttest.AgentScene;
//import kr.ac.korea.esel.agenttest.InstrumentedClass;
//import kr.ac.korea.esel.agenttest.TargetClass;
//import kr.ac.korea.esel.rts.hash.ClassLevelHash;
//import org.junit.jupiter.api.Assertions;
//import org.junit.jupiter.api.BeforeAll;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.File;
//import java.io.IOException;
//import java.net.URISyntaxException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.Set;
//
//public class ClassLevelAgentTest extends AgentScene {
//    private static final Logger logger = LoggerFactory.getLogger(ClassLevelAgentTest.class);
//    private static long InstrumentedClassHash;
//
//    public ClassLevelAgentTest() {
//        super(MonitorLevel.Class);
//    }
//
//    private static long computeHash(Class<?> cls) throws URISyntaxException, IOException {
//        Path cp = Paths.get(cls.getProtectionDomain().getCodeSource().getLocation().toURI());
//        cp = cp.resolve(cls.getName().replace('.', File.separatorChar) + ".class");
//        Assertions.assertTrue(Files.exists(cp), cp.toString());
//        return ClassLevelHash.compute(cp);
//    }
//
//    @BeforeAll
//    public static void computeHash() throws URISyntaxException, IOException {
//        InstrumentedClassHash = computeHash(InstrumentedClass.class);
//    }
//
//    @Override
//    protected void assertInstanceCreation(Set<Long> ids) throws URISyntaxException, IOException {
//        Assertions.assertTrue(ids.remove(InstrumentedClassHash));
//    }
//
//    @Override
//    protected void assertInstanceMethod(Set<Long> ids) throws URISyntaxException, IOException {
//        Assertions.assertTrue(ids.remove(InstrumentedClassHash));
//    }
//
//    @Override
//    protected void assertStaticMethod(Set<Long> ids) throws URISyntaxException, IOException {
//        Assertions.assertTrue(ids.remove(InstrumentedClassHash));
//
//    }
//
//    @Override
//    protected void assertTwoClasses(Set<Long> ids) throws URISyntaxException, IOException {
//        Assertions.assertTrue(ids.remove(InstrumentedClassHash));
//        Assertions.assertTrue(ids.remove(computeHash(TargetClass.class)));
//    }
//}
