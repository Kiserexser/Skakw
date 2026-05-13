package com.swill.killaura;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public class KillAuraMod implements ModInitializer {

    private static boolean enabled = false;
    private static KeyBinding toggleKey;
    private static final Random random = new Random();
    
    private static int tickCounter = 0;
    private static int packetCounter = 0;
    private static double fakeY = 0;

    @Override
    public void onInitialize() {
        System.out.println("[Freeze] Мод загружен - Максимальный обход");

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.freeze.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.freeze"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (toggleKey.wasPressed()) {
                enabled = !enabled;
                String status = enabled ? "§aЗАВИСАНИЕ" : "§cВЫКЛ";
                client.player.sendMessage(Text.literal("§l[Freeze] §r" + status), true);
                
                if (enabled) {
                    fakeY = client.player.getY();
                }
            }

            if (!enabled) return;
            if (client.player.isCreative()) return;
            
            Vec3d vel = client.player.getVelocity();
            
            // Обход 1: Блокировка падения
            if (vel.y < 0) {
                client.player.setVelocity(vel.x, 0, vel.z);
            }
            
            // Обход 2: Подмена пакетов
            packetCounter++;
            if (packetCounter >= 5) {
                packetCounter = 0;
                if (client.getNetworkHandler() != null) {
                    PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.PositionAndOnGround(
                        client.player.getX(),
                        fakeY,
                        client.player.getZ(),
                        true
                    );
                    client.getNetworkHandler().sendPacket(packet);
                }
            }
            
            // Обход 3: Микро-движения
            tickCounter++;
            if (tickCounter > 8 + random.nextInt(12)) {
                tickCounter = 0;
                double offsetX = (random.nextDouble() - 0.5) * 0.008;
                double offsetZ = (random.nextDouble() - 0.5) * 0.008;
                client.player.setVelocity(vel.x + offsetX, 0, vel.z + offsetZ);
            }
            
            // Обход 4: Сброс при ударе
            if (client.player.hurtTime > 0) {
                client.player.setVelocity(vel.x, 0, vel.z);
                fakeY = client.player.getY();
            }
            
            // Обход 5: Фиксация Y
            if (Math.abs(client.player.getY() - fakeY) > 0.1) {
                client.player.setPosition(client.player.getX(), fakeY, client.player.getZ());
            }
        });
    }
}
