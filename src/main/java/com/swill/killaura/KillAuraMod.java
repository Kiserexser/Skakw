package com.swill.killaura;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class KillAuraMod implements ModInitializer {

    private static boolean godMode = false;
    private static KeyBinding toggleKey;
    
    // Для бессмертия
    private static float lastHealth = 20;
    private static int lastFood = 20;
    private static int lastAir = 300;
    
    // Для защиты от игроков
    private static Vec3d lastPosition = Vec3d.ZERO;
    private static float lastYaw = 0;
    private static float lastPitch = 0;

    @Override
    public void onInitialize() {
        System.out.println("[GodMode] Режим бога + защита от игроков загружен! Нажми R");

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.godmode.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.godmode"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (toggleKey.wasPressed()) {
                godMode = !godMode;
                String status = godMode ? "§aВКЛЮЧЕН" : "§cВЫКЛЮЧЕН";
                client.player.sendMessage(Text.literal("§l[GodMode] §r" + status), true);
                
                if (godMode) {
                    lastHealth = client.player.getHealth();
                    lastFood = client.player.getHungerManager().getFoodLevel();
                    lastAir = client.player.getAir();
                    lastPosition = client.player.getPos();
                    lastYaw = client.player.getYaw();
                    lastPitch = client.player.getPitch();
                }
            }

            if (!godMode) return;

            // === ЗАЩИТА ОТ ИГРОКОВ ===
            // 1. Игроки не могут тебя ударить
            if (client.player.hurtTime > 0) {
                client.player.hurtTime = 0;
                client.player.lastHurtTime = 0;
                // Восстанавливаем здоровье если кто-то смог ударить
                client.player.setHealth(lastHealth);
            }
            
            // 2. Игроки не могут тебя сдвинуть (отбрасывание)
            if (client.player.getVelocity().length() > 0.5 && client.player.hurtTime > 0) {
                client.player.setVelocity(0, client.player.getVelocity().y, 0);
            }
            
            // 3. Возврат на позицию если кто-то пытался телепортировать
            if (client.player.getPos().distanceTo(lastPosition) > 5) {
                client.player.setPosition(lastPosition.x, lastPosition.y, lastPosition.z);
            }
            
            // 4. Игроки не могут изменить твой обзор
            if (Math.abs(client.player.getYaw() - lastYaw) > 30) {
                client.player.setYaw(lastYaw);
                client.player.setPitch(lastPitch);
            }
            
            // === ОСНОВНЫЕ ФУНКЦИИ РЕЖИМА БОГА ===
            
            // Бесконечное здоровье
            if (client.player.getHealth() < lastHealth) {
                client.player.setHealth(lastHealth);
            } else if (client.player.getHealth() > lastHealth) {
                lastHealth = client.player.getHealth();
            }
            
            // Бесконечная еда
            if (client.player.getHungerManager().getFoodLevel() < 20) {
                client.player.getHungerManager().setFoodLevel(20);
                client.player.getHungerManager().setSaturationLevel(20);
            }
            
            // Бесконечный воздух
            if (client.player.getAir() < 300) {
                client.player.setAir(300);
            }
            
            // Не горит
            client.player.setFireTicks(0);
            
            // Нет урона от падения
            client.player.fallDistance = 0;
            
            // Защита от лавы
            if (client.player.isInLava()) {
                client.player.setVelocity(client.player.getVelocity().x, 0.3, client.player.getVelocity().z);
            }
            
            // Убираем вредные эффекты
            client.player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.POISON);
            client.player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.HUNGER);
            client.player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.WITHER);
            client.player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS);
            client.player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.WEAKNESS);
            client.player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS);
            
            // Бесконечная прочность
            if (client.player.getMainHandStack() != null) {
                client.player.getMainHandStack().setDamage(0);
            }
            
            // Полёт
            if (!client.player.isCreative()) {
                client.player.getAbilities().allowFlying = true;
                if (client.options.jumpKey.isPressed()) {
                    client.player.getAbilities().flying = true;
                } else if (client.player.isOnGround()) {
                    client.player.getAbilities().flying = false;
                }
            }
            
            // Сохраняем позицию и обзор каждые 20 тиков
            if (client.player.age % 20 == 0) {
                lastPosition = client.player.getPos();
                lastYaw = client.player.getYaw();
                lastPitch = client.player.getPitch();
            }
        });
        
        // Обработка получения урона (событие)
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity == MinecraftClient.getInstance().player && godMode) {
                // Игроки не могут нанести урон
                if (source.getAttacker() instanceof PlayerEntity) {
                    return false; // Урон не проходит
                }
                // От мобов тоже защита
                if (source.getAttacker() != null && !(source.getAttacker() instanceof PlayerEntity)) {
                    return false;
                }
            }
            return true;
        });
    }
}
