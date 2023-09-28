package mindustry.arcModule.ui;

import arc.Events;
import arc.func.*;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.game.Team;
import mindustry.gen.*;
import arc.util.Time;
import mindustry.Vars;
import mindustry.arcModule.NumberFormat;
import mindustry.arcModule.RFuncs;
import mindustry.arcModule.ui.dialogs.TeamSelectDialog;
import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.core.*;
import mindustry.game.EventType;
import mindustry.game.Teams;
import mindustry.type.Item;
import mindustry.type.UnitType;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;

import static mindustry.Vars.*;
import static mindustry.gen.Tex.*;
import static mindustry.ui.Styles.*;

public class OtherCoreItemDisplay extends Table {
    public Seq<Teams.TeamData> teams = new Seq<>();

    private float lastUpd = 0f, fontScl = 0.8f;
    private boolean show = false, showStat = true, showItem = true, showUnit = true;

    private Table teamsTable;

    private TextButton.TextButtonStyle textStyle;

    private Seq<Teams.TeamData> forceShowTeam = new Seq<>();

    public OtherCoreItemDisplay() {

        textStyle = new TextButton.TextButtonStyle() {{
            down = flatOver;
            up = pane;
            over = flatDownBase;
            font = Fonts.def;
            fontColor = Color.white;
            disabledFontColor = Color.gray;
            checked = flatDown;
        }};

        teamsTable = new Table();
        rebuild();

        Events.on(EventType.WorldLoadEvent.class, e -> {
            forceShowTeam.clear();
        });
    }

    void rebuild() {
        clear();
        if (!show) {
            button("[red]+", textStyle, () -> {
                show = !show;
                rebuild();
            }).left().width(40f).fillY().get().left();
        } else {
            table(t -> {
                t.table(buttons -> {
                    buttons.button("[red]×", textStyle, () -> {
                        show = !show;
                        rebuild();
                    }).size(40f).row();

                    buttons.button("T", textStyle, () -> {
                        new TeamSelectDialog(team -> {
                            if (forceShowTeam.contains(team.data())) forceShowTeam.remove(team.data());
                            else forceShowTeam.add(team.data());
                            updateTeamList();
                            teamsRebuild();
                        }, team -> teams.contains(team.data()), false).show();
                    }).checked(gg -> false).size(40f).row();

                    buttons.button(Blocks.worldProcessor.emoji(), textStyle, () -> {
                        showStat = !showStat;
                        teamsRebuild();
                    }).checked(a -> showStat).size(40f).row();

                    buttons.button(content.items().get(0).emoji(), textStyle, () -> {
                        showItem = !showItem;
                        teamsRebuild();
                    }).checked(a -> showItem).size(40f).row();

                    buttons.button(UnitTypes.mono.emoji(), textStyle, () -> {
                        showUnit = !showUnit;
                        teamsRebuild();
                    }).checked(a -> showUnit).size(40f);
                }).left();

                teamsRebuild();

                t.add(teamsTable).left();

            }).left();
        }
    }

