package junit.kr.ac.korea.agent.data;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import junit.kr.ac.korea.agent.data.TraditionalAgentDataset.DatasetImpl;

public class TraditionalAgentDataset extends AgentDatasetImpl<DatasetImpl> {
    private static final Logger LOGGER = Logger.getLogger(TraditionalAgentDataset.class.getName());
    public TraditionalAgentDataset(Path out) {
        super(out);
    }

    public DatasetImpl create() {
        return new DatasetImpl();
    }

    @Override
    public void dump() throws IOException {
        LOGGER.fine(() -> "Dump to " + out.toString());

        int total = 0;
        for (DatasetImpl item : all) {
            total += item.coverage.size();
        }

        LongWriter writer = new LongWriter(total);
        for (DatasetImpl ts : all) {
            writer.merge(ts.coverage);
        }

        writer.write(out);
    }

    static class DatasetImpl implements ThreadedDataset {
        LongSet coverage = new LongOpenHashSet(2048 * 2);

        @Override
        public void addClass(int hash) {
            coverage.add(hash);
        }

        @Override
        public void addInstanceMethod(int hash) {
            coverage.add(hash);
        }

        @Override
        public void add(int methodHash, int classHash) {
            if (coverage.add(methodHash)) {
                coverage.add(classHash);
            }
        }
    }
}
