package junit.kr.ac.korea.agent.recorder;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.logging.Logger;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import junit.kr.ac.korea.agent.data.AgentDataset;
import junit.kr.ac.korea.agent.data.AgentResult;
import junit.kr.ac.korea.agent.data.EmptyDataset;
import junit.kr.ac.korea.agent.data.ThreadedDataset;

public abstract class RecorderService {
    private static final Logger LOGGER = Logger.getLogger(RecorderService.class.getName());
    private static final AgentDataset EMPTY = new EmptyDataset();
    public static final Int2ObjectMap<Object> CONTEXT = new Int2ObjectOpenHashMap<>();
    public static final ReadWriteLock CONTEXT_LOCK = new ReentrantReadWriteLock();
    protected static AtomicInteger CONTEXT_INDEX = new AtomicInteger(0);

    private static boolean paused = false;
    private static AgentDataset instance = EMPTY;

    private String testName;
    private final Set<String> classes;
    private final Function<Path, AgentDataset> dataset;
    private final Path root;
    private final boolean dryrun;

    public RecorderService(Path root, Set<String> classes, Function<Path, AgentDataset> dataset, boolean dryrun) {
        this.root = root;
        this.dataset = dataset;
        this.dryrun = dryrun;
        this.classes = classes;
    }

    public AgentResult accept(Class<?> cls) {
        if (cls == null) {
            LOGGER.fine("Null class.");
            return null;
        }

        while (cls.getEnclosingClass() != null) {
            cls = cls.getEnclosingClass();
        }

        String name = cls.getName();
        if (restart(name)) return null;
        if (classes.contains(name)) return start(name);

        if (name.toLowerCase().contains("abstract")) {
            return terminate();
        }

        LOGGER.severe(() -> "An unexpected class: " + name);
        throw new RuntimeException("An unexpected class: " + name);
    }

    public void pause() {
        if (paused) {
            LOGGER.fine(() -> "Pause(): " + testName);
            paused = true;
        }
    }

    public AgentResult terminate() {
        if (dryrun) return null;
        else {
            AgentResult ret = instance;
            instance = EMPTY;
            return ret;
        }
    }

    private boolean restart(String name) {
        if (!name.equals(testName)) return false;
        if (paused) LOGGER.fine(() -> "Restart " + testName);

        paused = false;
        return true;
    }

    private AgentResult start(String name) {
        LOGGER.fine(() -> "Found " + name);

        AgentResult ret = terminate();
        start(name, dataset.apply(root.resolve(name + AgentResult.COV_SUFFIX)));
        
        return ret;
    }

    private void start(String name, AgentDataset dataset) {
        if (dataset == null) LOGGER.severe("instance will be null");
        testName = name;
        instance = dataset;
        paused = false;
    }

    static ThreadedDataset data() {
        return (paused ? EMPTY : instance).get();
    }

    public abstract TraceCallee createCallee();
}
