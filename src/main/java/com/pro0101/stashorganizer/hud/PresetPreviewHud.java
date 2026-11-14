package com.pro0101.stashorganizer.hud;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import com.pro0101.stashorganizer.modules.render.Shaders;
import com.pro0101.stashorganizer.modules.render.Skybox;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists the sky and shader presets, marking the active one.
 *
 * <p>The point is to choose without guessing: the presets are only names in the module settings, and
 * the names say little about the colour a preset will actually paint. The active row is highlighted,
 * carries a swatch of the colour the preset uses, and a disabled module dims its whole section
 * instead of pretending a preset is in use.
 */
public class PresetPreviewHud extends HudElement {
    public static final HudElementInfo<PresetPreviewHud> INFO = new HudElementInfo<>(
        StashOrganizerAddon.HUD_GROUP,
        "preset-preview",
        "Lists the sky and shader presets, marking the active one with its colour.",
        PresetPreviewHud::new
    );

    private static final String TITLE_TEXT = "Presets";
    private static final Color BACKGROUND = new Color(0, 0, 0, 120);
    private static final Color TITLE = Color.WHITE;
    private static final Color HEADER = new Color(220, 220, 220);
    private static final Color ACTIVE = new Color(120, 230, 160);
    private static final Color INACTIVE = new Color(150, 150, 150);
    private static final Color OFF = new Color(105, 105, 105);

    private static final double PADDING = 2;
    private static final double INDENT = 9;
    private static final double SWATCH = 6;

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Boolean> skybox = this.sgGeneral.add(new BoolSetting.Builder()
        .name("skybox")
        .description("List the sky presets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shaders = this.sgGeneral.add(new BoolSetting.Builder()
        .name("shaders")
        .description("List the shader presets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swatch = this.sgGeneral.add(new BoolSetting.Builder()
        .name("swatch")
        .description("Draw the colour of the active preset next to its name.")
        .defaultValue(true)
        .build()
    );

    private final List<Line> lines = new ArrayList<>();

    public PresetPreviewHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        Skybox skies = Modules.get().get(Skybox.class);
        Shaders glows = Modules.get().get(Shaders.class);

        lines.clear();
        lines.add(new Line(TITLE_TEXT, TITLE, false, null));

        if (this.skybox.get()) {
            lines.add(new Line("Skybox" + stateOf(skies), HEADER, false, null));

            boolean on = skies != null && skies.isActive();
            Skybox.Mode active = skies == null ? null : skies.mode();

            for (Skybox.Mode mode : Skybox.Mode.values()) {
                boolean selected = on && mode == active;
                lines.add(row(mode.name(), selected, on, selected && skies != null ? skies.tintColor() : null));
            }
        }

        if (this.shaders.get()) {
            lines.add(new Line("Shaders" + stateOf(glows), HEADER, false, null));

            boolean on = glows != null && glows.isActive();
            Shaders.Mode active = glows == null ? null : glows.mode();

            for (Shaders.Mode mode : Shaders.Mode.values()) {
                boolean selected = on && mode == active;
                lines.add(row(mode.name(), selected, on, selected && glows != null ? glows.playerColor() : null));
            }
        }

        double lineHeight = renderer.textHeight(true) + 1;
        double width = 0;

        for (Line line : lines) {
            width = Math.max(width, renderer.textWidth(line.text(), true) + (line.indent() ? INDENT : 0));
        }

        setSize(width + PADDING * 2 + (this.swatch.get() ? SWATCH : 0), lineHeight * lines.size() + PADDING * 2);

        HudBloom.panel(renderer, x, y, getWidth(), getHeight(), ACTIVE);
        renderer.quad(x, y, getWidth(), getHeight(), BACKGROUND);

        double textX = x + PADDING;
        double textY = y + PADDING;

        for (Line line : lines) {
            if (line.swatch() != null && this.swatch.get()) {
                renderer.quad(textX, textY + (lineHeight - SWATCH) / 2, SWATCH, SWATCH, line.swatch());
            }

            double startX = line.indent() ? textX + INDENT : textX;

            if (line.indent()) {
                renderer.text(line.text(), startX, textY, line.color(), true);
            } else {
                HudBloom.text(renderer, line.text(), startX, textY, line.color(), true);
            }

            textY += lineHeight;
        }
    }

    /**
     * Builds one preset row.
     *
     * @param name preset name
     * @param selected whether this preset is the active one
     * @param moduleOn whether the owning module is on
     * @param swatch colour of the preset, may be null
     * @return the row
     */
    private static Line row(String name, boolean selected, boolean moduleOn, SettingColor swatch) {
        String text = (selected ? "> " : "  ") + name;
        Color color = moduleOn ? (selected ? ACTIVE : INACTIVE) : OFF;

        return new Line(text, color, true, selected ? swatch : null);
    }

    /**
     * Returns the marker appended to a section header.
     *
     * @param module module of the section, may be null
     * @return empty when the module is on, a dimmed marker otherwise
     */
    private static String stateOf(Module module) {
        return module != null && module.isActive() ? "" : "  (off)";
    }

    /**
     * One rendered row.
     *
     * @param text text to draw
     * @param color text colour
     * @param indent whether the row is indented under a header
     * @param swatch colour square, may be null
     */
    private record Line(String text, Color color, boolean indent, SettingColor swatch) {
    }
}
