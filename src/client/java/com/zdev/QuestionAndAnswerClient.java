package com.zdev;

import net.fabricmc.api.ClientModInitializer;
import com.zdev.client.QuestionOverlayHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;
import net.minecraft.network.PacketByteBuf;

public class QuestionAndAnswerClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		ClientPlayNetworking.registerGlobalReceiver(
			new Identifier("question-and-answer", "show_question"),
			(client, handler, buf, responseSender) -> {
				String question = buf.readString();
				int seconds = buf.readInt();
				String answer = buf.readString();
				client.execute(() -> {
					showQuestionOverlay(question, seconds);
					QuestionOverlayHandler.setAnswer(answer);
				});
			}
		);
		ClientPlayNetworking.registerGlobalReceiver(
			new Identifier("question-and-answer", "hide_timer"),
			(client, handler, buf, responseSender) -> {
				client.execute(() -> com.zdev.client.QuestionOverlayHandler.setHideTimer(true));
			}
		);
		ClientPlayNetworking.registerGlobalReceiver(
			new Identifier("question-and-answer", "show_timer"),
			(client, handler, buf, responseSender) -> {
				client.execute(() -> com.zdev.client.QuestionOverlayHandler.setHideTimer(false));
			}
		);
		ClientPlayNetworking.registerGlobalReceiver(
			new Identifier("question-and-answer", "hide_timer_only"),
			(client, handler, buf, responseSender) -> {
				client.execute(() -> com.zdev.client.QuestionOverlayHandler.setHideTimer(true));
			}
		);
		ClientPlayNetworking.registerGlobalReceiver(
			new Identifier("question-and-answer", "hide_answer"),
			(client, handler, buf, responseSender) -> {
				client.execute(() -> com.zdev.client.QuestionOverlayHandler.clearAnswer());
			}
		);
	}

	public static void showQuestionOverlay(String question, int seconds) {
		QuestionOverlayHandler.setQuestion(question, seconds);
	}

	public static void hideQuestionOverlay() {
		QuestionOverlayHandler.clearQuestion();
	}
}