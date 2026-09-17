package mchorse.bbs_mod.ui.film;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.overwrite.POVClip;
import mchorse.bbs_mod.camera.clips.overwrite.PovHudData;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.clips.ClipContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Pixel-perfect vanilla Minecraft HUD renderer for POV camera clip.
 */
public class UIPovHudRenderer
{
    private static final Identifier ICONS = new Identifier("minecraft", "textures/gui/icons.png");
    private static final Identifier WIDGETS = new Identifier("minecraft", "textures/gui/widgets.png");

    public static void render(MatrixStack stack, Batcher2D batcher, ClipContext context)
    {
        List<PovHudData> list = POVClip.getPovHuds(context);

        if (list == null || list.isEmpty())
        {
            return;
        }

        PovHudData data = list.get(list.size() - 1);
        POVClip clip = data.clip;

        if (!clip.enabled.get() || !clip.showHud.get() || data.factor <= 0F)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        net.minecraft.client.gl.Framebuffer fb = mc.getFramebuffer();

        int fbWidth = fb.textureWidth;
        int fbHeight = fb.textureHeight;

        int guiScale = mc.options.getGuiScale().getValue();
        int scaleFactor = 1;

        if (guiScale <= 0)
        {
            while (fbWidth / (scaleFactor + 1) >= 320 && fbHeight / (scaleFactor + 1) >= 240)
            {
                scaleFactor++;
            }
        }
        else
        {
            scaleFactor = guiScale;
            while (scaleFactor > 1 && (fbWidth / scaleFactor < 320 || fbHeight / scaleFactor < 240))
            {
                scaleFactor--;
            }
        }

        int scaledWidth = (int) Math.ceil((double) fbWidth / scaleFactor);
        int scaledHeight = (int) Math.ceil((double) fbHeight / scaleFactor);

        Matrix4f cache = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f ortho = new Matrix4f().ortho(0, scaledWidth, scaledHeight, 0, -1000, 3000);

        RenderSystem.setProjectionMatrix(ortho, VertexSorter.BY_Z);

        RenderSystem.getModelViewStack().push();
        RenderSystem.getModelViewStack().loadIdentity();
        RenderSystem.applyModelViewMatrix();

        stack.push();
        stack.loadIdentity();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);

        DrawContext drawContext = batcher.getContext();

        IEntity entity = null;

        if (context instanceof CameraClipContext cameraContext && data.replay != null)
        {
            entity = cameraContext.entities.get(data.replay.getId());
        }

        if (clip.showCrosshair.get())
        {
            renderCrosshair(drawContext, scaledWidth, scaledHeight, clip, data, entity, mc);
        }

        if (clip.showHotbar.get())
        {
            renderHotbar(drawContext, scaledWidth, scaledHeight, clip, data, entity, mc);
        }

        if (clip.showExperience.get())
        {
            renderExperience(drawContext, scaledWidth, scaledHeight, clip, data, entity, mc);
        }

        if (clip.showHealth.get())
        {
            renderHealth(drawContext, scaledWidth, scaledHeight, clip, data, entity, mc);
        }

        if (clip.showHunger.get())
        {
            renderHunger(drawContext, scaledWidth, scaledHeight, clip, data, entity, mc);
        }

        if (clip.showArmor.get())
        {
            renderArmor(drawContext, scaledWidth, scaledHeight, clip, data, entity, mc);
        }

        drawContext.draw();

