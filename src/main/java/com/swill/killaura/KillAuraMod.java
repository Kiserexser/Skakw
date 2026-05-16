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
        CHECK_AREA,         // 0. Проверка радиуса
        PLANT_SAPLING,      // 1. Посадка
        USE_BONE_MEAL,      // 2. Костная мука
        BREAK_LEAVES,       // 3. Листва мотыгой
        CHOP_WOOD           // 4. Дуб топором
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
    
    // ========== 0. ПРОВЕРКА РАДИУСА 10 БЛОКОВ ==========
    private void checkArea(MinecraftClient client) {
        boolean hasWood = false;
        boolean hasLeaves = false;
        
        BlockPos center = client.player.getBlockPos();
        
        // Проверяем радиус 10 блоков
        for (int dx = -10; dx <= 10; dx++) {
            for (int dy = -10; dy <= 10; dy++) {
                for (int dz = -10; dz <= 10; dz++) {
                    BlockPos p = center.add(dx, dy, dz);
                    String name = client.world.getBlockState(p).getBlock().getName().getString().toLowerCase();
                    
                    if (name.contains("log") || name.contains("wood")) {
                        hasWood = true;
                    }
                    if (name.contains("leaves")) {
                        hasLeaves = true;
                    }
                }
            }
        }
        
        if (hasWood || hasLeaves) {
            // Есть дерево или листва → НЕ САЖАЕМ
            client.player.sendMessage(Text.literal("§c🚫 В радиусе 10 блоков есть дерево/листва! Саженец НЕ посажен"), true);
            actionDelay = 40;
            // Если есть дерево/листва - рубим их
            if (hasWood) {
                scanNearbyTree(client, center);
                if (!woodBlocks.isEmpty()) {
                    currentStage = Stage.CHOP_WOOD;
                    client.player.sendMessage(Text.literal("§eНайдено дерево! Рублю..."), true);
                }
            } else if (hasLeaves) {
                scanNearbyLeaves(client, center);
                if (!leavesBlocks.isEmpty()) {
                    currentStage = Stage.BREAK_LEAVES;
                    client.player.sendMessage(Text.literal("§eНайдена листва! Убираю..."), true);
                }
            }
        } else {
            // Нет дерева → можно сажать
            currentStage = Stage.PLANT_SAPLING;
            client.player.sendMessage(Text.literal("§a✅ Радиус чист! Можно сажать саженец"), true);
        }
    }
    
    // Сканирование дерева поблизости
    private void scanNearbyTree(MinecraftClient client, BlockPos center) {
        woodBlocks.clear();
        leavesBlocks.clear();
        
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
    }
    
    private void scanNearbyLeaves(MinecraftClient client, BlockPos center) {
        leavesBlocks.clear();
        
        for (int dx = -10; dx <= 10; dx++) {
            for (int dy = -10; dy <= 10; dy++) {
                for (int dz = -10; dz <= 10; dz++) {
                    BlockPos p = center.add(dx, dy, dz);
                    String name = client.world.getBlockState(p).getBlock().getName().getString().toLowerCase();
                    
                    if (name.contains("leaves")) {
                        leavesBlocks.add(p);
                    }
                }
            }
        }
    }

    // ========== 1. ПОСАДКА САЖЕНЦА ==========
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
        }
    }

    // ========== 2. КОСТНАЯ МУКА (ДО РОСТА) ==========
    private void useBoneMeal(MinecraftClient client) {
        if (saplingPos == null) {
            currentStage = Stage.CHECK_AREA;
            return;
        }
        
        int slot = findBoneMealInInventory(client);
        if (slot == -1) {
            client.player.sendMessage(Text.literal("§cНет костной муки в инвентаре!"), true);
            return;
        }
        
        int prevSlot = client.player.getInventory().selectedSlot;
        // Перекладываем костную муку в хот бар если её там нет
        if (slot >= 9) {
            // Меняем местами с первым слотом
            var hotbarStack = client.player.getInventory().getStack(0);
            var boneMealStack = client.player.getInventory().getStack(slot);
            client.player.getInventory().setStack(0, boneMealStack);
            client.player.getInventory().setStack(slot, hotbarStack);
            slot = 0;
        }
        
        client.player.getInventory().selectedSlot = slot;
        
        Vec3d hitPos = new Vec3d(saplingPos.getX() + 0.5, saplingPos.getY() + 0.5, saplingPos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, saplingPos, false);
        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
        boneMealAttempts++;
        
        client.player.getInventory().selectedSlot = prevSlot;
        actionDelay = 5;
        
        // Проверяем выросло ли дерево
        new Thread(() -> {
            try {
                Thread.sleep(200);
                MinecraftClient.getInstance().execute(() -> {
                    scanTree(client, saplingPos);
                    if (!woodBlocks.isEmpty()) {
                        currentStage = Stage.BREAK_LEAVES;
                        client.player.sendMessage(Text.literal("§a🌲 Дерево выросло! Блоков дуба: " + woodBlocks.size() + ", листвы: " + leavesBlocks.size()), true);
                    } else if (boneMealAttempts > 30) {
                        client.player.sendMessage(Text.literal("§cНе удалось вырастить дерево!"), true);
                        currentStage = Stage.CHECK_AREA;
                    }
                });
            } catch (InterruptedException e) {}
        }).start();
    }

    // ========== 3. ЛИСТВА МОТЫГОЙ ==========
    private void breakLeaves(MinecraftClient client) {
        int slot = findHoeInHotbar(client);
        if (slot == -1) {
            client.player.sendMessage(Text.literal("§cНет мотыги в хот баре!"), true);
            return;
        }
        
        if (leavesBlocks.isEmpty()) {
            currentStage = Stage.CHOP_WOOD;
            client.player.sendMessage(Text.literal("§a✅ Вся листва срублена! Беру топор..."), true);
            return;
        }
        
        int prevSlot = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = slot;
        
        // Ломаем до 5 блоков листвы за раз (для плавности)
        int broken = 0;
        while (!leavesBlocks.isEmpty() && broken < 5) {
            BlockPos pos = leavesBlocks.poll();
            if (!client.world.getBlockState(pos).isAir()) {
                client.interactionManager.attackBlock(pos, Direction.UP);
                client.player.swingHand(Hand.MAIN_HAND);
                broken++;
            }
        }
        
        client.player.getInventory().selectedSlot = prevSlot;
        actionDelay = 3;
        
        if (leavesBlocks.size() % 10 == 0) {
            client.player.sendMessage(Text.literal("§7🍃 Листва: осталось " + leavesBlocks.size()), true);
        }
    }

    // ========== 4. ДУБ ТОПОРОМ ==========
    private void chopWood(MinecraftClient client) {
        int slot = findAxeInHotbar(client);
        if (slot == -1) {
            client.player.sendMessage(Text.literal("§cНет топора в хот баре!"), true);
            return;
        }
        
        if (woodBlocks.isEmpty()) {
            // Цикл завершён
            currentStage = Stage.CHECK_AREA;
            saplingPos = null;
            client.player.sendMessage(Text.literal("§a🎉 Дерево полностью обработано! Проверяю радиус..."), true);
            return;
        }
        
        int prevSlot = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = slot;
        
        // Ломаем до 5 блоков дуба за раз
        int broken = 0;
        while (!woodBlocks.isEmpty() && broken < 5) {
            BlockPos pos = woodBlocks.poll();
            if (!client.world.getBlockState(pos).isAir()) {
                client.interactionManager.attackBlock(pos, Direction.UP);
                client.player.swingHand(Hand.MAIN_HAND);
                broken++;
            }
        }
        
        client.player.getInventory().selectedSlot = prevSlot;
        actionDelay = 3;
        
        if (woodBlocks.size() % 5 == 0) {
            client.player.sendMessage(Text.literal("§7🪓 Дуб: осталось " + woodBlocks.size() + " блоков"), true);
        }
    }

    // ========== СКАНИРОВАНИЕ ВЫРОСШЕГО ДЕРЕВА ==========
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
            
            for (int dx = -5; dx <= 5; dx++) {
                for (int dy = -5; dy <= 10; dy++) {
                    for (int dz = -5; dz <= 5; dz++) {
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
                           Items.JUNGLE_SAPLING, Items.ACACIA_SAPLING, Items.DARK_OAK_SAPLING,
                           Items.MANGROVE_PROPAGULE, Items.CHERRY_SAPLING};
        for (int i = 0; i < 9; i++) {
            Item item = client.player.getInventory().getStack(i).getItem();
            for (Item s : saplings) if (item == s) return i;
        }
        return -1;
    }
    
    private int findAxeInHotbar(MinecraftClient client) {
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                return i;
            }
        }
        return -1;
    }
    
    private int findHoeInHotbar(MinecraftClient client) {
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getStack(i).getItem() instanceof HoeItem) {
                return i;
            }
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
