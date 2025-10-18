package com.zdev;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.block.Blocks;
import net.minecraft.world.World;
import net.minecraft.util.Formatting;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.util.HashSet;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.*;
import net.minecraft.sound.SoundEvent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;
import org.joml.Vector3f;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.world.GameMode;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.text.Style;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import java.io.InputStream;
import net.minecraft.util.math.Box;

public class QuestionAndAnswer implements ModInitializer {
	public static final String MOD_ID = "question-and-answer";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final String CONFIG_FILE_NAME = "question-and-answer.json";

	private static class Config {
		List<String> questions;
		List<Map<String, Integer>> startPositions;
		String direction;
		Integer questionTime;
		List<String> bridgeBlocks;
		Integer winZ;
		String buildParticle;
		String removeParticle;
		String buildSound;
		String removeSound;
		String tntSound;
		String answerSound;
		String winMessage;
		String violationMessage;
		String answerMessage;
		String roundStoppedMessage;
		List<Integer> forcedRemoveSteps;
		String winColor;
		String winNameColor;
		String answerColor;
		String violationColor;
		String stoppedColor;
		String password;
		String winTitleMessage;
		String violationTitleMessage;
		String answerTitleMessage;
		String roundStoppedTitleMessage;
		boolean showWinTitle;
		boolean showViolationTitle;
		boolean showAnswerTitle;
		boolean showRoundStoppedTitle;
		String chatPrefix;
		String chatSuffix;
		boolean chatEmptyLine;
		String spectatorAnswerMessage;
		String notParticipantMessage;
		String alreadyAnsweredMessage;
		String eliminatedMessage;
		String invalidAnswerMessage;
		String allAnsweredMessage;
		String playerNotFoundMessage;
		String positionAssignedMessage;
		String modBlockedMessage;
		String roundStartedMessage;
		String nextQuestionMessage;
		String configReloadedMessage;
		String answerAcceptedMessage;
		String noSuchQuestionMessage;
		String questionSelectedMessage;
		String bridgesClearedMessage;
		String customQuestionSetMessage;
		String finishMessage;
	}
	private Config config;
	private final Map<Integer, String> playerPositions = new HashMap<>();
	private String currentQuestion = null;
	private final Map<String, Boolean> answeredPlayers = new HashMap<>();
	private final Map<String, String> playerAnswers = new HashMap<>();
	private String direction = "south";
	private int questionTime = 30;
	private boolean acceptingAnswers = false;
	private Timer questionTimer = null;
	private static final Identifier SHOW_QUESTION_PACKET = new Identifier(MOD_ID, "show_question");
	private final Map<Integer, BlockPos> lastBridgeEnd = new HashMap<>();
	private int currentRound = 0;
	private List<String> bridgeBlocks = List.of("minecraft:stone");
	private int winZ = 100;
	private final Set<String> deadPlayers = new HashSet<>();
	private final Set<String> roundPlayers = new HashSet<>();
	private int forcedRemoveBlocks = 0;
	private int forcedRemoveStep = 0;
	private int forcedRemoveDelay = 500;
	private String buildParticle = "happy_villager";
	private String removeParticle = "smoke";
	private String buildSound = "block.note_block.pling";
	private String removeSound = "block.note_block.pling";
	private String tntSound = "entity.tnt.primed";
	private String answerSound = "entity.player.levelup";
	private String winMessage = "Победители: ";
	private String violationMessage = " нарушил правила!";
	private String answerMessage = " ответил на вопрос!";
	private String roundStoppedMessage = "Раунд остановлен!";
	private final Map<String, List<UUID>> bridgeTextEntities = new HashMap<>();
	private int destroyedBlocksCount = 0;
	private boolean modEnabled = true;
	private int bridgesToBuild = 0;
	private Runnable bridgesDoneCallback = null;
	private String chatPrefix = "";
	private String chatSuffix = "";
	private boolean chatEmptyLine = true;
	private long questionEndTimeMillis = 0;
	private boolean bridgesBuildingStarted = false;

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		ensureConfigFile();
		loadConfig();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			SuggestionProvider<ServerCommandSource> onlinePlayersSuggestion = (context, builder) -> {
				MinecraftServer server = context.getSource().getServer();
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
					builder.suggest(player.getEntityName());
				}
				return builder.buildFuture();
			};
			dispatcher.register(CommandManager.literal("setpos")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.argument("номер", IntegerArgumentType.integer(1, 4))
				.then(CommandManager.argument("ник", StringArgumentType.word())
				.suggests(onlinePlayersSuggestion)
				.executes(ctx -> {
					if (!modEnabled) {
						ctx.getSource().sendFeedback(() -> Text.literal(config.modBlockedMessage != null ? config.modBlockedMessage : "Мод заблокирован. Неверный пароль в config.json!"), false);
						return 0;
					}
					int num = IntegerArgumentType.getInteger(ctx, "номер");
					String nick = StringArgumentType.getString(ctx, "ник");
					ServerPlayerEntity player = ctx.getSource().getServer().getPlayerManager().getPlayer(nick);
					if (player == null) {
						ctx.getSource().sendFeedback(() -> Text.literal(config.playerNotFoundMessage != null ? config.playerNotFoundMessage : "Игрок с ником '" + nick + "' не найден на сервере!"), false);
						return 0;
					}
					playerPositions.put(num, nick);
					ctx.getSource().sendFeedback(() -> Text.literal(config.positionAssignedMessage != null ? config.positionAssignedMessage : "Позиция " + num + " закреплена за " + nick), false);
					return 1;
				}))));
			dispatcher.register(CommandManager.literal("start")
				.requires(source -> source.hasPermissionLevel(2))
				.executes(ctx -> {
					startRound(ctx.getSource().getServer(), false);
					ctx.getSource().sendFeedback(() -> Text.literal(config.roundStartedMessage != null ? config.roundStartedMessage : "Раунд начат!"), false);
					return 1;
				}));
			dispatcher.register(CommandManager.literal("next")
				.requires(source -> source.hasPermissionLevel(2))
				.executes(ctx -> {
					startRound(ctx.getSource().getServer(), true);
					ctx.getSource().sendFeedback(() -> Text.literal(config.nextQuestionMessage != null ? config.nextQuestionMessage : "Следующий вопрос!"), false);
					return 1;
				}));
			dispatcher.register(CommandManager.literal("qareload")
				.requires(source -> source.hasPermissionLevel(2))
				.executes(ctx -> {
					loadConfig();
					ctx.getSource().sendFeedback(() -> Text.literal(config.configReloadedMessage != null ? config.configReloadedMessage : "Конфиг перезагружен!"), false);
					return 1;
				}));
			dispatcher.register(CommandManager.literal("answer")
				.requires(source -> true)
				.then(CommandManager.argument("ответ", StringArgumentType.greedyString())
				.executes(ctx -> {
					if (!modEnabled) {
						ctx.getSource().sendFeedback(() -> Text.literal(config.modBlockedMessage != null ? config.modBlockedMessage : "Мод заблокирован. Неверный пароль в config.json!"), false);
						return 0;
					}
					ServerPlayerEntity player = ctx.getSource().getPlayer();
					if (player == null) return 0;
					if (player.interactionManager.getGameMode() == GameMode.SPECTATOR) {
						ctx.getSource().sendFeedback(() -> Text.literal(config.spectatorAnswerMessage != null ? config.spectatorAnswerMessage : "В режиме наблюдателя отвечать нельзя!"), false);
						return 0;
					}
					if (!playerPositions.containsValue(player.getEntityName())) {
						ctx.getSource().sendFeedback(() -> Text.literal(config.notParticipantMessage != null ? config.notParticipantMessage : "Вы не участвуете в игре! Используйте /setpos."), false);
						return 0;
					}
					if (deadPlayers.contains(player.getEntityName())) {
						ctx.getSource().sendFeedback(() -> Text.literal(config.eliminatedMessage != null ? config.eliminatedMessage : "Вы выбыли из раунда!"), false);
						return 0;
					}
					String playerKey = player.getEntityName();
					if (answeredPlayers.getOrDefault(playerKey, false)) {
						ctx.getSource().sendFeedback(() -> Text.literal(config.alreadyAnsweredMessage != null ? config.alreadyAnsweredMessage : "Вы уже ответили!"), false);
						return 0;
					}
					String answer = StringArgumentType.getString(ctx, "ответ");
					// Проверка: только русские буквы, одно слово, без пробелов
					if (!answer.matches("^[А-Яа-яЁё]+$")) {
						ctx.getSource().sendFeedback(() -> Text.literal(config.invalidAnswerMessage != null ? config.invalidAnswerMessage : "Ответ должен быть одним словом на русском без пробелов!"), false);
						return 0;
					}
					playerAnswers.put(playerKey, answer);
					answeredPlayers.put(playerKey, true);
					playAnswerSound(player);
					sendAnswerMessageToAll(ctx.getSource().getServer(), playerKey);
					// Проверяем, сколько игроков с позицией (установлен /setpos)
					int totalActivePlayers = 0;
					for (int i = 1; i <= 4; i++) {
						String nick = playerPositions.get(i);
						if (nick != null && !deadPlayers.contains(nick)) {
							totalActivePlayers++;
						}
					}
					if (answeredPlayers.size() >= totalActivePlayers) {
						acceptingAnswers = false;
						if (questionTimer != null) questionTimer.cancel();
						for (ServerPlayerEntity p : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
							PacketByteBuf allBuf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
							allBuf.writeString(currentQuestion);
							allBuf.writeInt(questionTime);
							String answerForP = playerAnswers.getOrDefault(p.getEntityName(), "");
							allBuf.writeString(answerForP);
							ServerPlayNetworking.send(p, SHOW_QUESTION_PACKET, allBuf);
						}
						LOGGER.info("Все игроки ответили! Строим мосты.");
						// Скрываем только таймер
						for (ServerPlayerEntity p : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
							PacketByteBuf hideTimerBuf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
							hideTimerBuf.writeString("HIDE_TIMER_ONLY");
							ServerPlayNetworking.send(p, new Identifier(MOD_ID, "hide_timer_only"), hideTimerBuf);
						}
						if (!bridgesBuildingStarted) {
							bridgesBuildingStarted = true;
							buildAllBridgesAndTeleport(ctx.getSource().getServer());
						}
					}
					return 1;
				})
			));
			dispatcher.register(CommandManager.literal("removeanswer")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.argument("номер", IntegerArgumentType.integer(1, 4))
				.executes(ctx -> {
					if (!modEnabled) {
						ctx.getSource().sendFeedback(() -> Text.literal(config.modBlockedMessage != null ? config.modBlockedMessage : "Мод заблокирован. Неверный пароль в config.json!"), false);
						return 0;
					}
					int num = IntegerArgumentType.getInteger(ctx, "номер");
					String nick = playerPositions.get(num);
					if (nick == null) {
						ctx.getSource().sendFeedback(() -> Text.literal(config.playerNotFoundMessage != null ? config.playerNotFoundMessage : "На этой позиции нет игрока!"), false);
						return 0;
					}
					ServerPlayerEntity player = ctx.getSource().getServer().getPlayerManager().getPlayer(nick);
					if (player == null) {
						ctx.getSource().sendFeedback(() -> Text.literal(config.playerNotFoundMessage != null ? config.playerNotFoundMessage : "Игрок не найден на сервере!"), false);
						return 0;
					}
					removeLastAnswerWithDelay(num, player, ctx.getSource().getServer());
					sendViolationMessageToAll(ctx.getSource().getServer(), nick);
					return 1;
				})));
			dispatcher.register(CommandManager.literal("stopround")
				.requires(source -> source.hasPermissionLevel(2))
				.executes(ctx -> {
					stopRound(ctx.getSource().getServer());
					ctx.getSource().sendFeedback(() -> Text.literal(config.finishMessage != null ? config.finishMessage : "Раунд остановлен!"), false);
					return 1;
				}));
			dispatcher.register(CommandManager.literal("listquestions")
				.requires(source -> source.hasPermissionLevel(0))
				.executes(ctx -> {
					for (int i = 0; i < config.questions.size(); i++) {
						String q = config.questions.get(i);
						int num = i + 1;
						ctx.getSource().sendFeedback(() -> Text.literal(num + ". " + q), false);
					}
					return 1;
				}));
			dispatcher.register(CommandManager.literal("setnextquestion")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.argument("номер", IntegerArgumentType.integer(1, 1000))
				.executes(ctx -> {
					if (!modEnabled) {
						ctx.getSource().sendFeedback(() -> Text.literal(config.modBlockedMessage != null ? config.modBlockedMessage : "Мод заблокирован. Неверный пароль в config.json!"), false);
						return 0;
					}
					int num = IntegerArgumentType.getInteger(ctx, "номер");
					if (num < 1 || num > config.questions.size()) {
						ctx.getSource().sendFeedback(() -> Text.literal(config.noSuchQuestionMessage != null ? config.noSuchQuestionMessage : "Нет такого вопроса!"), false);
						return 0;
					}
					bridgesBuildingStarted = false;
					for (int i = 1; i <= 4; i++) {
						String nick = playerPositions.get(i);
						if (nick == null) continue;
						ServerPlayerEntity player = ctx.getSource().getServer().getPlayerManager().getPlayer(nick);
						if (player != null) {
							lastBridgeEnd.put(i, new BlockPos(config.startPositions.get(i-1).get("x"), config.startPositions.get(i-1).get("y"), config.startPositions.get(i-1).get("z")));
						}
					}
					currentRound++;
					currentQuestion = config.questions.get(num - 1);
					answeredPlayers.clear();
					playerAnswers.clear();
					acceptingAnswers = true;
					roundPlayers.clear();
					deadPlayers.clear();
					for (ServerPlayerEntity player : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
						PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
						buf.writeString(currentQuestion);
						buf.writeInt(questionTime);
						buf.writeString(""); // сбрасываем ответ
						ServerPlayNetworking.send(player, SHOW_QUESTION_PACKET, buf);
						// Сбросить таймер (hideTimer = false)
						PacketByteBuf showTimerBuf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
						ServerPlayNetworking.send(player, new Identifier(MOD_ID, "show_timer"), showTimerBuf);
					}
					if (questionTimer != null) questionTimer.cancel();
					questionEndTimeMillis = System.currentTimeMillis() + questionTime * 1000L;
					questionTimer = new Timer();
					questionTimer.schedule(new TimerTask() {
						@Override
						public void run() {
							acceptingAnswers = false;
							if (!bridgesBuildingStarted) {
								bridgesBuildingStarted = true;
								buildAllBridgesAndTeleport(ctx.getSource().getServer());
							}
							// Скрываем только таймер
							for (ServerPlayerEntity p : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
								PacketByteBuf hideTimerBuf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
								hideTimerBuf.writeString("HIDE_TIMER_ONLY");
								ServerPlayNetworking.send(p, new Identifier(MOD_ID, "hide_timer_only"), hideTimerBuf);
							}
						}
					}, questionTime * 1000L);
					ctx.getSource().sendFeedback(() -> Text.literal(config.questionSelectedMessage != null ? config.questionSelectedMessage : "Вопрос выбран!"), false);
					return 1;
				})));
			dispatcher.register(CommandManager.literal("clearbridges")
				.requires(source -> source.hasPermissionLevel(2))
				.executes(ctx -> {
					clearAllBridgesAndLetters(ctx.getSource().getServer());
					ctx.getSource().sendFeedback(() -> Text.literal(config.bridgesClearedMessage != null ? config.bridgesClearedMessage : "Все мосты и буквы удалены!"), false);
					return 1;
				}));
			dispatcher.register(CommandManager.literal("question")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.argument("вопрос", StringArgumentType.greedyString())
				.executes(ctx -> {
					if (!modEnabled) {
						ctx.getSource().sendFeedback(() -> Text.literal(config.modBlockedMessage != null ? config.modBlockedMessage : "Мод заблокирован. Неверный пароль в config.json!"), false);
						return 0;
					}
					bridgesBuildingStarted = false;
					currentRound++;
					currentQuestion = StringArgumentType.getString(ctx, "вопрос");
					answeredPlayers.clear();
					playerAnswers.clear();
					acceptingAnswers = true;
					roundPlayers.clear();
					deadPlayers.clear();
					for (ServerPlayerEntity player : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
						PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
						buf.writeString(currentQuestion);
						buf.writeInt(questionTime);
						buf.writeString(""); // сбрасываем ответ
						ServerPlayNetworking.send(player, SHOW_QUESTION_PACKET, buf);
						// Сбросить таймер (hideTimer = false)
						PacketByteBuf showTimerBuf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
						ServerPlayNetworking.send(player, new Identifier(MOD_ID, "show_timer"), showTimerBuf);
					}
					if (questionTimer != null) questionTimer.cancel();
					questionEndTimeMillis = System.currentTimeMillis() + questionTime * 1000L;
					questionTimer = new Timer();
					questionTimer.schedule(new TimerTask() {
						@Override
						public void run() {
							acceptingAnswers = false;
							if (!bridgesBuildingStarted) {
								bridgesBuildingStarted = true;
								buildAllBridgesAndTeleport(ctx.getSource().getServer());
							}
							for (ServerPlayerEntity p : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
								PacketByteBuf hideTimerBuf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
								hideTimerBuf.writeString("HIDE_TIMER_ONLY");
								ServerPlayNetworking.send(p, new Identifier(MOD_ID, "hide_timer_only"), hideTimerBuf);
							}
						}
					}, questionTime * 1000L);
					ctx.getSource().sendFeedback(() -> Text.literal(config.customQuestionSetMessage != null ? config.customQuestionSetMessage : "Вопрос задан!"), false);
					return 1;
				})));
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				BlockPos pos = player.getBlockPos();
				BlockPos below = pos.down();
				if (player.isDead() || player.isRemoved() || player.getHealth() <= 0) {
					if (player.interactionManager.getGameMode() != GameMode.SPECTATOR) {
						player.changeGameMode(GameMode.SPECTATOR);
					}
					continue;
				}
				if (player.getWorld().getBlockState(below).getBlock().getTranslationKey().contains("water") || player.isTouchingWater()) {
					player.damage(player.getDamageSources().drown(), 1000f);
				}
			}
		});
		LOGGER.info("Hello Fabric world!");
	}

	private void ensureConfigFile() {
		try {
			Path configDir = FabricLoader.getInstance().getConfigDir();
			Path configFile = configDir.resolve(CONFIG_FILE_NAME);
			if (!Files.exists(configFile)) {
				try (InputStream in = getClass().getClassLoader().getResourceAsStream("assets/question-and-answer/config.json")) {
					if (in != null) {
						Files.createDirectories(configDir);
						Files.copy(in, configFile, StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}
		} catch (Exception e) {
			LOGGER.error("Не удалось создать config/question-and-answer.json", e);
		}
	}

	private void loadConfig() {
		Path configFile = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
		try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(configFile), "UTF-8")) {
			Gson gson = new Gson();
			Type type = new TypeToken<Config>(){}.getType();
			config = gson.fromJson(reader, type);
			if (config != null && config.getClass().getDeclaredFields() != null) {
				if (config.questionTime != null) questionTime = config.questionTime;
				if (config.bridgeBlocks != null && !config.bridgeBlocks.isEmpty()) bridgeBlocks = config.bridgeBlocks;
				if (config.winZ != null) winZ = config.winZ;
				if (config.buildParticle != null) buildParticle = config.buildParticle;
				if (config.removeParticle != null) removeParticle = config.removeParticle;
				if (config.buildSound != null) buildSound = config.buildSound;
				if (config.removeSound != null) removeSound = config.removeSound;
				if (config.tntSound != null) tntSound = config.tntSound;
				if (config.answerSound != null) answerSound = config.answerSound;
				if (config.winMessage != null) winMessage = config.winMessage;
				if (config.violationMessage != null) violationMessage = config.violationMessage;
				if (config.answerMessage != null) answerMessage = config.answerMessage;
				if (config.roundStoppedMessage != null) roundStoppedMessage = config.roundStoppedMessage;
				if (config.forcedRemoveSteps != null && !config.forcedRemoveSteps.isEmpty()) forcedRemoveBlocks = config.forcedRemoveSteps.get(Math.min(currentRound, config.forcedRemoveSteps.size()-1));
				if (config.winColor == null) config.winColor = "green";
				if (config.winNameColor == null) config.winNameColor = "yellow";
				if (config.answerColor == null) config.answerColor = "white";
				if (config.violationColor == null) config.violationColor = "red";
				if (config.stoppedColor == null) config.stoppedColor = "gray";
				if (config.chatPrefix != null) chatPrefix = config.chatPrefix;
				if (config.chatSuffix != null) chatSuffix = config.chatSuffix;
				if (config.chatEmptyLine) chatEmptyLine = config.chatEmptyLine;
			}
		} catch (Exception e) {
			LOGGER.error("Не удалось загрузить конфиг", e);
			config = new Config();
			config.questions = new ArrayList<>();
			config.startPositions = new ArrayList<>();
			questionTime = 30;
			bridgeBlocks = List.of("minecraft:stone");
			winZ = 100;
		}
	}

	private void startRound(MinecraftServer server, boolean next) {
		bridgesBuildingStarted = false;
		destroyedBlocksCount = 0;
		for (int i = 1; i <= 4; i++) {
			String nick = playerPositions.get(i);
			if (nick == null) continue;
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(nick);
			if (player == null) continue;
			Map<String, Integer> pos = config.startPositions.size() >= i ? config.startPositions.get(i-1) : null;
			if (pos != null) {
				player.teleport(server.getOverworld(), pos.get("x"), pos.get("y") + 1, pos.get("z"), player.getYaw(), player.getPitch());
			}
		}
		if (!next) {
			for (int i = 1; i <= 4; i++) {
				String nick = playerPositions.get(i);
				if (nick == null) continue;
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(nick);
				if (player == null) continue;
				Map<String, Integer> pos = config.startPositions.size() >= i ? config.startPositions.get(i-1) : null;
				if (pos != null) {
					player.teleport(server.getOverworld(), pos.get("x"), pos.get("y") + 1, pos.get("z"), player.getYaw(), player.getPitch());
				}
			}
		} else {
			currentRound++;
		}
		if (config.questions != null && !config.questions.isEmpty()) {
			if (currentRound < config.questions.size()) {
				currentQuestion = config.questions.get(currentRound);
			} else {
				currentQuestion = config.questions.get(config.questions.size() - 1);
			}
			LOGGER.info("Выбран вопрос: " + currentQuestion);
			answeredPlayers.clear();
			playerAnswers.clear();
			acceptingAnswers = true;
			roundPlayers.clear();
			deadPlayers.clear();
			for (int i = 1; i <= 4; i++) {
				String nick = playerPositions.get(i);
				if (nick != null) roundPlayers.add(nick);
			}
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
				buf.writeString(currentQuestion);
				buf.writeInt(questionTime);
				buf.writeString(""); // сбрасываем ответ
				ServerPlayNetworking.send(player, SHOW_QUESTION_PACKET, buf);
				// Сбросить таймер (hideTimer = false)
				PacketByteBuf showTimerBuf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
				ServerPlayNetworking.send(player, new Identifier(MOD_ID, "show_timer"), showTimerBuf);
			}
			if (questionTimer != null) questionTimer.cancel();
			questionEndTimeMillis = System.currentTimeMillis() + questionTime * 1000L;
			questionTimer = new Timer();
			questionTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					acceptingAnswers = false;
					buildAllBridgesAndTeleport(server);
					// Скрываем только таймер
					for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
						PacketByteBuf hideTimerBuf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
						hideTimerBuf.writeString("HIDE_TIMER_ONLY");
						ServerPlayNetworking.send(p, new Identifier(MOD_ID, "hide_timer_only"), hideTimerBuf);
					}
				}
			}, questionTime * 1000L);
			LOGGER.info("roundPlayers (startRound): " + roundPlayers);
		} else {
			LOGGER.warn("Вопросы не найдены или пусты!");
		}
	}

	private void buildAllBridgesAndTeleport(MinecraftServer server) {
		bridgesToBuild = 0;
		for (int i = 1; i <= 4; i++) {
			String nick = playerPositions.get(i);
			if (nick == null || deadPlayers.contains(nick)) continue;
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(nick);
			if (player == null) continue;
			String answer = playerAnswers.getOrDefault(nick, "");
			int len = answer.length();
			if (len > 0) bridgesToBuild++;
			buildBridgeWithDelay(player, len, server);
		}
		int removeCount = 0;
		if (config.forcedRemoveSteps != null && !config.forcedRemoveSteps.isEmpty()) {
			if (currentRound > 0) { // No removal on round 0 (the very first round)
				int index = currentRound - 1;
				if (index < config.forcedRemoveSteps.size()) {
					removeCount = config.forcedRemoveSteps.get(index);
				} else {
					// Use last value for all subsequent rounds
					removeCount = config.forcedRemoveSteps.get(config.forcedRemoveSteps.size() - 1);
				}
			}
		}
		if (removeCount > 0) {
			destroyBridgeBlocksFromStart(server, removeCount);
		}
		// После строительства всех мостов вызываем checkWinners
		bridgesDoneCallback = () -> {
			checkWinners(server);
			for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
				PacketByteBuf hideAnswerBuf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
				hideAnswerBuf.writeString("HIDE_ANSWER");
				ServerPlayNetworking.send(p, new Identifier(MOD_ID, "hide_answer"), hideAnswerBuf);
			}
		};
	}

	private void buildBridgeWithDelay(ServerPlayerEntity player, int length, MinecraftServer server) {
		if (length <= 0) return;
		World world = player.getWorld();
		int playerIndex = -1;
		for (Map.Entry<Integer, String> entry : playerPositions.entrySet()) {
			if (entry.getValue().equals(player.getEntityName())) {
				playerIndex = entry.getKey();
				break;
			}
		}
		if (playerIndex == -1) return;
		final int idx = playerIndex;
		Timer timer = new Timer();
		final int dx = 0, dz = 1;
		for (int i = 1; i <= length; i++) {
			int step = i;
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					server.execute(() -> {
						BlockPos prev = QuestionAndAnswer.this.lastBridgeEnd.getOrDefault(idx, player.getBlockPos());
						BlockPos center = new BlockPos(prev.getX() + dx, prev.getY(), prev.getZ() + dz);
						int sideX = dz;
						int sideZ = -dx;
						BlockPos left = center.add(sideX, 0, sideZ);
						BlockPos right = center.add(-sideX, 0, -sideZ);
						Block bridgeBlock = getCurrentBridgeBlock();
						world.setBlockState(center, bridgeBlock.getDefaultState());
						world.setBlockState(left, bridgeBlock.getDefaultState());
						world.setBlockState(right, bridgeBlock.getDefaultState());
						QuestionAndAnswer.this.lastBridgeEnd.put(idx, center);
						player.teleport(server.getOverworld(), center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5, player.getYaw(), player.getPitch());
						if (world instanceof ServerWorld) {
							playBlockSound((ServerWorld) world, center);
							spawnParticles((ServerWorld) world, center);
							String answer = QuestionAndAnswer.this.playerAnswers.getOrDefault(player.getEntityName(), "");
							if (answer != null && answer.length() >= step) {
								// Верх — только одна буква на блок (реверс)
								char topLetter = answer.charAt(answer.length() - step);
								removeTextDisplaysAt((ServerWorld) world, center.getX() + 0.5, center.getY() + 1.01, center.getZ() + 0.5);
								spawnTextDisplayNBT((ServerWorld) world, center.getX() + 0.5, center.getY() + 1.01, center.getZ() + 0.5, topLetter, new ArrayList<>(), "top_reversed", center);
								// Слева и справа — только одна буква на блок (обычный порядок)
								char sideLetter = answer.charAt(step - 1);
								double off = 0.501;
								float yawLeft = 0, yawRight = 0;
								double xL = 0, zL = 0, xR = 0, zR = 0;
								switch (direction.toLowerCase()) {
									case "north":
										xL = center.getX() + 0.5 + off; zL = center.getZ() + 0.5; yawLeft = 90;
										xR = center.getX() + 0.5 - off; zR = center.getZ() + 0.5; yawRight = -90;
										break;
									case "south":
										xL = center.getX() + 0.5 - off; zL = center.getZ() + 0.5; yawLeft = -90;
										xR = center.getX() + 0.5 + off; zR = center.getZ() + 0.5; yawRight = 90;
										break;
									case "west":
										xL = center.getX() + 0.5; zL = center.getZ() + 0.5 - off; yawLeft = 180;
										xR = center.getX() + 0.5; zR = center.getZ() + 0.5 + off; yawRight = 0;
										break;
									case "east":
									default:
										xL = center.getX() + 0.5; zL = center.getZ() + 0.5 + off; yawLeft = 0;
										xR = center.getX() + 0.5; zR = center.getZ() + 0.5 - off; yawRight = 180;
										break;
								}
								removeTextDisplaysAt((ServerWorld) world, xL, center.getY() + 0.5, zL);
								spawnTextDisplayNBT((ServerWorld) world, xL, center.getY() + 0.5, zL, sideLetter, new ArrayList<>(), "left", center);
								removeTextDisplaysAt((ServerWorld) world, xR, center.getY() + 0.5, zR);
								spawnTextDisplayNBT((ServerWorld) world, xR, center.getY() + 0.5, zR, sideLetter, new ArrayList<>(), "right", center);
							}
						}
						// Если это последний блок моста для игрока
						if (step == length) {
							bridgesToBuild--;
							if (bridgesToBuild <= 0 && bridgesDoneCallback != null) {
								bridgesDoneCallback.run();
								bridgesDoneCallback = null;
							}
						}
					});
				}
			}, 500L * (i - 1));
		}
	}

	private Block getCurrentBridgeBlock() {
		int idx = currentRound % bridgeBlocks.size();
		String blockId = bridgeBlocks.get(idx);
		return Registries.BLOCK.get(new Identifier(blockId));
	}

	private void sendChatMessageToAll(MinecraftServer server, MutableText msg) {
		if (chatEmptyLine) {
			for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
				p.sendMessage(Text.literal(""));
			}
		}
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			p.sendMessage(msg);
		}
		if (chatEmptyLine) {
			for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
				p.sendMessage(Text.literal(""));
			}
		}
	}

	private void sendAnswerMessageToAll(MinecraftServer server, String playerName) {
		MutableText msg = parseColoredText(chatPrefix)
			.append(Text.literal(playerName)
				.setStyle(Style.EMPTY.withColor(getFormattingByCode(config.winNameColor)).withBold(false)))
			.append(parseColoredText(config.answerMessage))
			.append(parseColoredText(chatSuffix));
		sendChatMessageToAll(server, msg);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (Boolean.TRUE.equals(config.showAnswerTitle)) {
				MutableText titleMsg = parseColoredText(config.answerTitleMessage);
				p.networkHandler.sendPacket(new TitleS2CPacket(titleMsg));
			}
		}
	}

	private void sendViolationMessageToAll(MinecraftServer server, String playerName) {
		MutableText msg = parseColoredText(chatPrefix)
			.append(Text.literal(playerName)
				.setStyle(Style.EMPTY.withColor(getFormattingByCode(config.violationColor)).withBold(false)))
			.append(parseColoredText(config.violationMessage))
			.append(parseColoredText(chatSuffix));
		sendChatMessageToAll(server, msg);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (Boolean.TRUE.equals(config.showViolationTitle)) {
				MutableText titleMsg = parseColoredText(config.violationTitleMessage);
				p.networkHandler.sendPacket(new TitleS2CPacket(titleMsg));
			}
		}
	}

	private void playAnswerSound(ServerPlayerEntity player) {
		player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
	}

	private void playBlockSound(ServerWorld world, BlockPos pos) {
		world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.BLOCKS, 1.0f, 1.0f);
	}

	private void spawnParticles(ServerWorld world, BlockPos pos) {
		world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.1);
	}

	private void spawnBridgeLetterDisplays(ServerWorld world, BlockPos pos, String answer, String direction) {
		List<UUID> uuids = new ArrayList<>();
		// Сверху — задом наперёд и развёрнуто
		if (answer != null && !answer.isEmpty()) {
			String reversed = new StringBuilder(answer).reverse().toString();
			for (int i = 0; i < reversed.length(); i++) {
				// Позиция для каждой буквы сверху
				double x = pos.getX() + 0.5;
				double y = pos.getY() + 1.01;
				double z = pos.getZ() + 0.5 + i; // буквы идут вдоль моста
				spawnTextDisplayNBT(world, x, y, z, reversed.charAt(i), uuids, "top_reversed", pos);
			}
		}
		// Слева и справа (как раньше, по одной букве)
		double off = 0.501;
		float yawLeft = 0, yawRight = 0;
		double xL = 0, zL = 0, xR = 0, zR = 0;
		switch (direction.toLowerCase()) {
			case "north":
				xL = pos.getX() + 0.5 + off; zL = pos.getZ() + 0.5; yawLeft = 90;
				xR = pos.getX() + 0.5 - off; zR = pos.getZ() + 0.5; yawRight = -90;
				break;
			case "south":
				xL = pos.getX() + 0.5 - off; zL = pos.getZ() + 0.5; yawLeft = -90;
				xR = pos.getX() + 0.5 + off; zR = pos.getZ() + 0.5; yawRight = 90;
				break;
			case "west":
				xL = pos.getX() + 0.5; zL = pos.getZ() + 0.5 - off; yawLeft = 180;
				xR = pos.getX() + 0.5; zR = pos.getZ() + 0.5 + off; yawRight = 0;
				break;
			case "east":
			default:
				xL = pos.getX() + 0.5; zL = pos.getZ() + 0.5 + off; yawLeft = 0;
				xR = pos.getX() + 0.5; zR = pos.getZ() + 0.5 - off; yawRight = 180;
				break;
		}
		// Для боковых — только первая буква слова
		char letter = (answer != null && !answer.isEmpty()) ? answer.charAt(0) : ' ';
		spawnTextDisplayNBT(world, xL, pos.getY() + 0.5, zL, letter, uuids, "left", pos);
		spawnTextDisplayNBT(world, xR, pos.getY() + 0.5, zR, letter, uuids, "right", pos);
	}

	private void spawnTextDisplayNBT(ServerWorld world, double x, double y, double z, char letter, List<UUID> uuids, String position, BlockPos blockPos) {
		if (letter == ' ' || !Character.isLetter(letter)) return;
		NbtCompound nbt = new NbtCompound();
		nbt.putString("id", "minecraft:text_display");
		double letterX = x;
		double letterY = y;
		double letterZ = z;
		NbtList rotList = new NbtList();
		if (position.equals("top_reversed")) {
			letterZ = z - 0.7;
			rotList.add(NbtFloat.of(180f));
			rotList.add(NbtFloat.of(270f));
		} else if (position.equals("top")) {
			letterZ = z - 0.9;
			rotList.add(NbtFloat.of(0f));
			rotList.add(NbtFloat.of(270f));
		} else if (position.equals("right")) {
			letterX = x - 2.05;
			letterY = y - 0.7;
			rotList.add(NbtFloat.of(90f));
			rotList.add(NbtFloat.of(0f));
		} else if (position.equals("left")) {
			letterX = x + 2.05;
			letterY = y - 0.7;
			rotList.add(NbtFloat.of(270f));
			rotList.add(NbtFloat.of(0f));
		} else {
			rotList.add(NbtFloat.of(0f));
			rotList.add(NbtFloat.of(270f));
		}
		NbtList posList = new NbtList();
		posList.add(NbtDouble.of(letterX));
		posList.add(NbtDouble.of(letterY));
		posList.add(NbtDouble.of(letterZ));
		nbt.put("Pos", posList);
		nbt.put("Rotation", rotList);
		NbtCompound transformation = new NbtCompound();
		NbtList leftRot = new NbtList();
		leftRot.add(NbtFloat.of(0f));
		leftRot.add(NbtFloat.of(0f));
		leftRot.add(NbtFloat.of(0f));
		leftRot.add(NbtFloat.of(1f));
		transformation.put("left_rotation", leftRot);
		NbtList rightRot = new NbtList();
		rightRot.add(NbtFloat.of(0f));
		rightRot.add(NbtFloat.of(0f));
		rightRot.add(NbtFloat.of(0f));
		rightRot.add(NbtFloat.of(1f));
		transformation.put("right_rotation", rightRot);
		NbtList translation = new NbtList();
		translation.add(NbtFloat.of(0f));
		translation.add(NbtFloat.of(0f));
		translation.add(NbtFloat.of(0f));
		transformation.put("translation", translation);
		NbtList scale = new NbtList();
		scale.add(NbtFloat.of(5.0f));
		scale.add(NbtFloat.of(5.0f));
		scale.add(NbtFloat.of(5.0f));
		transformation.put("scale", scale);
		nbt.put("transformation", transformation);
		nbt.putInt("background", 0);
		nbt.putString("text", "\"" + letter + "\"");
		nbt.putBoolean("shadow", true);
		var entity = EntityType.TEXT_DISPLAY.create(world);
		if (entity != null) {
			entity.readNbt(nbt);
			entity.setPos(letterX, letterY, letterZ);
			entity.setYaw(rotList.getFloat(0));
			entity.setPitch(rotList.getFloat(1));
			world.spawnEntity(entity);
			uuids.add(entity.getUuid());
			// Сохраняем UUID по ключу BlockPos+side
			String key = blockPos.toShortString() + "_" + position;
			List<UUID> list = bridgeTextEntities.getOrDefault(key, new ArrayList<>());
			list.add(entity.getUuid());
			bridgeTextEntities.put(key, list);
		}
	}

	private void removeBridgeLetterDisplays(ServerWorld world, BlockPos pos) {
		String[] sides = {"top_reversed", "left", "right"};
		for (String side : sides) {
			String key = pos.toShortString() + "_" + side;
			List<UUID> uuids = bridgeTextEntities.remove(key);
		if (uuids != null) {
			for (UUID uuid : uuids) {
				var entity = world.getEntity(uuid);
				if (entity instanceof TextDisplayEntity) {
					entity.kill();
					}
				}
			}
		}
	}

	private void removeLastAnswerWithDelay(int playerIndex, ServerPlayerEntity player, MinecraftServer server) {
		String answer = playerAnswers.getOrDefault(player.getEntityName(), "");
		int length = answer.length();
		if (length <= 0) return;
		World world = player.getWorld();
		Block bridgeBlock = getCurrentBridgeBlock();
		final int dx = 0, dz = 1;
		BlockPos end = lastBridgeEnd.getOrDefault(playerIndex, player.getBlockPos());
		Timer timer = new Timer();
		for (int i = 0; i < length; i++) {
			int step = i;
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					server.execute(() -> {
					BlockPos center = new BlockPos(end.getX() - dx * step, end.getY(), end.getZ() - dz * step);
					int sideX = dz;
					int sideZ = -dx;
					BlockPos left = center.add(sideX, 0, sideZ);
					BlockPos right = center.add(-sideX, 0, -sideZ);
					world.setBlockState(center, Blocks.AIR.getDefaultState());
					world.setBlockState(left, Blocks.AIR.getDefaultState());
					world.setBlockState(right, Blocks.AIR.getDefaultState());
					if (world instanceof ServerWorld) {
						playBlockSound((ServerWorld) world, center);
					}
					removeBridgeLetterDisplays((ServerWorld) world, center);
					removeBridgeLetterDisplays((ServerWorld) world, left);
					removeBridgeLetterDisplays((ServerWorld) world, right);
					if (step < length - 1) {
						BlockPos next = new BlockPos(end.getX() - dx * (step + 1), end.getY(), end.getZ() - dz * (step + 1));
						server.execute(() -> player.teleport(server.getOverworld(), next.getX() + 0.5, next.getY() + 1, next.getZ() + 0.5, player.getYaw(), player.getPitch()));
						lastBridgeEnd.put(playerIndex, next);
					} else {
						BlockPos start = lastBridgeEnd.getOrDefault(playerIndex, player.getBlockPos());
						BlockPos found = null;
						for (int j = length; j >= 0; j--) {
							BlockPos candidate = new BlockPos(end.getX() - dx * j, end.getY(), end.getZ() - dz * j);
							if (!world.getBlockState(candidate).isAir()) {
								found = candidate;
								break;
							}
						}
						BlockPos tp = (found != null) ? found : start;
						server.execute(() -> player.teleport(server.getOverworld(), tp.getX() + 0.5, tp.getY() + 1, tp.getZ() + 0.5, player.getYaw(), player.getPitch()));
						lastBridgeEnd.put(playerIndex, tp);
					}
					});
				}
			}, 200L * i);
		}
	}

	private void checkWinners(MinecraftServer server) {
		List<String> winners = new ArrayList<>();
		int maxBridgeZ = Integer.MIN_VALUE;
		for (int i = 1; i <= 4; i++) {
			String nick = playerPositions.get(i);
			if (nick == null || deadPlayers.contains(nick)) continue;
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(nick);
			if (player == null) continue;
			BlockPos pos = lastBridgeEnd.get(i);
			if (pos == null) continue;
			if (pos.getZ() > maxBridgeZ) maxBridgeZ = pos.getZ();
			double playerZ = player.getZ();
			LOGGER.info("Проверка победы: игрок " + nick + ", posZ=" + pos.getZ() + ", playerZ=" + playerZ + ", winZ=" + winZ);
			boolean win = pos.getZ() >= winZ;
			if (win) winners.add(nick);
		}
		LOGGER.info("winZ=" + winZ + ", максимальный конец моста (max posZ)=" + maxBridgeZ);
		if (winZ > maxBridgeZ) {
			LOGGER.warn("[ВНИМАНИЕ] winZ больше, чем максимальный конец моста! Победа невозможна при текущих настройках.");
		}
		if (!winners.isEmpty()) {
			MutableText msg = parseColoredText(chatPrefix)
				.append(parseColoredText(config.winMessage).setStyle(Style.EMPTY.withColor(getFormattingByCode(config.winColor)).withBold(false)));
			for (String w : winners) {
				msg.append(Text.literal(w + " ").setStyle(Style.EMPTY.withColor(getFormattingByCode(config.winNameColor)).withBold(false)));
			}
			msg.append(parseColoredText(chatSuffix));
			sendChatMessageToAll(server, msg);
			for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
				if (Boolean.TRUE.equals(config.showWinTitle)) {
					MutableText titleMsg = parseColoredText(config.winTitleMessage);
					for (String w : winners) {
						titleMsg.append(Text.literal(" " + w).setStyle(Style.EMPTY.withColor(getFormattingByCode(config.winNameColor)).withBold(false)));
					}
					p.networkHandler.sendPacket(new TitleS2CPacket(titleMsg));
				}
			}
			stopRound(server);
			currentRound++;
		}
	}

	private void stopRound(MinecraftServer server) {
		acceptingAnswers = false;
		currentQuestion = null;
		if (questionTimer != null) questionTimer.cancel();
		roundPlayers.clear();
		deadPlayers.clear();
		MutableText msg = parseColoredText(chatPrefix)
			.append(parseColoredText(config.roundStoppedMessage).setStyle(Style.EMPTY.withColor(getFormattingByCode(config.stoppedColor)).withBold(false)))
			.append(parseColoredText(chatSuffix));
		sendChatMessageToAll(server, msg);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (Boolean.TRUE.equals(config.showRoundStoppedTitle)) {
				MutableText titleMsg = parseColoredText(config.roundStoppedTitleMessage);
				p.networkHandler.sendPacket(new TitleS2CPacket(titleMsg));
			}
		}
		// Сбросить номер раунда, чтобы forcedRemoveSteps начинался сначала
		currentRound = 0;
	}

	private void destroyBridgeBlocksFromStart(MinecraftServer server, int count) {
		destroyedBlocksCount += count;
		for (int i = 1; i <= 4; i++) {
			String nick = playerPositions.get(i);
			if (nick == null || deadPlayers.contains(nick)) continue;
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(nick);
			if (player == null) continue;
			BlockPos start = config.startPositions.size() >= i ? new BlockPos(config.startPositions.get(i-1).get("x"), config.startPositions.get(i-1).get("y"), config.startPositions.get(i-1).get("z")) : null;
			if (start == null) continue;
			int dx = 0, dz = 1;
			World world = player.getWorld();
			Timer timer = new Timer();
			for (int j = destroyedBlocksCount - count; j < destroyedBlocksCount; j++) {
				int step = j;
				timer.schedule(new TimerTask() {
					@Override
					public void run() {
						server.execute(() -> {
						BlockPos center = new BlockPos(start.getX() + dx * step, start.getY(), start.getZ() + dz * step);
						int sideX = dz;
						int sideZ = -dx;
						BlockPos left = center.add(sideX, 0, sideZ);
						BlockPos right = center.add(-sideX, 0, -sideZ);
						world.setBlockState(center, Blocks.AIR.getDefaultState());
						world.setBlockState(left, Blocks.AIR.getDefaultState());
						world.setBlockState(right, Blocks.AIR.getDefaultState());
						if (world instanceof ServerWorld) {
							((ServerWorld) world).playSound(null, center, Registries.SOUND_EVENT.get(new Identifier(tntSound)), SoundCategory.BLOCKS, 1.0f, 1.0f);
								((ServerWorld) world).spawnParticles(
									getParticleEffectByName(removeParticle),
									center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5,
									8, 0.2, 0.2, 0.2, 0.1
								);
						}
						removeBridgeLetterDisplays((ServerWorld) world, center);
						removeBridgeLetterDisplays((ServerWorld) world, left);
						removeBridgeLetterDisplays((ServerWorld) world, right);
						});
					}
				}, forcedRemoveDelay * (step - (destroyedBlocksCount - count)));
			}
		}
	}

	private ParticleEffect getParticleEffectByName(String name) {
		switch (name) {
			case "smoke": return ParticleTypes.SMOKE;
			case "explosion": return ParticleTypes.EXPLOSION;
			case "happy_villager": return ParticleTypes.HAPPY_VILLAGER;
			case "cloud": return ParticleTypes.CLOUD;
			case "flame": return ParticleTypes.FLAME;
			// Добавьте другие стандартные частицы по необходимости
			default: return ParticleTypes.SMOKE;
		}
	}

	private void clearAllBridgesAndLetters(MinecraftServer server) {
		for (int i = 1; i <= 4; i++) {
			BlockPos start = config.startPositions.size() >= i ? new BlockPos(config.startPositions.get(i-1).get("x"), config.startPositions.get(i-1).get("y"), config.startPositions.get(i-1).get("z")) : null;
			if (start == null) continue;
			int dx = 0, dz = 1;
			World world = server.getOverworld();
			// Удаляем блоки до winZ включительно
			for (int j = 0; ; j++) {
				BlockPos center = new BlockPos(start.getX() + dx * j, start.getY(), start.getZ() + dz * j);
				if (center.getZ() > winZ) break;
				int sideX = dz;
				int sideZ = -dx;
				BlockPos left = center.add(sideX, 0, sideZ);
				BlockPos right = center.add(-sideX, 0, -sideZ);
				world.setBlockState(center, Blocks.AIR.getDefaultState());
				world.setBlockState(left, Blocks.AIR.getDefaultState());
				world.setBlockState(right, Blocks.AIR.getDefaultState());
				if (world instanceof ServerWorld) {
					removeBridgeLetterDisplays((ServerWorld) world, center);
					removeBridgeLetterDisplays((ServerWorld) world, left);
					removeBridgeLetterDisplays((ServerWorld) world, right);
				}
			}
			// Сброс lastBridgeEnd для этого игрока на старт
			lastBridgeEnd.put(i, start);
		}
		// Очищаем карту сущностей букв
		bridgeTextEntities.clear();
	}

	private Formatting getFormattingByCode(String code) {
		if (code == null) return Formatting.WHITE;
		switch (code.toLowerCase()) {
			case "&0": return Formatting.BLACK;
			case "&1": return Formatting.DARK_BLUE;
			case "&2": return Formatting.DARK_GREEN;
			case "&3": return Formatting.DARK_AQUA;
			case "&4": return Formatting.DARK_RED;
			case "&5": return Formatting.DARK_PURPLE;
			case "&6": return Formatting.GOLD;
			case "&7": return Formatting.GRAY;
			case "&8": return Formatting.DARK_GRAY;
			case "&9": return Formatting.BLUE;
			case "&a": return Formatting.GREEN;
			case "&b": return Formatting.AQUA;
			case "&c": return Formatting.RED;
			case "&d": return Formatting.LIGHT_PURPLE;
			case "&e": return Formatting.YELLOW;
			case "&f": return Formatting.WHITE;
			default: return Formatting.WHITE;
		}
	}

	private MutableText parseColoredText(String input) {
		if (input == null) return Text.literal("");
		MutableText result = Text.literal("");
		Formatting current = Formatting.WHITE;
		StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (c == '&' && i + 1 < input.length()) {
				if (buffer.length() > 0) {
					result.append(Text.literal(buffer.toString()).setStyle(Style.EMPTY.withColor(current)));
					buffer.setLength(0);
				}
				String code = "&" + input.charAt(i + 1);
				current = getFormattingByCode(code);
				i++;
			} else {
				buffer.append(c);
			}
		}
		if (buffer.length() > 0) {
			result.append(Text.literal(buffer.toString()).setStyle(Style.EMPTY.withColor(current)));
		}
		return result;
	}

	private boolean isTextDisplayPresent(ServerWorld world, double x, double y, double z, char letter) {
		List<TextDisplayEntity> entities = world.getEntitiesByClass(
			TextDisplayEntity.class,
			new Box(x-0.1, y-0.1, z-0.1, x+0.1, y+0.1, z+0.1),
			e -> true
		);
		for (TextDisplayEntity entity : entities) {
			if (entity.getCustomName() != null && entity.getCustomName().getString().equals(String.valueOf(letter))) {
				return true;
			}
		}
		return false;
	}

	private void removeTextDisplaysAt(ServerWorld world, double x, double y, double z) {
		List<TextDisplayEntity> entities = world.getEntitiesByClass(
			TextDisplayEntity.class,
			new Box(x-0.1, y-0.1, z-0.1, x+0.1, y+0.1, z+0.1),
			e -> true
		);
		for (TextDisplayEntity entity : entities) {
			entity.kill();
		}
	}
}