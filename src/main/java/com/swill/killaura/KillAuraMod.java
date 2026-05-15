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
    
    // Тайминг меча (12 тиков = 0.6 сек)
    private static int attackCooldown = 0;
    private static final int SWORD_CD_TICKS = 12;
    
    // Плавный поворот (камера не дёргается)
    private static float targetYaw = 0;
    private static float targetPitch = 0;
    private static int rotationTicks = 0;
    private static float serverYaw = 0;
    private static float serverPitch = 0;
    
    // Крит шанс (как у меча)
    private static int critChance = 85;

    @Override
    public void onInitialize() {
        System.out.println("[KillAura] Беспалевная KillAura загружена! Нажми R");

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
                String status = enabled ? "§aВКЛЮЧЕН" : "§cВЫКЛЮЧЕН";
                client.player.sendMessage(Text.literal("§l[KillAura] §r" + status), true);
            }

            if (!enabled) return;
            if (client.currentScreen != null) return;
            
            // === ТОЛЬКО МЕЧ! ===
            boolean hasSword = client.player.getMainHandStack().getItem() instanceof SwordItem;
            if (!hasSword) return;
            
            // === ТАЙМИНГ МЕЧА (КТ как у меча) ===
            // Меч бьёт раз в 12 тиков (0.6 сек)
            if (attackCooldown > 0) {
                attackCooldown--;
                return;
            }
            
            // Дополнительная проверка зарядки меча
            float cooldown = client.player.getAttackCooldownProgress(0);
            if (cooldown < 0.98f) return;

            // Поиск цели
            Entity target = findTarget(client);
            if (target == null) return;
            
            // === ПЛАВНЫЙ ПОВОРОТ (камера не дёргается) ===
            if (rotationTicks == 0) {
                Vec3d targetPos = target.getBoundingBox().getCenter();
                double dx = targetPos.x - client.player.getX();
                double dy = targetPos.y - client.player.getEyeY();
                double dz = targetPos.z - client.player.getZ();
                double dh = Math.sqrt(dx * dx + dz * dz);
                
                targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
                targetPitch = (float) -Math.toDegrees(Math.atan2(dy, dh));
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
            
            // === КРИТ (как у меча) ===
            boolean willCrit = false;
            if (client.player.isOnGround() && random.nextInt(100) < critChance) {
                client.player.jump();
                willCrit = true;
            }
            
            // === АТАКА ===
            client.interactionManager.attackEntity(client.player, target);
            client.player.swingHand(Hand.MAIN_HAND);
            
            // Устанавливаем КД как у меча
            attackCooldown = SWORD_CD_TICKS;
            
            if (willCrit) {
                client.player.sendMessage(Text.literal("§c⚡ КРИТ! §7" + target.getName().getString()), true);
            }
            
            // Возвращаем углы
            client.player.setYaw(serverYaw);
            client.player.setPitch(serverPitch);
        });
    }

    private Entity findTarget(MinecraftClient client) {
        Entity nearest = null;
        double nearestDist = 4.2; // Безопасная дистанция
        
        Box box = client.player.getBoundingBox().expand(5.0);
        List<Entity> entities = client.world.getOtherEntities(client.player, box);
        
        for (Entity e : entities) {
            if (!(e instanceof LivingEntity)) continue;
            if (e == client.player) continue;
            if (e instanceof PlayerEntity && ((PlayerEntity)e).isCreative()) continue;
            
            double dist = client.player.distanceTo(e);
            if (dist > nearestDist) continue;
            if (!client.player.canSee(e)) continue;
            
            nearestDist = dist;
            nearest = e;
        }
        return nearest;
    }
}
