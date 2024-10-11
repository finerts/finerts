//package kr.ac.korea.esel.agenttest;
//
//import it.unimi.dsi.fastutil.longs.LongSet;
//import junit.kr.ac.korea.esel.agent.Agent;
//import junit.kr.ac.korea.esel.agent.InternalAgent.MonitorLevel;
//import kr.ac.korea.esel.rts.data.Dependencies;
//import org.junit.jupiter.api.Assertions;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//import java.io.IOException;
//import java.net.URISyntaxException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.Map;
//import java.util.Set;
//
//public abstract class AgentScene {
//    private final static Path artifact = Paths.get("deps.bin");
//    private final MonitorLevel level;
//
//    public AgentScene(MonitorLevel level) {
//        this.level = level;
//    }
//
//    protected abstract void assertInstanceCreation(Set<Long> ids) throws URISyntaxException, IOException;
//
//    protected abstract void assertInstanceMethod(Set<Long> ids) throws URISyntaxException, IOException;
//
//    protected abstract void assertStaticMethod(Set<Long> ids) throws URISyntaxException, IOException;
//
//    protected abstract void assertTwoClasses(Set<Long> ids) throws URISyntaxException, IOException;
//
//    @Test
//    public void testInstanceCreation() throws IOException, URISyntaxException {
//        Agent.startSession("test");
//        InstrumentedClass obj = new InstrumentedClass();
//        Agent.endSession("test");
//        Agent.dumpResult();
//        LongSet ids = Dependencies.load(artifact).get("test");
//        assertInstanceCreation(ids);
//        Assertions.assertTrue(ids.isEmpty());
//    }
//
//    @BeforeEach
//    public void resetAgent() throws IOException {
//        Agent.reset();
//        Files.deleteIfExists(artifact);
//    }
//
//    @Test
//    public void testInstanceMethod() throws IOException, URISyntaxException {
//        Agent.startSession("test");
//        InstrumentedClass obj = new InstrumentedClass();
//        obj.testOwnField();
//        Agent.endSession("test");
//        Agent.dumpResult();
//        LongSet ids = Dependencies.load(artifact).get("test");
//        assertInstanceMethod(ids);
//        Assertions.assertTrue(ids.isEmpty());
//    }
//
//    @Test
//    public void testStaticMethod() throws IOException, URISyntaxException {
//        Agent.startSession("test");
//        InstrumentedClass.staticMethod();
//        Agent.endSession("test");
//        Agent.dumpResult();
//        LongSet ids = Dependencies.load(artifact).get("test");
//        assertStaticMethod(ids);
//        Assertions.assertTrue(ids.isEmpty());
//    }
//
//    @Test
//    public void testTwoClasses() throws IOException, URISyntaxException {
//        Agent.startSession("test");
//        InstrumentedClass obj = new InstrumentedClass();
//        obj.testInstanceFieldAccess();
//        Agent.endSession("test");
//        Agent.dumpResult();
//        LongSet ids = Dependencies.load(artifact).get("test");
//        assertTwoClasses(ids);
//        Assertions.assertTrue(ids.isEmpty());
//    }
//
//    @Test
//    public void testMultipleSession() throws IOException, URISyntaxException {
//        Agent.startSession("test");
//        new InstrumentedClass();
//        Agent.endSession("test");
//        Agent.startSession("test2");
//        Agent.endSession("test2");
//        Agent.dumpResult();
//
//        Map<String, LongSet> ids = Dependencies.load(artifact);
//        Assertions.assertTrue(ids.get("test2").isEmpty());
//        Assertions.assertFalse(ids.get("test").isEmpty());
//    }
//}
