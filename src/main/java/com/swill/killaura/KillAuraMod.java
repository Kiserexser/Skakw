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
    
    private enum State { PLANT, GROW, CHOP_WOOD, BREAK_LEAVES }
    private static State currentState = State.PLANT;
    private static BlockPos saplingPos = null;
    private static Queue<BlockPos> woodBlocks = new LinkedList<>();
    private static Queue<BlockPos> leavesBlocks = new LinkedList<>();
    private static int actionDelay = 0;

    @Override
    public void onInitialize() {
        System.out.println("[TreeFarmer] Мод загружен! R - вкл/выкл");

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
                client.player.sendMessage(Text.literal("§l[Farmer] §r" + (enabled ? "§aON" : "§cOFF")), true);
                if (!enabled) {
                    currentState = State.PLANT;
                    saplingPos = null;
                    woodBlocks.clear();
                    leavesBlocks.clear();
                }
            }
            if (!enabled) return;
            
            if (actionDelay > 0) {
                actionDelay--;
                return;
            }

            switch (currentState) {
                case PLANT:
                    plantSapling(client);
                    break;
                case GROW:
                    growTree(client);
                    break;
                case CHOP_WOOD:
                    chopWood(client);
                    break;
                case BREAK_LEAVES:
                    breakLeaves(client);
                    break;
            }
        });
    }

    private void plantSapling(MinecraftClient client) {
        int slot = findSapling(client);
        if (slot == -1) { 
            client.player.sendMessage(Text.literal("§cНет саженца!"), true); 
            return; 
        }
        
        client.player.getInventory().selectedSlot = slot;
        BlockPos ground = client.player.getBlockPos().down();
        BlockPos plantPos = ground.up();
        
        if (client.world.getBlockState(plantPos).isAir()) {
            Vec3d hitPos = new Vec3d(plantPos.getX() + 0.5, plantPos.getY() + 0.5, plantPos.getZ() + 0.5);
            BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, plantPos, false);
            client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
            saplingPos = plantPos;
            currentState = State.GROW;
            actionDelay = 10;
            client.player.sendMessage(Text.literal("§a🌱 Саженец посажен"), true);
        } else {
            scanTree(client, plantPos);
            if (!woodBlocks.isEmpty()) {
                currentState = State.CHOP_WOOD;
                client.player.sendMessage(Text.literal("§eНайдено старое дерево, рублю..."), true);
            }
        }
    }

    private void growTree(MinecraftClient client) {
        if (saplingPos == null) { 
            currentState = State.PLANT; 
            return; 
        }
        
        int slot = findItem(client, Items.BONE_MEAL);
        if (slot == -1) { 
            client.player.sendMessage(Text.literal("§cНет костной муки!"), true); 
            return; 
        }
        
        int prev = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = slot;
        
        Vec3d hitPos = new Vec3d(saplingPos.getX() + 0.5, saplingPos.getY() + 0.5, saplingPos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, saplingPos, false);
        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
        
        client.player.getInventory().selectedSlot = prev;
        actionDelay = 15;
        
        new Thread(() -> {
            try {
                Thread.sleep(600);
                MinecraftClient.getInstance().execute(() -> {
                    scanTree(client, saplingPos);
                    if (!woodBlocks.isEmpty()) {
                        currentState = State.CHOP_WOOD;
                        client.player.sendMessage(Text.literal("§a🌲 Дерево выросло! Блоков: " + woodBlocks.size()), true);
                    } else {
                        client.player.sendMessage(Text.literal("§eДерево не выросло, пробую ещё раз..."), true);
                        currentState = State.GROW;
                    }
                });
            } catch (InterruptedException e) {}
        }).start();
    }

    private void chopWood(MinecraftClient client) {
        int axeSlot = -1;
        for (int i = 0; i < 9; i++) {
            Item item = client.player.getInventory().getStack(i).getItem();
            if (item instanceof AxeItem) {
                axeSlot = i;
                break;
            }
        }
        
        if (axeSlot == -1) { 
            client.player.sendMessage(Text.literal("§cНет топора в горячем баре!"), true); 
            return; 
        }
        
        if (woodBlocks.isEmpty()) {
            currentState = State.BREAK_LEAVES;
            client.player.sendMessage(Text.literal("§a🪓 Дерево срублено, убираю листву..."), true);
            return;
        }
        
        int prevSlot = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = axeSlot;
        
        BlockPos pos = woodBlocks.poll();
        
        // Ломаем блок БЕЗ lookAt
        client.interactionManager.attackBlock(pos, Direction.UP);
        client.player.swingHand(Hand.MAIN_HAND);
        
        client.player.getInventory().selectedSlot = prevSlot;
        actionDelay = 4;
        
        if (woodBlocks.size() % 5 == 0) {
            client.player.sendMessage(Text.literal("§7🪓 Рублю... Осталось: " + woodBlocks.size()), true);
        }
    }

    private void breakLeaves(MinecraftClient client) {
        int hoeSlot = -1;
        for (int i = 0; i < 9; i++) {
            Item item = client.player.getInventory().getStack(i).getItem();
            if (item instanceof HoeItem) {
                hoeSlot = i;
                break;
            }
        }
        
        if (hoeSlot == -1) { 
            client.player.sendMessage(Text.literal("§cНет мотыги в горячем баре!"), true); 
            return; 
        }
        
        if (leavesBlocks.isEmpty()) {
            currentState = State.PLANT;
            saplingPos = null;
            client.player.sendMessage(Text.literal("§a✅ Дерево полностью обработано!"), true);
            return;
        }
        
        int prevSlot = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = hoeSlot;
        
        BlockPos pos = leavesBlocks.poll();
        
        // Ломаем блок БЕЗ lookAt
        client.interactionManager.attackBlock(pos, Direction.UP);
        client.player.swingHand(Hand.MAIN_HAND);
        
        client.player.getInventory().selectedSlot = prevSlot;
        actionDelay = 2;
        
        if (leavesBlocks.size() % 10 == 0) {
            client.player.sendMessage(Text.literal("§7🍃 Убираю листву... Осталось: " + leavesBlocks.size()), true);
        }
    }

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
            
            for (int dx = -7; dx <= 7; dx++) {
                for (int dy = -7; dy <= 7; dy++) {
                    for (int dz = -7; dz <= 7; dz++) {
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
        client.player.sendMessage(Text.literal("§a📊 Дерево: §f" + woodBlocks.size() + " §aблоков, листва: §f" + leavesBlocks.size()), true);
    }

    private int findSapling(MinecraftClient client) {
        Item[] saplings = {Items.OAK_SAPLING, Items.SPRUCE_SAPLING, Items.BIRCH_SAPLING, 
                           Items.JUNGLE_SAPLING, Items.ACACIA_SAPLING, Items.DARK_OAK_SAPLING,
                           Items.MANGROVE_PROPAGULE, Items.CHERRY_SAPLING};
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
