//package junit.kr.ac.korea.esel.agent.junit.kr.ac.korea.esel.instrument;
//
//import java.io.IOException;
//import java.io.InputStream;
//import kr.ac.korea.esel.junit.kr.ac.korea.esel.instrument.ClassLevelInstrumentor;
//import kr.ac.korea.esel.junit.kr.ac.korea.esel.agent.MethodLevelInstrumentor;
//import java.net.URISyntaxException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.ArrayList;
//import java.util.List;
//
//import kr.ac.korea.esel.rts.hash.ClassLevelHash;
//import kr.ac.korea.esel.rts.hash.MethodLevelHash;
//import org.junit.jupiter.api.Assertions;
//import org.junit.jupiter.api.Test;
//import org.objectweb.asm.ClassReader;
//import org.objectweb.asm.ClassVisitor;
//import org.objectweb.asm.MethodVisitor;
//import org.objectweb.asm.Opcodes;
//import org.objectweb.asm.Type;
//
//public class InstrumentTest {
//
//  private static final String className = InstrumentedClass.class.getName().replace(".", "/");
//  private static final Path location;
//
//  static {
//    try {
//      Path root = Paths
//              .get(InstrumentedClass.class.getProtectionDomain().getCodeSource().getLocation().toURI());
//      location = root.resolve(className + ".class");
//    } catch (URISyntaxException e) {
//      throw new RuntimeException(e);
//    }
//  }
//
//  @Test
//  public void testClassLevelInstrumentation() throws IOException {
//    final byte[] instrumented;
//    long classId;
//    try (InputStream in = Files.newInputStream(location)) {
//      ClassReader reader = new ClassReader(in);
//      classId = ClassLevelHash.compute(reader);
//      ClassLevelInstrumentor instrumentor = new ClassLevelInstrumentor(classId, reader);
//      reader.accept(instrumentor, 0);
//      instrumented = instrumentor.toByteArray();
//    }
//
//    ClassReader reader = new ClassReader(instrumented);
//    reader.accept(new ClassVisitor(Opcodes.ASM7) {
//      @Override
//      public MethodVisitor visitMethod(int access, String methodName, String methodDesc,
//              String signature, String[] exceptions) {
//        return new ClassLevelVerifier(access, methodName, methodDesc, classId);
//      }
//    }, 0);
//  }
//
//  @Test
//  public void testMethodLevelInstrumentation() throws IOException {
//    final byte[] instrumented;
//    final MethodLevelHash hashes;
//
//    try (InputStream in = Files.newInputStream(location)) {
//      ClassReader reader = new ClassReader(in);
//      hashes = MethodLevelHash.compute(reader);
//      MethodLevelInstrumentor instrumentor = new MethodLevelInstrumentor(hashes, reader);
//      reader.accept(instrumentor, 0);
//      instrumented = instrumentor.toByteArray();
//    }
//
//    ClassReader reader = new ClassReader(instrumented);
//    reader.accept(new ClassVisitor(Opcodes.ASM7) {
//      @Override
//      public MethodVisitor visitMethod(int access, String methodName, String methodDesc,
//              String signature, String[] exceptions) {
//        return new MethodLevelVerifier(access, methodName, methodDesc, hashes);
//      }
//    }, 0);
//  }
//
//  abstract static class Verifier extends MethodVisitor {
//    List<Object> params = new ArrayList<>(3);
//    final String methodName;
//    final String fullName;
//    final boolean isPrivate;
//    final boolean isStatic;
//    final boolean clinit;
//    final boolean init;
//    boolean foundProbe;
//    String expectedStaticFieldOwner;
//
//    @Override
//    public void visitEnd() {
//      Assertions.assertTrue(foundProbe || hasNoProbe(), fullName);
//    }
//
//    @Override
//    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
//      if (owner.equals(className) || !name.equals("staticField")) {
//        Assertions.assertNull(expectedStaticFieldOwner);
//      } else {
//        Assertions.assertEquals(expectedStaticFieldOwner, owner);
//      }
//    }
//
//    @Override
//    public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
//            boolean isInterface) {
//      if ("visit".equals(name) && "junit/kr/ac/korea/esel/agent/InternalAgent".equals(owner)) {
//        Type desc = Type.getMethodType(descriptor);
//        Type[] argTypes = desc.getArgumentTypes();
//        Object param = params.get(params.size() - 1);
//        if (argTypes.length == 1) {
//          String typeName = argTypes[0].toString();
//          if (typeName.equals("J")) {
//            // The entrance probe for this method.
//            expectedStaticFieldOwner = null;
//            assertProbeParamter(Long.parseLong(param.toString()));
//            foundProbe = true;
//          } else if (typeName.equals("Ljava/lang/Class;")) {
//            // The probe for static fields.
//            expectedStaticFieldOwner = ((Type) param).getInternalName();
//          } else {
//            Assertions.fail(typeName);
//          }
//        } else if (argTypes.length == 2) {
//          expectedStaticFieldOwner = null;
//          long second = Long.parseLong(params.get(params.size() - 2).toString());
//          assertProbeParamter(Long.parseLong(param.toString()), second);
//          foundProbe = true;
//        }
//        params.clear();
//      }
//    }
//
//    @Override
//    public void visitLdcInsn(Object value) {
//      params.add(value);
//    }
//
//    Verifier(int access, String methodName, String desc) {
//      super(Opcodes.ASM7);
//      this.methodName = methodName;
//      this.fullName = methodName + desc;
//      this.isPrivate = (access & Opcodes.ACC_PRIVATE) != 0;
//      this.isStatic = (access & Opcodes.ACC_STATIC) != 0;
//      this.foundProbe = false;
//      this.clinit = "<clinit>".equals(methodName);
//      this.init = "<init>".equals(methodName);
//    }
//
//    abstract void assertProbeParamter(long id);
//
//    abstract void assertProbeParamter(long classId, long methodId);
//
//    abstract boolean hasNoProbe();
//  }
//
//  static class MethodLevelVerifier extends Verifier {
//
//    private final MethodLevelHash hashes;
//    private final String desc;
//
//    public MethodLevelVerifier(int access, String methodName, String desc, MethodLevelHash hashes) {
//      super(access, methodName, desc);
//      this.hashes = hashes;
//      this.desc = desc;
//    }
//
//    @Override
//    void assertProbeParamter(long id) {
//      if (super.clinit) {
//        Assertions.assertEquals(id, hashes.getClassId(), super.fullName);
//      } else {
//        Assertions.assertTrue(super.isPrivate || !(super.init || super.isStatic), super.fullName);
//        Assertions.assertEquals(id, hashes.getMethodId(super.methodName, desc).longValue(), super.fullName);
//      }
//    }
//
//    @Override
//    void assertProbeParamter(long classId, long methodId) {
//      Assertions.assertFalse(super.isPrivate, super.fullName);
//      Assertions.assertTrue(super.init || super.isStatic, super.fullName);
//      Assertions.assertArrayEquals(new long[]{classId, methodId}, new long[]{hashes.getClassId(), hashes.getMethodId(super.methodName, desc)}, super.fullName);
//    }
//
//    @Override
//    boolean hasNoProbe() {
//      return false;
//    }
//  }
//
//  static class ClassLevelVerifier extends Verifier {
//
//    private final long classId;
//
//    public ClassLevelVerifier(int access, String methodName, String desc, long classId) {
//      super(access, methodName, desc);
//      this.classId = classId;
//    }
//
//    @Override
//    void assertProbeParamter(long id) {
//      Assertions.assertEquals(id, classId);
//    }
//
//    @Override
//    void assertProbeParamter(long classId, long methodId) {
//      Assertions.fail(super.fullName);
//    }
//
//    @Override
//    boolean hasNoProbe() {
//      if (super.isPrivate) {
//        return true;
//      }
//
//      if (!super.isStatic) {
//        return true;
//      }
//
//      if (super.methodName.endsWith("init>")) {
//        return true;
//      }
//
//      return false;
//    }
//  }
//}
