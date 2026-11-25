package com.vetements.next;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

public class ModCore {
    private static final Properties PROPS = new Properties();
    private static final File CONFIG_FILE = new File("config/vetements-blood.properties");

    private static boolean chatEnabled = true;
    private static boolean autoSpace = true;

    private static String exclPrefix = "";
    private static String exclSuffix = "";

    private static String plainPrefix = "";
    private static String plainSuffix = "";

    private static int fontStyle = 0;

    private static boolean mathEnabled = true;
    private static boolean autoAuthEnabled = false;
    private static String authPassword = "";
    private static String authCommand = "/login";

    private static boolean fullbrightEnabled = false;
    private static double previousGamma = 1.0D;
    private static boolean gammaSaved = false;

    private static boolean freeLookEnabled = true;
    private static boolean freeLookActive = false;
    private static float cameraYaw = 0.0F;
    private static float cameraPitch = 0.0F;
    private static double lastMouseX = 0.0D;
    private static double lastMouseY = 0.0D;
    private static boolean firstMouseMove = true;

    private static boolean rshiftWasDown = false;
    private static boolean bWasDown = false;

    private static final String[] STYLE_NAMES = new String[]{
        "Обычный",
        "Жирный (§l)",
        "Курсив (§o)",
        "Жирный+Курсив",
        "Стиль ✦"
    };

    private static final String[] MATH_TRIGGERS = new String[]{
        "реши", "сколько будет", "пример", "вычисли", "посчитай", "math"
    };

    private static final Pattern MATH_PAIR_PATTERN =
        Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(-?\\d+(?:\\.\\d+)?)");

    public static void init() {
        loadConfig();
        MinecraftForge.EVENT_BUS.register(ModCore.class);
    }

    public static void loadConfig() {
        try {
            if (CONFIG_FILE.exists()) {
                try (FileInputStream fis = new FileInputStream(CONFIG_FILE);
                     InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
                    PROPS.load(reader);
                }
            }
        } catch (IOException ignored) {
        }

        chatEnabled = Boolean.parseBoolean(PROPS.getProperty("chat_enabled", "true"));
        autoSpace = Boolean.parseBoolean(PROPS.getProperty("auto_space", "true"));

        exclPrefix = PROPS.getProperty("excl_prefix", "");
        exclSuffix = PROPS.getProperty("excl_suffix", "");

        plainPrefix = PROPS.getProperty("plain_prefix", "");
        plainSuffix = PROPS.getProperty("plain_suffix", "");

        fontStyle = parseIntSafe(PROPS.getProperty("font_style", "0"), 0);
        if (fontStyle < 0 || fontStyle >= STYLE_NAMES.length) {
            fontStyle = 0;
        }

        mathEnabled = Boolean.parseBoolean(PROPS.getProperty("math_enabled", "true"));
        autoAuthEnabled = Boolean.parseBoolean(PROPS.getProperty("auto_auth_enabled", "false"));
        authPassword = PROPS.getProperty("auth_password", "");
        authCommand = PROPS.getProperty("auth_command", "/login");

        fullbrightEnabled = Boolean.parseBoolean(PROPS.getProperty("fullbright", "false"));
        freeLookEnabled = Boolean.parseBoolean(PROPS.getProperty("freelook", "true"));
    }

    public static void saveConfig() {
        PROPS.setProperty("chat_enabled", String.valueOf(chatEnabled));
        PROPS.setProperty("auto_space", String.valueOf(autoSpace));
        PROPS.setProperty("excl_prefix", exclPrefix);
        PROPS.setProperty("excl_suffix", exclSuffix);
        PROPS.setProperty("plain_prefix", plainPrefix);
        PROPS.setProperty("plain_suffix", plainSuffix);
        PROPS.setProperty("font_style", String.valueOf(fontStyle));
        PROPS.setProperty("math_enabled", String.valueOf(mathEnabled));
        PROPS.setProperty("auto_auth_enabled", String.valueOf(autoAuthEnabled));
        PROPS.setProperty("auth_password", authPassword);
        PROPS.setProperty("auth_command", authCommand);
        PROPS.setProperty("fullbright", String.valueOf(fullbrightEnabled));
        PROPS.setProperty("freelook", String.valueOf(freeLookEnabled));

        try {
            if (CONFIG_FILE.getParentFile() != null && !CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                PROPS.store(writer, "Vetements Blood Config");
            }
        } catch (IOException ignored) {
        }
    }

    private static int parseIntSafe(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) {
            return;
        }

        if (fullbrightEnabled) {
            if (!gammaSaved) {
                previousGamma = mc.options.gamma;
                gammaSaved = true;
            }
            if (mc.options.gamma < 15.0D) {
                mc.options.gamma = 15.0D;
            }
        } else if (gammaSaved) {
            mc.options.gamma = previousGamma;
            gammaSaved = false;
        }

        if (mc.player == null || mc.screen != null) {
            freeLookActive = false;
            firstMouseMove = true;
            return;
        }

        long window = mc.getWindow().getWindow();
        boolean altDown = freeLookEnabled && (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS);

