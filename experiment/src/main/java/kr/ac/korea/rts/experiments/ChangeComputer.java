package kr.ac.korea.rts.experiments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import kr.ac.korea.esel.rts.hash.encoder.ClassHashItem;
import kr.ac.korea.esel.rts.hash.encoder.MethodHashItem;

public class ChangeComputer {
    private final Map<String, ClassItem> head;
    private final Map<String, ClassItem> base;

    public ChangeComputer(String head, String base, Function<byte[], ClassHashItem> func) throws IOException {
        this(head, base, func, false);
    }

    public ChangeComputer(String head, String base, Function<byte[], ClassHashItem> func, boolean ignoreTestScope) throws IOException {
        this.head = load(head, func, ignoreTestScope);
        this.base = load(base, func, ignoreTestScope);
    }

    public void run(ChangeListener listener) {
        new CompareIterator<>(head.values())
            .visitChanges(base.values(), new ChangeEventListener<ClassItem>() {
                @Override
                public void onChange(ClassItem item, ChangeType type) {
                    listener.addClass(type, item);
                }

                @Override
                public void onEquals(ClassItem head, ClassItem base) {
                    iterateMethodHashes(listener, base, head);
                }

                @Override
                public boolean isEqualContent(ClassItem head, ClassItem base) {
                    return head.hash.classHash() == base.hash.classHash();
                }

                @Override
                public int compare(ClassItem head, ClassItem base) {
                    return head.hash.internalClassName().compareTo(base.hash.internalClassName());
                }
            } );
    }

    private void iterateMethodHashes(ChangeListener listener, ClassItem old, ClassItem current) {
        new CompareIterator<>(MethodItem.fromClass(current))
            .visitChanges(MethodItem.fromClass(old), new ChangeEventListener<MethodItem>() {
                @Override
                public void onChange(MethodItem item, ChangeType type) {
                    listener.addMethod(type, item);
                }

                @Override
                public int compare(MethodItem head, MethodItem base) {
                    return head.getMethod().methodSignature().compareTo(base.getMethod().methodSignature());
                }

                @Override
                public boolean isEqualContent(MethodItem head, MethodItem base) {
                    return head.getMethod().methodHash() == base.getMethod().methodHash();
                } 
            });
    }

    static enum ChangeType {
        Added, Deleted, Modified
    }

    private static Map<String, ClassItem> load(String classpaths, Function<byte[], ClassHashItem> factory, boolean ignoreTestScope) throws IOException {
        Map<String, ClassItem> hashes = new TreeMap<>();
        for (String cp : classpaths.split(":")) {
            boolean isTestScope = cp.endsWith("test-classes");
            if (ignoreTestScope && isTestScope) continue;

            try(Stream<Path> files = Files.walk(Paths.get(cp)).filter(x -> x.getFileName().toString().endsWith(".class"))) {
                Iterator<Path> iterator = files.iterator();
                while (iterator.hasNext()) {
                    Path file = iterator.next();
                    byte[] bytes = Files.readAllBytes(file);
                    ClassHashItem hash = factory.apply(bytes);
                    hashes.put(hash.internalClassName(), new ClassItem(isTestScope, hash));
                }
            }
        }

        return hashes;
    }

    interface ChangeListener {
        void addClass(ChangeType type, ClassItem item);
        void addMethod(ChangeType type, MethodItem item);
    }

    private static final class CompareIterator<T> {
        private final Iterator<T> iterator;
        private T last;

        public CompareIterator(Iterator<T> iterator) {
            this.iterator = iterator;
        }

        public CompareIterator(Iterable<T> iterable) {
            this(iterable.iterator());
        }

        public T next() {
            if (iterator.hasNext()) {
                last = iterator.next();
            } else {
                last = null;
            }

            return last;
        }

        public T last() {
            if (last == null) last = next();
            return last;
        }

        public T consumeLast() {
            T ret = last();
            next();
            return ret;
        }

        public void consumeAll(Consumer<T> consumer) {
            while (true) {
                T last = consumeLast();
                if (last == null) return;

                consumer.accept(last);
            }
        }

        public void visitChanges(Iterable<T> base, ChangeEventListener<T> visitor) {
            CompareIterator<T> old = new CompareIterator<>(base);
    
            while (true) {
                T currentVersion = last();
                if (currentVersion == null) {
                    old.consumeAll(o -> visitor.onChange(o, ChangeType.Deleted));
                    return;
                }
    
                T oldVersion = old.last();
                if (oldVersion == null) {
                    consumeAll(o -> visitor.onChange(o, ChangeType.Added));
                    return;
                }
                
                int diff = visitor.compare(currentVersion, oldVersion);
                if (diff < 0) {
                    visitor.onChange(consumeLast(), ChangeType.Added);
                } else if (diff > 0) {
                    visitor.onChange(old.consumeLast(), ChangeType.Deleted);
                } else {
                    if (visitor.isEqualContent(currentVersion, oldVersion) ) {
                        visitor.onEquals(currentVersion, oldVersion);
                    } else {
                        visitor.onChange(oldVersion, ChangeType.Modified);
                    }
    
                    consumeLast();
                    old.consumeLast();
                }
            }
        }
    }

    private interface ChangeEventListener<T> extends Comparator<T> {
        boolean isEqualContent(T head, T base);

        void onChange(T item, ChangeType type);
        default void onEquals(T head, T base) {

        }
    }

    public static final class MethodItem {
        private final ClassItem parent;

        private final MethodHashItem method;

        public MethodHashItem getMethod() {
            return method;
        }

        public ClassItem getParent() {
            return parent;
        }

        public MethodItem(ClassItem parent, MethodHashItem method) {
            this.parent = parent;
            this.method = method;
        }

        public static Iterable<MethodItem> fromClass(ClassItem parent) {
            return new Iterable<MethodItem>() {
                private final Iterator<MethodHashItem> iterator = parent.hash.iterateMethods().iterator();

                @Override
                public Iterator<MethodItem> iterator() {
                    return new Iterator<MethodItem>() {

                        @Override
                        public boolean hasNext() {
                            return iterator.hasNext();
                        }

                        @Override
                        public MethodItem next() {
                            return new MethodItem(parent, iterator.next());
                        }
                    };
                }
            };
        }
    }

    public static final class ClassItem {
        private final boolean isTestScope;
        private final ClassHashItem hash;

        public ClassItem(boolean isTestScope, ClassHashItem hash) {
            this.isTestScope = isTestScope;
            this.hash = hash;
        }

        public ClassHashItem getItem() {
            return hash;
        }

        public boolean isTestScope() {
            return isTestScope;
        }
    }
}
