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
        PLANT_SAPLING,
        USE_BONE_MEAL,
        BREAK_LEAVES_HALF,
        CHOP_OAK,
        BREAK_LEAVES_REST
    }
    
    private static Stage currentStage = Stage.PLANT_SAPLING;
    private static BlockPos saplingPos = null;
    private static Queue<BlockPos> leavesFirstHalf = new LinkedList<>();
    private static Queue<BlockPos> oakBlocks = new LinkedList<>();
    private static Queue<BlockPos> leavesSecondHalf = new LinkedList<>();
    private static int actionDelay = 0;
    private static boolean treeExists = false;
    private static boolean leavesExist = false;

    @Override
    public void onInitialize() {
        System.out.println("[FastFarmer] МГНОВЕННЫЙ фермер загружен! R - вкл/выкл");

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.treefarmer.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.treefarmer"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (toggleKey.wasPressed()) {
                enabled = !enabled;
                client.player.sendMessage(Text.literal("§l[FastFarmer] §r" + (enabled ? "§aON §7(Мгновенный режим)" : "§cOFF")), true);
                if (enabled) {
                    currentStage = Stage.PLANT_SAPLING;
                }
            }
            if (!enabled) return;
            
            if (actionDelay > 0) {
                actionDelay--;
                return;
            }

            // Проверка наличия дерева
            checkForExistingTree(client);
            
            // Если есть дерево или листва - не сажаем
            if ((treeExists || leavesExist) && currentStage == Stage.PLANT_SAPLING) {
                if (treeExists && !oakBlocks.isEmpty()) {
                    currentStage = Stage.CHOP_OAK;
                } else if (leavesExist && !leavesFirstHalf.isEmpty()) {
                    currentStage = Stage.BREAK_LEAVES_HALF;
                }
                return;
            }

            switch (currentStage) {
                case PLANT_SAPLING:
                    plantSapling(client);
                    break;
                case USE_BONE_MEAL:
                    useBoneMeal(client);
                    break;
                case BREAK_LEAVES_HALF:
                    breakLeavesHalf(client);
                    break;
                case CHOP_OAK:
                    chopOak(client);
                    break;
                case BREAK_LEAVES_REST:
                    breakLeavesRest(client);
                    break;
            }
            
            // МГНОВЕННО - без задержек
            actionDelay = 0;
        });
    }
    
    private void checkForExistingTree(MinecraftClient client) {
        treeExists = false;
        leavesExist = false;
        oakBlocks.clear();
        leavesFirstHalf.clear();
        leavesSecondHalf.clear();
        
        BlockPos center = client.player.getBlockPos();
        
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -5; dy <= 10; dy++) {
                for (int dz = -8; dz <= 8; dz++) {
                    BlockPos p = center.add(dx, dy, dz);
                    String name = client.world.getBlockState(p).getBlock().getName().getString().toLowerCase();
                    
                    if (name.contains("log") || name.contains("wood")) {
                        treeExists = true;
                        oakBlocks.add(p);
                    }
                    else if (name.contains("leaves")) {
                        leavesExist = true;
                        leavesFirstHalf.add(p);
                    }
                }
            }
        }
    }

    private void plantSapling(MinecraftClient client) {
        if (treeExists || leavesExist) return;
        
        int slot = findSapling(client);
        if (slot == -1) return;
        
        client.player.getInventory().selectedSlot = slot;
        BlockPos plantPos = client.player.getBlockPos().down().up();
        
        if (client.world.getBlockState(plantPos).isAir()) {
            Vec3d hitPos = new Vec3d(plantPos.getX() + 0.5, plantPos.getY() + 0.5, plantPos.getZ() + 0.5);
            BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, plantPos, false);
            client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
            saplingPos = plantPos;
            currentStage = Stage.USE_BONE_MEAL;
        }
    }

    private void useBoneMeal(MinecraftClient client) {
        if (saplingPos == null) {
            currentStage = Stage.PLANT_SAPLING;
            return;
        }
        
        int slot = findItem(client, Items.BONE_MEAL);
        if (slot == -1) return;
        
        int prevSlot = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = slot;
        
        Vec3d hitPos = new Vec3d(saplingPos.getX() + 0.5, saplingPos.getY() + 0.5, saplingPos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, saplingPos, false);
        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
        
        client.player.getInventory().selectedSlot = prevSlot;
        
        // Мгновенная проверка роста
        scanTree(client, saplingPos);
        if (!oakBlocks.isEmpty()) {
            currentStage = Stage.BREAK_LEAVES_HALF;
            // Разделяем листву на две половины
            List<BlockPos> allLeaves = new ArrayList<>(leavesFirstHalf);
            allLeaves.addAll(leavesSecondHalf);
            leavesFirstHalf.clear();
            leavesSecondHalf.clear();
            int half = allLeaves.size() / 2;
            for (int i = 0; i < half; i++) leavesFirstHalf.add(allLeaves.get(i));
            for (int i = half; i < allLeaves.size(); i++) leavesSecondHalf.add(allLeaves.get(i));
        }
    }

    private void breakLeavesHalf(MinecraftClient client) {
        int slot = findItem(client, Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE, 
                            Items.GOLDEN_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE);
        if (slot == -1) return;
        
        if (leavesFirstHalf.isEmpty()) {
            currentStage = Stage.CHOP_OAK;
            return;
        }
        
        int prevSlot = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = slot;
        
        // Ломаем ВСЮ первую половину за один тик
        while (!leavesFirstHalf.isEmpty()) {
            BlockPos pos = leavesFirstHalf.poll();
            if (!client.world.getBlockState(pos).isAir()) {
                client.interactionManager.attackBlock(pos, Direction.UP);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
        
        client.player.getInventory().selectedSlot = prevSlot;
        currentStage = Stage.CHOP_OAK;
    }

    private void chopOak(MinecraftClient client) {
        int slot = findItem(client, Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, 
                            Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE);
        if (slot == -1) return;
        
        if (oakBlocks.isEmpty()) {
            currentStage = Stage.BREAK_LEAVES_REST;
            return;
        }
        
        int prevSlot = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = slot;
        
        // Ломаем ВЕСЬ ДУБ за один тик
        while (!oakBlocks.isEmpty()) {
            BlockPos pos = oakBlocks.poll();
            if (!client.world.getBlockState(pos).isAir()) {
                client.interactionManager.attackBlock(pos, Direction.UP);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
        
        client.player.getInventory().selectedSlot = prevSlot;
        currentStage = Stage.BREAK_LEAVES_REST;
    }

    private void breakLeavesRest(MinecraftClient client) {
        int slot = findItem(client, Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE, 
                            Items.GOLDEN_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE);
        if (slot == -1) return;
        
        if (leavesSecondHalf.isEmpty()) {
            currentStage = Stage.PLANT_SAPLING;
            saplingPos = null;
            treeExists = false;
            leavesExist = false;
            return;
        }
        
        int prevSlot = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = slot;
        
        // Ломаем ВСЮ вторую половину за один тик
        while (!leavesSecondHalf.isEmpty()) {
            BlockPos pos = leavesSecondHalf.poll();
            if (!client.world.getBlockState(pos).isAir()) {
                client.interactionManager.attackBlock(pos, Direction.UP);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
        
        client.player.getInventory().selectedSlot = prevSlot;
        currentStage = Stage.PLANT_SAPLING;
        saplingPos = null;
        treeExists = false;
        leavesExist = false;
    }

    private void scanTree(MinecraftClient client, BlockPos start) {
        oakBlocks.clear();
        leavesFirstHalf.clear();
        leavesSecondHalf.clear();
        
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start);
        
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (visited.contains(pos)) continue;
            visited.add(pos);
            
            for (int dx = -7; dx <= 7; dx++) {
                for (int dy = -7; dy <= 12; dy++) {
                    for (int dz = -7; dz <= 7; dz++) {
                        BlockPos p = pos.add(dx, dy, dz);
                        if (visited.contains(p)) continue;
                        String name = client.world.getBlockState(p).getBlock().getName().getString().toLowerCase();
                        
                        if (name.contains("log") || name.contains("wood")) {
                            oakBlocks.add(p);
                            queue.add(p);
                        }
                        else if (name.contains("leaves")) {
                            leavesFirstHalf.add(p);
                        }
                    }
                }
            }
        }
    }

    private int findSapling(MinecraftClient client) {
        Item[] saplings = {Items.OAK_SAPLING, Items.SPRUCE_SAPLING, Items.BIRCH_SAPLING, 
                           Items.JUNGLE_SAPLING, Items.ACACIA_SAPLING, Items.DARK_OAK_SAPLING};
        for (int i = 0; i < 9; i++) {
            Item item = client.player.getInventory().getStack(i).getItem();
            for (Item s : saplings) if (item == s) return i;
        }
        return -1;
    }

    private int findItem(MinecraftClient client, Item... items) {
        for (int i = 0; i < 9; i++) {
            Item item = client.player.getInventory().getStack(i).getItem();
            for (Item target : items) if (item == target) return i;
        }
        return -1;
    }
}
