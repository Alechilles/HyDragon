package com.alechilles.hydragon.compat;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Reads NPC role support through the active Hytale API generation.
 *
 * <p>Update 6 moved state, marked-entity, and world support into ECS components. The
 * old Role getters still exist in 0.5.7, so this boundary binds only the methods that
 * belong to the detected generation. No Update 6-only class is linked by name.</p>
 */
public final class HytaleNpcSupportAccess {
    private static final String UPDATE_6_MARKER =
            "com.hypixel.hytale.server.npc.instructions.ExecutionSupport";
    private static final boolean UPDATE_6 = detectsUpdate6();

    private static final MethodHandle LEGACY_STATE_SUPPORT = bindLegacy(
            "getStateSupport", StateSupport.class);
    private static final MethodHandle LEGACY_MARKED_ENTITY_SUPPORT = bindLegacy(
            "getMarkedEntitySupport", MarkedEntitySupport.class);
    private static final MethodHandle LEGACY_WORLD_SUPPORT = bindLegacy(
            "getWorldSupport", WorldSupport.class);

    private static final MethodHandle ECS_STATE_SUPPORT = bindEcs(
            StateSupport.class);
    private static final MethodHandle ECS_MARKED_ENTITY_SUPPORT = bindEcs(
            MarkedEntitySupport.class);
    private static final MethodHandle ECS_WORLD_SUPPORT = bindEcs(
            WorldSupport.class);

    private HytaleNpcSupportAccess() {
    }

    /**
     * Returns the state support for an NPC, or {@code null} when the role/reference is
     * unavailable or the active API cannot read it.
     */
    public static StateSupport stateSupport(
            Role role, Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        return access(role, ref, accessor, LEGACY_STATE_SUPPORT, ECS_STATE_SUPPORT, StateSupport.class);
    }

    /**
     * Returns marked-entity support for an NPC, or {@code null} when it is unavailable.
     */
    public static MarkedEntitySupport markedEntitySupport(
            Role role, Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        return access(role, ref, accessor, LEGACY_MARKED_ENTITY_SUPPORT,
                ECS_MARKED_ENTITY_SUPPORT, MarkedEntitySupport.class);
    }

    /**
     * Returns world support for an NPC, or {@code null} when it is unavailable.
     */
    public static WorldSupport worldSupport(
            Role role, Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        return access(role, ref, accessor, LEGACY_WORLD_SUPPORT, ECS_WORLD_SUPPORT, WorldSupport.class);
    }

    static boolean isUpdate6() {
        return UPDATE_6;
    }

    private static <T> T access(
            Role role,
            Ref<EntityStore> ref,
            ComponentAccessor<EntityStore> accessor,
            MethodHandle legacy,
            MethodHandle ecs,
            Class<T> type) {
        if (UPDATE_6) {
            // The Update 6 component accessors require a live reference and accessor. Check both
            // before invoking the handle so an unloaded NPC remains an ordinary missing support.
            if (ref == null || !ref.isValid() || accessor == null || ecs == null) return null;
            try {
                Object support = ecs.invoke(ref, accessor);
                return type.cast(support);
            } catch (Throwable ignored) {
                return null;
            }
        }
        if (role == null || legacy == null) return null;
        try {
            Object support = legacy.invoke(role);
            return type.cast(support);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean detectsUpdate6() {
        try {
            Class.forName(UPDATE_6_MARKER, false, HytaleNpcSupportAccess.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    private static MethodHandle bindLegacy(String methodName, Class<?> returnType) {
        if (UPDATE_6) return null;
        try {
            return MethodHandles.publicLookup().findVirtual(
                    Role.class, methodName, MethodType.methodType(returnType));
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static MethodHandle bindEcs(Class<?> supportType) {
        if (!UPDATE_6) return null;
        try {
            return MethodHandles.publicLookup().findStatic(
                    supportType,
                    "get",
                    MethodType.methodType(supportType, Ref.class, ComponentAccessor.class));
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
