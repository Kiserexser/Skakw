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
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;

public class KillAuraMod implements ModInitializer {
    
    // Настройки
    private static boolean enabled = true;
    private static KeyBinding toggleKey;
    
    // Обход 1 + 2: Strict Mode + Random Delay
    private static int tickCounter = 0;
    private static int nextAttackDelay = 0;
    private static final Random random = new Random();
    
    // Обход 3: Sprint Reset
    private static boolean wasSprinting = false;
    
    // Обход 4: GuiMove (автоматически проверяется)
    
    // Обход 5: Smooth Rotation
    private static float targetYaw = 0;
    private static float targetPitch = 0;
    private static float currentYaw = 0;
    private static float currentPitch = 0;
    private static boolean rotating = false;
    
    // Обход 6: Miss Chance
    private static final int MISS_CHANCE_PERCENT = 8; // 8% промахов
    
    // Обход 7: Safe Reach
    private static final double MAX_REACH = 3.2;
    
    // Обход 9: Silent Aim (храним старые углы)
    private static float lastSentYaw = 0;
    private static float lastSentPitch = 0;
    
    // Обход 10: Target Switch Delay
    private static Entity currentTarget = null;
    private static int targetSwitchCooldown = 0;
    private static final int TARGET_SWITCH_DELAY_MS = 400;
    
    // Обход 12: Fake Entity Swap
    private static Vec3d fakeEntityPos = null;
    
    @Override
    public void onInitialize() {
        // Регистрация клавиши R
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.killaura.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "category.killaura"
        ));
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Проверка клавиши
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                System.out.println("[KillAura] " + (enabled ? "ВКЛЮЧЕН" : "ВЫКЛЮЧЕН"));
            }
            
            if (!enabled || client.player == null || client.world == null) return;
            
            // Обход 4: GuiMove — не работаем в инвентаре
            if (client.currentScreen != null) return;
            
            // Обход 11: Tick Sync — проверка зарядки
            if (client.player.getAttackCooldownProgress(0) < 0.95f) return;
            
            // Обход 1 + 2: Strict Mode + Random Delay
            tickCounter++;
            if (nextAttackDelay == 0) {
                // Рандомная задержка 80-140 мс (4-7 тиков при 20 тиков/сек)
                nextAttackDelay = 2 + random.nextInt(5);
            }
            if (tickCounter < nextAttackDelay) return;
            tickCounter = 0;
            nextAttackDelay = 0;
            
            // Поиск цели
            Entity target = getBestTarget(client);
            if (target == null) {
                currentTarget = null;
                rotating = false;
                return;
            }
            
            // Обход 10: Target Switch Delay
            if (currentTarget != target) {
                if (targetSwitchCooldown > 0) return;
                currentTarget = target;
                targetSwitchCooldown = TARGET_SWITCH_DELAY_MS / 50; // тики
            }
            if (targetSwitchCooldown > 0) {
                targetSwitchCooldown--;
                return;
            }
            
            // Обход 12: Fake Entity Swap
            if (fakeEntityPos == null || random.nextInt(20) == 0) {
                fakeEntityPos = client.player.getPos().add(
                    (random.nextDouble() - 0.5) * 2.5,
                    random.nextDouble() * 1.5,
                    (random.nextDouble() - 0.5) * 2.5
                );
            }
            
            // Обход 5: Smooth Rotation
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
            
            // Плавный поворот (2-5 тиков)
            float yawDiff = targetYaw - currentYaw;
            float pitchDiff = targetPitch - currentPitch;
            float step = 15f; // градусов в секунду (0.75 за тик)
            
            if (Math.abs(yawDiff) < step && Math.abs(pitchDiff) < step) {
                currentYaw = targetYaw;
                currentPitch = targetPitch;
                rotating = false;
            } else {
                currentYaw += Math.signum(yawDiff) * step;
                currentPitch += Math.signum(pitchDiff) * step;
            }
            
            // Обход 9: Silent Aim — сохраняем старые углы перед установкой
            lastSentYaw = client.player.getYaw();
            lastSentPitch = client.player.getPitch();
            
            // Временно меняем углы (только клиент)
            client.player.setYaw(currentYaw);
            client.player.setPitch(currentPitch);
            
            // Обход 6: Miss Chance
            boolean miss = random.nextInt(100) < MISS_CHANCE_PERCENT;
            
            // Обход 3: Sprint Reset
            wasSprinting = client.player.isSprinting();
            if (wasSprinting) {
                client.player.setSprinting(false);
            }
            
            // Обход 12: Атака на фейковую позицию
            if (!miss && fakeEntityPos != null) {
                double distToFake = client.player.getPos().distanceTo(fakeEntityPos);
                if (distToFake <= MAX_REACH) {
                    // Имитация атаки
                    client.interactionManager.attackEntity(client.player, target);
                    client.player.swingHand(Hand.MAIN_HAND);
                }
            }
            
            // Возвращаем спринт
            if (wasSprinting) {
                client.player.setSprinting(true);
            }
            
            // Обход 9: Silent Aim — возвращаем старые углы для сервера
            client.player.setYaw(lastSentYaw);
            client.player.setPitch(lastSentPitch);
            
            // Небольшая пауза после атаки (обход 1)
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
    
    // Поиск лучшей цели с обходами 7 и 8
    private Entity getBestTarget(MinecraftClient client) {
        Entity bestTarget = null;
        double bestDistance = MAX_REACH;
        
        // Получаем всех живых существ в радиусе 4 блока
        Box searchBox = client.player.getBoundingBox().expand(4.0);
        List<Entity> entities = client.world.getOtherEntities(client.player, searchBox);
        
        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity instanceof PlayerEntity && ((PlayerEntity)entity).isCreative()) continue;
            if (entity == client.player) continue;
            
            // Обход 7: Safe Reach
            double distance = client.player.distanceTo(entity);
            if (distance > MAX_REACH) continue;
            
            // Обход 8: Visible Check (Raycast)
            if (!isVisible(client, entity)) continue;
            
            if (distance < bestDistance) {
                bestDistance = distance;
                bestTarget = entity;
            }
        }
        
        return bestTarget;
    }
    
    // Raycast для проверки видимости цели (обход 8)
    private boolean isVisible(MinecraftClient client, Entity target) {
        Vec3d start = client.player.getEyePos();
        Vec3d end = target.getBoundingBox().getCenter();
        
        HitResult result = client.world.raycast(
            new net.minecraft.util.math.RaycastContext(
                start, end,
                net.minecraft.util.math.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.util.math.RaycastContext.FluidHandling.NONE,
                client.player
            )
        );
        
        // Если луч упёрся в блок, цель за стеной
        return result.getType() == HitResult.Type.MISS ||
               result.getPos().distanceTo(end) < 0.5;
    }
}
