package com.swill.killaura;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.systems.RenderSystem;

import java.awt.Color;

public class KillAuraMod implements ModInitializer {

    private static boolean enabled = false;
    private static KeyBinding toggleKey;
    
    // 3x хитбоксы
    private static final float HITBOX_MULTIPLIER = 3.0f;

    @Override
    public void onInitialize() {
        System.out.println("[3xHitbox] Мод загружен! Нажми R для вкл/выкл");

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.hitbox.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.hitbox"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (toggleKey.wasPressed()) {
                enabled = !enabled;
                String status = enabled ? "§aВКЛЮЧЕН §7(3x хитбоксы)" : "§cВЫКЛЮЧЕН";
                client.player.sendMessage(Text.literal("§l[3xHitbox] §r" + status), true);
            }
        });
        
        // Рендер 3x хитбоксов и обводки
        WorldRenderEvents.LAST.register(context -> {
            if (!enabled) return;
            
            MatrixStack matrices = context.matrixStack();
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            
            for (Entity entity : client.world.getEntities()) {
                if (!(entity instanceof LivingEntity)) continue;
                if (entity == client.player) continue;
                
                // Увеличенный хитбокс (3x)
                Box bigBox = getBigHitbox(entity);
                
                // Цвет обводки
                Color color = getEntityColor(entity);
                
                // Рисуем обводку и заливку
                drawBoxOutline(matrices, bigBox, color);
                drawBoxFill(matrices, bigBox, new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
            }
            
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        });
    }
    
    // Получение увеличенного хитбокса (3x)
    private Box getBigHitbox(Entity entity) {
        Box normalBox = entity.getBoundingBox();
        double width = (normalBox.maxX - normalBox.minX) * HITBOX_MULTIPLIER;
        double height = (normalBox.maxY - normalBox.minY) * HITBOX_MULTIPLIER;
        double centerX = (normalBox.minX + normalBox.maxX) / 2;
        double centerY = (normalBox.minY + normalBox.maxY) / 2;
        double centerZ = (normalBox.minZ + normalBox.maxZ) / 2;
        
        return new Box(
            centerX - width / 2,
            centerY - height / 2,
            centerZ - width / 2,
            centerX + width / 2,
            centerY + height / 2,
            centerZ + width / 2
        );
    }
    
    // Цвет для обводки
    private Color getEntityColor(Entity entity) {
        if (entity instanceof PlayerEntity) {
            return new Color(255, 0, 0, 200); // Красный для игроков
        }
        if (entity.isAttackable()) {
            return new Color(255, 165, 0, 200); // Оранжевый для враждебных
        }
        return new Color(0, 255, 0, 200); // Зелёный для нейтральных
    }
    
    // Рисование обводки
    private void drawBoxOutline(MatrixStack matrices, Box box, Color color) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        
        MatrixStack.Entry entry = matrices.peek();
        var matrix = entry.getPositionMatrix();
        
        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        
        Vec3d cameraPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        double x = box.minX - cameraPos.x;
        double y = box.minY - cameraPos.y;
        double z = box.minZ - cameraPos.z;
        double w = box.maxX - box.minX;
        double h = box.maxY - box.minY;
        
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float a = color.getAlpha() / 255f;
        
        // Нижняя грань
        buffer.vertex(matrix, (float)x, (float)y, (float)z).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)y, (float)z).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)y, (float)z).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)y, (float)(z + w)).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)y, (float)(z + w)).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x, (float)y, (float)(z + w)).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x, (float)y, (float)(z + w)).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x, (float)y, (float)z).color(r, g, b, a).next();
        
        // Верхняя грань
        buffer.vertex(matrix, (float)x, (float)(y + h), (float)z).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)(y + h), (float)z).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)(y + h), (float)z).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)(y + h), (float)(z + w)).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)(y + h), (float)(z + w)).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x, (float)(y + h), (float)(z + w)).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x, (float)(y + h), (float)(z + w)).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x, (float)(y + h), (float)z).color(r, g, b, a).next();
        
        // Вертикальные рёбра
        buffer.vertex(matrix, (float)x, (float)y, (float)z).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x, (float)(y + h), (float)z).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)y, (float)z).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)(y + h), (float)z).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)y, (float)(z + w)).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)(y + h), (float)(z + w)).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x, (float)y, (float)(z + w)).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x, (float)(y + h), (float)(z + w)).color(r, g, b, a).next();
        
        tessellator.draw();
    }
    
    // Рисование заливки
    private void drawBoxFill(MatrixStack matrices, Box box, Color color) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        
        MatrixStack.Entry entry = matrices.peek();
        var matrix = entry.getPositionMatrix();
        
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        
        Vec3d cameraPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        double x = box.minX - cameraPos.x;
        double y = box.minY - cameraPos.y;
        double z = box.minZ - cameraPos.z;
        double w = box.maxX - box.minX;
        double h = box.maxY - box.minY;
        
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float a = color.getAlpha() / 255f;
        
        // Передняя грань
        buffer.vertex(matrix, (float)x, (float)y, (float)z).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)y, (float)z).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)(y + h), (float)z).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x, (float)(y + h), (float)z).color(r, g, b, a).next();
        
        // Задняя грань
        buffer.vertex(matrix, (float)x, (float)y, (float)(z + w)).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x, (float)(y + h), (float)(z + w)).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)(y + h), (float)(z + w)).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)y, (float)(z + w)).color(r, g, b, a).next();
        
        // Левая грань
        buffer.vertex(matrix, (float)x, (float)y, (float)z).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x, (float)(y + h), (float)z).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x, (float)(y + h), (float)(z + w)).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x, (float)y, (float)(z + w)).color(r, g, b, a).next();
        
        // Правая грань
        buffer.vertex(matrix, (float)(x + w), (float)y, (float)z).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)y, (float)(z + w)).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)(y + h), (float)(z + w)).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)(y + h), (float)z).color(r, g, b, a).next();
        
        // Верхняя грань
        buffer.vertex(matrix, (float)x, (float)(y + h), (float)z).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)(y + h), (float)z).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)(y + h), (float)(z + w)).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x, (float)(y + h), (float)(z + w)).color(r, g, b, a).next();
        
        // Нижняя грань
        buffer.vertex(matrix, (float)x, (float)y, (float)z).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x, (float)y, (float)(z + w)).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)y, (float)(z + w)).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)(x + w), (float)y, (float)z).color(r, g, b, a).next();
        
        tessellator.draw();
    }
}
