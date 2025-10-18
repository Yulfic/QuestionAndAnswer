package com.zdev.client;

public class QuestionOverlayHandler {
    private static String questionText = null;
    private static boolean showQuestion = false;
    private static int timeLeft = 0;
    private static int totalTime = 0;
    private static long lastUpdate = 0;
    private static String playerAnswer = null;
    private static boolean hideTimer = false;
    private static boolean hideAnswer = false;

    public static void setQuestion(String text, int seconds) {
        questionText = text;
        showQuestion = true;
        timeLeft = seconds;
        totalTime = seconds;
        lastUpdate = System.currentTimeMillis();
        hideAnswer = false;
    }

    public static void tickTimer() {
        if (showQuestion && timeLeft > 0) {
            long now = System.currentTimeMillis();
            int elapsed = (int)((now - lastUpdate) / 1000);
            if (elapsed > 0) {
                timeLeft = Math.max(0, totalTime - elapsed);
            }
        }
    }

    public static int getTimeLeft() {
        tickTimer();
        return timeLeft;
    }

    public static void clearQuestion() {
        showQuestion = false;
        timeLeft = 0;
        totalTime = 0;
    }

    public static String getQuestion() {
        return questionText;
    }

    public static boolean isExpired() {
        tickTimer();
        return showQuestion && timeLeft <= 0;
    }

    public static boolean shouldShow() {
        tickTimer();
        return showQuestion && questionText != null && timeLeft > 0;
    }

    public static void setAnswer(String answer) {
        playerAnswer = answer;
    }

    public static String getAnswer() {
        return playerAnswer;
    }

    public static void clearAnswer() {
        playerAnswer = null;
        hideAnswer = true;
    }

    public static boolean isHideAnswer() {
        return hideAnswer;
    }

    public static void resetHideAnswer() {
        hideAnswer = false;
    }

    public static void setHideTimer(boolean hide) {
        hideTimer = hide;
    }

    public static boolean isHideTimer() {
        return hideTimer;
    }
} 