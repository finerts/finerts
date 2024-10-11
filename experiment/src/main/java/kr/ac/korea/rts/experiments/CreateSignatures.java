package kr.ac.korea.rts.experiments;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import kr.ac.korea.esel.rts.hash.encoder.Hasher;

public class CreateSignatures {
    private static final Path root = Paths.get("signatures");

    public static void run(String out, Collection<String> files) throws IOException {
        Files.createDirectories(root);
        Path outFile = Paths.get(out);
        Files.deleteIfExists(outFile);

        LongSet collected = new LongOpenHashSet(4096);
        for (String path : files) {
            compute(collected, Paths.get(path));
        }

        ByteBuffer buf = ByteBuffer.allocate(collected.size() * Long.BYTES);
        LongIterator iterator = collected.iterator();
        while (iterator.hasNext()) {
            long v = iterator.nextLong();
            buf.putLong(v);
        }

        buf.flip();
        try (FileChannel ch = FileChannel.open(outFile, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            ch.write(buf);
        }
    }

    private static void compute(LongSet collected, Path lib) throws IOException {
        Path out = root.resolve(lib.getFileName() + ".cov");
        if (Files.exists(out)) {
            LongBuffer buf = ByteBuffer.wrap(Files.readAllBytes(out)).asLongBuffer();
            while (buf.hasRemaining()) {
                collected.add(buf.get());
            }
        } else {
            LongSet hashes = new LongOpenHashSet(4096);
            try (JarInputStream in = new JarInputStream(Files.newInputStream(lib))) {
                JarEntry entry;
                while ((entry = in.getNextJarEntry()) != null) {
                    if (!entry.getName().endsWith(".class"))
                        continue;

                    ClassReader reader = new ClassReader(in);
                    reader.accept(new SignatureVisitor(hashes), ClassReader.SKIP_CODE);
                }
            }

            ByteBuffer buf = ByteBuffer.allocate(hashes.size() * Long.BYTES);
            LongIterator iterator = hashes.iterator();
            while (iterator.hasNext()) {
                long v = iterator.nextLong();
                collected.add(v);
                buf.putLong(v);
            }

            buf.flip();
            try (FileChannel ch = FileChannel.open(out, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                ch.write(buf);
            }
        }
    }

    private static class SignatureVisitor extends ClassVisitor {
        private final LongSet hashes;

        public SignatureVisitor(LongSet hashes) {
            super(Opcodes.ASM9);
            this.hashes = hashes;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                String signature, String[] exceptions) {
            int isImpl = access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT);
            if (isImpl != 0) return null;
            if (name.endsWith("init>")) return null;

            int isPublic = access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED);
            if (isPublic == 0) return null;

            Hasher hash = new Hasher();
            hash.update(name);
            hash.update(descriptor);
            hashes.add(hash.get());

            return null;
        }

    }
}