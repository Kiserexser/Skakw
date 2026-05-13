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
import net.minecraft.item.SwordItem;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Random;

public class KillAuraMod implements ModInitializer {

    private static boolean enabled = false;
    private static KeyBinding keyBinding;
    private static final Random random = new Random();
    
    private static Entity currentTarget = null;
    private static int targetSwitchCooldown = 0;
    
    // Silent Aim
    private static float serverYaw = 0;
    private static float serverPitch = 0;
    
    // Плавный поворот
    private static float targetYaw = 0;
    private static float targetPitch = 0;
    private static int rotationTicks = 0;
    
    // Тайминг меча
    private static int attackTimer = 0;
    private static final int SWORD_COOLDOWN_TICKS = 12;

    @Override
    public void onInitialize() {
        System.out.println("[KillAura] Загружен - Крит 90% | Тайминг меча");

        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.killaura.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.killaura"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (keyBinding.wasPressed()) {
                enabled = !enabled;
                String status = enabled ? "§aВКЛЮЧЕН §7(Крит 90%)" : "§cВЫКЛЮЧЕН";
                client.player.sendMessage(Text.literal("§l[KillAura] §r" + status), true);
            }

            if (!enabled) return;
            if (client.currentScreen != null) return;
            
            // Тайминг меча
            if (attackTimer > 0) {
                attackTimer--;
                return;
            }
            
            // Проверка что в руке меч
            boolean hasSword = client.player.getMainHandStack().getItem() instanceof SwordItem;
            if (!hasSword) return;
            
            // Проверка зарядки меча
            float cooldown = client.player.getAttackCooldownProgress(0);
            if (cooldown < 0.99f) return;

            Entity target = findBestTarget(client);
            if (target == null) {
                currentTarget = null;
                rotationTicks = 0;
                return;
            }
            
            // Задержка смены цели
            if (currentTarget != target) {
                if (targetSwitchCooldown > 0) return;
                currentTarget = target;
                targetSwitchCooldown = 4;
            }
            if (targetSwitchCooldown > 0) {
                targetSwitchCooldown--;
                return;
            }
            
            // Плавный поворот к цели
            Vec3d targetPos = target.getBoundingBox().getCenter();
            double dx = targetPos.x - client.player.getX();
            double dy = targetPos.y - client.player.getEyeY();
            double dz = targetPos.z - client.player.getZ();
            double dh = Math.sqrt(dx * dx + dz * dz);
            
            float aimYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
            float aimPitch = (float) -Math.toDegrees(Math.atan2(dy, dh));
            
            if (rotationTicks == 0) {
                targetYaw = aimYaw;
                targetPitch = aimPitch;
                rotationTicks = 2;
            }
            
            float currentYaw = client.player.getYaw();
            float currentPitch = client.player.getPitch();
            float newYaw = currentYaw + (targetYaw - currentYaw) / rotationTicks;
            float newPitch = currentPitch + (targetPitch - currentPitch) / rotationTicks;
            rotationTicks--;
            
            // Silent Aim
            serverYaw = client.player.getYaw();
            serverPitch = client.player.getPitch();
            client.player.setYaw(newYaw);
            client.player.setPitch(newPitch);
            
            // === КРИТ 90% ===
            boolean isCrit = false;
            if (client.player.isOnGround() && random.nextInt(100) < 90) {
                client.player.jump();
                isCrit = true;
            }
            
            // Атака
            client.interactionManager.attackEntity(client.player, target);
            client.player.swingHand(Hand.MAIN_HAND);
            
            attackTimer = SWORD_COOLDOWN_TICKS;
            
            if (isCrit) {
                client.player.sendMessage(Text.literal("§c⚡ КРИТ 90%! §7" + target.getName().getString()), true);
            }
            
            // Возвращаем углы
            client.player.setYaw(serverYaw);
            client.player.setPitch(serverPitch);
        });
    }

    private Entity findBestTarget(MinecraftClient client) {
        Entity bestTarget = null;
        double bestDistance = 4.0;
        
        Box searchBox = client.player.getBoundingBox().expand(5.0);
        List<Entity> entities = client.world.getOtherEntities(client.player, searchBox);
        
        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity == client.player) continue;
            if (entity instanceof PlayerEntity && ((PlayerEntity)entity).isCreative()) continue;
            
            double distance = client.player.distanceTo(entity);
            if (distance > bestDistance) continue;
            if (!client.player.canSee(entity)) continue;
            
            bestDistance = distance;
            bestTarget = entity;
        }
        return bestTarget;
    }
}
