package com.vetements.next;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModCore {

    public static boolean chatFormatEnabled = true;
    public static boolean autoMathEnabled = true;
    public static boolean copyChatEnabled = true;
    public static boolean gameConsoleEnabled = true;

    private static final List<String> CONSOLE_LOGS = new LinkedList<>();
    private static final Pattern MATH_PATTERN = Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/xX×])\\s*(-?\\d+(?:\\.\\d+)?)");

    private static String pendingMathAnswer = null;
    private static int mathDelayTicks = 0;
    private static int mathCooldownTicks = 0;

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new ModCore());
    }

    public static List<String> getConsoleLogs() {
        return Collections.unmodifiableList(CONSOLE_LOGS);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (event.getKey() != GLFW.GLFW_KEY_RIGHT_SHIFT) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (mc.screen instanceof VetementsBloodMenu) {
            mc.setScreen(null);
            return;
        }
        if (mc.screen == null) {
            mc.setScreen(new VetementsBloodMenu(null));
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (mathCooldownTicks > 0) {
            mathCooldownTicks--;
        }
        if (pendingMathAnswer != null) {
            if (mathDelayTicks > 0) {
                mathDelayTicks--;
            } else {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && autoMathEnabled) {
                    mc.player.chat(pendingMathAnswer);
                    mathCooldownTicks = 60;
                }
                pendingMathAnswer = null;
            }
        }
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        String raw = event.getMessage().getString();
        if (raw == null || raw.isEmpty()) {
            return;
        }
        String clean = stripFormatting(raw);
        String lower = clean.toLowerCase(Locale.ROOT);

        if (gameConsoleEnabled && isConsoleMessage(lower)) {
            if (CONSOLE_LOGS.size() >= 100) {
                CONSOLE_LOGS.remove(0);
            }
            CONSOLE_LOGS.add(clean);
        }

        if (autoMathEnabled && pendingMathAnswer == null && mathCooldownTicks <= 0) {
            String solved = trySolveMath(clean);
            if (solved != null) {
                pendingMathAnswer = solved;
                mathDelayTicks = 12;
            }
        }

        if (chatFormatEnabled) {
            Minecraft mc = Minecraft.getInstance();
            String playerName = (mc.player != null) ? mc.player.getName().getString() : "";
            String formatted = TextFormatting.DARK_RED + "» " + TextFormatting.GRAY + clean;
            if (!playerName.isEmpty() && clean.contains(playerName)) {
                formatted = formatted.replace(playerName,
                        TextFormatting.RED + "" + TextFormatting.BOLD + playerName + TextFormatting.RESET + TextFormatting.GRAY);
            }
            event.setMessage(new StringTextComponent(formatted));
        }
    }

    @SubscribeEvent
    public void onChatMouseClick(GuiScreenEvent.MouseClickedEvent.Pre event) {
        if (!copyChatEnabled) {
            return;
        }
        if (!(event.getGui() instanceof ChatScreen)) {
            return;
        }
        if (event.getButton() != 1) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui == null) {
            return;
        }

        String lastLine = getLastChatLine(mc);
        if (lastLine != null && !lastLine.isEmpty()) {
            mc.keyboardHandler.setClipboard(lastLine);
            mc.player.displayClientMessage(
                    new StringTextComponent(TextFormatting.DARK_RED + "[VETEMENTS] " + TextFormatting.GRAY + "Скопировано: " + TextFormatting.WHITE + lastLine),
                    true
            );
            event.setCanceled(true);
        }
    }

    private static String getLastChatLine(Minecraft mc) {
        try {
            List<?> messages = mc.gui.getChat().getMessages();
            if (messages != null && !messages.isEmpty()) {
                Object last = messages.get(0);
                if (last != null) {
                    String text = last.toString();
                    int idx = text.indexOf("text='");
                    if (idx != -1) {
                        int end = text.indexOf('\'', idx + 6);
                        if (end != -1) {
                            return stripFormatting(text.substring(idx + 6, end));
                        }
                    }
                    return stripFormatting(text);
                }
            }
        } catch (Throwable ignored) {
        }
        return CONSOLE_LOGS.isEmpty() ? null : CONSOLE_LOGS.get(CONSOLE_LOGS.size() - 1);
    }

    private static boolean isConsoleMessage(String lower) {
        return lower.contains("[gameconsole]") || lower.contains("gameconsole") || lower.contains("[console]");
    }

    private static String trySolveMath(String text) {
        if (text.startsWith("»") || text.startsWith("[VETEMENTS]")) {
            return null;
        }
        Matcher m = MATH_PATTERN.matcher(text);
        if (!m.find()) {
            return null;
        }
        try {
            double a = Double.parseDouble(m.group(1));
            String op = m.group(2);
            double b = Double.parseDouble(m.group(3));
            double res;
            switch (op) {
                case "+":
                    res = a + b;
                    break;
                case "-":
                    res = a - b;
                    break;
                case "*":
                case "x":
                case "X":
                case "×":
                    res = a * b;
                    break;
                case "/":
                    if (b == 0.0) return null;
                    res = a / b;
                    break;
                default:
                    return null;
            }
            if (Math.abs(res - Math.rint(res)) < 1e-9) {
                return Long.toString(Math.round(res));
            }
            return String.format(Locale.ROOT, "%.2f", res);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String stripFormatting(String s) {
        return s.replaceAll("§.", "");
    }

    public static class VetementsBloodMenu extends Screen {
        private static final int COL_BACKDROP        = 0x7A050406;
        private static final int COL_MAIN_BG         = 0xEB0E0D10;
        private static final int COL_SIDEBAR_BG      = 0xF209080A;
        private static final int COL_CARD_BG         = 0xCC141216;
        private static final int COL_CARD_HOVER      = 0xE61B171D;
        private static final int COL_ICON_BG         = 0xFF0B0A0D;

        private static final int COL_BORDER          = 0xFF281B20;
        private static final int COL_BORDER_HOVER    = 0xFF5A1D28;
        private static final int COL_BLOOD_ACCENT    = 0xFFB81D33;
        private static final int COL_BLOOD_BRIGHT    = 0xFFE02842;
        private static final int COL_BLOOD_DARK      = 0xFF3A0D15;

        private static final int COL_TAB_ACTIVE_TOP  = 0xFF2B1218;
        private static final int COL_TAB_ACTIVE_BOT  = 0xFF170D11;
        private static final int COL_TAB_HOVER       = 0xAA1A1418;

        private static final int COL_TEXT_MAIN       = 0xFFF4EFEF;
        private static final int COL_TEXT_SUB        = 0xFF9A8E92;
        private static final int COL_TEXT_DIM        = 0xFF685D61;

        private static final int COL_TOGGLE_OFF      = 0xFF221D21;
        private static final int COL_KNOB            = 0xFFF7F2F3;

        private final Screen parent;
        private final List<String> categories = Arrays.asList("Чат", "Визуал", "Доп.", "Читы", "Прочее");
        private int selectedCategory = 0;

        private String searchQuery = "";
        private boolean searchFocused = false;
        private int tickCounter = 0;

        private final Map<String, List<ModuleItem>> modulesByCategory = new LinkedHashMap<>();
        private final List<RectF> categoryRects = new ArrayList<>();
        private final List<RectF> moduleRects = new ArrayList<>();
        private RectF windowRect = new RectF(0, 0, 0, 0);
        private RectF searchRect = new RectF(0, 0, 0, 0);
        private RectF footerRect = new RectF(0, 0, 0, 0);

        public VetementsBloodMenu(Screen parent) {
            super(new StringTextComponent("VETEMENTS"));
            this.parent = parent;
            initModules();
        }

        private void initModules() {
            modulesByCategory.put("Чат", Arrays.asList(
                    new ModuleItem("=", "Формат чата", "Префиксы, суффиксы, шрифт и радуга", chatFormatEnabled, false, v -> chatFormatEnabled = v),
                    new ModuleItem("+", "Примеры", "Авто-решает примеры из чат-игры", autoMathEnabled, false, v -> autoMathEnabled = v),
                    new ModuleItem("o", "Копирование", "ПКМ по чату копирует текст", copyChatEnabled, false, v -> copyChatEnabled = v),
                    new ModuleItem("Console", "Console", "Ловит [GameConsole] в отдельный буфер", gameConsoleEnabled, false, v -> gameConsoleEnabled = v)
            ));
            modulesByCategory.put("Визуал", Arrays.asList(
                    new ModuleItem("Eye", "Fullbright", "Максимальная яркость без теней", true, false, null),
                    new ModuleItem("HUD", "WaterMark", "Кровавая плашка в углу экрана", true, false, null)
            ));
            modulesByCategory.put("Доп.", Arrays.asList(
                    new ModuleItem("Cfg", "Конфиги", "Сохранение и загрузка профилей", false, true, null)
            ));
            modulesByCategory.put("Читы", Arrays.asList(
                    new ModuleItem("!", "AutoSprint", "Автоматический бег при движении", false, false, null),
                    new ModuleItem("+", "NoFall", "Блокировка урона от падения", false, false, null)
            ));
            modulesByCategory.put("Прочее", Arrays.asList(
                    new ModuleItem("i", "Информация", "Версия клиента и бинды", false, true, null)
            ));
        }

        @Override
        public void tick() {
            super.tick();
            tickCounter++;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void render(MatrixStack ms, int mouseX, int mouseY, float partialTicks) {
            fill(ms, 0, 0, this.width, this.height, COL_BACKDROP);

            float winW = Math.min(this.width - 28.0F, 700.0F);
            float winH = Math.min(this.height - 24.0F, 345.0F);
            float winX = (this.width - winW) * 0.5F;
            float winY = (this.height - winH) * 0.5F;
            windowRect = new RectF(winX, winY, winW, winH);

            float sidebarW = 152.0F;
            float mainRadius = 14.0F;

            drawRoundedRect(ms, winX, winY, winW, winH, mainRadius, COL_MAIN_BG);
            drawRoundedRectLeft(ms, winX, winY, sidebarW, winH, mainRadius, COL_SIDEBAR_BG);
            drawLineVertical(ms, winX + sidebarW, winY + 1.0F, winY + winH - 1.0F, COL_BORDER);
            drawRoundedOutline(ms, winX, winY, winW, winH, mainRadius, COL_BORDER);

            ms.pushPose();
            float titleScale = 1.15F;
            ms.scale(titleScale, titleScale, 1.0F);
            String brand = "VETEMENTS";
            float brandX = (winX + sidebarW * 0.5F) / titleScale;
            float brandY = (winY + 20.0F) / titleScale;
            drawCenteredString(ms, this.font, brand, (int) brandX, (int) brandY, COL_TEXT_MAIN);
            ms.popPose();

            categoryRects.clear();
            float tabX = winX + 11.0F;
            float tabW = sidebarW - 22.0F;
            float tabY = winY + 50.0F;
            float tabH = 27.0F;
            float tabGap = 5.0F;

            for (int i = 0; i < categories.size(); i++) {
                String cat = categories.get(i);
                float y = tabY + i * (tabH + tabGap);
                RectF r = new RectF(tabX, y, tabW, tabH);
                categoryRects.add(r);

                boolean active = (i == selectedCategory);
                boolean hovered = r.contains(mouseX, mouseY);

                if (active) {
                    drawRoundedRectGradient(ms, r.x, r.y, r.w, r.h, 8.0F, COL_TAB_ACTIVE_TOP, COL_TAB_ACTIVE_BOT);
                    drawRoundedOutline(ms, r.x, r.y, r.w, r.h, 8.0F, COL_BLOOD_ACCENT);
                } else if (hovered) {
                    drawRoundedRect(ms, r.x, r.y, r.w, r.h, 8.0F, COL_TAB_HOVER);
                    drawRoundedOutline(ms, r.x, r.y, r.w, r.h, 8.0F, COL_BORDER);
                }

                int dotColor = active ? COL_BLOOD_BRIGHT : (hovered ? COL_TEXT_SUB : COL_TEXT_DIM);
                drawRoundedRect(ms, r.x + 9.0F, r.y + (r.h - 6.0F) * 0.5F, 6.0F, 6.0F, 3.0F, dotColor);

                int textColor = active ? COL_TEXT_MAIN : (hovered ? COL_TEXT_MAIN : COL_TEXT_SUB);
                drawString(ms, this.font, cat, (int) (r.x + 21.0F), (int) (r.y + (r.h - 8.0F) * 0.5F), textColor);
            }

            float footH = 26.0F;
            float footY = winY + winH - footH - 11.0F;
            footerRect = new RectF(winX + 11.0F, footY, sidebarW - 22.0F, footH);
            drawRoundedRect(ms, footerRect.x, footerRect.y, footerRect.w, footerRect.h, 9.0F, COL_CARD_BG);
            drawRoundedOutline(ms, footerRect.x, footerRect.y, footerRect.w, footerRect.h, 9.0F, COL_BORDER);
            drawCenteredString(ms, this.font, "awe", (int) (footerRect.x + footerRect.w * 0.5F), (int) (footerRect.y + 9.0F), COL_TEXT_SUB);

            float contentX = winX + sidebarW;
            float contentW = winW - sidebarW;
            float pad = 16.0F;

            float searchH = 26.0F;
            searchRect = new RectF(contentX + pad, winY + 14.0F, contentW - pad * 2.0F, searchH);
            drawRoundedRect(ms, searchRect.x, searchRect.y, searchRect.w, searchRect.h, 9.0F, COL_CARD_BG);
            drawRoundedOutline(ms, searchRect.x, searchRect.y, searchRect.w, searchRect.h, 9.0F, searchFocused ? COL_BLOOD_ACCENT : COL_BORDER);

            float glassCx = searchRect.x + 13.0F;
            float glassCy = searchRect.y + searchRect.h * 0.5F - 1.0F;
            strokeCircle(ms, glassCx, glassCy, 3.5F, searchFocused ? COL_BLOOD_BRIGHT : COL_TEXT_DIM);
            drawLine(ms, glassCx + 2.5F, glassCy + 2.5F, glassCx + 5.0F, glassCy + 5.0F, searchFocused ? COL_BLOOD_BRIGHT : COL_TEXT_DIM);

            String searchText;
            int searchTextCol;
            if (searchQuery.isEmpty() && !searchFocused) {
                searchText = "Поиск модулей...";
                searchTextCol = COL_TEXT_DIM;
            } else {
                String cursor = (searchFocused && (tickCounter / 10) % 2 == 0) ? "|" : "";
                searchText = searchQuery + cursor;
                searchTextCol = COL_TEXT_MAIN;
            }
            drawString(ms, this.font, searchText, (int) (searchRect.x + 24.0F), (int) (searchRect.y + 9.0F), searchTextCol);

            moduleRects.clear();
            List<ModuleItem> list = getFilteredModules();
            float gridTop = searchRect.y + searchRect.h + 13.0F;
            float gridBottom = winY + winH - 14.0F;

            float gapX = 11.0F;
            float gapY = 9.0F;
            int cols = 2;
            float cardW = (contentW - pad * 2.0F - gapX) / cols;
            float cardH = 48.0F;

            for (int i = 0; i < list.size(); i++) {
                ModuleItem item = list.get(i);
                int col = i % cols;
                int row = i / cols;

                float cx = contentX + pad + col * (cardW + gapX);
                float cy = gridTop + row * (cardH + gapY);
                if (cy + cardH > gridBottom) {
                    break;
                }

                RectF cardRect = new RectF(cx, cy, cardW, cardH);
                moduleRects.add(cardRect);

                boolean hovered = cardRect.contains(mouseX, mouseY);
                drawRoundedRect(ms, cx, cy, cardW, cardH, 11.0F, hovered ? COL_CARD_HOVER : COL_CARD_BG);
                drawRoundedOutline(ms, cx, cy, cardW, cardH, 11.0F, hovered ? COL_BORDER_HOVER : COL_BORDER);

                float iconBoxSize = 26.0F;
                float ix = cx + 10.0F;
                float iy = cy + (cardH - iconBoxSize) * 0.5F;
                drawRoundedRect(ms, ix, iy, iconBoxSize, iconBoxSize, 7.0F, COL_ICON_BG);
                drawRoundedOutline(ms, ix, iy, iconBoxSize, iconBoxSize, 7.0F, item.enabled ? COL_BLOOD_DARK : COL_BORDER);

                String iconLabel = trimToWidth(item.icon, (int) (iconBoxSize - 4));
                drawCenteredString(ms, this.font, iconLabel, (int) (ix + iconBoxSize * 0.5F), (int) (iy + 9.0F),
                        item.enabled ? COL_BLOOD_BRIGHT : COL_TEXT_SUB);

                float textX = ix + iconBoxSize + 9.0F;
                float rightControlW = item.badgeOnly ? 54.0F : 32.0F;
                int maxTextW = Math.max(24, (int) ((cx + cardW - 10.0F - rightControlW) - textX));

                String title = trimToWidth(item.title, maxTextW);
                String desc = trimToWidth(item.description, maxTextW);
                drawString(ms, this.font, title, (int) textX, (int) (cy + 11.0F), COL_TEXT_MAIN);
                drawString(ms, this.font, desc, (int) textX, (int) (cy + 25.0F), COL_TEXT_SUB);

                if (item.badgeOnly) {
                    float bw = 50.0F;
                    float bh = 18.0F;
                    float bx = cx + cardW - bw - 10.0F;
                    float by = cy + (cardH - bh) * 0.5F;
                    drawRoundedRect(ms, bx, by, bw, bh, 8.0F, COL_ICON_BG);
                    drawRoundedOutline(ms, bx, by, bw, bh, 8.0F, COL_BORDER);
                    drawCenteredString(ms, this.font, "Не задан", (int) (bx + bw * 0.5F), (int) (by + 5.0F), COL_TEXT_SUB);
                } else {
                    float tw = 28.0F;
                    float th = 15.0F;
                    float tx = cx + cardW - tw - 10.0F;
                    float ty = cy + (cardH - th) * 0.5F;
                    float tr = th * 0.5F;

                    int trackCol = item.enabled ? COL_BLOOD_ACCENT : COL_TOGGLE_OFF;
                    int trackBorder = item.enabled ? COL_BLOOD_BRIGHT : COL_BORDER;
                    drawRoundedRect(ms, tx, ty, tw, th, tr, trackCol);
                    drawRoundedOutline(ms, tx, ty, tw, th, tr, trackBorder);

                    float knobRadius = 5.2F;
                    float knobCx = item.enabled ? (tx + tw - tr) : (tx + tr);
                    float knobCy = ty + tr;
                    fillCircle(ms, knobCx, knobCy, knobRadius, COL_KNOB);
                }
            }

            RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            super.render(ms, mouseX, mouseY, partialTicks);
        }

        private List<ModuleItem> getFilteredModules() {
            String cat = categories.get(selectedCategory);
            List<ModuleItem> base = modulesByCategory.getOrDefault(cat, Collections.emptyList());
            String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
            if (q.isEmpty()) {
                return base;
            }
            List<ModuleItem> out = new ArrayList<>();
            for (ModuleItem m : base) {
                if (m.title.toLowerCase(Locale.ROOT).contains(q) || m.description.toLowerCase(Locale.ROOT).contains(q)) {
                    out.add(m);
                }
            }
            return out;
        }

        private String trimToWidth(String text, int maxW) {
            if (text == null || this.font.width(text) <= maxW) {
                return text == null ? "" : text;
            }
            String ellipsis = "...";
            int avail = Math.max(0, maxW - this.font.width(ellipsis));
            return this.font.plainSubstrByWidth(text, avail) + ellipsis;
        }

        private void drawRoundedRect(MatrixStack ms, float x, float y, float w, float h, float r, int color) {
            r = Math.min(r, Math.min(w, h) * 0.5F);
            fillRectF(ms, x + r, y, x + w - r, y + h, color);
            fillRectF(ms, x, y + r, x + r, y + h - r, color);
            fillRectF(ms, x + w - r, y + r, x + w, y + h - r, color);

            fillCornerFan(ms, x + r, y + r, r, 180.0F, 270.0F, color);
            fillCornerFan(ms, x + w - r, y + r, r, 270.0F, 360.0F, color);
            fillCornerFan(ms, x + w - r, y + h - r, r, 0.0F, 90.0F, color);
            fillCornerFan(ms, x + r, y + h - r, r, 90.0F, 180.0F, color);
        }

        private void drawRoundedRectLeft(MatrixStack ms, float x, float y, float w, float h, float r, int color) {
            r = Math.min(r, Math.min(w, h) * 0.5F);
            fillRectF(ms, x + r, y, x + w, y + h, color);
            fillRectF(ms, x, y + r, x + r, y + h - r, color);

            fillCornerFan(ms, x + r, y + r, r, 180.0F, 270.0F, color);
            fillCornerFan(ms, x + r, y + h - r, r, 90.0F, 180.0F, color);
        }

        private void drawRoundedRectGradient(MatrixStack ms, float x, float y, float w, float h, float r, int topColor, int botColor) {
            r = Math.min(r, Math.min(w, h) * 0.5F);
            fillRectGradientF(ms, x + r, y, x + w - r, y + h, topColor, botColor);
            fillRectGradientF(ms, x, y + r, x + r, y + h - r, topColor, botColor);
            fillRectGradientF(ms, x + w - r, y + r, x + w, y + h - r, topColor, botColor);

            fillCornerFan(ms, x + r, y + r, r, 180.0F, 270.0F, topColor);
            fillCornerFan(ms, x + w - r, y + r, r, 270.0F, 360.0F, topColor);
            fillCornerFan(ms, x + w - r, y + h - r, r, 0.0F, 90.0F, botColor);
            fillCornerFan(ms, x + r, y + h - r, r, 90.0F, 180.0F, botColor);
        }

        private void drawRoundedOutline(MatrixStack ms, float x, float y, float w, float h, float r, int color) {
            r = Math.min(r, Math.min(w, h) * 0.5F);
            drawLine(ms, x + r, y, x + w - r, y, color);
            drawLine(ms, x + r, y + h, x + w - r, y + h, color);
            drawLine(ms, x, y + r, x, y + h - r, color);
            drawLine(ms, x + w, y + r, x + w, y + h - r, color);

            strokeCornerArc(ms, x + r, y + r, r, 180.0F, 270.0F, color);
            strokeCornerArc(ms, x + w - r, y + r, r, 270.0F, 360.0F, color);
            strokeCornerArc(ms, x + w - r, y + h - r, r, 0.0F, 90.0F, color);
            strokeCornerArc(ms, x + r, y + h - r, r, 90.0F, 180.0F, color);
        }

        private void fillCircle(MatrixStack ms, float cx, float cy, float radius, int color) {
            fillCornerFan(ms, cx, cy, radius, 0.0F, 360.0F, color);
        }

        private void strokeCircle(MatrixStack ms, float cx, float cy, float radius, int color) {
            strokeCornerArc(ms, cx, cy, radius, 0.0F, 360.0F, color);
        }

        private void fillRectF(MatrixStack ms, float x1, float y1, float x2, float y2, int color) {
            float a = ((color >> 24) & 0xFF) / 255.0F;
            float r = ((color >> 16) & 0xFF) / 255.0F;
            float g = ((color >> 8) & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;

            Matrix4f mat = ms.last().pose();
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.getBuilder();

            RenderSystem.enableBlend();
            RenderSystem.disableTexture();
            RenderSystem.defaultBlendFunc();
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            buf.vertex(mat, x1, y2, 0.0F).color(r, g, b, a).endVertex();
            buf.vertex(mat, x2, y2, 0.0F).color(r, g, b, a).endVertex();
            buf.vertex(mat, x2, y1, 0.0F).color(r, g, b, a).endVertex();
            buf.vertex(mat, x1, y1, 0.0F).color(r, g, b, a).endVertex();
            tess.end();
            RenderSystem.enableTexture();
        }

        private void fillRectGradientF(MatrixStack ms, float x1, float y1, float x2, float y2, int topCol, int botCol) {
            float a1 = ((topCol >> 24) & 0xFF) / 255.0F;
            float r1 = ((topCol >> 16) & 0xFF) / 255.0F;
            float g1 = ((topCol >> 8) & 0xFF) / 255.0F;
            float b1 = (topCol & 0xFF) / 255.0F;

            float a2 = ((botCol >> 24) & 0xFF) / 255.0F;
            float r2 = ((botCol >> 16) & 0xFF) / 255.0F;
            float g2 = ((botCol >> 8) & 0xFF) / 255.0F;
            float b2 = (botCol & 0xFF) / 255.0F;

            Matrix4f mat = ms.last().pose();
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.getBuilder();

            RenderSystem.enableBlend();
            RenderSystem.disableTexture();
            RenderSystem.defaultBlendFunc();
            RenderSystem.shadeModel(GL11.GL_SMOOTH);
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            buf.vertex(mat, x1, y2, 0.0F).color(r2, g2, b2, a2).endVertex();
            buf.vertex(mat, x2, y2, 0.0F).color(r2, g2, b2, a2).endVertex();
            buf.vertex(mat, x2, y1, 0.0F).color(r1, g1, b1, a1).endVertex();
            buf.vertex(mat, x1, y1, 0.0F).color(r1, g1, b1, a1).endVertex();
            tess.end();
            RenderSystem.shadeModel(GL11.GL_FLAT);
            RenderSystem.enableTexture();
        }

        private void drawLineVertical(MatrixStack ms, float x, float y1, float y2, int color) {
            drawLine(ms, x, y1, x, y2, color);
        }

        private void drawLine(MatrixStack ms, float x1, float y1, float x2, float y2, int color) {
            float a = ((color >> 24) & 0xFF) / 255.0F;
            float r = ((color >> 16) & 0xFF) / 255.0F;
            float g = ((color >> 8) & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;

            Matrix4f mat = ms.last().pose();
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.getBuilder();

            RenderSystem.enableBlend();
            RenderSystem.disableTexture();
            RenderSystem.defaultBlendFunc();
            RenderSystem.lineWidth(1.0F);
            buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            buf.vertex(mat, x1, y1, 0.0F).color(r, g, b, a).endVertex();
            buf.vertex(mat, x2, y2, 0.0F).color(r, g, b, a).endVertex();
            tess.end();
            RenderSystem.enableTexture();
        }

        private void fillCornerFan(MatrixStack ms, float cx, float cy, float radius, float startDeg, float endDeg, int color) {
            float a = ((color >> 24) & 0xFF) / 255.0F;
            float r = ((color >> 16) & 0xFF) / 255.0F;
            float g = ((color >> 8) & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;

            int steps = Math.max(8, (int) (radius * 2.5F));
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

            int steps = Math.max(10, (int) (radius * 2.5F));
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

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            float mx = (float) mouseX;
            float my = (float) mouseY;

            if (!windowRect.contains(mx, my)) {
                this.minecraft.setScreen(parent);
                return true;
            }

            searchFocused = searchRect.contains(mx, my);

            for (int i = 0; i < categoryRects.size(); i++) {
                if (categoryRects.get(i).contains(mx, my)) {
                    selectedCategory = i;
                    return true;
                }
            }

            List<ModuleItem> filtered = getFilteredModules();
            int count = Math.min(moduleRects.size(), filtered.size());
            for (int i = 0; i < count; i++) {
                if (moduleRects.get(i).contains(mx, my)) {
                    ModuleItem item = filtered.get(i);
                    item.enabled = !item.enabled;
                    if (item.onToggle != null) {
                        item.onToggle.accept(item.enabled);
                    }
                    return true;
                }
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            if (searchFocused && codePoint >= 32 && codePoint != 127) {
                searchQuery += codePoint;
                return true;
            }
            return super.charTyped(codePoint, modifiers);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (searchFocused) {
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    searchFocused = false;
                    return true;
                }
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @FunctionalInterface
        private interface BoolConsumer {
            void accept(boolean value);
        }

        private static class ModuleItem {
            final String icon;
            final String title;
            final String description;
            final boolean badgeOnly;
            final BoolConsumer onToggle;
            boolean enabled;

            ModuleItem(String icon, String title, String description, boolean enabled, boolean badgeOnly, BoolConsumer onToggle) {
                this.icon = icon;
                this.title = title;
                this.description = description;
                this.enabled = enabled;
                this.badgeOnly = badgeOnly;
                this.onToggle = onToggle;
            }
        }

        private static class RectF {
            final float x;
            final float y;
            final float w;
            final float h;

            RectF(float x, float y, float w, float h) {
                this.x = x;
                this.y = y;
                this.w = w;
                this.h = h;
            }

            boolean contains(float px, float py) {
                return px >= x && px <= x + w && py >= y && py <= y + h;
            }
        }
    }
}
