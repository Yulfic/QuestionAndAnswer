package com.zdev.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.zdev.client.QuestionOverlayHandler;

@Mixin(InGameHud.class)
public class QuestionOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void renderQuestionOverlay(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (QuestionOverlayHandler.shouldShow()) {
            MinecraftClient client = MinecraftClient.getInstance();
            Window window = client.getWindow();
            int width = window.getScaledWidth();
            int y = 10;
            int padding = 8;
            String display = "\uD83D\uDCDD  " + QuestionOverlayHandler.getQuestion();
            int textWidth = client.textRenderer.getWidth(display);
            int boxWidth = textWidth + padding * 2;
            int boxHeight = 28;
            int x = (width - boxWidth) / 2;
            int radius = 6;
            // Синий прямоугольник
            context.fill(x, y, x + boxWidth, y + boxHeight, 0xAA3366FF);
            // Стираем углы прозрачным цветом
            context.fill(x, y, x + radius, y + radius, 0x00000000); // Левый верхний
            context.fill(x + boxWidth - radius, y, x + boxWidth, y + radius, 0x00000000); // Правый верхний
            context.fill(x, y + boxHeight - radius, x + radius, y + boxHeight, 0x00000000); // Левый нижний
            context.fill(x + boxWidth - radius, y + boxHeight - radius, x + boxWidth, y + boxHeight, 0x00000000); // Правый нижний
            // Текст по центру прямоугольника
            int textX = x + (boxWidth - textWidth) / 2;
            int textY = y + (boxHeight - 8) / 2; // 8 — высота текста примерно
            context.drawTextWithShadow(client.textRenderer, display, textX, textY, 0xFFFFFF);

            // Таймер
            if (!com.zdev.client.QuestionOverlayHandler.isHideTimer()) {
                int timeLeft = QuestionOverlayHandler.getTimeLeft();
                String timerText = "\u23F1  " + timeLeft + " сек";
                int timerWidth = client.textRenderer.getWidth(timerText) + padding * 2;
                int timerHeight = 20;
                int timerX = (width - timerWidth) / 2;
                int timerY = y + boxHeight + 8;
                // Синий прямоугольник под таймер
                context.fill(timerX, timerY, timerX + timerWidth, timerY + timerHeight, 0xAA3366FF);
                // Скругление углов таймера
                context.fill(timerX, timerY, timerX + radius, timerY + radius, 0x00000000);
                context.fill(timerX + timerWidth - radius, timerY, timerX + timerWidth, timerY + radius, 0x00000000);
                context.fill(timerX, timerY + timerHeight - radius, timerX + radius, timerY + timerHeight, 0x00000000);
                context.fill(timerX + timerWidth - radius, timerY + timerHeight - radius, timerX + timerWidth, timerY + timerHeight, 0x00000000);
                // Текст таймера по центру
                int timerTextX = timerX + (timerWidth - client.textRenderer.getWidth(timerText)) / 2;
                int timerTextY = timerY + (timerHeight - 8) / 2;
                context.drawTextWithShadow(client.textRenderer, timerText, timerTextX, timerTextY, 0xFFFFFF);
            }
            // Ответ игрока отображается независимо от таймера, если не скрыт
            String answer = com.zdev.client.QuestionOverlayHandler.getAnswer();
            if (!com.zdev.client.QuestionOverlayHandler.isHideAnswer() && answer != null && !answer.isEmpty()) {
                int answerY = y + boxHeight + 8;
                if (!com.zdev.client.QuestionOverlayHandler.isHideTimer()) {
                    answerY += 20 + 8; // если таймер виден, ответ ниже
                }
                String answerText = "\u2B50  " + answer;
                int answerWidth = client.textRenderer.getWidth(answerText) + padding * 2;
                int answerHeight = 20;
                int answerX = (width - answerWidth) / 2;
                context.fill(answerX, answerY, answerX + answerWidth, answerY + answerHeight, 0xAA3366FF);
                context.fill(answerX, answerY, answerX + radius, answerY + radius, 0x00000000);
                context.fill(answerX + answerWidth - radius, answerY, answerX + answerWidth, answerY + radius, 0x00000000);
                context.fill(answerX, answerY + answerHeight - radius, answerX + radius, answerY + answerHeight, 0x00000000);
                context.fill(answerX + answerWidth - radius, answerY + answerHeight - radius, answerX + answerWidth, answerY + answerHeight, 0x00000000);
                int answerTextX = answerX + (answerWidth - client.textRenderer.getWidth(answerText)) / 2;
                int answerTextY = answerY + (answerHeight - 8) / 2;
                context.drawTextWithShadow(client.textRenderer, answerText, answerTextX, answerTextY, 0xFFFF55);
            }
        }
    }
} 