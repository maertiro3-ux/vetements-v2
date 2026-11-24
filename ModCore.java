package com.vetements.next;

import com.mojang.blaze3d.matrix.MatrixStack;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
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
    private static final File CONFIG_FILE = new File("config/vetements-next.properties");

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
        Pattern.compile("(-?\d+(?:\.\d+)?)\s*([+\-*/])\s*(-?\d+(?:\.\d+)?)");

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
                PROPS.store(writer, "Vetements Next Config");
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
                mc.setScreen(new VetementsNextMenu());
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                rshiftWasDown = false;
            }
        }

        if (event.getKey() == GLFW.GLFW_KEY_B) {
            if (event.getAction() == GLFW.GLFW_PRESS && !bWasDown) {
                bWasDown = true;
                toggleFullbright();
                sendInfo("§d[Vetements Next] §fFullbright: " + (fullbrightEnabled ? "§aВКЛ" : "§cВЫКЛ"));
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

        if (!triggered && !lower.contains("=") && !lower.matches(".*\d+\s*[+\-*/xх×:]\s*\d+.*")) {
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
        String[] parts = fullCommand.split("\s+", 3);
        if (parts.length == 1 || "help".equalsIgnoreCase(parts[1])) {
            sendInfo("§d[Vetements Next] §fКоманды:");
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
                sendInfo("§d[Vetements Next] §fОбработка чата: " + (chatEnabled ? "§aВКЛ" : "§cВЫКЛ"));
                break;
            case "eprefix":
                exclPrefix = "none".equalsIgnoreCase(arg) ? "" : arg;
                saveConfig();
                sendInfo("§d[Vetements Next] §fПрефикс с '!': §e" + (exclPrefix.isEmpty() ? "нет" : exclPrefix));
                break;
            case "esuffix":
                exclSuffix = "none".equalsIgnoreCase(arg) ? "" : arg;
                saveConfig();
                sendInfo("§d[Vetements Next] §fСуффикс с '!': §e" + (exclSuffix.isEmpty() ? "нет" : exclSuffix));
                break;
            case "pprefix":
                plainPrefix = "none".equalsIgnoreCase(arg) ? "" : arg;
                saveConfig();
                sendInfo("§d[Vetements Next] §fПрефикс без '!': §e" + (plainPrefix.isEmpty() ? "нет" : plainPrefix));
                break;
            case "psuffix":
                plainSuffix = "none".equalsIgnoreCase(arg) ? "" : arg;
                saveConfig();
                sendInfo("§d[Vetements Next] §fСуффикс без '!': §e" + (plainSuffix.isEmpty() ? "нет" : plainSuffix));
                break;
            case "style":
                cycleFontStyle();
                sendInfo("§d[Vetements Next] §fСтиль текста: §e" + getFontStyleName());
                break;
            case "space":
                toggleAutoSpace();
                sendInfo("§d[Vetements Next] §fАвтопробел: " + (autoSpace ? "§aВКЛ" : "§cВЫКЛ"));
                break;
            case "math":
                toggleMath();
                sendInfo("§d[Vetements Next] §fПримеры: " + (mathEnabled ? "§aВКЛ" : "§cВЫКЛ"));
                break;
            case "auth":
                handleAuthSubcommand(arg);
                break;
            case "status":
                sendInfo("§d[Vetements Next] §fЧат: " + (chatEnabled ? "§aВКЛ" : "§cВЫКЛ")
                    + " §7| §fС '!': §e[" + exclPrefix + "] ... [" + exclSuffix + "]"
                    + " §7| §fБез '!': §b[" + plainPrefix + "] ... [" + plainSuffix + "]"
                    + " §7| §fСтиль: §d" + getFontStyleName()
                    + " §7| §fПробел: " + (autoSpace ? "§aВКЛ" : "§cВЫКЛ")
                    + " §7| §fПримеры: " + (mathEnabled ? "§aВКЛ" : "§cВЫКЛ")
                    + " §7| §fAuthMe: " + (autoAuthEnabled ? "§aВКЛ" : "§cВЫКЛ"));
                break;
            default:
                sendInfo("§d[Vetements Next] §cНеизвестная команда. Напиши §f.vet help");
                break;
        }
    }

    private static void handleAuthSubcommand(String arg) {
        if (arg.isEmpty()) {
            sendInfo("§d[Vetements Next] §fИспользование: .vet auth <on|off|pass <пароль>|cmd </login>>");
            return;
        }
        if ("on".equalsIgnoreCase(arg)) {
            autoAuthEnabled = true;
            saveConfig();
            sendInfo("§d[Vetements Next] §fАвто-вход AuthMe: §aВКЛ");
        } else if ("off".equalsIgnoreCase(arg)) {
            autoAuthEnabled = false;
            saveConfig();
            sendInfo("§d[Vetements Next] §fАвто-вход AuthMe: §cВЫКЛ");
        } else if (arg.toLowerCase(Locale.ROOT).startsWith("pass ")) {
            authPassword = arg.substring(5).trim();
            saveConfig();
            sendInfo("§d[Vetements Next] §fПароль AuthMe сохранён.");
        } else if (arg.toLowerCase(Locale.ROOT).startsWith("cmd ")) {
            authCommand = arg.substring(4).trim();
            saveConfig();
            sendInfo("§d[Vetements Next] §fКоманда AuthMe: §e" + authCommand);
        } else {
            sendInfo("§d[Vetements Next] §cНеверный аргумент auth.");
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

    public static class VetementsNextMenu extends Screen {
        public VetementsNextMenu() {
            super(new StringTextComponent("Меню Vetements Next"));
        }

        @Override
        protected void init() {
            int colWidth = 156;
            int btnHeight = 20;
            int gap = 6;
            int leftX = this.width / 2 - colWidth - gap / 2;
            int rightX = this.width / 2 + gap / 2;
            int startY = this.height / 2 - 68;

            addButton(new Button(leftX, startY, colWidth, btnHeight,
                toggleLabel("Fullbright", ModCore.isFullbrightEnabled()), btn -> {
                    ModCore.toggleFullbright();
                    btn.setMessage(toggleLabel("Fullbright", ModCore.isFullbrightEnabled()));
                }));

            addButton(new Button(rightX, startY, colWidth, btnHeight,
                toggleLabel("FreeLook", ModCore.isFreeLookEnabled()), btn -> {
                    ModCore.toggleFreeLook();
                    btn.setMessage(toggleLabel("FreeLook", ModCore.isFreeLookEnabled()));
                }));

            addButton(new Button(leftX, startY + 26, colWidth, btnHeight,
                toggleLabel("Чат-модуль", ModCore.isChatEnabled()), btn -> {
                    ModCore.toggleChat();
                    btn.setMessage(toggleLabel("Чат-модуль", ModCore.isChatEnabled()));
                }));

            addButton(new Button(rightX, startY + 26, colWidth, btnHeight,
                toggleLabel("Автопробел", ModCore.isAutoSpace()), btn -> {
                    ModCore.toggleAutoSpace();
                    btn.setMessage(toggleLabel("Автопробел", ModCore.isAutoSpace()));
                }));

            addButton(new Button(leftX, startY + 52, colWidth, btnHeight,
                toggleLabel("Авто-примеры", ModCore.isMathEnabled()), btn -> {
                    ModCore.toggleMath();
                    btn.setMessage(toggleLabel("Авто-примеры", ModCore.isMathEnabled()));
                }));

            addButton(new Button(rightX, startY + 52, colWidth, btnHeight,
                toggleLabel("Авто-вход AuthMe", ModCore.isAutoAuthEnabled()), btn -> {
                    ModCore.toggleAutoAuth();
                    btn.setMessage(toggleLabel("Авто-вход AuthMe", ModCore.isAutoAuthEnabled()));
                }));

            addButton(new Button(leftX, startY + 78, colWidth, btnHeight,
                new StringTextComponent("Префикс '!': §e" + emptyOrText(ModCore.getExclPrefix())),
                btn -> openChatPrompt(".vet eprefix ")));

            addButton(new Button(rightX, startY + 78, colWidth, btnHeight,
                new StringTextComponent("Суффикс '!': §e" + emptyOrText(ModCore.getExclSuffix())),
                btn -> openChatPrompt(".vet esuffix ")));

            addButton(new Button(leftX, startY + 104, colWidth, btnHeight,
                new StringTextComponent("Префикс без '!': §b" + emptyOrText(ModCore.getPlainPrefix())),
                btn -> openChatPrompt(".vet pprefix ")));

            addButton(new Button(rightX, startY + 104, colWidth, btnHeight,
                new StringTextComponent("Суффикс без '!': §b" + emptyOrText(ModCore.getPlainSuffix())),
                btn -> openChatPrompt(".vet psuffix ")));

            addButton(new Button(leftX, startY + 130, colWidth * 2 + gap, btnHeight,
                new StringTextComponent("Стиль шрифта: §d" + ModCore.getFontStyleName()),
                btn -> {
                    ModCore.cycleFontStyle();
                    btn.setMessage(new StringTextComponent("Стиль шрифта: §d" + ModCore.getFontStyleName()));
                }));
        }

        private void openChatPrompt(String prefixCommand) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(null);
            if (mc.player != null) {
                mc.player.sendMessage(
                    new StringTextComponent("§d[Vetements Next] §fВведи текст на русском/английском (или §7none§f чтобы убрать): §e" + prefixCommand),
                    mc.player.getUUID()
                );
                mc.setScreen(new ChatScreen(prefixCommand));
            }
        }

        private StringTextComponent toggleLabel(String name, boolean state) {
            return new StringTextComponent(name + ": " + (state ? "§aВКЛ" : "§cВЫКЛ"));
        }

        private String emptyOrText(String val) {
            return (val == null || val.isEmpty()) ? "нет" : val;
        }

        @Override
        public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
            this.renderBackground(matrixStack);
            drawCenteredString(matrixStack, this.font, "§dVETEMENTS NEXT §f— Меню настроек (Правый Shift)", this.width / 2, this.height / 2 - 88, 0xFFFFFF);
            super.render(matrixStack, mouseX, mouseY, partialTicks);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
