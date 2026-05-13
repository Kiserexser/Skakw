package com.swill.treefarmer;

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

public class TreeFarmerMod implements ModInitializer {

    private static boolean enabled = false;
    private static KeyBinding toggleKey;
    
    private enum State {
        PLANT_SAPLING,
        GROW_TREE,
        CHOP_WOOD,
        BREAK_LEAVES
    }
    
    private static State currentState = State.PLANT_SAPLING;
    private static BlockPos saplingPos = null;
    private static Queue<BlockPos> woodBlocks = new LinkedList<>();
    private static Queue<BlockPos> leavesBlocks = new LinkedList<>();

    @Override
    public void onInitialize() {
        System.out.println("[TreeFarmer] Мод загружен - БЕЗ ЗАДЕРЖЕК! (R - вкл/выкл)");

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
                String status = enabled ? "§aВКЛЮЧЕН" : "§cВЫКЛЮЧЕН";
                client.player.sendMessage(Text.literal("§l[TreeFarmer] §r" + status), true);
                if (!enabled) {
                    currentState = State.PLANT_SAPLING;
                    saplingPos = null;
                    woodBlocks.clear();
                    leavesBlocks.clear();
                }
            }

            if (!enabled) return;
            
            // БЕЗ ЗАДЕРЖЕК - сразу выполняем действие каждый тик
            switch (currentState) {
                case PLANT_SAPLING:
                    plantSapling(client);
                    break;
                case GROW_TREE:
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

    // 1. Сажаем саженец
    private void plantSapling(MinecraftClient client) {
        int saplingSlot = findItemInHotbar(client, 
            Items.OAK_SAPLING, Items.SPRUCE_SAPLING, Items.BIRCH_SAPLING,
            Items.JUNGLE_SAPLING, Items.ACACIA_SAPLING, Items.DARK_OAK_SAPLING);
        
        if (saplingSlot == -1) {
            client.player.sendMessage(Text.literal("§cНет саженца!"), true);
            return;
        }
        
        client.player.getInventory().selectedSlot = saplingSlot;
        
        BlockPos groundPos = client.player.getBlockPos().down();
        if (!client.world.getBlockState(groundPos).isAir()) {
            saplingPos = groundPos.up();
            Vec3d hitPos = new Vec3d(saplingPos.getX() + 0.5, saplingPos.getY() + 0.5, saplingPos.getZ() + 0.5);
            BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, saplingPos, false);
            
            if (client.interactionManager != null) {
                client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
                currentState = State.GROW_TREE;
            }
        }
    }

    // 2. Выращиваем костной мукой (мгновенно)
    private void growTree(MinecraftClient client) {
        if (saplingPos == null) {
            currentState = State.PLANT_SAPLING;
            return;
        }
        
        int boneMealSlot = findItemInInventory(client, Items.BONE_MEAL);
        if (boneMealSlot == -1) {
            client.player.sendMessage(Text.literal("§cНет костной муки!"), true);
            enabled = false;
            return;
        }
        
        int prevSlot = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = boneMealSlot;
        
        Vec3d hitPos = new Vec3d(saplingPos.getX() + 0.5, saplingPos.getY() + 0.5, saplingPos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, saplingPos, false);
        
        if (client.interactionManager != null) {
            client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
            // Сразу ищем все блоки дерева
            scanTreeBlocks(client, saplingPos);
            currentState = State.CHOP_WOOD;
        }
        
        client.player.getInventory().selectedSlot = prevSlot;
    }

    // 3. Рубим все блоки дерева топором (мгновенно без задержки)
    private void chopWood(MinecraftClient client) {
        int axeSlot = findItemInInventory(client,
            Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE,
            Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE);
        
        if (axeSlot == -1) {
            client.player.sendMessage(Text.literal("§cНет топора!"), true);
            enabled = false;
            return;
        }
        
        int prevSlot = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = axeSlot;
        
        // Ломаем все блоки дерева без задержки
        while (!woodBlocks.isEmpty()) {
            BlockPos pos = woodBlocks.poll();
            if (client.world.getBlockState(pos).isAir()) continue;
            
            if (client.interactionManager != null) {
                client.interactionManager.attackBlock(pos, client.player.getActiveHand());
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
        
        currentState = State.BREAK_LEAVES;
        client.player.getInventory().selectedSlot = prevSlot;
    }

    // 4. Срубаем всю листву мотыгой (мгновенно)
    private void breakLeaves(MinecraftClient client) {
        int hoeSlot = findItemInInventory(client,
            Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE,
            Items.GOLDEN_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE);
        
        if (hoeSlot == -1) {
            client.player.sendMessage(Text.literal("§cНет мотыги!"), true);
            enabled = false;
            return;
        }
        
        int prevSlot = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = hoeSlot;
        
        // Ломаем всю листву
        while (!leavesBlocks.isEmpty()) {
            BlockPos pos = leavesBlocks.poll();
            if (client.world.getBlockState(pos).isAir()) continue;
            
            if (client.interactionManager != null) {
                client.interactionManager.attackBlock(pos, client.player.getActiveHand());
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
        
        // Возвращаемся к посадке нового дерева
        currentState = State.PLANT_SAPLING;
        saplingPos = null;
        client.player.getInventory().selectedSlot = prevSlot;
        client.player.sendMessage(Text.literal("§a✅ Дерево полностью обработано!"), true);
    }

    // Сканирование всех блоков дерева и листвы
    private void scanTreeBlocks(MinecraftClient client, BlockPos start) {
        woodBlocks.clear();
        leavesBlocks.clear();
        
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start);
        
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (visited.contains(pos)) continue;
            visited.add(pos);
            
            // Радиус поиска 7 блоков
            for (int dx = -7; dx <= 7; dx++) {
                for (int dy = -7; dy <= 7; dy++) {
                    for (int dz = -7; dz <= 7; dz++) {
                        BlockPos checkPos = pos.add(dx, dy, dz);
                        if (visited.contains(checkPos)) continue;
                        
                        String name = client.world.getBlockState(checkPos).getBlock().getName().getString().toLowerCase();
                        
                        if (name.contains("log") || name.contains("wood")) {
                            woodBlocks.add(checkPos);
                            queue.add(checkPos);
                        } else if (name.contains("leaves")) {
                            leavesBlocks.add(checkPos);
                        }
                    }
                }
            }
        }
    }

    // Поиск предмета в горячем баре
    private int findItemInHotbar(MinecraftClient client, Item... items) {
        Set<Item> targetItems = new HashSet<>(Arrays.asList(items));
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (targetItems.contains(stack.getItem())) {
                return i;
            }
        }
        return -1;
    }

    // Поиск предмета во всём инвентаре
    private int findItemInInventory(MinecraftClient client, Item... items) {
        Set<Item> targetItems = new HashSet<>(Arrays.asList(items));
        for (int i = 0; i < 36; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (targetItems.contains(stack.getItem())) {
                if (i < 9) return i;
                else return i - 36; // переводим в слоты горячего бара
            }
        }
        return -1;
    }
}
