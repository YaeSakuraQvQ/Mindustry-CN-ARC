package mindustry.arcModule.toolpack;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.event.Touchable;
import arc.scene.ui.Dialog;
import arc.scene.ui.Label;
import arc.scene.ui.Slider;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.gen.Iconc;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.ui.dialogs.BaseDialog;

import java.lang.reflect.Field;

import static mindustry.Vars.*;
import static mindustry.arcModule.ElementUtils.*;

public class arcPlayerEffect {
    private static Effect playerEffect = Fx.none;
    private static float effectChance = 0;

    private static int effectTime = 0;

    private static int effectCooldown = 1;
    private static Effect refEffect = Fx.none;

    private static boolean show = true;
    private static int curCounter = 0;

    static {
        Events.run(EventType.Trigger.update, () -> {
            curCounter += 1;
            if (curCounter >= effectCooldown) {
                curCounter = 0;
                show = true;
            } else show = false;
        });
        Events.on(EventType.WorldLoadEvent.class,event-> readConfig());
    }

    public static void arcPlayerEffectSetting() {
        Dialog efTable = new BaseDialog("玩家特效设置");
        arcSliderTable(efTable.cont, "特效概率", effectChance, 0, 1, 0.05f, i -> (int) (i * 100) + "%", s -> effectChance = s);
        arcSliderTableInt(efTable.cont, "特效间隔", effectCooldown, 1, 30, 1, i -> "每" + i + "帧", s -> effectCooldown = s);
        arcSliderTableInt(efTable.cont, "特效时长", (int) playerEffect.lifetime, 0, 300, 5, i -> i + "帧", s -> {
            playerEffect.lifetime = s;
            effectTime = s;
        });
        efTable.cont.pane(tt -> {
            for (Field field : Fx.class.getDeclaredFields()) {
                if (field.getGenericType() != Effect.class) continue;
                Effect thisEffect;
                try {
                    thisEffect = (Effect) field.get(null);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                tt.check(field.getName(), refEffect == thisEffect, a -> {
                    playerEffect = thisEffect;
                    refEffect = thisEffect;
                    playerEffect.lifetime = effectTime;
                }).left().row();
            }
        });
        efTable.addCloseButton();
        efTable.buttons.button("保存", arcPlayerEffect::saveConfig);
        efTable.buttons.button("读取", arcPlayerEffect::readConfig);
        efTable.show();
    }

    private static void saveConfig() {
        Core.settings.put("arcPlayerEffectPro", effectChance);
        Core.settings.put("arcPlayerEffectTime", playerEffect.lifetime);
        Core.settings.put("arcPlayerEffectType", refEffect.id);
        Core.settings.put("arcPlayerEffectCooldown", effectCooldown);
        ui.arcInfo("[cyan]已保存设置");
    }

    private static void readConfig() {
        effectChance = Core.settings.getFloat("arcPlayerEffectPro");
        refEffect = Effect.get(Core.settings.getInt("arcPlayerEffectType"));
        playerEffect = refEffect;
        playerEffect.lifetime = Core.settings.getFloat("arcPlayerEffectTime");
        effectTime = (int) playerEffect.lifetime;
        effectCooldown = Core.settings.getInt("arcPlayerEffectCooldown");
        ui.arcInfo("[cyan]已读取设置");
    }

    public static void drawPlayerEffect(Unit unit) {
        Color effectColor = unit.controller() == player ? getPlayerEffectColor() : unit.team.color;

        boolean drawCircle = (unit.controller() == player && Core.settings.getInt("superUnitEffect") != 0) || (unit.controller() instanceof Player && Core.settings.getInt("superUnitEffect") == 2);
        if (drawCircle) {
            // 射程圈
            float curStroke = (float) Core.settings.getInt("playerEffectCurStroke") / 10f;

            Lines.stroke(Lines.getStroke() * curStroke);

            Draw.z(Layer.effect - 2f);
            Draw.color(effectColor);

            Tmp.v1.trns(unit.rotation - 90, unit.x, unit.y).add(unit.x, unit.y);

            if (curStroke > 0) {
                for (int i = 0; i < 5; i++) {
                    float rot = unit.rotation + i * 360f / 5 + Time.time * 0.5f;
                    Lines.arc(unit.x, unit.y, unit.type.maxRange, 0.14f, rot, (int) (50 + unit.type.maxRange / 10));
                }
            }
        }
        // 武器圈
        if (Core.settings.getInt("unitTargetType") > 0) {
            Draw.z(Layer.effect);
            Draw.color(effectColor, 0.8f);
            Lines.stroke(1f);
            Lines.line(unit.x, unit.y, unit.aimX, unit.aimY);
            switch (Core.settings.getInt("unitTargetType")) {
                case 1:
                    Lines.dashCircle(unit.aimX, unit.aimY, 8);
                    break;
                case 2:
                    Drawf.target(unit.aimX, unit.aimY, 6f, 0.7f, effectColor);
                    break;
                case 3:
                    Drawf.target2(unit.aimX, unit.aimY, 6f, 0.7f, effectColor);
                    break;
                case 4:
                    Drawf.targetc(unit.aimX, unit.aimY, 6f, 0.7f, effectColor);
                    break;
                case 5:
                    Drawf.targetd(unit.aimX, unit.aimY, 6f, 0.7f, effectColor);
                    break;
            }
        }

        // 特效轨迹
        if (show && Mathf.chanceDelta(effectChance)) playerEffect.at(unit.x, unit.y, effectColor);
        Draw.reset();
    }

    public static void drawPlayerBuildRange(Unit unit) {
        Draw.z(Layer.effect - 2f);
        Draw.color(Pal.heal);

        Lines.stroke(2f);

        for (int i = 0; i < 3; i++) {
            float rot = player.unit().rotation + i * 360f / 3 + 15f;
            Lines.arc(unit.x, unit.y, unit.type.buildRange, 0.25f, rot, (int) (50 + unit.type.buildRange / 10));
            Draw.rect(Icon.wrench.getRegion(), unit.x + player.unit().type.buildRange * Mathf.cos((float) Math.toRadians(rot-15f)),  unit.y + player.unit().type.buildRange * Mathf.sin((float) Math.toRadians(rot-15f)),8f,8f);
        }

        Draw.reset();
    }

}