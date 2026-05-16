package com.swill.killaura;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class KillAuraMod implements ModInitializer {

    private static boolean enabled = false;
    private static KeyBinding toggleKey;
    
    private enum Stage {
        CHECK_AREA,
        PLANT_SAPLING,
        USE_BONE_MEAL,
        BREAK_LEAVES,
        CHOP_WOOD
    }
    
    private static Stage currentStage = Stage.CHECK_AREA;
    private static BlockPos saplingPos = null;
    private static Queue<BlockPos> leavesBlocks = new LinkedList<>();
    private static Queue<BlockPos> woodBlocks = new LinkedList<>();
    private static int actionDelay = 0;
    private static int boneMealAttempts = 0;

    @Override
    public void onInitialize() {
        System.out.println("[SmartFarmer] Умный фермер загружен! R - вкл/выкл");

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.smartfarmer.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.smartfarmer"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (toggleKey.wasPressed()) {
                enabled = !enabled;
                client.player.sendMessage(Text.literal("§l[SmartFarmer] §r" + (enabled ? "§aON" : "§cOFF")), true);
                if (enabled) {
                    currentStage = Stage.CHECK_AREA;
                    woodBlocks.clear();
                    leavesBlocks.clear();
                }
            }
            if (!enabled) return;
            
            if (actionDelay > 0) {
                actionDelay--;
                return;
            }

            switch (currentStage) {
                case CHECK_AREA:
                    checkArea(client);
                    break;
                case PLANT_SAPLING:
                    plantSapling(client);
                    break;
                case USE_BONE_MEAL:
                    useBoneMeal(client);
                    break;
                case BREAK_LEAVES:
                    breakLeaves(client);
                    break;
                case CHOP_WOOD:
                    chopWood(client);
                    break;
            }
        });
    }
    
    // ========== ПРОВЕРКА РАДИУСА ==========
    private void checkArea(MinecraftClient client) {
        woodBlocks.clear();
        leavesBlocks.clear();
        
        BlockPos center = client.player.getBlockPos();
        
        // Сканируем радиус 10 блоков
        for (int dx = -10; dx <= 10; dx++) {
            for (int dy = -10; dy <= 10; dy++) {
                for (int dz = -10; dz <= 10; dz++) {
                    BlockPos p = center.add(dx, dy, dz);
                    String name = client.world.getBlockState(p).getBlock().getName().getString().toLowerCase();
                    
                    if (name.contains("log") || name.contains("wood")) {
                        woodBlocks.add(p);
                    }
                    if (name.contains("leaves")) {
                        leavesBlocks.add(p);
                    }
                }
            }
        }
        
        // Если есть дерево или листва - рубим
        if (!woodBlocks.isEmpty()) {
            currentStage = Stage.CHOP_WOOD;
            client.player.sendMessage(Text.literal("§eНайдено дерево! Рублю топором... §7(" + woodBlocks.size() + " блоков)"), true);
        } 
        else if (!leavesBlocks.isEmpty()) {
            currentStage = Stage.BREAK_LEAVES;
            client.player.sendMessage(Text.literal("§eНайдена листва! Убираю мотыгой... §7(" + leavesBlocks.size() + " блоков)"), true);
        }
        else {
            // Нет дерева и листвы - можно сажать
            currentStage = Stage.PLANT_SAPLING;
            client.player.sendMessage(Text.literal("§a✅ Радиус чист! Сажаю саженец..."), true);
        }
        
        actionDelay = 5;
    }

    // ========== ПОСАДКА ==========
    private void plantSapling(MinecraftClient client) {
        int slot = findSaplingInHotbar(client);
        if (slot == -1) {
            client.player.sendMessage(Text.literal("§cНет саженца в хот баре!"), true);
            return;
        }
        
        client.player.getInventory().selectedSlot = slot;
        BlockPos plantPos = client.player.getBlockPos().down().up();
        
        if (client.world.getBlockState(plantPos).isAir()) {
            Vec3d hitPos = new Vec3d(plantPos.getX() + 0.5, plantPos.getY() + 0.5, plantPos.getZ() + 0.5);
            BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, plantPos, false);
            client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
            saplingPos = plantPos;
            currentStage = Stage.USE_BONE_MEAL;
            boneMealAttempts = 0;
            actionDelay = 10;
            client.player.sendMessage(Text.literal("§a🌱 Саженец посажен!"), true);
        } else {
            client.player.sendMessage(Text.literal("§cНельзя посадить саженец здесь!"), true);
            currentStage = Stage.CHECK_AREA;
            actionDelay = 20;
        }
    }

    // ========== КОСТНАЯ МУКА ==========
    private void useBoneMeal(MinecraftClient client) {
        if (saplingPos == null) {
            currentStage = Stage.CHECK_AREA;
            return;
        }
        
        int slot = findBoneMealInInventory(client);
        if (slot == -1) {
            client.player.sendMessage(Text.literal("§cНет костной муки в инвентаре!"), true);
            currentStage = Stage.CHECK_AREA;
            return;
        }
        
        int prevSlot = client.player.getInventory().selectedSlot;
        
        // Если костная мука не в хот баре - перекладываем
        if (slot >= 9) {
            var boneMealStack = client.player.getInventory().getStack(slot);
            var firstSlotStack = client.player.getInventory().getStack(0);
            client.player.getInventory().setStack(0, boneMealStack);
            client.player.getInventory().setStack(slot, firstSlotStack);
            slot = 0;
        }
        
        client.player.getInventory().selectedSlot = slot;
        
        Vec3d hitPos = new Vec3d(saplingPos.getX() + 0.5, saplingPos.getY() + 0.5, saplingPos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, saplingPos, false);
        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
        boneMealAttempts++;
        
        client.player.getInventory().selectedSlot = prevSlot;
        actionDelay = 8;
        
        // Проверяем выросло ли дерево
        if (boneMealAttempts % 3 == 0) {
            new Thread(() -> {
                try {
                    Thread.sleep(300);
                    MinecraftClient.getInstance().execute(() -> {
                        scanTree(client, saplingPos);
                        if (!woodBlocks.isEmpty()) {
                            currentStage = Stage.BREAK_LEAVES;
                            client.player.sendMessage(Text.literal("§a🌲 Дерево выросло! Древесины: " + woodBlocks.size() + ", листвы: " + leavesBlocks.size()), true);
                        } else if (boneMealAttempts > 20) {
                            client.player.sendMessage(Text.literal("§cДерево не выросло! Проверяю радиус..."), true);
                            currentStage = Stage.CHECK_AREA;
                        }
                    });
                } catch (InterruptedException e) {}
            }).start();
        }
    }

    // ========== ЛИСТВА МОТЫГОЙ ==========
    private void breakLeaves(MinecraftClient client) {
        // Проверяем мотыгу в хот баре
        int hoeSlot = -1;
        for (int i = 0; i < 9; i++) {
            Item item = client.player.getInventory().getStack(i).getItem();
            if (item instanceof HoeItem) {
                hoeSlot = i;
                break;
            }
        }
        
        if (hoeSlot == -1) {
            client.player.sendMessage(Text.literal("§cНет мотыги в хот баре!"), true);
            return;
        }
        
        if (leavesBlocks.isEmpty()) {
            // После листвы рубим дуб
            currentStage = Stage.CHOP_WOOD;
            client.player.sendMessage(Text.literal("§a✅ Листва срублена! Беру топор..."), true);
            return;
        }
        
        int prevSlot = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = hoeSlot;
        
        // Ломаем до 3 блоков за раз
        int broken = 0;
        while (!leavesBlocks.isEmpty() && broken < 3) {
            BlockPos pos = leavesBlocks.poll();
            if (!client.world.getBlockState(pos).isAir()) {
                client.interactionManager.attackBlock(pos, Direction.UP);
                client.player.swingHand(Hand.MAIN_HAND);
                broken++;
            }
        }
        
        client.player.getInventory().selectedSlot = prevSlot;
        actionDelay = 2;
        
        if (leavesBlocks.size() > 0 && leavesBlocks.size() % 8 == 0) {
            client.player.sendMessage(Text.literal("§7🍃 Листва: осталось " + leavesBlocks.size()), true);
        }
    }

    // ========== ДУБ ТОПОРОМ ==========
    private void chopWood(MinecraftClient client) {
        // Проверяем топор в хот баре
        int axeSlot = -1;
        for (int i = 0; i < 9; i++) {
            Item item = client.player.getInventory().getStack(i).getItem();
            if (item instanceof AxeItem) {
                axeSlot = i;
                break;
            }
        }
        
        if (axeSlot == -1) {
            client.player.sendMessage(Text.literal("§cНет топора в хот баре!"), true);
            return;
        }
        
        if (woodBlocks.isEmpty()) {
            // Цикл завершён, проверяем радиус снова
            currentStage = Stage.CHECK_AREA;
            saplingPos = null;
            client.player.sendMessage(Text.literal("§a🎉 Дерево полностью обработано! Проверяю радиус..."), true);
            return;
        }
        
        int prevSlot = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = axeSlot;
        
        // Ломаем до 3 блоков за раз
        int broken = 0;
        while (!woodBlocks.isEmpty() && broken < 3) {
            BlockPos pos = woodBlocks.poll();
            if (!client.world.getBlockState(pos).isAir()) {
                client.interactionManager.attackBlock(pos, Direction.UP);
                client.player.swingHand(Hand.MAIN_HAND);
                broken++;
            }
        }
        
        client.player.getInventory().selectedSlot = prevSlot;
        actionDelay = 3;
        
        if (woodBlocks.size() > 0 && woodBlocks.size() % 5 == 0) {
            client.player.sendMessage(Text.literal("§7🪓 Древесина: осталось " + woodBlocks.size()), true);
        }
    }

    // ========== СКАНИРОВАНИЕ ДЕРЕВА ==========
    private void scanTree(MinecraftClient client, BlockPos start) {
        woodBlocks.clear();
        leavesBlocks.clear();
        
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start);
        
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (visited.contains(pos)) continue;
            visited.add(pos);
            
            for (int dx = -6; dx <= 6; dx++) {
                for (int dy = -5; dy <= 12; dy++) {
                    for (int dz = -6; dz <= 6; dz++) {
                        BlockPos p = pos.add(dx, dy, dz);
                        if (visited.contains(p)) continue;
                        String name = client.world.getBlockState(p).getBlock().getName().getString().toLowerCase();
                        
                        if (name.contains("log") || name.contains("wood")) {
                            woodBlocks.add(p);
                            queue.add(p);
                        }
                        else if (name.contains("leaves")) {
                            leavesBlocks.add(p);
                        }
                    }
                }
            }
        }
    }

    // ========== ПОИСК В ХОТ БАРЕ ==========
    private int findSaplingInHotbar(MinecraftClient client) {
        Item[] saplings = {Items.OAK_SAPLING, Items.SPRUCE_SAPLING, Items.BIRCH_SAPLING, 
                           Items.JUNGLE_SAPLING, Items.ACACIA_SAPLING, Items.DARK_OAK_SAPLING};
        for (int i = 0; i < 9; i++) {
            Item item = client.player.getInventory().getStack(i).getItem();
            for (Item s : saplings) if (item == s) return i;
        }
        return -1;
    }
    
    private int findBoneMealInInventory(MinecraftClient client) {
        for (int i = 0; i < 36; i++) {
            if (client.player.getInventory().getStack(i).getItem() == Items.BONE_MEAL) {
                return i;
            }
        }
        return -1;
    }
}
