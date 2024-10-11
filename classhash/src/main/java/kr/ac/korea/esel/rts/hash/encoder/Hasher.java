package kr.ac.korea.esel.rts.hash.encoder;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;

public class Hasher {
    private final CRC32 crc = new CRC32();

    public void update(final byte b) {
        crc.update(b);
    }

    public void update(final byte[] bytes) {
        for (byte b : bytes) {
            update(b);
        }
    }

    public void update(final ByteBuffer buf) {
        while (buf.hasRemaining()) {
            update(buf.get());
        }
    }

    public void update(Hasher child) {
        update(child.crc.getValue());
    }

    public void update(long value) {
        for (int i = Long.BYTES - 1; i >= 0; i--) {
            update((byte)value);
            value >>= Byte.SIZE;
        }
    }

    public void update(char value) {
        for (int i = Character.BYTES - 1; i >= 0; i--) {
            update((byte)value);
            value >>= Character.SIZE;
        }
    }

    public void update(short value) {
        for (int i = Short.BYTES - 1; i >= 0; i--) {
            update((byte)value);
            value >>= Short.SIZE;
        }
    }

    public void update(int value) {
        for (int i = Integer.BYTES - 1; i >= 0; i--) {
            update((byte)value);
            value >>= Integer.SIZE;
        }
    }

    public void update(float value) {
        update(Float.floatToRawIntBits(value));
    }

    public void update(double value) {
        update(Double.doubleToRawLongBits(value));
    }

    public void update(Object o) {
        if (o == null) {
            update((byte)0);
        } else if (o instanceof Byte) {
            update((byte) o);
        } else if (o instanceof Boolean) {
            update((boolean) o);
        } else if (o instanceof Character) {
            update((char)o);
        } else if (o instanceof Short) {
            update((short) o);
        } else if (o instanceof Integer) {
            update((int) o);
        } else if (o instanceof Long) {
            update((long) o);
        } else if (o instanceof Float) {
            update((float) o);
        } else if (o instanceof Double) {
            update((double) o);
        } else if (o instanceof Type) {
            update(((Type) o).toString());
        } else if (o instanceof Handle) {
            update((Handle) o);
        } else if (o instanceof String) {
            update((String) o);
        } else if (o instanceof byte[]) {
            update((byte[]) o);
        } else if (o instanceof int[]) {
            for (int i : ((int[]) o)) {
                update(i);
            }
        } else if (o instanceof long[]) {
            for (long i : ((long[]) o)) {
                update(i);
            }
        } else if (o instanceof short[]) {
            for (short i : ((short[]) o)) {
                update(i);
            }
        } else if (o instanceof char[]) {
            for (char i : ((char[]) o)) {
                update(i);
            }
        } else if (o instanceof boolean[]) {
            for (boolean i : ((boolean[]) o)) {
                update(i);
            }
        } else if (o instanceof float[]) {
            for (float i : ((float[]) o)) {
                update(i);
            }
        } else if (o instanceof double[]) {
            for (double i : ((double[]) o)) {
                update(i);
            }
        } else {
            throw new RuntimeException(
                    "Could not identify type: " + o.getClass());
        }
    }

    void update(Handle h) {
        update(h.getDesc());
        update(h.getName());
        update(h.getOwner());
        update(h.getTag());
    }

    public void update(String v) {
        if (v != null) {
            update(v.getBytes(StandardCharsets.US_ASCII));
        }
    }

    public void update(boolean b) {
        update(b ? (byte)1 : (byte)0);
    }

    public int get() {
        return (int)crc.getValue();
    }

    public void clear() {
        crc.reset();
    }
}
