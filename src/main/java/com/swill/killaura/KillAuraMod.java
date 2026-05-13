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

            switch (currentState) {
                case PLANT: plantSapling(client); break;
                case GROW: growTree(client); break;
                case CHOP_WOOD: chopWood(client); break;
                case BREAK_LEAVES: breakLeaves(client); break;
            }
        });
    }

    private void plantSapling(MinecraftClient client) {
        int slot = findSapling(client);
        if (slot == -1) { client.player.sendMessage(Text.literal("§cНет саженца"), true); return; }
        
        client.player.getInventory().selectedSlot = slot;
        BlockPos ground = client.player.getBlockPos().down();
        
        if (!client.world.getBlockState(ground).isAir()) {
            saplingPos = ground.up();
            Vec3d hitPos = new Vec3d(saplingPos.getX() + 0.5, saplingPos.getY() + 0.5, saplingPos.getZ() + 0.5);
            BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, saplingPos, false);
            client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
            currentState = State.GROW;
        }
    }

    private void growTree(MinecraftClient client) {
        if (saplingPos == null) { currentState = State.PLANT; return; }
        
        int slot = findItem(client, Items.BONE_MEAL);
        if (slot == -1) { client.player.sendMessage(Text.literal("§cНет костной муки"), true); enabled = false; return; }
        
        int prev = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = slot;
        
        Vec3d hitPos = new Vec3d(saplingPos.getX() + 0.5, saplingPos.getY() + 0.5, saplingPos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, saplingPos, false);
        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
        
        scanTree(client, saplingPos);
        currentState = State.CHOP_WOOD;
        client.player.getInventory().selectedSlot = prev;
    }

    private void chopWood(MinecraftClient client) {
        int slot = findItem(client, Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE);
        if (slot == -1) { client.player.sendMessage(Text.literal("§cНет топора"), true); enabled = false; return; }
        
        int prev = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = slot;
        
        while (!woodBlocks.isEmpty()) {
            BlockPos pos = woodBlocks.poll();
            if (!client.world.getBlockState(pos).isAir()) {
                client.interactionManager.attackBlock(pos, client.player.getActiveHand());
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
        currentState = State.BREAK_LEAVES;
        client.player.getInventory().selectedSlot = prev;
    }

    private void breakLeaves(MinecraftClient client) {
        int slot = findItem(client, Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE);
        if (slot == -1) { client.player.sendMessage(Text.literal("§cНет мотыги"), true); enabled = false; return; }
        
        int prev = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = slot;
        
        while (!leavesBlocks.isEmpty()) {
            BlockPos pos = leavesBlocks.poll();
            if (!client.world.getBlockState(pos).isAir()) {
                client.interactionManager.attackBlock(pos, client.player.getActiveHand());
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
        currentState = State.PLANT;
        saplingPos = null;
        client.player.getInventory().selectedSlot = prev;
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
            
            for (int dx = -6; dx <= 6; dx++) {
                for (int dy = -6; dy <= 6; dy++) {
                    for (int dz = -6; dz <= 6; dz++) {
                        BlockPos p = pos.add(dx, dy, dz);
                        if (visited.contains(p)) continue;
                        String name = client.world.getBlockState(p).getBlock().getName().getString().toLowerCase();
                        if (name.contains("log")) { woodBlocks.add(p); queue.add(p); }
                        else if (name.contains("leaves")) leavesBlocks.add(p);
                    }
                }
            }
        }
    }

    private int findSapling(MinecraftClient client) {
        Item[] saplings = {Items.OAK_SAPLING, Items.SPRUCE_SAPLING, Items.BIRCH_SAPLING, Items.JUNGLE_SAPLING, Items.ACACIA_SAPLING, Items.DARK_OAK_SAPLING};
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
        for (int i = 9; i < 36; i++) {
            Item item = client.player.getInventory().getStack(i).getItem();
            for (Item target : items) if (item == target) return i - 36;
        }
        return -1;
    }
}