        stack.pop();
        RenderSystem.getModelViewStack().pop();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(cache, VertexSorter.BY_Z);
    }

    private static void renderCrosshair(DrawContext drawContext, int scaledWidth, int scaledHeight, POVClip clip, PovHudData data, IEntity entity, MinecraftClient mc)
    {
        int centerX = scaledWidth / 2;
        int centerY = scaledHeight / 2;

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
            GlStateManager.SrcFactor.ONE_MINUS_DST_COLOR,
            GlStateManager.DstFactor.ONE_MINUS_SRC_COLOR,
            GlStateManager.SrcFactor.ONE,
            GlStateManager.DstFactor.ZERO
        );

        drawContext.drawTexture(ICONS, centerX - 7, centerY - 7, 0, 0, 15, 15);

        RenderSystem.defaultBlendFunc();

        if (clip.dynamicCrosshair.get())
        {
            float attackCooldown = 1.0F;

            if (entity instanceof MCEntity mcEnt && mcEnt.getMcEntity() instanceof PlayerEntity player)
            {
                attackCooldown = player.getAttackCooldownProgress(0.0F);
            }
            else if (mc.player != null)
            {
                attackCooldown = mc.player.getAttackCooldownProgress(0.0F);
            }

            if (attackCooldown < 1.0F)
            {
                int x = centerX - 8;
                int y = centerY - 7 + 16;
                int progress = (int) (attackCooldown * 17.0F);

                drawContext.drawTexture(ICONS, x, y, 36, 94, 16, 4);

                if (progress > 0)
                {
                    drawContext.drawTexture(ICONS, x, y, 52, 94, progress, 4);
                }
            }
            else if (mc.targetedEntity != null)
            {
                drawContext.drawTexture(ICONS, centerX - 7, centerY - 7 + 16, 68, 94, 15, 16);
            }
        }
    }

    private static void renderHotbar(DrawContext drawContext, int scaledWidth, int scaledHeight, POVClip clip, PovHudData data, IEntity entity, MinecraftClient mc)
    {
        int hotbarX = (scaledWidth - 182) / 2;
        int hotbarY = scaledHeight - 22;

        drawContext.drawTexture(WIDGETS, hotbarX, hotbarY, 0, 0, 182, 22);

        int selectedSlot = 0;

        if (data.replay != null)
        {
            selectedSlot = data.replay.keyframes.getSelectedSlot(data.exactTick);
        }
        else if (entity != null)
        {
            selectedSlot = entity.getSelectedSlot();
        }
        else if (mc.player != null)
        {
            selectedSlot = mc.player.getInventory().selectedSlot;
        }

        drawContext.drawTexture(WIDGETS, hotbarX - 1 + selectedSlot * 20, hotbarY - 1, 0, 22, 24, 24);

        ItemStack offhand = ItemStack.EMPTY;

        if (data.replay != null)
        {
            offhand = data.replay.keyframes.getEquipmentChannel(EquipmentSlot.OFFHAND).interpolate(data.exactTick, ItemStack.EMPTY);
        }
        if ((offhand == null || offhand.isEmpty()) && entity != null)
        {
            offhand = entity.getEquipmentStack(EquipmentSlot.OFFHAND);
        }
        if ((offhand == null || offhand.isEmpty()) && mc.player != null)
        {
            offhand = mc.player.getOffHandStack();
        }

        if (offhand != null && !offhand.isEmpty())
        {
            int offhandX = hotbarX - 29;
            int offhandY = scaledHeight - 23;

            drawContext.drawTexture(WIDGETS, offhandX, offhandY, 24, 22, 29, 24);
            drawContext.drawItem(offhand, offhandX + 3, offhandY + 4);
            drawContext.drawItemInSlot(mc.textRenderer, offhand, offhandX + 3, offhandY + 4);
        }

        for (int i = 0; i < 9; i++)
        {
            ItemStack stack = ItemStack.EMPTY;

            if (data.replay != null)
            {
                stack = data.replay.keyframes.hotbar.get(i).interpolate(data.exactTick, ItemStack.EMPTY);
            }
            if ((stack == null || stack.isEmpty()) && entity != null)
            {
                stack = entity.getHotbarStack(i);
            }
            if ((stack == null || stack.isEmpty()) && mc.player != null && mc.player.getInventory() != null)
            {
                stack = mc.player.getInventory().getStack(i);
            }

            if (stack != null && !stack.isEmpty())
            {
                int itemX = hotbarX + 3 + i * 20;
                int itemY = hotbarY + 3;

                drawContext.drawItem(stack, itemX, itemY);
                drawContext.drawItemInSlot(mc.textRenderer, stack, itemX, itemY);
            }
        }
    }

    private static void renderExperience(DrawContext drawContext, int scaledWidth, int scaledHeight, POVClip clip, PovHudData data, IEntity entity, MinecraftClient mc)
    {
        int xpBarX = (scaledWidth - 182) / 2;
        int xpBarY = scaledHeight - 29;

        drawContext.drawTexture(ICONS, xpBarX, xpBarY, 0, 64, 182, 5);

        float progress = 0F;
        int level = 0;

        if (clip.customStats.get())
        {
            progress = clip.xpProgress.get();
            level = clip.xpLevel.get();
        }
        else if (entity instanceof MCEntity mcEnt && mcEnt.getMcEntity() instanceof PlayerEntity player)
        {
            progress = player.experienceProgress;
            level = player.experienceLevel;
        }
        else if (mc.player != null)
        {
            progress = mc.player.experienceProgress;
            level = mc.player.experienceLevel;
        }

        int fillWidth = (int) (progress * 183.0F);

        if (fillWidth > 182)
        {
            fillWidth = 182;
        }
        if (fillWidth > 0)
        {
            drawContext.drawTexture(ICONS, xpBarX, xpBarY, 0, 69, fillWidth, 5);
        }

        if (level > 0)
        {
            String text = String.valueOf(level);
            int textX = (scaledWidth - mc.textRenderer.getWidth(text)) / 2;
            int textY = scaledHeight - 35;

            drawContext.drawText(mc.textRenderer, text, textX + 1, textY, 0, false);
            drawContext.drawText(mc.textRenderer, text, textX - 1, textY, 0, false);
            drawContext.drawText(mc.textRenderer, text, textX, textY + 1, 0, false);
            drawContext.drawText(mc.textRenderer, text, textX, textY - 1, 0, false);
            drawContext.drawText(mc.textRenderer, text, textX, textY, 8453920, false);
        }
    }

    private static void renderHealth(DrawContext drawContext, int scaledWidth, int scaledHeight, POVClip clip, PovHudData data, IEntity entity, MinecraftClient mc)
    {
        int healthX = scaledWidth / 2 - 91;
        int healthY = scaledHeight - 39;

        float hp = 20F;
        boolean damageFlash = false;

        if (clip.customStats.get())
        {
            hp = clip.hp.get();
        }
        else if (entity instanceof MCEntity mcEnt && mcEnt.getMcEntity() instanceof LivingEntity living)
        {
            hp = living.getHealth();
            damageFlash = living.hurtTime > 0;
        }
        else if (mc.player != null)
        {
            hp = mc.player.getHealth();
            damageFlash = mc.player.hurtTime > 0;
        }

        int halfHearts = (int) Math.ceil(hp);

        for (int i = 0; i < 10; i++)
        {
            int x = healthX + i * 8;
            int y = healthY;

            int bgU = damageFlash ? 25 : 16;
            drawContext.drawTexture(ICONS, x, y, bgU, 0, 9, 9);

            int heartIndex = i * 2;

            if (heartIndex + 1 < halfHearts)
            {
                int fillU = damageFlash ? 70 : 52;
                drawContext.drawTexture(ICONS, x, y, fillU, 0, 9, 9);
            }
            else if (heartIndex + 1 == halfHearts)
            {
                int fillU = damageFlash ? 79 : 61;
                drawContext.drawTexture(ICONS, x, y, fillU, 0, 9, 9);
            }
        }
    }

    private static void renderHunger(DrawContext drawContext, int scaledWidth, int scaledHeight, POVClip clip, PovHudData data, IEntity entity, MinecraftClient mc)
    {
        float hunger = 20F;

        if (clip.customStats.get())
        {
            hunger = clip.hunger.get();
        }
        else if (entity instanceof MCEntity mcEnt && mcEnt.getMcEntity() instanceof PlayerEntity player)
        {
            hunger = player.getHungerManager().getFoodLevel();
        }
        else if (mc.player != null)
        {
            hunger = mc.player.getHungerManager().getFoodLevel();
        }

        int halfFood = (int) Math.ceil(hunger);

        for (int i = 0; i < 10; i++)
        {
            int x = scaledWidth / 2 + 91 - i * 8 - 9;
            int y = scaledHeight - 39;

            drawContext.drawTexture(ICONS, x, y, 16, 27, 9, 9);

            int foodIndex = i * 2;

            if (foodIndex + 1 < halfFood)
            {
                drawContext.drawTexture(ICONS, x, y, 52, 27, 9, 9);
            }
            else if (foodIndex + 1 == halfFood)
            {
                drawContext.drawTexture(ICONS, x, y, 61, 27, 9, 9);
            }
        }
    }

    private static void renderArmor(DrawContext drawContext, int scaledWidth, int scaledHeight, POVClip clip, PovHudData data, IEntity entity, MinecraftClient mc)
    {
        int armorValue = 0;

        if (entity instanceof MCEntity mcEnt && mcEnt.getMcEntity() instanceof LivingEntity living)
        {
            armorValue = living.getArmor();
        }
        else if (data.replay != null)
        {
            EquipmentSlot[] slots = new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

            for (EquipmentSlot slot : slots)
            {
                ItemStack stack = data.replay.keyframes.getEquipmentChannel(slot).interpolate(data.exactTick, ItemStack.EMPTY);

                if ((stack == null || stack.isEmpty()) && entity != null)
                {
                    stack = entity.getEquipmentStack(slot);
                }
                if (stack != null && stack.getItem() instanceof ArmorItem armor)
                {
                    armorValue += armor.getProtection();
                }
            }
        }
        else if (mc.player != null)
        {
            armorValue = mc.player.getArmor();
        }

        if (armorValue <= 0)
        {
            return;
        }

        int armorX = scaledWidth / 2 - 91;
        int armorY = scaledHeight - 49;

        for (int i = 0; i < 10; i++)
        {
            int x = armorX + i * 8;
            int y = armorY;

            if (i * 2 + 1 < armorValue)
            {
                drawContext.drawTexture(ICONS, x, y, 34, 9, 9, 9);
            }
            else if (i * 2 + 1 == armorValue)
            {
                drawContext.drawTexture(ICONS, x, y, 25, 9, 9, 9);
            }
            else if (i * 2 < armorValue)
            {
                drawContext.drawTexture(ICONS, x, y, 43, 9, 9, 9);
            }
        }
    }
}
