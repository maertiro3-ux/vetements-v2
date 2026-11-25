package com.vetements.next;

import com.mojang.blaze3d.matrix.MatrixStack;
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
import net.minecraft.util.math.MathHelper;
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
        private static final int COL_BACKDROP = 0xB8050203;
        private static final int COL_PANEL_LEFT = 0xEE0B0507;
        private static final int COL_PANEL_RIGHT = 0xE210070A;
        private static final int COL_BORDER_DARK = 0xFF3A0A12;
        private static final int COL_BORDER_BLOOD = 0xFFB81D32;
        private static final int COL_BORDER_BRIGHT = 0xFFE62E46;
        private static final int COL_TAB_ACTIVE = 0xFF2B080E;
        private static final int COL_CARD = 0xD814080C;
        private static final int COL_CARD_HOVER = 0xF01E0B12;
        private static final int COL_SEARCH_BG = 0xFF14070B;
        private static final int COL_TEXT_MAIN = 0xFFF5ECEE;
        private static final int COL_TEXT_MUTED = 0xFF9E848A;
        private static final int COL_BLOOD_TEXT = 0xFFFF2A44;
        private static final int COL_TOGGLE_OFF_BG = 0xFF28191D;
        private static final int COL_TOGGLE_OFF_KNOB = 0xFF8C777C;
        private static final int COL_TOGGLE_ON_BG = 0xFF7D0E1E;
        private static final int COL_TOGGLE_ON_KNOB = 0xFFFF334E;

        private Category selectedCategory = Category.VISUAL;
        private TextFieldWidget searchBox;
        private final List<ModuleCard> allCards = new ArrayList<>();

        private int winX;
        private int winY;
        private int winW;
        private int winH;
        private int sideW;

        public VetementsBloodMenu() {
            super(new StringTextComponent("VETEMENTS"));
            buildCards();
        }

        private void buildCards() {
            allCards.clear();

            allCards.add(new ModuleCard() {
                public Category category() { return Category.VISUAL; }
                public String title() { return "Fullbright"; }
                public String description() { return "Максимальная яркость мира (бинд B)"; }
                public String keywords() { return "fullbright gamma яркость свет"; }
                public boolean isSwitch() { return true; }
                public boolean switchState() { return ModCore.isFullbrightEnabled(); }
                public String actionLabel() { return ""; }
                public void onTrigger() { ModCore.toggleFullbright(); }
            });

            allCards.add(new ModuleCard() {
                public Category category() { return Category.VISUAL; }
                public String title() { return "FreeLook"; }
                public String description() { return "Свободный обзор камеры на Left Alt"; }
                public String keywords() { return "freelook камера обзор alt"; }
                public boolean isSwitch() { return true; }
                public boolean switchState() { return ModCore.isFreeLookEnabled(); }
                public String actionLabel() { return ""; }
                public void onTrigger() { ModCore.toggleFreeLook(); }
            });

            allCards.add(new ModuleCard() {
                public Category category() { return Category.CHAT; }
                public String title() { return "Чат-модуль"; }
                public String description() { return "Форматирование исходящих сообщений"; }
                public String keywords() { return "чат chat формат"; }
                public boolean isSwitch() { return true; }
                public boolean switchState() { return ModCore.isChatEnabled(); }
                public String actionLabel() { return ""; }
                public void onTrigger() { ModCore.toggleChat(); }
            });

            allCards.add(new ModuleCard() {
                public Category category() { return Category.CHAT; }
                public String title() { return "Автопробел"; }
                public String description() { return "Добавляет пробел в конце сообщения"; }
                public String keywords() { return "автопробел пробел space"; }
                public boolean isSwitch() { return true; }
                public boolean switchState() { return ModCore.isAutoSpace(); }
                public String actionLabel() { return ""; }
                public void onTrigger() { ModCore.toggleAutoSpace(); }
            });

            allCards.add(new ModuleCard() {
                public Category category() { return Category.AUTO; }
                public String title() { return "Авто-примеры"; }
                public String description() { return "Автоматически решает примеры в чате"; }
                public String keywords() { return "примеры математика math"; }
                public boolean isSwitch() { return true; }
                public boolean switchState() { return ModCore.isMathEnabled(); }
                public String actionLabel() { return ""; }
                public void onTrigger() { ModCore.toggleMath(); }
            });

            allCards.add(new ModuleCard() {
                public Category category() { return Category.AUTO; }
                public String title() { return "Авто-вход AuthMe"; }
                public String description() { return "Автоматический вход при запросе /login"; }
                public String keywords() { return "authme вход login пароль"; }
                public boolean isSwitch() { return true; }
                public boolean switchState() { return ModCore.isAutoAuthEnabled(); }
                public String actionLabel() { return ""; }
                public void onTrigger() { ModCore.toggleAutoAuth(); }
            });

            allCards.add(new ModuleCard() {
                public Category category() { return Category.TEXT; }
                public String title() { return "Префикс '!'"; }
                public String description() { return "Текущий: " + emptyOrText(ModCore.getExclPrefix()); }
                public String keywords() { return "префикс восклицательный prefix !"; }
                public boolean isSwitch() { return false; }
                public boolean switchState() { return false; }
                public String actionLabel() { return "Задать"; }
                public void onTrigger() { openChatPrompt(".vet eprefix "); }
            });

            allCards.add(new ModuleCard() {
                public Category category() { return Category.TEXT; }
                public String title() { return "Суффикс '!'"; }
                public String description() { return "Текущий: " + emptyOrText(ModCore.getExclSuffix()); }
                public String keywords() { return "суффикс восклицательный suffix !"; }
                public boolean isSwitch() { return false; }
                public boolean switchState() { return false; }
                public String actionLabel() { return "Задать"; }
                public void onTrigger() { openChatPrompt(".vet esuffix "); }
            });

            allCards.add(new ModuleCard() {
                public Category category() { return Category.TEXT; }
                public String title() { return "Префикс без '!'"; }
                public String description() { return "Текущий: " + emptyOrText(ModCore.getPlainPrefix()); }
                public String keywords() { return "префикс обычный prefix"; }
                public boolean isSwitch() { return false; }
                public boolean switchState() { return false; }
                public String actionLabel() { return "Задать"; }
                public void onTrigger() { openChatPrompt(".vet pprefix "); }
            });

            allCards.add(new ModuleCard() {
                public Category category() { return Category.TEXT; }
                public String title() { return "Суффикс без '!'"; }
                public String description() { return "Текущий: " + emptyOrText(ModCore.getPlainSuffix()); }
                public String keywords() { return "суффикс обычный suffix"; }
                public boolean isSwitch() { return false; }
                public boolean switchState() { return false; }
                public String actionLabel() { return "Задать"; }
                public void onTrigger() { openChatPrompt(".vet psuffix "); }
            });

            allCards.add(new ModuleCard() {
                public Category category() { return Category.TEXT; }
                public String title() { return "Стиль шрифта"; }
                public String description() { return "Текущий: " + ModCore.getFontStyleName(); }
                public String keywords() { return "шрифт стиль font"; }
                public boolean isSwitch() { return false; }
                public boolean switchState() { return false; }
                public String actionLabel() { return "Сменить"; }
                public void onTrigger() { ModCore.cycleFontStyle(); }
            });
        }

        @Override
        protected void init() {
            winW = Math.min(this.width - 32, 620);
            winH = Math.min(this.height - 28, 320);
            if (winW < 360) winW = Math.max(260, this.width - 16);
            if (winH < 200) winH = Math.max(170, this.height - 16);

            winX = (this.width - winW) / 2;
            winY = (this.height - winH) / 2;
            sideW = Math.min(155, winW / 3);

            int searchX = winX + sideW + 16;
            int searchY = winY + 14;
            int searchW = winW - sideW - 32;

            String prevQuery = searchBox != null ? searchBox.getValue() : "";
            searchBox = new TextFieldWidget(this.font, searchX, searchY, searchW, 20,
                new StringTextComponent("Поиск модулей..."));
            searchBox.setMaxLength(64);
            searchBox.setValue(prevQuery);
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

        private void openChatPrompt(String prefixCommand) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(null);
            if (mc.player != null) {
                mc.player.sendMessage(
                    new StringTextComponent("§4[VETEMENTS] §fВведи значение (или §7none§f чтобы убрать): §c" + prefixCommand),
                    mc.player.getUUID()
                );
                mc.setScreen(new ChatScreen(prefixCommand));
            }
        }

        private String emptyOrText(String val) {
            return (val == null || val.isEmpty()) ? "нет" : val;
        }

        @Override
        public void render(MatrixStack ms, int mouseX, int mouseY, float partialTicks) {
            fill(ms, 0, 0, this.width, this.height, COL_BACKDROP);

            fill(ms, winX, winY, winX + winW, winY + winH, COL_PANEL_RIGHT);
            fill(ms, winX, winY, winX + sideW, winY + winH, COL_PANEL_LEFT);
            drawHollowRect(ms, winX, winY, winX + winW, winY + winH, COL_BORDER_BLOOD);
            vLine(ms, winX + sideW, winY, winY + winH, COL_BORDER_DARK);

            int titleY = winY + 18;
            drawCenteredString(ms, this.font, "§4§lV E T E M E N T S", winX + sideW / 2, titleY, COL_BLOOD_TEXT);
            drawCenteredString(ms, this.font, "§8BLOOD EDITION", winX + sideW / 2, titleY + 11, COL_TEXT_MUTED);

            Category[] cats = Category.values();
            int tabStartY = winY + 48;
            int tabH = 24;
            int tabGap = 4;
            for (int i = 0; i < cats.length; i++) {
                Category cat = cats[i];
                int ty = tabStartY + i * (tabH + tabGap);
                int tx1 = winX + 10;
                int tx2 = winX + sideW - 10;
                boolean active = (cat == selectedCategory) && (searchBox == null || searchBox.getValue().trim().isEmpty());
                boolean hovered = mouseX >= tx1 && mouseX <= tx2 && mouseY >= ty && mouseY <= ty + tabH;

                if (active) {
                    fill(ms, tx1, ty, tx2, ty + tabH, COL_TAB_ACTIVE);
                    drawHollowRect(ms, tx1, ty, tx2, ty + tabH, COL_BORDER_BLOOD);
                    fill(ms, tx1, ty, tx1 + 3, ty + tabH, COL_BORDER_BRIGHT);
                } else if (hovered) {
                    fill(ms, tx1, ty, tx2, ty + tabH, COL_CARD);
                    drawHollowRect(ms, tx1, ty, tx2, ty + tabH, COL_BORDER_DARK);
                }

                String labelText = (active ? "§c§l" : (hovered ? "§f" : "§7")) + cat.glyph + "  " + cat.label;
                drawString(ms, this.font, labelText, tx1 + 10, ty + (tabH - 8) / 2, COL_TEXT_MAIN);
            }

            int footerH = 22;
            int fx1 = winX + 10;
            int fx2 = winX + sideW - 10;
            int fy1 = winY + winH - footerH - 10;
            int fy2 = winY + winH - 10;
            boolean footerHover = mouseX >= fx1 && mouseX <= fx2 && mouseY >= fy1 && mouseY <= fy2;
            fill(ms, fx1, fy1, fx2, fy2, footerHover ? COL_TAB_ACTIVE : COL_CARD);
            drawHollowRect(ms, fx1, fy1, fx2, fy2, footerHover ? COL_BORDER_BRIGHT : COL_BORDER_BLOOD);
            drawCenteredString(ms, this.font, "§4§lawe", (fx1 + fx2) / 2, fy1 + (footerH - 8) / 2, COL_BLOOD_TEXT);

            int sbX1 = searchBox.x - 4;
            int sbY1 = searchBox.y - 2;
            int sbX2 = searchBox.x + searchBox.getWidth() + 4;
            int sbY2 = searchBox.y + searchBox.getHeight() + 2;
            fill(ms, sbX1, sbY1, sbX2, sbY2, COL_SEARCH_BG);
            drawHollowRect(ms, sbX1, sbY1, sbX2, sbY2, searchBox.isFocused() ? COL_BORDER_BRIGHT : COL_BORDER_DARK);

            if (searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
                drawString(ms, this.font, "§7⌕ Поиск модулей...", searchBox.x + 2, searchBox.y + 6, COL_TEXT_MUTED);
            }
            searchBox.render(ms, mouseX, mouseY, partialTicks);

            List<ModuleCard> cards = getVisibleCards();
            int areaX = winX + sideW + 16;
            int areaY = sbY2 + 12;
            int areaW = winW - sideW - 32;
            int areaH = winY + winH - areaY - 12;

            int cols = areaW >= 320 ? 2 : 1;
            int gapX = 10;
            int gapY = 10;
            int cardW = (areaW - gapX * (cols - 1)) / cols;
            int cardH = 44;

            for (int i = 0; i < cards.size(); i++) {
                int col = i % cols;
                int row = i / cols;
                int cx1 = areaX + col * (cardW + gapX);
                int cy1 = areaY + row * (cardH + gapY);
                int cx2 = cx1 + cardW;
                int cy2 = cy1 + cardH;

                if (cy2 > winY + winH - 8) {
                    break;
                }

                ModuleCard card = cards.get(i);
                boolean hovered = mouseX >= cx1 && mouseX <= cx2 && mouseY >= cy1 && mouseY <= cy2;
                boolean activeState = card.isSwitch() && card.switchState();

                fill(ms, cx1, cy1, cx2, cy2, hovered ? COL_CARD_HOVER : COL_CARD);
                drawHollowRect(ms, cx1, cy1, cx2, cy2,
                    activeState ? COL_BORDER_BLOOD : (hovered ? COL_BORDER_BLOOD : COL_BORDER_DARK));

                int badgeSize = 22;
                int bx1 = cx1 + 8;
                int by1 = cy1 + (cardH - badgeSize) / 2;
                fill(ms, bx1, by1, bx1 + badgeSize, by1 + badgeSize, COL_TAB_ACTIVE);
                drawHollowRect(ms, bx1, by1, bx1 + badgeSize, by1 + badgeSize,
                    activeState ? COL_BORDER_BRIGHT : COL_BORDER_DARK);
                drawCenteredString(ms, this.font, activeState ? "§c" + card.category().glyph : "§7" + card.category().glyph,
                    bx1 + badgeSize / 2, by1 + (badgeSize - 8) / 2, COL_TEXT_MAIN);

                int textX = bx1 + badgeSize + 8;
                int rightReserve = card.isSwitch() ? 38 : (this.font.width(card.actionLabel()) + 18);
                String titleStr = trimToWidth(card.title(), Math.max(40, cardW - (textX - cx1) - rightReserve));
                String descStr = trimToWidth(card.description(), Math.max(40, cardW - (textX - cx1) - rightReserve));

                drawString(ms, this.font, "§f" + titleStr, textX, cy1 + 9, COL_TEXT_MAIN);
                drawString(ms, this.font, "§7" + descStr, textX, cy1 + 22, COL_TEXT_MUTED);

                if (card.isSwitch()) {
                    int swW = 24;
                    int swH = 12;
                    int swX1 = cx2 - swW - 10;
                    int swY1 = cy1 + (cardH - swH) / 2;
                    int swX2 = swX1 + swW;
                    int swY2 = swY1 + swH;

                    fill(ms, swX1, swY1, swX2, swY2, activeState ? COL_TOGGLE_ON_BG : COL_TOGGLE_OFF_BG);
                    drawHollowRect(ms, swX1, swY1, swX2, swY2, activeState ? COL_BORDER_BRIGHT : COL_BORDER_DARK);

                    int knobSize = 8;
                    int knobX = activeState ? (swX2 - knobSize - 2) : (swX1 + 2);
                    int knobY = swY1 + 2;
                    fill(ms, knobX, knobY, knobX + knobSize, knobY + knobSize,
                        activeState ? COL_TOGGLE_ON_KNOB : COL_TOGGLE_OFF_KNOB);
                } else {
                    String act = card.actionLabel();
                    int bw = this.font.width(act) + 10;
                    int bh = 16;
                    int bxAction1 = cx2 - bw - 8;
                    int byAction1 = cy1 + (cardH - bh) / 2;
                    int bxAction2 = bxAction1 + bw;
                    int byAction2 = byAction1 + bh;
                    boolean actHover = mouseX >= bxAction1 && mouseX <= bxAction2 && mouseY >= byAction1 && mouseY <= byAction2;

                    fill(ms, bxAction1, byAction1, bxAction2, byAction2, actHover ? COL_TOGGLE_ON_BG : COL_TAB_ACTIVE);
                    drawHollowRect(ms, bxAction1, byAction1, bxAction2, byAction2, actHover ? COL_BORDER_BRIGHT : COL_BORDER_BLOOD);
                    drawCenteredString(ms, this.font, "§c" + act, (bxAction1 + bxAction2) / 2, byAction1 + (bh - 8) / 2, COL_BLOOD_TEXT);
                }
            }

            if (cards.isEmpty()) {
                drawCenteredString(ms, this.font, "§7Модули не найдены", areaX + areaW / 2, areaY + Math.max(20, areaH / 2 - 4), COL_TEXT_MUTED);
            }
        }

        private String trimToWidth(String text, int maxWidth) {
            if (text == null) return "";
            if (this.font.width(text) <= maxWidth) return text;
            String ellipsis = "...";
            int avail = Math.max(0, maxWidth - this.font.width(ellipsis));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (this.font.width(sb.toString() + c) > avail) {
                    break;
                }
                sb.append(c);
            }
            return sb.append(ellipsis).toString();
        }

        private void drawHollowRect(MatrixStack ms, int x1, int y1, int x2, int y2, int color) {
            hLine(ms, x1, x2, y1, color);
            hLine(ms, x1, x2, y2, color);
            vLine(ms, x1, y1, y2, color);
            vLine(ms, x2, y1, y2, color);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return super.mouseClicked(mouseX, mouseY, button);
            }

            int mx = (int) mouseX;
            int my = (int) mouseY;

            if (searchBox != null && searchBox.mouseClicked(mouseX, mouseY, button)) {
                this.setFocused(searchBox);
                searchBox.setFocus(true);
                return true;
            } else if (searchBox != null) {
                searchBox.setFocus(false);
            }

            Category[] cats = Category.values();
            int tabStartY = winY + 48;
            int tabH = 24;
            int tabGap = 4;
            for (int i = 0; i < cats.length; i++) {
                int ty = tabStartY + i * (tabH + tabGap);
                int tx1 = winX + 10;
                int tx2 = winX + sideW - 10;
                if (mx >= tx1 && mx <= tx2 && my >= ty && my <= ty + tabH) {
                    selectedCategory = cats[i];
                    if (searchBox != null) {
                        searchBox.setValue("");
                    }
                    return true;
                }
            }

            int footerH = 22;
            int fx1 = winX + 10;
            int fx2 = winX + sideW - 10;
            int fy1 = winY + winH - footerH - 10;
            int fy2 = winY + winH - 10;
            if (mx >= fx1 && mx <= fx2 && my >= fy1 && my <= fy2) {
                sendInfo("§4[VETEMENTS] §c§lawe");
                return true;
            }

            int sbY2 = searchBox.y + searchBox.getHeight() + 2;
            List<ModuleCard> cards = getVisibleCards();
            int areaX = winX + sideW + 16;
            int areaY = sbY2 + 12;
            int areaW = winW - sideW - 32;

            int cols = areaW >= 320 ? 2 : 1;
            int gapX = 10;
            int gapY = 10;
            int cardW = (areaW - gapX * (cols - 1)) / cols;
            int cardH = 44;

            for (int i = 0; i < cards.size(); i++) {
                int col = i % cols;
                int row = i / cols;
                int cx1 = areaX + col * (cardW + gapX);
                int cy1 = areaY + row * (cardH + gapY);
                int cx2 = cx1 + cardW;
                int cy2 = cy1 + cardH;

                if (cy2 > winY + winH - 8) {
                    break;
                }

                if (mx >= cx1 && mx <= cx2 && my >= cy1 && my <= cy2) {
                    cards.get(i).onTrigger();
                    return true;
                }
            }

            return super.mouseClicked(mouseX, mouseY, button);
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
