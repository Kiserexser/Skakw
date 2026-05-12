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
import org.lwjgl.glfw.GLFW;

public class KillAuraMod implements ModInitializer {
    
    private static boolean enabled = true;
    private static KeyBinding toggleKey;
    
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
            }
            
            if (enabled && client.player != null && client.world != null) {
                if (client.player.getAttackCooldownProgress(0) < 0.9f) return;
                
                Entity target = null;
                double bestDist = 4.5;
                
                for (Entity entity : client.world.getEntities()) {
                    if (entity == client.player) continue;
                    if (!(entity instanceof LivingEntity)) continue;
                    if (entity instanceof PlayerEntity && ((PlayerEntity)entity).isCreative()) continue;
                    
                    double dist = client.player.distanceTo(entity);
                    if (dist < bestDist) {
                        bestDist = dist;
                        target = entity;
                    }
                }
                
                if (target != null && client.interactionManager != null) {
                    client.interactionManager.attackEntity(client.player, target);
                    client.player.swingHand(Hand.MAIN_HAND);
                }
            }
        });
    }
}
