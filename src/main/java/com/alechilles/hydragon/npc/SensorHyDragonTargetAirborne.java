package com.alechilles.hydragon.npc;

import com.alechilles.alecstamework.npc.compat.NpcSupportAccess;
import com.alechilles.alecstamework.npc.sensors.TameworkSensorBase;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.controllers.MotionControllerFly;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;

final class SensorHyDragonTargetAirborne extends TameworkSensorBase {
    private final int targetSlot;
    private final AirborneTargetTracker tracker = new AirborneTargetTracker();

    SensorHyDragonTargetAirborne(BuilderSensorHyDragonTargetAirborne builder, int targetSlot) {
        super(builder);
        this.targetSlot = targetSlot;
    }

    @Override
    public boolean matches(Ref<EntityStore> ref, Role role, double dt, Store<EntityStore> store) {
        var marked = NpcSupportAccess.markedEntity(role, ref, store);
        var target = marked == null ? null : marked.getMarkedEntityRef(targetSlot);
        if (target != null && !target.isValid()) target = null;
        boolean alreadyFlying = role.getActiveMotionController() instanceof MotionControllerFly;
        return tracker.update(target, target != null && isAirborne(target, store), alreadyFlying, dt);
    }

    private static boolean isAirborne(Ref<EntityStore> target, Store<EntityStore> store) {
        var mounted = store.getComponent(target, MountedComponent.getComponentType());
        if (mounted != null && mounted.getMountedToEntity() != null && mounted.getMountedToEntity().isValid()) {
            target = mounted.getMountedToEntity();
        }
        var npc = store.getComponent(target, NPCEntity.getComponentType());
        if (npc != null && npc.getRole() != null) {
            var controller = npc.getRole().getActiveMotionController();
            return controller instanceof MotionControllerFly && !controller.onGround() && !controller.inWater();
        }
        var movement = store.getComponent(target, MovementStatesComponent.getComponentType());
        if (movement == null || movement.getMovementStates() == null) return false;
        var state = movement.getMovementStates();
        return !state.onGround && !state.swimming;
    }

    @Override
    public InfoProvider getSensorInfo() {
        return null;
    }

    @Override
    public void clearOnce() {
        super.clearOnce();
        tracker.reset();
    }
}
