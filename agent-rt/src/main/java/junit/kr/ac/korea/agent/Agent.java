package junit.kr.ac.korea.agent;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import junit.kr.ac.korea.agent.data.AgentDataset;
import junit.kr.ac.korea.agent.data.ProposedAgentDataset;
import junit.kr.ac.korea.agent.data.TraditionalAgentDataset;
import junit.kr.ac.korea.agent.instrumentor.ClassStrategy;
import junit.kr.ac.korea.agent.instrumentor.HybridStrategy;
import junit.kr.ac.korea.agent.instrumentor.ProposedStrategy;
import junit.kr.ac.korea.agent.instrumentor.Strategy;
import junit.kr.ac.korea.agent.recorder.AgentRecorder;
import junit.kr.ac.korea.agent.recorder.DebugRecorder;
import junit.kr.ac.korea.agent.recorder.TraceCallee;

public class Agent {
    private static final Logger logger = Logger.getLogger(Agent.class.getName());

    public static final class AgentInitFailException extends RuntimeException {

        public AgentInitFailException(Exception e) {
            super(e);
        }

        public AgentInitFailException(String msg) {
            super(msg);
        }

    }

    private static void configureLogger(Path root, String type, Level l) {
        Logger logger = Logger.getLogger(Agent.class.getPackage().getName());
        logger.setLevel(l);
        logger.setUseParentHandlers(false);

        FileHandler handler;
        try {
            handler = new FileHandler(root.resolve(type + ".log").toString());
        } catch (SecurityException | IOException e) {
            throw new AgentInitFailException(e);
        }

        handler.setLevel(l);
        handler.setFormatter(new Formatter() {

            @Override
            public String format(LogRecord record) {
                return record.getLoggerName() + ": " + record.getMessage() + "\n";
            }
            
        });
        logger.addHandler(handler);
    }

    public static void premain(String options, Instrumentation inst) throws IOException, NoSuchMethodException {
        String[] params = options.split(",");
        if (params.length != 3) {
            throw new IllegalArgumentException(options);
        }

        String type = params[0];
        Path outRoot = Paths.get(params[1]).toAbsolutePath();
        boolean dryrun = params[2].equals("true");

        String env = System.getenv("AGENT_TRACE");
        Level l = env != null ? Level.parse(env) : Level.INFO;

        configureLogger(outRoot, type, l);

        Set<String> classes = new HashSet<>(Files.readAllLines(outRoot.resolve("classes.lst")));
        Set<String> tests = new HashSet<>(Files.readAllLines(outRoot.resolve("tests.lst")));

        Function<Path, AgentDataset> dataset;
        Function<TraceCallee, Strategy> strategyFactory;
        switch (type) {
            case "hyrts":
                dataset = TraditionalAgentDataset::new;
                strategyFactory = HybridStrategy::new;
                break;
            case "class":
                dataset = TraditionalAgentDataset::new;
                strategyFactory = ClassStrategy::new;
                break;
            case "proposed":
                dataset = ProposedAgentDataset::new;
                strategyFactory = ProposedStrategy::new;
                break;
            default:
                throw new AgentInitFailException("Unknown agent type " + type);
        }

        Path artifactRoot = Files.createDirectories(outRoot.resolve(type));
        if (env == null) {
            AgentService.recorder = new AgentRecorder(artifactRoot, tests, dataset, dryrun);
        } else {
            logger.info("Use DebugRecorder");
            AgentService.recorder = new DebugRecorder(artifactRoot, tests, dataset, dryrun);
        }

        Strategy strategy = strategyFactory.apply(AgentService.recorder.createCallee());
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(final ClassLoader loader, final String classname, final Class<?> classBeingRedefined,
                    final ProtectionDomain protectionDomain, final byte[] classfileBuffer) {

                try {
                    if (!classes.contains(classname)) {
                        return null;
                    }

                    logger.finer(() -> "Instrument " + classname);
                    final ClassReader reader = new ClassReader(classfileBuffer);
                    final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);

                    ClassVisitor inst = strategy.create(reader, writer);
                    reader.accept(inst, 0);
                    return writer.toByteArray();
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.warning(() -> e.toString());
                    return null;
                }
            }
        });
    }
}
