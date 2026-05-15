package com.swill.killaura;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public class KillAuraMod implements ModInitializer {

    private static boolean clipEnabled = false;
    private static KeyBinding toggleKey;
    private static final Random random = new Random();
    
    // Обходы античита
    private static int tickCounter = 0;
    private static Vec3d lastPos = Vec3d.ZERO;
    private static boolean wasInWall = false;
    private static int noClipMode = 1; // 1 = полный обход

    @Override
    public void onInitialize() {
        System.out.println("[WallClip] Полный обход стен загружен! Нажми R");

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wallclip.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.wallclip"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (toggleKey.wasPressed()) {
                clipEnabled = !clipEnabled;
                String status = clipEnabled ? "§aВКЛЮЧЕН" : "§cВЫКЛЮЧЕН";
                client.player.sendMessage(Text.literal("§l[WallClip] §rПрохождение сквозь стены " + status), true);
                
                if (clipEnabled) {
                    client.player.noClip = true;
                    client.player.sendMessage(Text.literal("§7Теперь ты проходишь через ЛЮБЫЕ блоки! Тебя не отбрасывает назад"), true);
                } else {
                    client.player.noClip = false;
                }
            }

            if (!clipEnabled) return;
            
            // ========== ОСНОВНОЙ ФАЗИНГ (NoClip) ==========
            // Включаем режим прохождения через блоки
            client.player.noClip = true;
            
            // Отключаем коллизии с блоками
            client.player.setBoundingBox(client.player.getBoundingBox().contract(0.01, 0.01, 0.01));
            
            // ========== ПРЕДОТВРАЩАЕМ ОТБРАСЫВАНИЕ НАЗАД ==========
            // Сохраняем позицию если сервер пытается откатить
            if (lastPos == Vec3d.ZERO) {
                lastPos = client.player.getPos();
            }
            
            // Если сервер отбрасывает нас назад (античит) - возвращаемся обратно
            if (client.player.getPos().distanceTo(lastPos) < -0.1) {
                client.player.setPosition(lastPos.x, lastPos.y, lastPos.z);
                client.player.sendMessage(Text.literal("§c⚠ Античит пытается отбросить! Обхожу..."), true);
            }
            
            // ========== АКТИВНОЕ ДВИЖЕНИЕ СКВОЗЬ СТЕНЫ ==========
            double moveSpeed = 0.5;
            Vec3d pos = client.player.getPos();
            
            // Движение вперёд
            if (client.options.forwardKey.isPressed()) {
                double yaw = Math.toRadians(client.player.getYaw());
                double moveX = -Math.sin(yaw) * moveSpeed;
                double moveZ = Math.cos(yaw) * moveSpeed;
                client.player.setPosition(pos.x + moveX, pos.y, pos.z + moveZ);
                lastPos = client.player.getPos();
            }
            
            // Движение назад
            if (client.options.backKey.isPressed()) {
                double yaw = Math.toRadians(client.player.getYaw());
                double moveX = Math.sin(yaw) * moveSpeed;
                double moveZ = -Math.cos(yaw) * moveSpeed;
                client.player.setPosition(pos.x + moveX, pos.y, pos.z + moveZ);
                lastPos = client.player.getPos();
            }
            
            // Движение влево
            if (client.options.leftKey.isPressed()) {
                double yaw = Math.toRadians(client.player.getYaw() - 90);
                double moveX = -Math.sin(yaw) * moveSpeed;
                double moveZ = Math.cos(yaw) * moveSpeed;
                client.player.setPosition(pos.x + moveX, pos.y, pos.z + moveZ);
                lastPos = client.player.getPos();
            }
            
            // Движение вправо
            if (client.options.rightKey.isPressed()) {
                double yaw = Math.toRadians(client.player.getYaw() + 90);
                double moveX = -Math.sin(yaw) * moveSpeed;
                double moveZ = Math.cos(yaw) * moveSpeed;
                client.player.setPosition(pos.x + moveX, pos.y, pos.z + moveZ);
                lastPos = client.player.getPos();
            }
            
            // Движение вверх
            if (client.options.jumpKey.isPressed()) {
                client.player.setPosition(pos.x, pos.y + moveSpeed, pos.z);
                lastPos = client.player.getPos();
            }
            
            // Движение вниз (Shift)
            if (client.options.sneakKey.isPressed()) {
                client.player.setPosition(pos.x, pos.y - moveSpeed, pos.z);
                lastPos = client.player.getPos();
            }
            
            // ========== ОБХОД АНТИЧИТА ==========
            tickCounter++;
            if (tickCounter > 8) {
                tickCounter = 0;
                
                // Микро-движения для имитации нормы
                if (random.nextInt(100) < 15) {
                    double offsetX = (random.nextDouble() - 0.5) * 0.02;
                    double offsetZ = (random.nextDouble() - 0.5) * 0.02;
                    client.player.setPosition(
                        client.player.getX() + offsetX,
                        client.player.getY(),
                        client.player.getZ() + offsetZ
                    );
                }
            }
            
            // ========== ЗАПОМИНАЕМ ПОЗИЦИЮ КАЖДЫЙ ТИК ==========
            if (client.player.age % 5 == 0) {
                lastPos = client.player.getPos();
            }
            
            // ========== ОТКЛЮЧАЕМ ПРОВЕРКУ КОЛЛИЗИЙ ==========
            client.player.setOnGround(true);
            
            // ========== ДОПОЛНИТЕЛЬНЫЙ ОБХОД: Имитация полёта ==========
            if (client.player.getVelocity().y < -0.5) {
                client.player.setVelocity(client.player.getVelocity().x, -0.1, client.player.getVelocity().z);
            }
        });
    }
}
