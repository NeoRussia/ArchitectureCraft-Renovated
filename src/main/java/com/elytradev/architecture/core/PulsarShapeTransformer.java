package com.elytradev.architecture.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Adds Pulsar's positional light interfaces to {@code BlockShape} when Pulsar is
 * installed, so the light engine can ask a shape what its material emits and
 * filters.
 *
 * <p>Does nothing when Pulsar is absent: the interfaces are looked for as a
 * classpath resource and the bytes are returned unchanged. Any failure returns
 * the original class as well; a shape must never fail to load because of an
 * optional lighting integration.</p>
 */
public final class PulsarShapeTransformer implements IClassTransformer {

    private static final String TARGET = "com.elytradev.architecture.common.block.BlockShape";
    private static final String SOURCE = "com/sumirelabs/pulsar/api/PositionalColoredLightSource";
    private static final String FILTER = "com/sumirelabs/pulsar/api/PositionalColoredTranslucency";
    private static final String HELPER = "com/elytradev/architecture/compat/pulsar/PulsarShapeLight";
    private static final String DESCRIPTOR =
            "(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/block/state/IBlockState;)I";

    private static Boolean present;
    private static boolean warned;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null)
            return null;
        String target = transformedName != null ? transformedName : name;
        if (!TARGET.equals(target))
            return basicClass;
        if (!pulsarPresent())
            return basicClass;
        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassNode node = new ClassNode();
            reader.accept(node, 0);
            if (node.interfaces.contains(SOURCE))
                return basicClass;
            node.interfaces.add(SOURCE);
            node.interfaces.add(FILTER);
            addMethod(node, "getColoredLightEmission", "emission");
            addMethod(node, "getColoredTransmittance", "transmittance");
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                System.err.println("[ArchitectureCraft] Could not add Pulsar light interfaces to BlockShape: " + t);
            }
            return basicClass;
        }
    }

    /**
     * {@code public int method(IBlockAccess world, BlockPos pos, IBlockState state) {
     *     return PulsarShapeLight.helper(world, pos, state);
     * }}
     */
    private static void addMethod(ClassNode node, String name, String helper) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, DESCRIPTOR, null, null);
        InsnList code = new InsnList();
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new VarInsnNode(Opcodes.ALOAD, 2));
        code.add(new VarInsnNode(Opcodes.ALOAD, 3));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, helper, DESCRIPTOR, false));
        code.add(new InsnNode(Opcodes.IRETURN));
        method.instructions = code;
        method.maxStack = 4;
        method.maxLocals = 4;
        node.methods.add(method);
    }

    /** True when Pulsar's API is on the class path. */
    private static boolean pulsarPresent() {
        if (present == null) {
            try {
                ClassLoader loader = PulsarShapeTransformer.class.getClassLoader();
                present = loader != null && loader.getResource(SOURCE + ".class") != null;
            } catch (Throwable t) {
                present = Boolean.FALSE;
            }
        }
        return present;
    }
}