        if (altDown) {
            if (!freeLookActive) {
                freeLookActive = true;
                cameraYaw = mc.player.yRot;
                cameraPitch = mc.player.xRot;
                firstMouseMove = true;
            }
            double mouseX = mc.mouseHandler.xpos();
            double mouseY = mc.mouseHandler.ypos();
            if (firstMouseMove) {
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                firstMouseMove = false;
            } else {
                double dx = mouseX - lastMouseX;
                double dy = mouseY - lastMouseY;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                cameraYaw += (float) (dx * 0.15D);
                cameraPitch = MathHelper.clamp(cameraPitch + (float) (dy * 0.15D), -90.0F, 90.0F);
            }
        } else {
            freeLookActive = false;
            firstMouseMove = true;
        }
    }

    @SubscribeEvent
    public static void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
        if (freeLookActive) {
            event.setYaw(cameraYaw);
            event.setPitch(cameraPitch);
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            rshiftWasDown = false;
            bWasDown = false;
            return;
        }

        if (event.getKey() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            if (event.getAction() == GLFW.GLFW_PRESS && !rshiftWasDown) {
                rshiftWasDown = true;
                mc.setScreen(new VetementsBloodMenu());
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                rshiftWasDown = false;
            }
        }

        if (event.getKey() == GLFW.GLFW_KEY_B) {
            if (event.getAction() == GLFW.GLFW_PRESS && !bWasDown) {
                bWasDown = true;
                toggleFullbright();
                sendInfo("§4[VETEMENTS] §fFullbright: " + (fullbrightEnabled ? "§cВКЛ" : "§7ВЫКЛ"));
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                bWasDown = false;
            }
        }
    }

    @SubscribeEvent
    public static void onClientChat(ClientChatEvent event) {
        String raw = event.getMessage();
        if (raw == null || raw.isEmpty()) {
            return;
        }

        if (raw.startsWith(".vet")) {
            event.setCanceled(true);
            handleCommand(raw.trim());
            return;
        }

        if (raw.startsWith("/")) {
            return;
        }

        String processed = processOutgoingMessage(raw);
        if (!processed.equals(raw)) {
            if (processed.length() > 256) {
                processed = processed.substring(0, 256);
            }
            event.setMessage(processed);
        }
    }

    private static String applyFontStyle(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        switch (fontStyle) {
            case 1:
                return "§l" + text + "§r";
            case 2:
                return "§o" + text + "§r";
            case 3:
                return "§l§o" + text + "§r";
            case 4:
                return "✦ " + text + " ✦";
            case 0:
            default:
                return text;
        }
    }

    public static String processOutgoingMessage(String raw) {
        if (!chatEnabled || raw == null || raw.isEmpty()) {
            return raw;
        }

        boolean startsWithExcl = raw.startsWith("!");
        String messageBody = startsWithExcl ? raw.substring(1) : raw;

        if (autoSpace && !messageBody.isEmpty() && !messageBody.endsWith(" ")) {
            messageBody = messageBody + " ";
        }

        String styledBody = applyFontStyle(messageBody);

        String activePrefix = startsWithExcl ? exclPrefix : plainPrefix;
        String activeSuffix = startsWithExcl ? exclSuffix : plainSuffix;

        String formatted = styledBody;

        if (activePrefix != null && !activePrefix.isEmpty() && !formatted.startsWith(activePrefix)) {
            formatted = activePrefix + formatted;
        }

        if (activeSuffix != null && !activeSuffix.isEmpty() && !formatted.endsWith(activeSuffix)) {
            formatted = formatted + activeSuffix;
        }

        return startsWithExcl ? ("!" + formatted) : formatted;
    }

    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent event) {
        if (event.getMessage() != null) {
            onIncomingChatMessage(event.getMessage().getString());
        }
    }

    public static void onIncomingChatMessage(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) {
            return;
        }

        if (autoAuthEnabled && authPassword != null && !authPassword.isEmpty()) {
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.contains("/login") || lower.contains("войдите") || lower.contains("авториз")) {
                String cmd = (authCommand == null || authCommand.trim().isEmpty()) ? "/login" : authCommand.trim();
                if (!cmd.startsWith("/")) {
                    cmd = "/" + cmd;
                }
                player.chat(cmd + " " + authPassword);
                return;
            }
        }

        if (mathEnabled && !text.contains(player.getName().getString())) {
            Double ans = evaluateMath(text);
            if (ans != null) {
                String formatted = (Math.floor(ans) == ans) ? String.valueOf(ans.longValue()) : String.valueOf(ans);
                player.chat(formatted);
            }
        }
    }

    private static Double evaluateMath(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        boolean triggered = false;
        for (String trigger : MATH_TRIGGERS) {
            if (lower.contains(trigger)) {
                triggered = true;
                break;
            }
        }

        if (!triggered && !lower.contains("=") && !lower.matches(".*\\d+\\s*[+\\-*/xх×:]\\s*\\d+.*")) {
            return null;
        }

        int colonIdx = message.indexOf(':');
        String candidate = colonIdx >= 0 ? message.substring(colonIdx + 1) : message;
        candidate = candidate.replaceAll("(?i)(реши|сколько будет|пример|вычисли|посчитай|math|пожалуйста|плиз)", " ");
        int eqIdx = candidate.indexOf('=');
        if (eqIdx >= 0) {
            candidate = candidate.substring(0, eqIdx);
        }
        candidate = candidate.replace('?', ' ')
            .replace('!', ' ')
            .replace('x', '*')
            .replace('х', '*')
            .replace('×', '*')
            .replace(':', '/')
            .trim();

        Matcher m = MATH_PAIR_PATTERN.matcher(candidate);
        if (!m.find()) {
            return null;
        }

        double a = Double.parseDouble(m.group(1));
        String op = m.group(2);
        double b = Double.parseDouble(m.group(3));

        switch (op) {
            case "+":
                return a + b;
            case "-":
                return a - b;
            case "*":
                return a * b;
            case "/":
                return b == 0 ? null : a / b;
            default:
                return null;
        }
    }

    private static void handleCommand(String fullCommand) {
        String[] parts = fullCommand.split("\\s+", 3);
        if (parts.length == 1 || "help".equalsIgnoreCase(parts[1])) {
            sendInfo("§4[VETEMENTS] §fКоманды:");
            sendInfo("§7.vet toggle §f- вкл/выкл обработку чата");
            sendInfo("§7.vet eprefix <текст|none> §f- префикс для сообщений с '!'");
            sendInfo("§7.vet esuffix <текст|none> §f- суффикс для сообщений с '!'");
            sendInfo("§7.vet pprefix <текст|none> §f- префикс для сообщений без '!'");
            sendInfo("§7.vet psuffix <текст|none> §f- суффикс для сообщений без '!'");
            sendInfo("§7.vet style §f- переключить стиль шрифта");
            sendInfo("§7.vet space §f- автопробел в конце");
            sendInfo("§7.vet math §f- авторешение примеров");
            sendInfo("§7.vet auth <on|off|pass <пароль>|cmd </login>>");
            sendInfo("§7.vet status §f- текущие настройки");
            return;
        }

        String sub = parts[1].toLowerCase(Locale.ROOT);
        String arg = parts.length >= 3 ? parts[2] : "";

        switch (sub) {
            case "toggle":
                toggleChat();
                sendInfo("§4[VETEMENTS] §fОбработка чата: " + (chatEnabled ? "§cВКЛ" : "§7ВЫКЛ"));
                break;
            case "eprefix":
                exclPrefix = "none".equalsIgnoreCase(arg) ? "" : arg;
                saveConfig();
                sendInfo("§4[VETEMENTS] §fПрефикс с '!': §c" + (exclPrefix.isEmpty() ? "нет" : exclPrefix));
                break;
            case "esuffix":
                exclSuffix = "none".equalsIgnoreCase(arg) ? "" : arg;
                saveConfig();
                sendInfo("§4[VETEMENTS] §fСуффикс с '!': §c" + (exclSuffix.isEmpty() ? "нет" : exclSuffix));
                break;
            case "pprefix":
                plainPrefix = "none".equalsIgnoreCase(arg) ? "" : arg;
                saveConfig();
                sendInfo("§4[VETEMENTS] §fПрефикс без '!': §c" + (plainPrefix.isEmpty() ? "нет" : plainPrefix));
                break;
            case "psuffix":
                plainSuffix = "none".equalsIgnoreCase(arg) ? "" : arg;
                saveConfig();
                sendInfo("§4[VETEMENTS] §fСуффикс без '!': §c" + (plainSuffix.isEmpty() ? "нет" : plainSuffix));
                break;
            case "style":
                cycleFontStyle();
                sendInfo("§4[VETEMENTS] §fСтиль текста: §c" + getFontStyleName());
                break;
            case "space":
                toggleAutoSpace();
                sendInfo("§4[VETEMENTS] §fАвтопробел: " + (autoSpace ? "§cВКЛ" : "§7ВЫКЛ"));
                break;
            case "math":
                toggleMath();
                sendInfo("§4[VETEMENTS] §fПримеры: " + (mathEnabled ? "§cВКЛ" : "§7ВЫКЛ"));
                break;
            case "auth":
                handleAuthSubcommand(arg);
                break;
            case "status":
                sendInfo("§4[VETEMENTS] §fЧат: " + (chatEnabled ? "§cВКЛ" : "§7ВЫКЛ")
                    + " §7| §fС '!': §c[" + exclPrefix + "] ... [" + exclSuffix + "]"
                    + " §7| §fБез '!': §c[" + plainPrefix + "] ... [" + plainSuffix + "]"
                    + " §7| §fСтиль: §c" + getFontStyleName()
                    + " §7| §fПробел: " + (autoSpace ? "§cВКЛ" : "§7ВЫКЛ")
                    + " §7| §fПримеры: " + (mathEnabled ? "§cВКЛ" : "§7ВЫКЛ")
                    + " §7| §fAuthMe: " + (autoAuthEnabled ? "§cВКЛ" : "§7ВЫКЛ"));
                break;
            default:
                sendInfo("§4[VETEMENTS] §cНеизвестная команда. Напиши §f.vet help");
                break;
        }
    }

    private static void handleAuthSubcommand(String arg) {
        if (arg.isEmpty()) {
            sendInfo("§4[VETEMENTS] §fИспользование: .vet auth <on|off|pass <пароль>|cmd </login>>");
            return;
        }
        if ("on".equalsIgnoreCase(arg)) {
            autoAuthEnabled = true;
            saveConfig();
            sendInfo("§4[VETEMENTS] §fАвто-вход AuthMe: §cВКЛ");
        } else if ("off".equalsIgnoreCase(arg)) {
            autoAuthEnabled = false;
            saveConfig();
            sendInfo("§4[VETEMENTS] §fАвто-вход AuthMe: §7ВЫКЛ");
        } else if (arg.toLowerCase(Locale.ROOT).startsWith("pass ")) {
            authPassword = arg.substring(5).trim();
            saveConfig();
            sendInfo("§4[VETEMENTS] §fПароль AuthMe сохранён.");
        } else if (arg.toLowerCase(Locale.ROOT).startsWith("cmd ")) {
            authCommand = arg.substring(4).trim();
            saveConfig();
            sendInfo("§4[VETEMENTS] §fКоманда AuthMe: §c" + authCommand);
        } else {
            sendInfo("§4[VETEMENTS] §cНеверный аргумент auth.");
        }
    }

    private static void sendInfo(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(new StringTextComponent(message), mc.player.getUUID());
        }
    }

    public static boolean isChatEnabled() { return chatEnabled; }
    public static void toggleChat() { chatEnabled = !chatEnabled; saveConfig(); }

    public static boolean isAutoSpace() { return autoSpace; }
    public static void toggleAutoSpace() { autoSpace = !autoSpace; saveConfig(); }

    public static boolean isMathEnabled() { return mathEnabled; }
    public static void toggleMath() { mathEnabled = !mathEnabled; saveConfig(); }

    public static boolean isAutoAuthEnabled() { return autoAuthEnabled; }
    public static void toggleAutoAuth() { autoAuthEnabled = !autoAuthEnabled; saveConfig(); }

    public static boolean isFullbrightEnabled() { return fullbrightEnabled; }
    public static void toggleFullbright() { fullbrightEnabled = !fullbrightEnabled; saveConfig(); }

    public static boolean isFreeLookEnabled() { return freeLookEnabled; }
    public static void toggleFreeLook() { freeLookEnabled = !freeLookEnabled; saveConfig(); }

    public static String getExclPrefix() { return exclPrefix; }
    public static String getExclSuffix() { return exclSuffix; }
    public static String getPlainPrefix() { return plainPrefix; }
    public static String getPlainSuffix() { return plainSuffix; }

    public static String getFontStyleName() {
        return STYLE_NAMES[fontStyle];
    }

    public static void cycleFontStyle() {
        fontStyle = (fontStyle + 1) % STYLE_NAMES.length;
        saveConfig();
    }

    private enum Category {
        VISUAL("Визуал", "◈"),
        CHAT("Чат", "❖"),
        AUTO("Авто", "✦"),
        TEXT("Текст", "✺");

        final String label;
        final String glyph;

        Category(String label, String glyph) {
            this.label = label;
            this.glyph = glyph;
        }
    }

    private interface ModuleCard {
        Category category();
        String title();
        String description();
        String keywords();
        boolean isSwitch();
        boolean switchState();
        String actionLabel();
        void onTrigger();
    }

    public static class VetementsBloodMenu extends Screen {
        private static final int COL_BACKDROP = 0xA8060304;
        private static final int COL_PANEL_LEFT = 0xF50D080A;
        private static final int COL_PANEL_RIGHT = 0xEE120B0E;
        private static final int COL_DIVIDER = 0xFF281217;
        private static final int COL_BORDER_MAIN = 0xFF8A1626;
        private static final int COL_TAB_ACTIVE = 0xFF260B10;
        private static final int COL_TAB_ACCENT = 0xFFE0243C;
        private static final int COL_CARD = 0xEA180F13;
        private static final int COL_CARD_HOVER = 0xF822141A;
        private static final int COL_SEARCH_BG = 0xFF170C10;
        private static final int COL_SEARCH_FOCUS = 0xFFB81D32;
        private static final int COL_BTN_BG = 0xFF2B1117;
        private static final int COL_BTN_HOVER = 0xFF421620;
        private static final int COL_TEXT_MAIN = 0xFFF3ECEE;
        private static final int COL_TEXT_MUTED = 0xFF968287;
        private static final int COL_BLOOD_TEXT = 0xFFFF2B47;
        private static final int COL_TOGGLE_OFF_BG = 0xFF2A1D21;
        private static final int COL_TOGGLE_OFF_KNOB = 0xFF8C7A7F;
        private static final int COL_TOGGLE_ON_BG = 0xFF6E111E;
        private static final int COL_TOGGLE_ON_KNOB = 0xFFFF2E4A;

        private Category selectedCategory = Category.VISUAL;
        private TextFieldWidget searchBox;
        private final List<ModuleCard> allCards = new ArrayList<>();

        private int winX;
        private int winY;
        private int winW;
        private int winH;
        private int sideW;
        private int scrollOffset = 0;

        public VetementsBloodMenu() {
            super(new StringTextComponent("VETEMENTS"));
            buildCards();
        }

        private void buildCards() {
            allCards.clear();

            allCards.add(new ModuleCard() {
                public Category category() { return Category.VISUAL; }
                public String title() { return "Fullbright"; }
                public String description() { return "Яркость мира (бинд B)"; }
                public String keywords() { return "fullbright gamma яркость свет"; }
                public boolean isSwitch() { return true; }
                public boolean switchState() { return ModCore.isFullbrightEnabled(); }
                public String actionLabel() { return ""; }
                public void onTrigger() { ModCore.toggleFullbright(); }
            });

            allCards.add(new ModuleCard() {
                public Category category() { return Category.VISUAL; }
                public String title() { return "FreeLook"; }
                public String description() { return "Обзор камеры на Left Alt"; }
                public String keywords() { return "freelook камера обзор alt"; }
                public boolean isSwitch() { return true; }
                public boolean switchState() { return ModCore.isFreeLookEnabled(); }
                public String actionLabel() { return ""; }
                public void onTrigger() { ModCore.toggleFreeLook(); }
            });

            allCards.add(new ModuleCard() {
                public Category category() { return Category.CHAT; }
                public String title() { return "Чат-модуль"; }
                public String description() { return "Форматирование сообщений"; }
                public String keywords() { return "чат chat формат"; }
                public boolean isSwitch() { return true; }
                public boolean switchState() { return ModCore.isChatEnabled(); }
                public String actionLabel() { return ""; }
                public void onTrigger() { ModCore.toggleChat(); }
            });

            allCards.add(new ModuleCard() {
                public Category category() { return Category.CHAT; }
                public String title() { return "Автопробел"; }
                public String description() { return "Пробел в конце текста"; }
                public String keywords() { return "автопробел пробел space"; }
                public boolean isSwitch() { return true; }
                public boolean switchState() { return ModCore.isAutoSpace(); }
                public String actionLabel() { return ""; }
                public void onTrigger() { ModCore.toggleAutoSpace(); }
            });

            allCards.add(new ModuleCard() {
                public Category category() { return Category.AUTO; }
                public String title() { return "Авто-примеры"; }
                public String description() { return "Решает примеры в чате"; }
                public String keywords() { return "примеры математика math"; }
                public boolean isSwitch() { return true; }
                public boolean switchState() { return ModCore.isMathEnabled(); }
                public String actionLabel() { return ""; }
                public void onTrigger() { ModCore.toggleMath(); }
            });

            allCards.add(new ModuleCard() {
                public Category category() { return Category.AUTO; }
                public String title() { return "Авто-вход AuthMe"; }
                public String description() { return "Вход по запросу /login"; }
                public String keywords() { return "authme вход login пароль"; }
                public boolean isSwitch() { return true; }
                public boolean switchState() { return ModCore.isAutoAuthEnabled(); }
                public String actionLabel() { return ""; }
                public void onTrigger() { ModCore.toggleAutoAuth(); }
            });

            allCards.add(new ModuleCard() {
                public Category category() { return Category.TEXT; }
                public String title() { return "Префикс '!'"; }
                public String description() { return "Сейчас: " + emptyOrText(ModCore.getExclPrefix()); }
                public String keywords() { return "префикс восклицательный prefix !"; }
                public boolean isSwitch() { return false; }
                public boolean switchState() { return false; }
                public String actionLabel() { return "Задать"; }
                public void onTrigger() { openChatPrompt(".vet eprefix "); }
            });

            allCards.add(new ModuleCard() {
                public Category category() { return Category.TEXT; }
                public String title() { return "Суффикс '!'"; }
                public String description() { return "Сейчас: " + emptyOrText(ModCore.getExclSuffix()); }
                public String keywords() { return "суффикс восклицательный suffix !"; }
                public boolean isSwitch() { return false; }
                public boolean switchState() { return false; }
                public String actionLabel() { return "Задать"; }
                public void onTrigger() { openChatPrompt(".vet esuffix "); }
            });

            allCards.add(new ModuleCard() {
                public Category category() { return Category.TEXT; }
                public String title() { return "Префикс без '!'"; }
                public String description() { return "Сейчас: " + emptyOrText(ModCore.getPlainPrefix()); }
                public String keywords() { return "префикс обычный prefix"; }
                public boolean isSwitch() { return false; }
                public boolean switchState() { return false; }
                public String actionLabel() { return "Задать"; }
                public void onTrigger() { openChatPrompt(".vet pprefix "); }
            });

            allCards.add(new ModuleCard() {
                public Category category() { return Category.TEXT; }
                public String title() { return "Суффикс без '!'"; }
                public String description() { return "Сейчас: " + emptyOrText(ModCore.getPlainSuffix()); }
                public String keywords() { return "суффикс обычный suffix"; }
                public boolean isSwitch() { return false; }
                public boolean switchState() { return false; }
                public String actionLabel() { return "Задать"; }
                public void onTrigger() { openChatPrompt(".vet psuffix "); }
            });

            allCards.add(new ModuleCard() {
                public Category category() { return Category.TEXT; }
                public String title() { return "Стиль шрифта"; }
                public String description() { return "Сейчас: " + ModCore.getFontStyleName(); }
                public String keywords() { return "шрифт стиль font"; }
                public boolean isSwitch() { return false; }
                public boolean switchState() { return false; }
                public String actionLabel() { return "Сменить"; }
                public void onTrigger() { ModCore.cycleFontStyle(); }
            });
        }

        private static String emptyOrText(String value) {
            return (value == null || value.isEmpty()) ? "нет" : value;
        }

        private void openChatPrompt(String prefix) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new ChatScreen(prefix));
            }
        }

        @Override
        protected void init() {
            winW = Math.min(this.width - 32, 620);
            winH = Math.min(this.height - 28, 320);
            if (winW < 360) winW = Math.max(260, this.width - 16);
            if (winH < 200) winH = Math.max(170, this.height - 16);

            winX = (this.width - winW) / 2;
            winY = (this.height - winH) / 2;
            sideW = Math.min(152, winW / 3);

            int searchX = winX + sideW + 14;
            int searchY = winY + 12;
            int searchW = winW - sideW - 28;

            String prevQuery = searchBox != null ? searchBox.getValue() : "";
            searchBox = new TextFieldWidget(this.font, searchX + 6, searchY + 5, Math.max(40, searchW - 12), 14,
                new StringTextComponent("Поиск модулей..."));
            searchBox.setMaxLength(64);
            searchBox.setValue(prevQuery);
            searchBox.setBordered(false);
            searchBox.setTextColor(COL_TEXT_MAIN);
            this.children.add(searchBox);
        }

        private List<ModuleCard> getVisibleCards() {
            String query = searchBox != null ? searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
            List<ModuleCard> result = new ArrayList<>();
            for (ModuleCard card : allCards) {
                boolean matchesSearch = query.isEmpty()
                    || card.title().toLowerCase(Locale.ROOT).contains(query)
                    || card.description().toLowerCase(Locale.ROOT).contains(query)
                    || card.keywords().toLowerCase(Locale.ROOT).contains(query);

                if (!query.isEmpty()) {
                    if (matchesSearch) {
                        result.add(card);
                    }
                } else if (card.category() == selectedCategory) {
                    result.add(card);
                }
            }
            return result;
        }

        private int[] getCardBounds(int index, int rightX, int listTop, int rightW) {
            int cols = rightW >= 340 ? 2 : 1;
            int gap = 10;
            int cardW = (rightW - gap * (cols - 1)) / cols;
            int cardH = 44;
            int col = index % cols;
            int row = index / cols;
            int x1 = rightX + col * (cardW + gap);
            int y1 = listTop + row * (cardH + 8) - scrollOffset;
            return new int[]{x1, y1, x1 + cardW, y1 + cardH};
        }

        private int[] getFooterBounds() {
            int padX = 12;
            int btnH = 22;
            int x1 = winX + padX;
            int y1 = winY + winH - btnH - 12;
            int x2 = winX + sideW - padX;
            int y2 = y1 + btnH;
            return new int[]{x1, y1, x2, y2};
        }

        @Override
        public void render(MatrixStack ms, int mouseX, int mouseY, float partialTicks) {
            fill(ms, 0, 0, this.width, this.height, COL_BACKDROP);

            // Главное окно со скруглёнными углами
            int winR = 8;
            fillRoundedRect(ms, winX, winY, winX + winW, winY + winH, winR, COL_PANEL_RIGHT);
            fillRoundedLeftHalf(ms, winX, winY, winX + sideW, winY + winH, winR, COL_PANEL_LEFT);
            fill(ms, winX + sideW, winY, winX + sideW + 1, winY + winH, COL_DIVIDER);
            renderRoundedOutline(ms, winX, winY, winX + winW, winY + winH, winR, COL_BORDER_MAIN);

            // Логотип слева сверху
            drawCenteredString(ms, this.font, "§c§lVETEMENTS", winX + sideW / 2, winY + 14, COL_BLOOD_TEXT);
            drawCenteredString(ms, this.font, "§8BLOOD", winX + sideW / 2, winY + 26, COL_TEXT_MUTED);

            // Вкладки категорий слева
            Category[] cats = Category.values();
            int tabStartY = winY + 48;
            int tabH = 24;
            for (int i = 0; i < cats.length; i++) {
                Category c = cats[i];
                int ty1 = tabStartY + i * (tabH + 4);
                int ty2 = ty1 + tabH;
                int tx1 = winX + 10;
                int tx2 = winX + sideW - 10;
                boolean active = (c == selectedCategory);
                boolean hovered = mouseX >= tx1 && mouseX <= tx2 && mouseY >= ty1 && mouseY <= ty2;

                if (active) {
                    fillRoundedRect(ms, tx1, ty1, tx2, ty2, 5, COL_TAB_ACTIVE);
                    fill(ms, tx1 + 2, ty1 + 4, tx1 + 4, ty2 - 4, COL_TAB_ACCENT);
                } else if (hovered) {
                    fillRoundedRect(ms, tx1, ty1, tx2, ty2, 5, COL_CARD_HOVER);
                }

                String label = (active ? "§f" : "§7") + c.glyph + "  " + c.label;
                drawString(ms, this.font, label, tx1 + 10, ty1 + (tabH - 8) / 2, active ? COL_TEXT_MAIN : COL_TEXT_MUTED);
            }

            // Нижняя плашка awe
            int[] footer = getFooterBounds();
            fillRoundedRect(ms, footer[0], footer[1], footer[2], footer[3], 5, COL_CARD);
            renderRoundedOutline(ms, footer[0], footer[1], footer[2], footer[3], 5, COL_BORDER_MAIN);
            drawCenteredString(ms, this.font, "§c§lawe", (footer[0] + footer[2]) / 2, footer[1] + 7, COL_BLOOD_TEXT);

            // Строка поиска сверху справа
            int rightX = winX + sideW + 14;
            int searchY = winY + 12;
            int rightW = winW - sideW - 28;
            int searchH = 22;

            fillRoundedRect(ms, rightX, searchY, rightX + rightW, searchY + searchH, 5, COL_SEARCH_BG);
            renderRoundedOutline(ms, rightX, searchY, rightX + rightW, searchY + searchH, 5,
                (searchBox != null && searchBox.isFocused()) ? COL_SEARCH_FOCUS : COL_DIVIDER);

            if (searchBox != null && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
                drawString(ms, this.font, "Поиск модулей...", rightX + 8, searchY + 7, COL_TEXT_MUTED);
            }
            if (searchBox != null) {
                searchBox.render(ms, mouseX, mouseY, partialTicks);
            }

            // Карточки модулей со скруглёнными углами
            List<ModuleCard> visible = getVisibleCards();
            int listTop = searchY + searchH + 12;
            int listBottom = winY + winH - 12;

            for (int i = 0; i < visible.size(); i++) {
                ModuleCard card = visible.get(i);
                int[] b = getCardBounds(i, rightX, listTop, rightW);
                if (b[3] < listTop || b[1] > listBottom) {
                    continue;
                }

                boolean hovered = mouseX >= b[0] && mouseX <= b[2] && mouseY >= b[1] && mouseY <= b[3];
                fillRoundedRect(ms, b[0], b[1], b[2], b[3], 6, hovered ? COL_CARD_HOVER : COL_CARD);
                if (card.isSwitch() && card.switchState()) {
                    fill(ms, b[0] + 2, b[1] + 8, b[0] + 4, b[3] - 8, COL_TAB_ACCENT);
                }

                int textX = b[0] + 10;
                int rightControlArea = card.isSwitch() ? 34 : 54;
                int maxTextW = Math.max(40, (b[2] - textX) - rightControlArea);

                String titleText = trimToWidth(card.title(), maxTextW);
                String descText = trimToWidth(card.description(), maxTextW);

                drawString(ms, this.font, titleText, textX, b[1] + 8, COL_TEXT_MAIN);
                drawString(ms, this.font, descText, textX, b[1] + 22, COL_TEXT_MUTED);

                if (card.isSwitch()) {
                    int swW = 24;
                    int swH = 12;
                    int swX1 = b[2] - swW - 10;
                    int swY1 = b[1] + (b[3] - b[1] - swH) / 2;
                    boolean on = card.switchState();

                    fillRoundedRect(ms, swX1, swY1, swX1 + swW, swY1 + swH, 6, on ? COL_TOGGLE_ON_BG : COL_TOGGLE_OFF_BG);
                    int knobSize = 8;
                    int knobX = on ? (swX1 + swW - knobSize - 2) : (swX1 + 2);
                    fillRoundedRect(ms, knobX, swY1 + 2, knobX + knobSize, swY1 + swH - 2, 4,
                        on ? COL_TOGGLE_ON_KNOB : COL_TOGGLE_OFF_KNOB);
                } else {
                    int btnW = 44;
                    int btnH = 16;
                    int btnX1 = b[2] - btnW - 8;
                    int btnY1 = b[1] + (b[3] - b[1] - btnH) / 2;
                    boolean btnHover = mouseX >= btnX1 && mouseX <= btnX1 + btnW && mouseY >= btnY1 && mouseY <= btnY1 + btnH;

                    fillRoundedRect(ms, btnX1, btnY1, btnX1 + btnW, btnY1 + btnH, 4, btnHover ? COL_BTN_HOVER : COL_BTN_BG);
                    drawCenteredString(ms, this.font, card.actionLabel(), btnX1 + btnW / 2, btnY1 + 4, COL_BLOOD_TEXT);
                }
            }

            super.render(ms, mouseX, mouseY, partialTicks);
        }

        private void fillRoundedRect(MatrixStack ms, int x1, int y1, int x2, int y2, int r, int color) {
            int w = x2 - x1;
            int h = y2 - y1;
            if (w <= 0 || h <= 0) {
                return;
            }
            int rad = Math.min(r, Math.min(w / 2, h / 2));
            if (rad <= 0) {
                fill(ms, x1, y1, x2, y2, color);
                return;
            }

            fill(ms, x1 + rad, y1, x2 - rad, y1 + rad, color);
            fill(ms, x1 + rad, y2 - rad, x2 - rad, y2, color);
            fill(ms, x1, y1 + rad, x2, y2 - rad, color);

            fillCornerFan(ms, x1 + rad, y1 + rad, rad, 180.0F, 270.0F, color);
            fillCornerFan(ms, x2 - rad, y1 + rad, rad, 270.0F, 360.0F, color);
            fillCornerFan(ms, x2 - rad, y2 - rad, rad, 0.0F, 90.0F, color);
            fillCornerFan(ms, x1 + rad, y2 - rad, rad, 90.0F, 180.0F, color);
        }

        private void fillRoundedLeftHalf(MatrixStack ms, int x1, int y1, int x2, int y2, int r, int color) {
            int w = x2 - x1;
            int h = y2 - y1;
            if (w <= 0 || h <= 0) {
                return;
            }
            int rad = Math.min(r, Math.min(w, h / 2));
            if (rad <= 0) {
                fill(ms, x1, y1, x2, y2, color);
                return;
            }

            fill(ms, x1 + rad, y1, x2, y1 + rad, color);
            fill(ms, x1 + rad, y2 - rad, x2, y2, color);
            fill(ms, x1, y1 + rad, x2, y2 - rad, color);

            fillCornerFan(ms, x1 + rad, y1 + rad, rad, 180.0F, 270.0F, color);
            fillCornerFan(ms, x1 + rad, y2 - rad, rad, 90.0F, 180.0F, color);
        }

        private void renderRoundedOutline(MatrixStack ms, int x1, int y1, int x2, int y2, int r, int color) {
            int w = x2 - x1;
            int h = y2 - y1;
            if (w <= 2 || h <= 2) {
                return;
            }
            int rad = Math.min(r, Math.min(w / 2, h / 2));
            if (rad <= 0) {
                fill(ms, x1, y1, x2, y1 + 1, color);
                fill(ms, x1, y2 - 1, x2, y2, color);
                fill(ms, x1, y1, x1 + 1, y2, color);
                fill(ms, x2 - 1, y1, x2, y2, color);
                return;
            }

            fill(ms, x1 + rad, y1, x2 - rad, y1 + 1, color);
            fill(ms, x1 + rad, y2 - 1, x2 - rad, y2, color);
            fill(ms, x1, y1 + rad, x1 + 1, y2 - rad, color);
            fill(ms, x2 - 1, y1 + rad, x2, y2 - rad, color);

            strokeCornerArc(ms, x1 + rad, y1 + rad, rad, 180.0F, 270.0F, color);
            strokeCornerArc(ms, x2 - rad, y1 + rad, rad, 270.0F, 360.0F, color);
            strokeCornerArc(ms, x2 - rad, y2 - rad, rad, 0.0F, 90.0F, color);
            strokeCornerArc(ms, x1 + rad, y2 - rad, rad, 90.0F, 180.0F, color);
        }

        private void fillCornerFan(MatrixStack ms, float cx, float cy, float radius, float startDeg, float endDeg, int color) {
            float a = ((color >> 24) & 0xFF) / 255.0F;
            float r = ((color >> 16) & 0xFF) / 255.0F;
            float g = ((color >> 8) & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;

            int steps = Math.max(6, (int) (radius * 2));
            Matrix4f mat = ms.last().pose();
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.getBuilder();

            RenderSystem.enableBlend();
            RenderSystem.disableTexture();
            RenderSystem.defaultBlendFunc();
            buf.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
            buf.vertex(mat, cx, cy, 0.0F).color(r, g, b, a).endVertex();
            for (int i = 0; i <= steps; i++) {
                double ang = Math.toRadians(startDeg + (endDeg - startDeg) * (i / (double) steps));
                float px = cx + (float) (Math.cos(ang) * radius);
                float py = cy + (float) (Math.sin(ang) * radius);
                buf.vertex(mat, px, py, 0.0F).color(r, g, b, a).endVertex();
            }
            tess.end();
            RenderSystem.enableTexture();
        }

        private void strokeCornerArc(MatrixStack ms, float cx, float cy, float radius, float startDeg, float endDeg, int color) {
            float a = ((color >> 24) & 0xFF) / 255.0F;
            float r = ((color >> 16) & 0xFF) / 255.0F;
            float g = ((color >> 8) & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;

            int steps = Math.max(8, (int) (radius * 2));
            Matrix4f mat = ms.last().pose();
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.getBuilder();

            RenderSystem.enableBlend();
            RenderSystem.disableTexture();
            RenderSystem.defaultBlendFunc();
            RenderSystem.lineWidth(1.0F);
            buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i <= steps; i++) {
                double ang = Math.toRadians(startDeg + (endDeg - startDeg) * (i / (double) steps));
                float px = cx + (float) (Math.cos(ang) * radius);
                float py = cy + (float) (Math.sin(ang) * radius);
                buf.vertex(mat, px, py, 0.0F).color(r, g, b, a).endVertex();
            }
            tess.end();
            RenderSystem.enableTexture();
        }

        private String trimToWidth(String text, int maxWidth) {
            if (text == null || this.font.width(text) <= maxWidth) {
                return text == null ? "" : text;
            }
            String ellipsis = "...";
            int avail = Math.max(0, maxWidth - this.font.width(ellipsis));
            return this.font.plainSubstrByWidth(text, avail) + ellipsis;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return super.mouseClicked(mouseX, mouseY, button);
            }

            int mx = (int) mouseX;
            int my = (int) mouseY;

            if (searchBox != null) {
                searchBox.mouseClicked(mouseX, mouseY, button);
            }

            int[] footer = getFooterBounds();
            if (mx >= footer[0] && mx <= footer[2] && my >= footer[1] && my <= footer[3]) {
                sendInfo("§4[VETEMENTS] §cawe");
                return true;
            }

            Category[] cats = Category.values();
            int tabStartY = winY + 48;
            int tabH = 24;
            for (int i = 0; i < cats.length; i++) {
                int ty1 = tabStartY + i * (tabH + 4);
                int ty2 = ty1 + tabH;
                int tx1 = winX + 10;
                int tx2 = winX + sideW - 10;
                if (mx >= tx1 && mx <= tx2 && my >= ty1 && my <= ty2) {
                    selectedCategory = cats[i];
                    scrollOffset = 0;
                    return true;
                }
            }

            int rightX = winX + sideW + 14;
            int searchY = winY + 12;
            int rightW = winW - sideW - 28;
            int listTop = searchY + 22 + 12;
            int listBottom = winY + winH - 12;

            List<ModuleCard> visible = getVisibleCards();
            for (int i = 0; i < visible.size(); i++) {
                int[] b = getCardBounds(i, rightX, listTop, rightW);
                if (b[3] < listTop || b[1] > listBottom) {
                    continue;
                }
                if (mx >= b[0] && mx <= b[2] && my >= b[1] && my <= b[3]) {
                    visible.get(i).onTrigger();
                    return true;
                }
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            scrollOffset = Math.max(0, scrollOffset - (int) (delta * 18));
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (searchBox != null && searchBox.isFocused()) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    searchBox.setFocus(false);
                    return true;
                }
                if (searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            if (searchBox != null && searchBox.isFocused() && searchBox.charTyped(codePoint, modifiers)) {
                return true;
            }
            return super.charTyped(codePoint, modifiers);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
