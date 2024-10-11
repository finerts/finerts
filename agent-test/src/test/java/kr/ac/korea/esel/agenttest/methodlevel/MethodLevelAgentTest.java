//package kr.ac.korea.esel.agenttest.methodlevel;
//
//import junit.kr.ac.korea.esel.agent.InternalAgent.MonitorLevel;
//import kr.ac.korea.esel.agenttest.AgentScene;
//import kr.ac.korea.esel.agenttest.InstrumentedClass;
//import kr.ac.korea.esel.agenttest.TargetClass;
//import kr.ac.korea.esel.rts.hash.MethodLevelHash;
//import org.junit.jupiter.api.Assertions;
//
//import java.io.File;
//import java.io.IOException;
//import java.net.URISyntaxException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.Set;
//
//public class MethodLevelAgentTest extends AgentScene {
//
//    private static final MethodLevelHash InstrumentedHash;
//
//    static {
//        try {
//            InstrumentedHash = computeHash(InstrumentedClass.class);
//        } catch (URISyntaxException | IOException ex) {
//            throw new RuntimeException(ex);
//        }
//    }
//
//    public MethodLevelAgentTest() {
//        super(MonitorLevel.Method);
//    }
//
//    private static MethodLevelHash computeHash(Class<?> cls) throws URISyntaxException, IOException {
//        Path cp = Paths.get(cls.getProtectionDomain().getCodeSource().getLocation().toURI());
//        cp = cp.resolve(cls.getName().replace('.', File.separatorChar) + ".class");
//        Assertions.assertTrue(Files.exists(cp), cp.toString());
//        return MethodLevelHash.compute(cp);
//    }
//
//    @Override
//    protected void assertInstanceCreation(Set<Long> ids) throws URISyntaxException, IOException {
//        Assertions.assertTrue(ids.remove(InstrumentedHash.getClassId()));
//        Assertions.assertTrue(ids.remove(InstrumentedHash.getMethodId("<init>", "()V")));
//    }
//
//    @Override
//    protected void assertInstanceMethod(Set<Long> ids) throws URISyntaxException, IOException {
//        Assertions.assertTrue(ids.remove(InstrumentedHash.getClassId()));
//        Assertions.assertTrue(ids.remove(InstrumentedHash.getMethodId("<init>", "()V")));
//        Assertions.assertTrue(ids.remove(InstrumentedHash.getMethodId("testOwnField", "()V")));
//    }
//
//    @Override
//    protected void assertStaticMethod(Set<Long> ids) throws URISyntaxException, IOException {
//        Assertions.assertTrue(ids.remove(InstrumentedHash.getClassId()));
//        Assertions.assertTrue(ids.remove(InstrumentedHash.getMethodId("staticMethod", "()V")));
//    }
//
//    @Override
//    protected void assertTwoClasses(Set<Long> ids) throws URISyntaxException, IOException {
//        MethodLevelHash hashes = computeHash(TargetClass.class);
//        Assertions.assertTrue(ids.remove(InstrumentedHash.getClassId()));
//        Assertions.assertTrue(ids.remove(InstrumentedHash.getMethodId("testInstanceFieldAccess", "()V")));
//        Assertions.assertTrue(ids.remove(InstrumentedHash.getMethodId("<init>", "()V")));
//        Assertions.assertTrue(ids.remove(hashes.getMethodId("<init>", "()V")));
//        Assertions.assertTrue(ids.remove(hashes.getClassId()));
//    }
//}
