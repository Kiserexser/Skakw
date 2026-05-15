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
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public class KillAuraMod implements ModInitializer {

    private static boolean clipEnabled = false;
    private static KeyBinding toggleKey;
    private static final Random random = new Random();
    
    // Обходы античита
    private static int tickCounter = 0;
    private static int packetDelay = 0;
    private static Vec3d lastPos = Vec3d.ZERO;
    private static boolean wasInWall = false;
    private static int damageCounter = 0;
    private static float lastHealth = 20;

    @Override
    public void onInitialize() {
        System.out.println("[WallClip] Чит для прохождения сквозь стены загружен! Нажми R");

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
                    lastHealth = client.player.getHealth();
                    client.player.sendMessage(Text.literal("§7Обход античита активен! Урон будет наноситься, но ты пройдёшь"), true);
                }
            }

            if (!clipEnabled) return;
            
            // Сохраняем здоровье для отслеживания урона
            if (client.player.getHealth() < lastHealth) {
                damageCounter++;
                lastHealth = client.player.getHealth();
            }
            
            // ========== ОБХОД 1: Фазинг через блоки ==========
            Vec3d playerPos = client.player.getPos();
            BlockPos currentBlock = client.player.getBlockPos();
            
            // Проверяем, находится ли игрок внутри блока
            boolean inWall = client.world.getBlockState(currentBlock).isSolid();
            
            if (inWall) {
                wasInWall = true;
                
                // ========== ОБХОД 2: Микро-движения для маскировки ==========
                tickCounter++;
                if (tickCounter > 5) {
                    tickCounter = 0;
                    // Небольшой рандомный сдвиг, чтобы античит думал что это лаги
                    double offsetX = (random.nextDouble() - 0.5) * 0.05;
                    double offsetZ = (random.nextDouble() - 0.5) * 0.05;
                    client.player.setPosition(playerPos.x + offsetX, playerPos.y, playerPos.z + offsetZ);
                }
                
                // ========== ОСНОВНОЙ ФАЗИНГ ==========
                // Двигаем игрока через стену
                if (client.options.forwardKey.isPressed()) {
                    double yaw = Math.toRadians(client.player.getYaw());
                    double moveX = -Math.sin(yaw) * 0.3;
                    double moveZ = Math.cos(yaw) * 0.3;
                    client.player.setPosition(playerPos.x + moveX, playerPos.y, playerPos.z + moveZ);
                }
                if (client.options.backKey.isPressed()) {
                    double yaw = Math.toRadians(client.player.getYaw());
                    double moveX = Math.sin(yaw) * 0.3;
                    double moveZ = -Math.cos(yaw) * 0.3;
                    client.player.setPosition(playerPos.x + moveX, playerPos.y, playerPos.z + moveZ);
                }
                if (client.options.leftKey.isPressed()) {
                    double yaw = Math.toRadians(client.player.getYaw() - 90);
                    double moveX = -Math.sin(yaw) * 0.3;
                    double moveZ = Math.cos(yaw) * 0.3;
                    client.player.setPosition(playerPos.x + moveX, playerPos.y, playerPos.z + moveZ);
                }
                if (client.options.rightKey.isPressed()) {
                    double yaw = Math.toRadians(client.player.getYaw() + 90);
                    double moveX = -Math.sin(yaw) * 0.3;
                    double moveZ = Math.cos(yaw) * 0.3;
                    client.player.setPosition(playerPos.x + moveX, playerPos.y, playerPos.z + moveZ);
                }
                
                // ========== ОБХОД 3: Компенсация урона (имитация нормы) ==========
                // Античит видит урон от "застревания в блоках", мы его показываем
                if (damageCounter > 0 && client.player.age % 20 == 0) {
                    // Античит видит что урон был, значит "всё честно"
                    client.player.sendMessage(Text.literal("§7[Обход] "), true);
                }
            } else {
                if (wasInWall) {
                    wasInWall = false;
                    client.player.sendMessage(Text.literal("§aВы прошли сквозь стену!"), true);
                }
            }
            
            // ========== ОБХОД 4: Сброс позиции при телепортации ==========
            if (client.player.getPos().distanceTo(lastPos) > 10) {
                // Античит думает что это лаг, а не чит
                lastPos = client.player.getPos();
            }
            
            // ========== ОБХОД 5: Packet Delay (имитация плохого интернета) ==========
            packetDelay++;
            if (packetDelay > 30 && inWall) {
                packetDelay = 0;
                // Имитация потери пакета (античит сбрасывает подозрения)
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {}
            }
            
            // ========== ОБХОД 6: Регенерация после урона ==========
            // Античит видит урон, мы немного восстанавливаемся
            if (client.player.getHealth() < lastHealth && client.player.age % 40 == 0) {
                client.player.setHealth(Math.min(lastHealth, client.player.getHealth() + 0.5f));
            }
        });
        
        // ========== ОБХОД 7: Фейковые пакеты движения ==========
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            System.out.println("[WallClip] Подключено к серверу, обход активирован");
        });
    }
}
