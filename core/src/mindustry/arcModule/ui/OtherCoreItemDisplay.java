package mindustry.arcModule.ui;

import arc.Events;
import arc.func.*;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.Time;
import mindustry.Vars;
import mindustry.arcModule.NumberFormat;
import mindustry.arcModule.ui.dialogs.TeamSelectDialog;
import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.core.*;
import mindustry.game.EventType;
import mindustry.game.Team;
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

        Events.on(EventType.WorldLoadEvent.class,e->{
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
        teamsTable.label(() -> "").get().setFontScale(fontScl);
        for (Teams.TeamData team : teams) {
            if (team.team.id > 6)
                teamsTable.label(() -> "[#" + team.team.color + "]#" + team.team.id).get().setFontScale(fontScl);
            else
                teamsTable.label(() -> "[#" + team.team.color + "]" + team.team.localized()).get().setFontScale(fontScl);
        }
        teamsTable.row();
        teamsTable.label(() -> Blocks.coreNucleus.emoji()).get().setFontScale(fontScl);
        for (Teams.TeamData team : teams) {
            teamsTable.label(() -> "[#" + team.team.color + "]" + UI.formatAmount(team.cores.size)).padRight(1).get().setFontScale(fontScl);
        }
        teamsTable.row();
        teamsTable.label(() -> UnitTypes.mono.emoji()).get().setFontScale(fontScl);
        for (Teams.TeamData team : teams) {
            teamsTable.label(() -> "[#" + team.team.color + "]" + UI.formatAmount(team.units.size)).padRight(1).get().setFontScale(fontScl);
        }
        teamsTable.row();
        teamsTable.label(() -> UnitTypes.gamma.emoji()).get().setFontScale(fontScl);
        for (Teams.TeamData team : teams) {
            teamsTable.label(() -> "[#" + team.team.color + "]" + team.players.size).padRight(1).get().setFontScale(fontScl);
        }
        teamsTable.row();

        if (showStat) {
            teamsTable.image().color(getThemeColor()).fillX().height(1).colspan(999).padTop(3).padBottom(3).row();
            addTeamData(teamsTable, Blocks.siliconSmelter.uiIcon, team -> team.rules().cheat, false);
            addTeamData(teamsTable, Blocks.arc.uiIcon, team -> state.rules.blockDamage(team));
            addTeamData(teamsTable, Blocks.titaniumWall.uiIcon, team -> state.rules.blockHealth(team));
            addTeamData(teamsTable, UnitTypes.corvus.uiIcon, team -> state.rules.unitDamage(team));
            addTeamData(teamsTable, Blocks.repairTurret.uiIcon, team -> state.rules.unitHealth(team));
            addTeamData(teamsTable, UnitTypes.zenith.uiIcon, team -> state.rules.unitCrashDamage(team));
            addTeamData(teamsTable, UnitTypes.poly.uiIcon, team -> state.rules.buildSpeed(team));
            addTeamData(teamsTable, Blocks.tetrativeReconstructor.uiIcon, team -> state.rules.unitBuildSpeed(team));
            addTeamData(teamsTable, Blocks.basicAssemblerModule.uiIcon, team -> state.rules.unitCost(team));
            teamsTable.row();
        }

        if (showItem) {
            teamsTable.image().color(getThemeColor()).fillX().height(1).colspan(999).padTop(3).padBottom(3).row();
            boolean[] dispItems = new boolean[content.items().size];
            for (Item item : content.items()) {
                for (Teams.TeamData team : teams) {
                    if (team.hasCore() && team.core().items.get(item) > 0)
                        dispItems[content.items().indexOf(item)] = true;
                }
            }

            for (Item item : content.items()) {
                if (dispItems[content.items().indexOf(item)]) {

                    //teamsTable.label(() -> item.emoji()).padRight(5f).left().get().setFontScale(fontScl);
                    teamsTable.image(item.uiIcon).size(15, 15).left().get();
                    for (Teams.TeamData team : teams) {
                        teamsTable.label(() -> "[#" + team.team.color + "]" + ((team.hasCore() && team.core().items.get(item) > 0) ? UI.formatAmount(team.core().items.get(item)) : "-")).get().setFontScale(fontScl);
                    }
                    teamsTable.row();

                }
            }
        }

        if (showUnit) {
            teamsTable.image().color(getThemeColor()).fillX().height(1).colspan(999).padTop(3).padBottom(3).row();
            boolean[] dispUnits = new boolean[content.units().size];
            for (UnitType unit : content.units()) {
                for (Teams.TeamData team : teams) {
                    if (team.countType(unit) > 0) dispUnits[content.units().indexOf(unit)] = true;
                }
            }

            for (UnitType unit : content.units()) {
                if (dispUnits[content.units().indexOf(unit)]) {

                    //teamsTable.label(() -> unit.emoji()).padRight(5f).left().get().setFontScale(fontScl);
                    teamsTable.image(unit.uiIcon).size(15, 15).left().get();
                    for (Teams.TeamData team : teams) {
                        teamsTable.label(() -> "[#" + team.team.color + "]" + (team.countType(unit) > 0 ? team.countType(unit) : "-")).get().setFontScale(fontScl);
                    }
                    teamsTable.row();

                }
            }
        }
    }

    private void updateTeamList() {
        teams = Vars.state.teams.getActive().copy();
        forceShowTeam.each(team -> teams.addUnique(team));
    }

    private void addTeamData(Table table, TextureRegion icon, Floatf<Team> checked) {
        addTeamData(table, icon, checked, 1f);
    }

    private void addTeamData(Table table, TextureRegion icon, Floatf<Team> checked, float defaultValue) {
        boolean show = false;
        boolean sameValue = true;
        float value = -1;
        for (Teams.TeamData teamData : teams) {
            if (checked.get(teamData.team) != defaultValue) show = true;
            if (value == -1) value = checked.get(teamData.team);
            else if (value != checked.get(teamData.team)) sameValue = false;
        }
        if (show) addTeamDate(table,icon, checked,sameValue,value);
    }

    private void addTeamDate(Table table, TextureRegion icon, Floatf<Team> checked, boolean sameValue, @Nullable float value){
        teamsTable.row();
        table.image(icon).size(15, 15).left();
        if (sameValue) {
            float finalValue = value;
            teamsTable.label(() -> getThemeColorCode() + NumberFormat.autoFixed(finalValue)).expandX().center().get().setFontScale(fontScl);
        } else {
            for (Teams.TeamData teamData : teams) {
                teamsTable.label(() -> "[#" + teamData.team.color + "]" + NumberFormat.autoFixed(checked.get(teamData.team))).get().setFontScale(fontScl);
            }
        }
    }

    private void addTeamData(Table table, TextureRegion icon, Boolf<Team> checked, Boolean defaultValue) {
        boolean show = false;
        boolean sameRevert = true;
        for (Teams.TeamData teamData : teams) {
            if (checked.get(teamData.team) != defaultValue) show = true;
            else sameRevert = false;
        }
        if (show) {
            teamsTable.row();
            table.image(icon).size(15, 15).left();
            if (sameRevert)
                teamsTable.label(() -> getThemeColorCode() + (!defaultValue ? " +" : " x")).fillX().get().setFontScale(fontScl);
            else {
                for (Teams.TeamData teamData : teams) {
                    teamsTable.label(() -> "[#" + teamData.team.color + "]" + (checked.get(teamData.team) ? "+" : "×")).get().setFontScale(fontScl);
                }
            }
        }
    }

}