    private void teamsRebuild() {
        teamsTable.clear();
        teamsTable.background(Styles.black6);
        teamsTable.update(() -> {
            if (Time.time - lastUpd > 120f) {
                lastUpd = Time.time;
                updateTeamList();
                teamsRebuild();
            }
        });
        teams.sort(teamData -> -teamData.cores.size);

        /**name + cores + units */
        teamsTable.add(); addTeamData(teamsTable,team -> team.team.id < 6 ? team.team.localized() : String.valueOf(team.team.id));
        addTeamData(teamsTable, Blocks.coreNucleus.uiIcon, team -> UI.formatAmount(team.cores.size));
        addTeamData(teamsTable, UnitTypes.mono.uiIcon, team -> UI.formatAmount(team.units.size));
        addTeamData(teamsTable, UnitTypes.gamma.uiIcon, team -> String.valueOf(team.players.size));

        if (showStat) {
            teamsTable.image().color(getThemeColor()).fillX().height(1).colspan(999).padTop(3).padBottom(3).row();
            addTeamDataCheck(teamsTable, Blocks.siliconSmelter.uiIcon, team -> team.team.rules().cheat, false);
            addTeamDataCheck(teamsTable, Blocks.arc.uiIcon, team -> state.rules.blockDamage(team.team));
            addTeamDataCheck(teamsTable, Blocks.titaniumWall.uiIcon, team -> state.rules.blockHealth(team.team));
            addTeamDataCheck(teamsTable, Blocks.buildTower.uiIcon, team -> state.rules.buildSpeed(team.team));
            addTeamDataCheck(teamsTable, UnitTypes.corvus.uiIcon, team -> state.rules.unitDamage(team.team));
            addTeamDataCheck(teamsTable, UnitTypes.oct.uiIcon, team -> state.rules.unitHealth(team.team));
            addTeamDataCheck(teamsTable, UnitTypes.zenith.uiIcon, team -> state.rules.unitCrashDamage(team.team));
            addTeamDataCheck(teamsTable, Blocks.tetrativeReconstructor.uiIcon, team -> state.rules.unitBuildSpeed(team.team));
            addTeamDataCheck(teamsTable, Blocks.basicAssemblerModule.uiIcon, team -> state.rules.unitCost(team.team));
            teamsTable.row();
        }

        if (showItem) {
            teamsTable.image().color(getThemeColor()).fillX().height(1).colspan(999).padTop(3).padBottom(3).row();
            for (Item item : content.items()) {
                boolean show = false;
                for (Teams.TeamData team : teams) {
                    if (team.hasCore() && team.core().items.get(item) > 0)
                        show = true;
                }
                if (show) {
                    addTeamData(teamsTable, item.uiIcon, team -> (team.hasCore() && team.core().items.get(item) > 0) ? UI.formatAmount(team.core().items.get(item)) : "-");
                }
            }
        }

        if (showUnit) {
            teamsTable.image().color(getThemeColor()).fillX().height(1).colspan(999).padTop(3).padBottom(3).row();
            for (UnitType unit : content.units()) {
                boolean show = false;
                for (Teams.TeamData team : teams) {
                    if (team.countType(unit) > 0)
                        show = true;
                }
                if (show) {
                    addTeamData(teamsTable, unit.uiIcon, team -> team.countType(unit) > 0 ? String.valueOf(team.countType(unit)) : "-");
                }
            }
        }
    }

    private void updateTeamList() {
        teams = Vars.state.teams.getActive().copy();
        if (state.rules.waveTimer) teams.addUnique(state.rules.waveTeam.data());
        forceShowTeam.each(team -> teams.addUnique(team));
    }

    private void addTeamDataCheck(Table table, TextureRegion icon, Floatf<Teams.TeamData> checked) {
        addTeamDataCheck(table, icon, checked, 1f);
    }

    private void addTeamDataCheck(Table table, TextureRegion icon, Floatf<Teams.TeamData> checked, float defaultValue) {
        boolean show = false;
        boolean sameValue = true;
        float value = -1;
        for (Teams.TeamData teamData : teams) {
            if (checked.get(teamData) != defaultValue) show = true;
            if (value == -1) value = checked.get(teamData);
            else if (value != checked.get(teamData)) sameValue = false;
        }
        if (show) {
            if (sameValue) addTeamData(table, icon, NumberFormat.autoFixed(value));
            else addTeamData(table, icon, team -> NumberFormat.autoFixed(checked.get(team)));
        }
    }

    private void addTeamDataCheck(Table table, TextureRegion icon, Boolf<Teams.TeamData> checked, Boolean defaultValue) {
        /** 检测是否一样，如果一样就只显示一个数值 */
        boolean show = false;
        boolean sameRevert = true;
        for (Teams.TeamData teamData : teams) {
            if (checked.get(teamData) != defaultValue) show = true;
            else sameRevert = false;
        }
        if (show) {
            if (sameRevert) addTeamData(table, icon, sameRevert ? " +" : " x");
            else addTeamData(table, icon, team -> checked.get(team) ? "+" : "×");
        }
    }

    private void addTeamData(Table table, TextureRegion icon, String value) {
        /** 只显示一个数值 */
        table.image(icon).size(15, 15).left();
        table.label(() -> getThemeColorCode() + value).fillX().get().setFontScale(fontScl);
        table.row();
    }

    private void addTeamData(Table table, TextureRegion icon, RFuncs.Stringf<Teams.TeamData> teamDataStringf) {
        /** 通用情况 */
        table.image(icon).size(15, 15).left();
        addTeamData(table, teamDataStringf);
    }

    private void addTeamData(Table table, RFuncs.Stringf<Teams.TeamData> teamDataStringf) {
        /** 通用情况 */
        for (Teams.TeamData teamData : teams) {
            table.label(() -> "[#" + teamData.team.color + "]" + teamDataStringf.get(teamData)).get().setFontScale(fontScl);
        }
        table.row();
    }

}