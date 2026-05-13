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
    private static int attackDelay = 0;
    
    // Реальные углы (то куда смотрит сервер)
    private static float realYaw = 0;
    private static float realPitch = 0;

    @Override
    public void onInitialize() {
        System.out.println("[KillAura] Split Aim - сервер видит лицо к цели, клиент смотрит куда хочет");

        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.killaura.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.killaura"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Toggle R
            if (keyBinding.wasPressed()) {
                enabled = !enabled;
                String status = enabled ? "§aВКЛЮЧЕН §7(Split Aim)" : "§cВЫКЛЮЧЕН";
                client.player.sendMessage(Text.literal("§l[KillAura] §r" + status), true);
            }

            if (!enabled) return;
            if (client.currentScreen != null) return;
            
            // Задержка как у меча
            if (attackDelay > 0) {
                attackDelay--;
                return;
            }

            Entity target = findTarget(client);
            if (target != null && client.interactionManager != null) {
                
                // === SPLIT AIM ===
                // Запоминаем где ты реально смотришь (от первого лица)
                float clientYaw = client.player.getYaw();
                float clientPitch = client.player.getPitch();
                
                // Вычисляем углы до цели (куда надо смотреть чтобы ударить)
                Vec3d targetPos = target.getBoundingBox().getCenter();
                double dx = targetPos.x - client.player.getX();
                double dy = targetPos.y - client.player.getEyeY();
                double dz = targetPos.z - client.player.getZ();
                double dh = Math.sqrt(dx * dx + dz * dz);
                
                float aimYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
                float aimPitch = (float) -Math.toDegrees(Math.atan2(dy, dh));
                
                // Ставим углы для сервера (как будто смотрим на цель)
                client.player.setYaw(aimYaw);
                client.player.setPitch(aimPitch);
                
                // Атакуем
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
                
                // МГНОВЕННО возвращаем углы клиента (твой реальный взгляд)
                client.player.setYaw(clientYaw);
                client.player.setPitch(clientPitch);
                // === КОНЕЦ SPLIT AIM ===
                
                // Задержка 4-7 тиков (200-350 мс) как у меча
                attackDelay = 4 + random.nextInt(4);
                
                // Дебаг в чат
                client.player.sendMessage(Text.literal("§7⚔ §f" + target.getName().getString()), true);
            }
        });
    }

    private Entity findTarget(MinecraftClient client) {
        Entity nearest = null;
        double nearestDist = 4.2;
        
        Box box = client.player.getBoundingBox().expand(5.0);
        List<Entity> entities = client.world.getOtherEntities(client.player, box);
        
        for (Entity e : entities) {
            if (!(e instanceof LivingEntity)) continue;
            if (e == client.player) continue;
            if (e instanceof PlayerEntity && ((PlayerEntity)e).isCreative()) continue;
            
            double dist = client.player.distanceTo(e);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = e;
            }
        }
        return nearest;
    }
}
