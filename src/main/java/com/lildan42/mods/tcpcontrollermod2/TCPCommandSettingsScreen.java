package com.lildan42.mods.tcpcontrollermod2;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.function.*;

public class TCPCommandSettingsScreen extends Screen {

    private static final String TITLE_ID = "gui.settings.title";
    private static final String EXIT_BUTTON_ID = "gui.settings.exit";
    private static final String UNBREAKABLE_BLOCKS_BUTTON_ID = "gui.settings.unbreakable_blocks";

    private static final int BUTTON_WIDTH = 120, BUTTON_HEIGHT = 20, BUTTON_H_SPACE = 25, BUTTON_V_SPACE = 10,
            SECTION_START_HEIGHT = 30, TITLE_HEIGHT = 5, BOTTOM_V_SPACE = 10, WHITE = 0xffffff;

    private static final String[] SECTION_TITLES = {
            "gui.settings.commands", "gui.settings.effects", "gui.settings.entities"
    };

    private static final int[] SECTION_TITLE_POSITIONS = { -BUTTON_WIDTH - BUTTON_H_SPACE, 0, BUTTON_WIDTH + BUTTON_H_SPACE };

    private final TCPCommandSettings settings;

    protected TCPCommandSettingsScreen() {
        super(new TranslatableComponent(TITLE_ID).withStyle(ChatFormatting.BOLD));
        this.settings = TCPCommandSettings.getInstance();
    }

    @Override
    protected void init() {
        int[] bottomYVals = {
                this.addToggleButtonList(-3 * BUTTON_WIDTH / 2 - BUTTON_H_SPACE,
                        this.settings.getCommandStream().toList(), this.settings::isCommandEnabled,
                        TextComponent::new, this.settings::setCommandEnabled),

                this.addToggleButtonList(-BUTTON_WIDTH / 2, this.settings.getEffectStream().toList(),
                        this.settings::isEffectEnabled,
                        effect -> this.getMCNamespaceComponent("effects", effect),
                        this.settings::setEffectEnabled),

                this.addToggleButtonList(BUTTON_WIDTH / 2 + BUTTON_H_SPACE, this.settings.getEntityStream().toList(),
                        this.settings::isEntityEnabled, entity -> this.getMCNamespaceComponent("entities", entity),
                        this.settings::setEntityEnabled)
        };

        int bottomY = Arrays.stream(bottomYVals).max().getAsInt() + BOTTOM_V_SPACE;

        if(this.minecraft != null)
            this.addRenderableWidget(this.settings.maxEffectStrengthOption
                    .createButton(this.minecraft.options, this.width / 2 - 3 * BUTTON_WIDTH / 2 - BUTTON_H_SPACE,
                            bottomY, BUTTON_WIDTH));

        this.addRenderableWidget(new Button(this.width / 2 - BUTTON_WIDTH / 2, bottomY, BUTTON_WIDTH, BUTTON_HEIGHT,
                new TranslatableComponent(EXIT_BUTTON_ID), btn -> this.onClose()));

        this.addRenderableWidget(CycleButton.onOffBuilder().withInitialValue(this.settings.unbreakableBlocksEnabled)
                .create(this.width / 2 + BUTTON_WIDTH / 2 + BUTTON_H_SPACE, bottomY, BUTTON_WIDTH, BUTTON_HEIGHT,
                        new TranslatableComponent(UNBREAKABLE_BLOCKS_BUTTON_ID),
                        (button, enabled) -> this.settings.unbreakableBlocksEnabled = enabled));
    }

    private TranslatableComponent getMCNamespaceComponent(String category, String name) {
        return new TranslatableComponent("settings.%s.%s".formatted(category, name.substring(name.indexOf(':') + 1)));
    }

    private int addToggleButtonList(int offsetFromCenter, List<String> values, Predicate<String> initValue, Function<String, Component> getComponent, BiConsumer<String, Boolean> onClick) {
        int yPos = SECTION_START_HEIGHT + BUTTON_V_SPACE;

        for(String value : values) {
            this.addRenderableWidget(CycleButton.onOffBuilder().withInitialValue(initValue.test(value))
                    .create(this.width / 2 + offsetFromCenter, yPos, BUTTON_WIDTH, BUTTON_HEIGHT, getComponent.apply(value),
                            (button, enabled) -> onClick.accept(value, enabled)));

            yPos += BUTTON_HEIGHT + BUTTON_V_SPACE;
        }

        return yPos;
    }

    @Override
    public void render(@NotNull PoseStack poseStack, int a, int b, float c) {
        this.renderDirtBackground(0);

        GuiComponent.drawCenteredString(poseStack, this.font, this.title, this.width / 2, TITLE_HEIGHT, WHITE);

        TranslatableComponent[] titles = Arrays.stream(SECTION_TITLES).map(TranslatableComponent::new)
                .toArray(TranslatableComponent[]::new);

        for(int i = 0; i < titles.length; i++) {
            GuiComponent.drawCenteredString(poseStack, this.font, titles[i], this.width / 2 + SECTION_TITLE_POSITIONS[i], SECTION_START_HEIGHT, WHITE);
        }

        super.render(poseStack, a, b, c);
    }

    @Override
    public void onClose() {
        if(this.minecraft != null)
            this.minecraft.setScreen(null);
    }
}
