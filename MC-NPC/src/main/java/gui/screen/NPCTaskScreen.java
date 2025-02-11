package gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import controller.GameController;
import model.NPCModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class NPCTaskScreen extends Screen {
    //private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation("yourmodid", "textures/gui/npc_task_gui.png");
    private static final ResourceLocation COMPLETED = new ResourceLocation("npcopenai", "textures/item/todo.png");
    private static final ResourceLocation PENDING = new ResourceLocation("npcopenai", "textures/item/to_do.png");
    private boolean hasShownCompletionToast = false;

    public NPCTaskScreen() {
        super(new TextComponent("NPC Task Manager"));
    }

    @Override
    protected void init() {
        super.init();
        hasShownCompletionToast = false;
        checkCompletedNPCs();
    }

    private void teleportToNPC(int index) {
        if (this.minecraft.player != null) {
            this.minecraft.player.chat("/findnpc " + index);
        }
    }


    private void checkCompletedNPCs() {
        if (!hasShownCompletionToast) {
            List<NPCModel> npcs = GameController.getInstance().getNpcs();
            long completedCount = npcs.stream().filter(NPCModel::areAllTasksCompleted).count();

            if (completedCount >= 5) {
                if (completedCount >= 10) {
                    showCompletionToast("你人真好，帮助了所有同学！", "祝你未来一切顺利！");
                } else if (completedCount >= 5) {
                    showCompletionToast("恭喜！", "已经完成了本次实验。");
                }
                hasShownCompletionToast = true;  // Set flag to true after showing toast
            }
        }
    }

    private void showCompletionToast(String title, String description) {
        Minecraft minecraft = Minecraft.getInstance();
        MutableComponent text = new TextComponent(title);
        MutableComponent desc = new TextComponent(description);
        SystemToast.add(minecraft.getToasts(), SystemToast.SystemToastIds.TUTORIAL_HINT, text, desc);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTicks);
        drawCenteredString(poseStack, this.font, "任务清单", this.width / 2 - 180, 20, 0xFFFFFF);

        List<NPCModel> npcs = GameController.getInstance().getNpcs();
        int yOffset = 50;
        int columnWidth = this.width / 2;
        int maxPerColumn = 5;
        int column = 0;
        int maxWidth = 0;  // Variable to track the maximum width of NPC info blocks

        for (int i = 0; i < npcs.size(); i++) {
            if (i % maxPerColumn == 0 && i > 0) {
                column++;
                yOffset = 50;  // Reset vertical position for new column
                maxWidth = 0;  // Reset max width for new column
            }
            NPCModel npc = npcs.get(i);
            String name = npc.getNPCName();
//            String role = npc.getRole();
            String event = npc.getEvent();

            int xBase = columnWidth * column + 30; // Set base x position for left alignment
            int xName = xBase;
//            int xRole = xName + font.width(name) + 5;
            int xEvent = xName + font.width(name) + 5;

            // Calculate the total width of the NPC block
            int blockWidth = font.width(name) + 5 + font.width(event);
            maxWidth = Math.max(maxWidth, blockWidth);

            ResourceLocation icon = npc.areAllTasksCompleted() ? COMPLETED : PENDING;
            RenderSystem.setShaderTexture(0, icon);
            blit(poseStack, xName - 20, yOffset - 4, 0, 0, 16, 16, 16, 16);

            font.draw(poseStack, name, xName, yOffset, 0xFFFFFF);  // Left-aligned NPC name
//            font.draw(poseStack, event, xRole, yOffset, 0xFFFFFF);  // Left-aligned NPC role
            font.draw(poseStack, event, xEvent, yOffset, 0xFFFFFF);  // Left-aligned NPC role

            // Check if the mouse is over the NPC name or role
//            if ((mouseX >= xName && mouseX <= xName + font.width(name) && mouseY >= yOffset && mouseY <= yOffset + font.lineHeight) ||
//                    (mouseX >= xRole && mouseX <= xRole + font.width(role) && mouseY >= yOffset && mouseY <= yOffset + font.lineHeight)) {
//                renderTooltip(poseStack, new TextComponent(npc.getDialogues().get(0)), mouseX, mouseY);
//            }
            if ((mouseX >= xName && mouseX <= xName + font.width(name) && mouseY >= yOffset && mouseY <= yOffset + font.lineHeight) ||
                    (mouseX >= xEvent && mouseX <= xEvent + font.width(event) && mouseY >= yOffset && mouseY <= yOffset + font.lineHeight)) {
                renderTooltip(poseStack, new TextComponent(npc.getDialogues().get(0)), mouseX, mouseY);
            }
            yOffset += 30;  // Increment the vertical offset for the next NPC
        }

        // Render buttons after calculating the max width of each column
        yOffset = 50;
        int finalColumn = 0; // Reset column counter for button rendering
        for (int i = 0; i < npcs.size(); i++) {
            if (i % maxPerColumn == 0 && i > 0) {
                finalColumn++;
                yOffset = 50;
            }
            final int index = i;  // Final variable for use in lambda
            int xButton = (columnWidth * finalColumn + 30) + maxWidth + 10;  // Calculate x position for the button based on max width
            this.addRenderableWidget(new Button(xButton, yOffset - 5, 15, 15, new TextComponent(">"), button -> {
                teleportToNPC(index);  // Use the final variable `index`
            }));
            yOffset += 30;
        }
    }
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}