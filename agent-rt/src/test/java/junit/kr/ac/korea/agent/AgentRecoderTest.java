//package junit.kr.ac.korea.agent;
//
//import kr.ac.korea.esel.rts.hash.CRC64Hash;
//import org.junit.jupiter.api.*;
//import org.objectweb.asm.Type;
//
//import java.io.IOException;
//import java.nio.CharBuffer;
//import java.nio.LongBuffer;
//import java.nio.MappedByteBuffer;
//import java.nio.channels.FileChannel;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.nio.file.StandardOpenOption;
//import java.util.*;
//import java.util.stream.Stream;
//
//public class AgentRecoderTest {
//    private static MappedByteBuffer agent;
//    private static final Path testRoot = Paths.get("testRoot");
//
//    @BeforeEach
//    public void initialize() throws IOException {
//        clear();
//        AgentRecorder.initialize(Files.createDirectories(testRoot).toString(), 2048);
//        agent = prepareMap("agent", 3 * 1000);
//    }
//
//    @AfterEach
//    public void clear() {
//        if (Files.exists(testRoot)) {
//            try (Stream<Path> s = Files.walk(testRoot).sorted(Comparator.reverseOrder())) {
//                s.forEach(x -> {
//                    try {
//                        Files.delete(x);
//                    } catch (IOException ignored) {
//                    }
//                });
//            } catch (IOException ignored) {
//            }
//        }
//    }
//
//    private MappedByteBuffer prepareMap(String name, int size) throws IOException {
//        return FileChannel.open(testRoot.resolve(name).normalize(), StandardOpenOption.WRITE, StandardOpenOption.READ, StandardOpenOption.CREATE)
//                .map(FileChannel.MapMode.READ_WRITE, 0, size);
//    }
//
//    private static long getNextHash(LongBuffer buf) throws InterruptedException {
//        if (!buf.hasRemaining()) buf.clear();
//        int pos = buf.position();
//
//        StringJoiner b = new StringJoiner(",");
//        for (int i = 0; i < buf.limit(); ++i) {
//            b.add(Long.toString(buf.get(i)));
//        }
//
//        System.out.println("Client: " + b);
//
//        while (true) {
//            long s = buf.get();
//            if (s != 0) {
//                buf.put(pos, 0); // Should fill zero at the read position.
//                return s;
//            }
//            buf.position(pos);
//        }
//    }
//
//    private String readPipeName() {
//        int size;
//        while (true) {
//            size = agent.getInt(0);
//            if (size != 0) {
//                break;
//            }
//        }
//
//        agent.limit(size + Integer.BYTES);
//        agent.position(4);
//
//        byte[] bytes = new byte[size];
//        agent.get(bytes);
//
//        agent.putInt(0, 0);
//        return new String(bytes);
//    }
//
//    @Test
//    void testAgentRequest() throws IOException, InterruptedException {
//        new Thread(() -> AgentRecorder.visit(5), "agentRequestTest").start();
//
//        String name = readPipeName();
//        Assertions.assertEquals(name, "agentRequestTest");
//
//        LongBuffer cov = prepareMap(name + ".coverage", 2048).asLongBuffer();
//
//        Assertions.assertEquals(5, getNextHash(cov));
//    }
//
//    private static final class TestThread extends Thread {
//        private final long i;
//        public TestThread(long i) {
//            super("multi" + i);
//            this.i = i;
//        }
//
//        @Override
//        public void run() {
//            AgentRecorder.visit(i);
//        }
//    }
//
//    @Test
//    void testAssumptions() {
//        Assertions.assertEquals(Type.getType(Object.class).getClassName(), Object.class.getTypeName());
//    }
//
//    @Test
//    void testMultipleAgentRequest() throws IOException, InterruptedException {
//        Set<String> answers = new HashSet<>();
//        List<Thread> th = new ArrayList<>(100);
//        for (int i = 10; i < 100; ++i) {
//            Thread x = new TestThread(i);
//            th.add(x);
//            answers.add(x.getName());
//        }
//
//        th.forEach(Thread::start);
//
//        for(int i = 0; i < 90; ++i) {
//            String name = readPipeName();
//            Assertions.assertTrue(answers.remove(name), name);
//
//            LongBuffer buf = prepareMap(name + ".coverage", 2048).asLongBuffer();
//            prepareMap(name + ".black", 2048);
//            Assertions.assertEquals(Integer.parseInt(name.substring(name.length() - 2)), getNextHash(buf));
//        }
//    }
//
//    @Test
//    void testOversizeCoverage() throws IOException, InterruptedException {
//        final int LO = 1000;
//        new Thread(() -> {
//            for (long i = 1; i < LO; ++i) {
//                AgentRecorder.visit(i);
//            }
//        }, "oversize").start();
//
//        LongBuffer cov = prepareMap("oversize.coverage", 2048).asLongBuffer();
//        Set<Long> answers = new HashSet<>();
//        for (long i = 1; i < LO; ++i) answers.add(i);
//
//        for (long i = 1; i < LO; ++i) {
//            long s = getNextHash(cov);
//            Assertions.assertTrue(answers.remove(s), Long.toString(s));
//        }
//
//        Assertions.assertTrue(answers.isEmpty(), answers.toString());
//    }
//}
