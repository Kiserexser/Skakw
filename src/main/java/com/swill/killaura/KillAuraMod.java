package com.swill.killaura;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Random;

public class KillAuraMod implements ModInitializer {

    private static boolean enabled = false;
    private static KeyBinding keyBinding;
    private static final Random random = new Random();
    private static int attackCooldown = 0;

    @Override
    public void onInitialize() {
        System.out.println("[KillAura] Mod initialized!");

        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.killaura.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.killaura"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Toggle with R key
            if (keyBinding.wasPressed()) {
                enabled = !enabled;
                String status = enabled ? "§aENABLED" : "§cDISABLED";
                client.player.sendMessage(Text.literal("§l[KillAura] §r" + status), true);
                System.out.println("[KillAura] " + (enabled ? "ENABLED" : "DISABLED"));
            }

            if (!enabled) return;

            // Don't work in inventory/gui
            if (client.currentScreen != null) return;

            // Attack cooldown (minecraft weapon cooldown)
            if (client.player.getAttackCooldownProgress(0) < 0.9f) return;

            // Custom cooldown between attacks (anti-cheat bypass)
            if (attackCooldown > 0) {
                attackCooldown--;
                return;
            }

            // Find nearest entity
            Entity target = findNearestEntity(client);

            if (target != null && client.interactionManager != null) {
                // Attack entity
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
                
                // Random delay between 3-7 ticks (150-350ms at 20 ticks/sec)
                attackCooldown = 3 + random.nextInt(5);
                
                // Debug message
                client.player.sendMessage(Text.literal("§7[KillAura] §fHit: §e" + target.getName().getString()), true);
            }
        });
    }

    private Entity findNearestEntity(MinecraftClient client) {
        Entity nearest = null;
        double nearestDistance = 4.2; // Max range

        // Search radius 5 blocks
        Box searchBox = client.player.getBoundingBox().expand(5.0);
        List<Entity> entities = client.world.getOtherEntities(client.player, searchBox);

        for (Entity entity : entities) {
            // Check if entity is attackable (living entity)
            if (!(entity instanceof LivingEntity)) continue;
            
            // Don't attack self
            if (entity == client.player) continue;
            
            // Don't attack creative mode players
            if (entity instanceof PlayerEntity && ((PlayerEntity) entity).isCreative()) continue;
            
            // Optional: only attack monsters (uncomment if you want)
            // if (!(entity instanceof Monster)) continue;

            double distance = client.player.distanceTo(entity);
            
            // Check line of sight (can't attack through walls)
            if (distance < nearestDistance && client.player.canSee(entity)) {
                nearestDistance = distance;
                nearest = entity;
            }
        }
        
        return nearest;
    }
}
