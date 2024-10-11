package kr.ac.korea.rts.experiments;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import kr.ac.korea.esel.rts.hash.ClassLevel;
import kr.ac.korea.esel.rts.hash.MethodLevel;
import kr.ac.korea.esel.rts.hash.hyRTSf;
import kr.ac.korea.esel.rts.hash.encoder.ClassHashItem;
import kr.ac.korea.esel.rts.hash.encoder.Hasher;
import kr.ac.korea.esel.rts.hash.encoder.MethodHashItem;
import kr.ac.korea.rts.experiments.ChangeComputer.ChangeListener;
import kr.ac.korea.rts.experiments.ChangeComputer.ChangeType;
import kr.ac.korea.rts.experiments.ChangeComputer.ClassItem;
import kr.ac.korea.rts.experiments.ChangeComputer.MethodItem;

public class TestSelector {
    public static void run(String level, String head, String base, String runtimeRoot) throws IOException {
        Function<byte[], ClassHashItem> func;
        Selector selector;
        Path root = Paths.get(runtimeRoot);

        switch (level) {
            case "class":
                func = ClassLevel::create;
                selector = new DefaultChangeSet();
                break;
            case "hyrts":
                func = hyRTSf::create;
                selector = new DefaultChangeSet();
                break;
            case "proposed":
                func = MethodLevel::create;
                selector = new ProposedChangeSet(root);
                break;
            default:
                throw new IllegalArgumentException(level);
        }

        ChangeComputer computer = new ChangeComputer(head, base, func);
        Set<String> tests = new HashSet<>(Files.readAllLines(root.resolve("tests.lst")));
        computer.run(selector);
        selector.select(tests, root.resolve(level));
    }

    private interface Selector extends ChangeListener {
        default void select(Set<String> tests, Path root) throws IOException {
            try(Stream<Path> cov = Files.list(root)) {
                Iterator<Path> iterator = cov.iterator();
                while (iterator.hasNext()) {
                    Path p = iterator.next();
                    String name = p.getFileName().toString();
                    name = name.substring(0, name.length() - 4);
                    if (tests.remove(name) && printIfSelected(p)) {
                        System.out.println(name);
                    }
                }
            }
        }

        boolean printIfSelected(Path p) throws IOException;
    }

    private static final class ProposedChangeSet implements Selector {
        private final LongSet signatures = new LongOpenHashSet(4096);
        private final LongSet changeSet = new LongOpenHashSet(4096);
        private final LongSet addedBlacklist = new LongOpenHashSet(4096);

        public ProposedChangeSet(Path runtimeRoot) throws IOException {
            byte[] bytes = Files.readAllBytes(runtimeRoot.resolve("signature.set"));
            LongBuffer buf = ByteBuffer.wrap(bytes).asLongBuffer();
            while (buf.hasRemaining()) {
                signatures.add(buf.get());
            }
        }

        @Override
        public void addClass(ChangeType type, ClassItem item) {
            changeSet.add(item.getItem().classHash());
        }

        @Override
        public void addMethod(ChangeType type, MethodItem item) {
            ClassItem classItem = item.getParent();
            MethodHashItem methodHash = item.getMethod();
            boolean isTestScope = classItem.isTestScope();
            if (isTestScope) {
                addClass(ChangeType.Modified, classItem);
            } else if (type == ChangeType.Added) {
                int signatureHash = methodHash.signatureHash();
                if (!methodHash.isPrivateMethod() && signatures.contains(signatureHash)) {
                    addClass(ChangeType.Modified, classItem);
                } else {
                    Hasher hash = new Hasher();
                    hash.clear();
                    hash.update(classItem.getItem().internalClassName().replace("/", "."));
                    hash.update(signatureHash);

                    addedBlacklist.add(hash.get());
                }
            } else {
                changeSet.add(methodHash.methodHash());
            }
        }

        @Override
        public boolean printIfSelected(Path p) throws IOException {
            LongBuffer buf = ByteBuffer.wrap(Files.readAllBytes(p)).asLongBuffer();
            long covSize = buf.get();
            while (covSize-- > 0) {
                long value = buf.get();
                if (addedBlacklist.contains(value)) {
                    return true;
                }
            }

            while(buf.hasRemaining()) {
                long value = buf.get();
                if (changeSet.contains(value)) {
                    return true;
                }
            }

            return false;
        }
    }

    private static final class DefaultChangeSet implements Selector {
        LongSet changeSet = new LongOpenHashSet(4096);

        @Override
        public void addClass(ChangeType type, ClassItem item) {
            changeSet.add(item.getItem().classHash());
        }

        @Override
        public void addMethod(ChangeType type, MethodItem item) {
            ClassItem classItem = item.getParent();
            boolean isTestScope = classItem.isTestScope();
            if (isTestScope) {
                addClass(ChangeType.Modified, classItem);
            } else {
                if (type == ChangeType.Modified) {
                    changeSet.add(item.getMethod().methodHash());
                } else {
                    addClass(ChangeType.Modified, item.getParent());
                }
            }
        }

        @Override
        public boolean printIfSelected(Path p) throws IOException {
            LongBuffer buf = ByteBuffer.wrap(Files.readAllBytes(p)).asLongBuffer();
            while (buf.hasRemaining()) {
                long value = buf.get();
                if (changeSet.contains(value)) {
                    return true;
                }
            }

            return false;
        }
    }
}
