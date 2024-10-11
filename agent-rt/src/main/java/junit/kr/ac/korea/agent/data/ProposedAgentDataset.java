package junit.kr.ac.korea.agent.data;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.logging.Logger;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import junit.kr.ac.korea.agent.data.ProposedAgentDataset.BlackDataset;
import junit.kr.ac.korea.agent.instrumentor.ProposedStrategy;
import junit.kr.ac.korea.agent.recorder.RecorderService;
import kr.ac.korea.esel.rts.hash.encoder.Hasher;

public class ProposedAgentDataset extends AgentDatasetImpl<BlackDataset> {
    private static final Logger LOGGER = Logger.getLogger(ProposedAgentDataset.class.getName());

    public BlackDataset create() {
        return new BlackDataset();
    }

    public ProposedAgentDataset(Path out) {
        super(out);
    }

    @Override
    public void dump() throws IOException {
        LOGGER.fine(() -> "Dump to " + out.toString());
        int covTotal = 0;
        LongSet black = new LongOpenHashSet(2048);

        for (BlackDataset ts : all) {
            covTotal += ts.coverage.size();
            ts.mergeBlack(black);
        }

        ByteBuffer content = ByteBuffer.allocate((black.size() + covTotal + 1) * Long.BYTES);
        content.putLong(black.size());

        LongWriter writer = new LongWriter(content);
        writer.forceMerge(black);

        for (BlackDataset ts : all) {
            writer.merge(ts.coverage);
        }

        writer.write(out);
    }

    static class BlackDataset implements ThreadedDataset {
        List<Class<?>> sources = new ArrayList<>(2048);
        IntList extras = new IntArrayList(2048);
        LongSet sourcesHash = new LongOpenHashSet(2048);

        LongSet coverage = new LongOpenHashSet(2048 * 2);

        @Override
        public void addClass(int hash) {
            coverage.add(hash);
        }

        public void mergeBlack(LongSet black) {
            IntIterator iter = extras.intIterator();
            Hasher hash = new Hasher();
            for (Class<?> src : sources) {
                ProposedStrategy.Extra extra;
                
                Lock l = RecorderService.CONTEXT_LOCK.readLock();
                extra = (ProposedStrategy.Extra) RecorderService.CONTEXT.get(iter.nextInt());
                l.unlock();

                Class<?> instanceType = src;
                while (true) {
                    // The classes between instanceType and dest should not have the signature.
                    hash.clear();
                    hash.update(instanceType.getTypeName());

                    if (hash.get() == extra.declaringClassNameHash) return;

                    hash.update(extra.signatureHash);

                    if (!black.add(hash.get())) {
                        break;
                    }
                } 
            }
        }

        @Override
        public void addInstanceMethod(int methodHash) {
            coverage.add(methodHash);
        }

        @Override
        public void add(int methodHash, int classHash) {
            if (coverage.add(methodHash)) {
                coverage.add(classHash);
            }
        }

        @Override
        public void addOverriableMethod(Object src, long key, int extraId)
                throws UnexpectedCoverageType {
            if (sourcesHash.add(key)) {
                sources.add(src.getClass());
                extras.add(extraId);
            }
        }
    }
}
