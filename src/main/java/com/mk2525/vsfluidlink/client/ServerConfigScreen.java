package com.mk2525.vsfluidlink.client;

import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ServerConfigScreen extends Screen {
    private record Option(String key, ForgeConfigSpec.ConfigValue<?> value, boolean buffer) {}

    private final Screen parent;
    private final List<Option> options;
    private final Map<Option, String> draft = new LinkedHashMap<>();
    private boolean buffers;
    private Button save;

    public ServerConfigScreen(Screen parent) {
        super(text("server"));
        this.parent = parent;
        var config = VsFluidLinkConfig.SERVER;
        options = List.of(
                new Option("maxLinkDistance", config.maxLinkDistance, false),
                new Option("magnetScanDistance", config.magnetScanDistance, false),
                new Option("magnetScanRadius", config.magnetScanRadius, false),
                new Option("restrictWorldToWorldConnection", config.restrictWorldToWorldConnection, false),
                new Option("restrictIntraShipConnection", config.restrictIntraShipConnection, false),
                new Option("fluidCapacity", config.fluidCapacity, true),
                new Option("itemSlots", config.itemSlots, true),
                new Option("energyCapacity", config.energyCapacity, true));
        if (canEdit()) {
            options.forEach(option -> draft.put(option, option.value().get().toString()));
        }
    }

    private static Component text(String key) {
        return Component.translatable("vsfluidlink.configuration." + key);
    }

    private static boolean canEdit() {
        Minecraft client = Minecraft.getInstance();
        return VsFluidLinkConfig.SERVER_SPEC.isLoaded() && client.hasSingleplayerServer()
                && !client.getSingleplayerServer().isPublished();
    }

    @Override
    protected void init() {
        save = null;
        int formWidth = Math.min(width - 32, 360);
        int left = (width - formWidth) / 2;
        addRenderableWidget(Button.builder(text("general"), button -> changePage(false))
                .bounds(left, 28, formWidth / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(text("buffers"), button -> changePage(true))
                .bounds(left + formWidth / 2 + 2, 28, formWidth / 2 - 2, 20).build());
        if (canEdit() && !draft.isEmpty()) {
            int row = 0;
            for (Option option : options) {
                if (option.buffer() != buffers) continue;
                int y = 58 + row++ * 28;
                int x = left + formWidth - 90;
                if (option.value() instanceof ForgeConfigSpec.BooleanValue) {
                    addRenderableWidget(new Checkbox(x, y, 20, 20, Component.empty(),
                            Boolean.parseBoolean(draft.get(option))) {
                        @Override
                        public void onPress() {
                            super.onPress();
                            draft.put(option, Boolean.toString(selected()));
                        }
                    });
                } else {
                    EditBox input = new EditBox(font, x, y, 90, 20, text(option.key()));
                    input.setMaxLength(32);
                    input.setValue(draft.get(option));
                    input.setResponder(value -> draft.put(option, value));
                    if (option.buffer()) input.setTooltip(Tooltip.create(text("restart")));
                    addRenderableWidget(input);
                }
            }
            save = addRenderableWidget(Button.builder(text("save"), button -> saveChanges())
                    .bounds(width / 2 - 102, height - 26, 100, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(width / 2 + 2, height - 26, 100, 20).build());
    }

    private void changePage(boolean buffers) {
        this.buffers = buffers;
        rebuildWidgets();
    }

    private Object parsed(Option option) {
        String value = draft.get(option);
        if (option.value() instanceof ForgeConfigSpec.BooleanValue) return Boolean.parseBoolean(value);
        if (option.value() instanceof ForgeConfigSpec.IntValue) return Integer.parseInt(value);
        double number = Double.parseDouble(value);
        if (!Double.isFinite(number)) throw new IllegalArgumentException("Non-finite config value");
        return number;
    }

    private boolean valid() {
        if (!canEdit() || draft.isEmpty()) return false;
        try {
            for (Option option : options) {
                ForgeConfigSpec.ValueSpec spec = VsFluidLinkConfig.SERVER_SPEC.getRaw(option.value().getPath());
                if (!spec.test(parsed(option))) return false;
            }
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void saveChanges() {
        if (!valid()) return;
        for (Option option : options) {
            Object value = parsed(option);
            if (option.value() instanceof ForgeConfigSpec.BooleanValue setting) setting.set((Boolean) value);
            else if (option.value() instanceof ForgeConfigSpec.IntValue setting) setting.set((Integer) value);
            else if (option.value() instanceof ForgeConfigSpec.DoubleValue setting) setting.set((Double) value);
        }
        VsFluidLinkConfig.SERVER_SPEC.save();
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        if (save != null) save.active = valid();
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        int formWidth = Math.min(width - 32, 360);
        int left = (width - formWidth) / 2;
        if (canEdit() && !draft.isEmpty()) {
            int row = 0;
            for (Option option : options) {
                if (option.buffer() != buffers) continue;
                graphics.drawWordWrap(font, text(option.key()), left, 62 + row++ * 28, formWidth - 104, 0xFFFFFF);
            }
            if (buffers) graphics.drawCenteredString(font, text("restart"), width / 2, height - 42, 0xFFD080);
        } else {
            graphics.drawWordWrap(font, text("unavailable"), left, 65, formWidth, 0xFFD080);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
