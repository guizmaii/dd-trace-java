package datadog.trace.agent.tooling.muzzle;

import datadog.trace.agent.tooling.Utils;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;

/** Visit a class and collect all references made by the visited class. */
public class ReferenceCreator extends ClassVisitor {
  /**
   * Generate all references reachable from a given class.
   *
   * @param entryPointClassName Starting point for generating references.
   * @param loader Classloader used to read class bytes.
   * @return Map of [referenceClassName -> Reference]
   */
  public static Map<String, Reference> createReferencesFrom(
      String entryPointClassName, ClassLoader loader) {
    final Set<String> visitedSources = new HashSet<>();
    final Map<String, Reference> references = new HashMap<>();

    final Queue<String> instrumentationQueue = new ArrayDeque<>();
    instrumentationQueue.add(entryPointClassName);

    while (!instrumentationQueue.isEmpty()) {
      final String className = instrumentationQueue.remove();
      visitedSources.add(className);
      try {
        final InputStream in = loader.getResourceAsStream(Utils.getResourceName(className));
        try {
          final ReferenceCreator cv = new ReferenceCreator(null);
          final ClassReader reader = new ClassReader(in);
          reader.accept(cv, ClassReader.SKIP_FRAMES);

          Map<String, Reference> instrumentationReferences = cv.getReferences();
          for (Map.Entry<String, Reference> entry : instrumentationReferences.entrySet()) {
            // Don't generate references created outside of the datadog instrumentation package.
            if (!visitedSources.contains(entry.getKey())
                && entry.getKey().startsWith("datadog.trace.instrumentation.")) {
              instrumentationQueue.add(entry.getKey());
            }
            if (references.containsKey(entry.getKey())) {
              references.put(
                  entry.getKey(), references.get(entry.getKey()).merge(entry.getValue()));
            } else {
              references.put(entry.getKey(), entry.getValue());
            }
          }

        } finally {
          if (in != null) {
            in.close();
          }
        }
      } catch (IOException ioe) {
        throw new IllegalStateException(ioe);
      }
    }
    return references;
  }

  private Map<String, Reference> references = new HashMap<>();
  private String refSourceClassName;

  private ReferenceCreator(ClassVisitor classVisitor) {
    super(Opcodes.ASM6, classVisitor);
  }

  public Map<String, Reference> getReferences() {
    return references;
  }

  private void addReference(Reference ref) {
    if (references.containsKey(ref.getClassName())) {
      references.put(ref.getClassName(), references.get(ref.getClassName()).merge(ref));
    } else {
      references.put(ref.getClassName(), ref);
    }
  }

  @Override
  public void visit(
      final int version,
      final int access,
      final String name,
      final String signature,
      final String superName,
      final String[] interfaces) {
    refSourceClassName = Utils.getClassName(name);
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public MethodVisitor visitMethod(
      final int access,
      final String name,
      final String descriptor,
      final String signature,
      final String[] exceptions) {
    return new AdviceReferenceMethodVisitor(
        super.visitMethod(access, name, descriptor, signature, exceptions));
  }

  private class AdviceReferenceMethodVisitor extends MethodVisitor {
    private int currentLineNumber = -1;

    public AdviceReferenceMethodVisitor(MethodVisitor methodVisitor) {
      super(Opcodes.ASM6, methodVisitor);
    }

    @Override
    public void visitLineNumber(final int line, final Label start) {
      currentLineNumber = line;
      super.visitLineNumber(line, start);
    }

    @Override
    public void visitMethodInsn(
        final int opcode,
        final String owner,
        final String name,
        final String descriptor,
        final boolean isInterface) {
      addReference(
          new Reference.Builder(owner).withSource(refSourceClassName, currentLineNumber).build());
    }
  }
}
