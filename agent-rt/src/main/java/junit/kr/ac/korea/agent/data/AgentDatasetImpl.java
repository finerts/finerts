package junit.kr.ac.korea.agent.data;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

public abstract class AgentDatasetImpl<T extends ThreadedDataset> implements AgentDataset {
    final Path out;

    AgentDatasetImpl(Path out) {
        this.out = out;
    }

    final List<T> all = new LinkedList<>();
    private final ThreadLocal<T> data = ThreadLocal.withInitial(() -> {
        T item = create();
        synchronized (all) {
            all.add(item);
        }

        return item;
    });

    public T get() {
        return data.get();
    }

    protected abstract T create();

    class LongWriter {
        private final ByteBuffer buf;
        private final LongSet visited = new LongOpenHashSet(4096);

        LongWriter(ByteBuffer buf) {
            this.buf = buf;
        }

        LongWriter(int maxSize) {
            this.buf = ByteBuffer.allocate(maxSize * Long.BYTES);
        }

        void forceMerge(LongSet data) {
            LongIterator iter = data.longIterator();
            while (iter.hasNext()) {
                long l = iter.nextLong();
                buf.putLong(l);
            }
        }

        void merge(LongSet data) {
            LongIterator iter = data.longIterator();
            while (iter.hasNext()) {
                long l = iter.nextLong();
                if (visited.add(l))
                    buf.putLong(l);
            }
        }

        public void write(Path out) throws IOException {
            Files.deleteIfExists(out);

            buf.flip();
            try (FileChannel channel = FileChannel.open(out,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                channel.write(buf);
            }
        }
    }
}
