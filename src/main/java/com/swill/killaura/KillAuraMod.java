package com.swill.killaura;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;
import java.util.Random;
import java.util.List;

public class KillAuraMod implements ModInitializer {
    
    private static boolean enabled = true;
    private static KeyBinding toggleKey;
    
    // Обходы 1+2
    private static int tickCounter = 0;
    private static int nextAttackDelay = 0;
    private static final Random random = new Random();
    
    // Обход 3
    private static boolean wasSprinting = false;
    
    // Обход 5
    private static float targetYaw = 0;
    private static float targetPitch = 0;
    private static float currentYaw = 0;
    private static float currentPitch = 0;
    private static boolean rotating = false;
    
    // Обход 6
    private static final int MISS_CHANCE_PERCENT = 8;
    
    // Обход 7
    private static final double MAX_REACH = 3.2;
    
    // Обход 9
    private static float lastSentYaw = 0;
    private static float lastSentPitch = 0;
    
    // Обход 10
    private static Entity currentTarget = null;
    private static int targetSwitchCooldown = 0;
    private static final int TARGET_SWITCH_DELAY_TICKS = 8;
    
    @Override
    public void onInitialize() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.killaura.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "category.killaura"
        ));
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                System.out.println("[KillAura] " + (enabled ? "ON" : "OFF"));
            }
            
            if (!enabled || client.player == null || client.world == null) return;
            if (client.currentScreen != null) return; // Обход 4
            if (client.player.getAttackCooldownProgress(0) < 0.95f) return; // Обход 11
            
            // Обходы 1+2
            tickCounter++;
            if (nextAttackDelay == 0) {
                nextAttackDelay = 2 + random.nextInt(5);
            }
            if (tickCounter < nextAttackDelay) return;
            tickCounter = 0;
            nextAttackDelay = 0;
            
            Entity target = getBestTarget(client);
            if (target == null) {
                currentTarget = null;
                rotating = false;
                return;
            }
            
            // Обход 10
            if (currentTarget != target) {
                if (targetSwitchCooldown > 0) return;
                currentTarget = target;
                targetSwitchCooldown = TARGET_SWITCH_DELAY_TICKS;
            }
            if (targetSwitchCooldown > 0) {
                targetSwitchCooldown--;
                return;
            }
            
            // Обход 5 - плавный поворот
            if (!rotating) {
                Vec3d targetPos = target.getBoundingBox().getCenter();
                double dx = targetPos.x - client.player.getX();
                double dy = targetPos.y - client.player.getEyeY();
                double dz = targetPos.z - client.player.getZ();
                double dh = Math.sqrt(dx * dx + dz * dz);
                
                targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
                targetPitch = (float) -Math.toDegrees(Math.atan2(dy, dh));
                
                currentYaw = client.player.getYaw();
                currentPitch = client.player.getPitch();
                rotating = true;
            }
            
            float yawDiff = targetYaw - currentYaw;
            float pitchDiff = targetPitch - currentPitch;
            float step = 12f;
            
            if (Math.abs(yawDiff) < step && Math.abs(pitchDiff) < step) {
                currentYaw = targetYaw;
                currentPitch = targetPitch;
                rotating = false;
            } else {
                currentYaw += Math.signum(yawDiff) * step;
                currentPitch += Math.signum(pitchDiff) * step;
            }
            
            // Обход 9 - Silent Aim
            lastSentYaw = client.player.getYaw();
            lastSentPitch = client.player.getPitch();
            client.player.setYaw(currentYaw);
            client.player.setPitch(currentPitch);
            
            // Обход 6
            boolean miss = random.nextInt(100) < MISS_CHANCE_PERCENT;
            
            // Обход 3
            wasSprinting = client.player.isSprinting();
            if (wasSprinting) {
                client.player.setSprinting(false);
            }
            
            if (!miss && client.interactionManager != null) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
            
            if (wasSprinting) {
                client.player.setSprinting(true);
            }
            
            client.player.setYaw(lastSentYaw);
            client.player.setPitch(lastSentPitch);
        });
    }
    
    private Entity getBestTarget(MinecraftClient client) {
        Entity bestTarget = null;
        double bestDistance = MAX_REACH;
        
        Box searchBox = client.player.getBoundingBox().expand(4.0);
        List<Entity> entities = client.world.getOtherEntities(client.player, searchBox);
        
        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity instanceof PlayerEntity && ((PlayerEntity)entity).isCreative()) continue;
            if (entity == client.player) continue;
            
            double distance = client.player.distanceTo(entity);
            if (distance > MAX_REACH) continue;
            
            // Обход 8 - Visible Check (упрощённый, без RaycastContext)
            if (!client.player.canSee(entity)) continue;
            
            if (distance < bestDistance) {
                bestDistance = distance;
                bestTarget = entity;
            }
        }
        return bestTarget;
    }
}
