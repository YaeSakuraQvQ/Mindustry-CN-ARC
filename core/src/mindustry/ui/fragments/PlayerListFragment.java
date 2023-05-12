package mindustry.ui.fragments;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.ImageButton.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.arcModule.ui.dialogs.TeamSelectDialog;
import mindustry.game.Team;
import mindustry.game.Teams;
import mindustry.input.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.net.*;
import mindustry.net.Packets.*;
import mindustry.ui.*;
import mindustry.world.meta.Stat;

import static mindustry.Vars.*;
import static mindustry.arcModule.RFuncs.getPrefix;
import static mindustry.input.InputHandler.follow;

public class PlayerListFragment{
    public Table content = new Table().marginRight(13f).marginLeft(13f);
    private boolean visible = false;
    private Interval timer = new Interval();
    private TextField search;
    public Seq<Player> players = new Seq<>();

    private float buttonSize = 30f;
    private boolean teamMode = false;

    public void build(Group parent){
        content.name = "players";
        parent.fill(cont -> {
            cont.name = "playerlist";
            cont.visible(() -> visible);
            cont.update(() -> {
                if(!(net.active() && state.isGame())){
                    visible = false;
                    return;
                }

                if(visible && timer.get(20)){
                    rebuild();
                    content.pack();
                    content.act(Core.graphics.getDeltaTime());
                    //hacky
                    Core.scene.act(0f);
                }
            });

            cont.table(Tex.buttonTrans, pane -> {
                pane.label(() -> Core.bundle.format(Groups.player.size() == 1 ? "players.single" : "players", Groups.player.size()));
                pane.row();

                search = pane.field(null, text -> rebuild()).grow().pad(8).name("search").maxTextLength(maxNameLength).get();
                search.setMessageText(Core.bundle.get("players.search"));

                pane.row();
                pane.pane(content).grow().scrollX(false);
                pane.row();

                pane.table(menu -> {
                    menu.defaults().growX().height(50f).fillY();
                    menu.name = "menu";

                    if (Core.settings.getBool("arcWayzerServerMode") && Core.settings.getBool("easyJS")) menu.button("快速换队",() -> teamMode = !teamMode);
                    menu.button("@server.bans", ui.bans::show).disabled(b -> net.client());
                    menu.button("@server.admins", ui.admins::show).disabled(b -> net.client());
                    menu.button("@close", this::toggle);
                }).margin(0f).pad(10f).growX();

            }).touchable(Touchable.enabled).margin(14f).minWidth(720f);
        });

        rebuild();
    }

    public void rebuild(){
        content.clear();

        float h = 40f;
        float bs = (h) - 2f;
        boolean found = false;

        players.clear();
        Groups.player.copy(players);

        players.sort(Structs.comps(Structs.comparing(Player::team), Structs.comparingBool(p -> !p.admin)));
        if(search.getText().length() > 0){
            players.filter(p -> Strings.stripColors(p.name().toLowerCase()).contains(search.getText().toLowerCase()));
        }

        for(var user : players){
            found = true;
            NetConnection connection = user.con;

            if(connection == null && net.server() && !user.isLocal()) return;
            if(search.getText().length() > 0 && !user.name().toLowerCase().contains(search.getText().toLowerCase()) && !Strings.stripColors(user.name().toLowerCase()).contains(search.getText().toLowerCase())) return;

            Table button = new Table();
            button.left();
            button.margin(5).marginBottom(10);

            ClickListener listener = new ClickListener();
            Table iconTable = new Table(){
                @Override
                public void draw(){
                    super.draw();
                    Draw.colorMul(user.team().color, listener.isOver() ? 1.3f : 1f);
                    Draw.alpha(parentAlpha);
                    Lines.stroke(Scl.scl(4f));
                    Lines.rect(x, y, width, height);
                    Draw.reset();
                }
            };
            boolean clickable = !(state.rules.fog && state.rules.pvp && user.team() != player.team());

            if(clickable){
                iconTable.addListener(listener);
                iconTable.addListener(new HandCursorListener());
            }
            iconTable.margin(8);
            iconTable.name = user.name();
            iconTable.touchable = Touchable.enabled;

            iconTable.tapped(() -> {
                if(!user.dead() && clickable){
                    Core.camera.position.set(user.unit());
                    ui.arcInfo("定位玩家：" + user.name);
                    if(control.input instanceof DesktopInput input){
                        input.panning = true;
                    }
                }
            });

            var style = new ImageButtonStyle(){{
                down = Styles.none;
                up = Styles.none;
                imageCheckedColor = Pal.accent;
                imageDownColor = Pal.accent;
                imageUpColor = Color.white;
                imageOverColor = Color.lightGray;
            }};

            var ustyle = new ImageButtonStyle(){{
                down = Styles.none;
                up = Styles.none;
                imageDownColor = Pal.accent;
                imageUpColor = Color.white;
                imageOverColor = Color.lightGray;
            }};

            iconTable.margin(4);
            iconTable.add(new Image(user.icon()).setScaling(Scaling.bounded)).grow();
            iconTable.name = user.name();

            if (Core.settings.getBool("arcWayzerServerMode")){
                button.add(iconTable).size(h);
                button.image(Icon.admin).visible(() -> user.admin && !(!user.isLocal() && net.server())).size(bs).get().updateVisibility();
                button.table(
                    t -> {
                        t.labelWrap("[#" + user.color().toString().toUpperCase() + "]" + user.name()).minWidth(380f);
                        t.touchable = Touchable.enabled;
                        t.tapped(()->{
                            Core.app.setClipboardText(user.name);
                            ui.arcInfo("复制昵称：" + user.name);
                        });
                    }
                ).width(400f).pad(10f).left();

                button.add().grow();

                button.button("[#" + getThemeColor() + "]♐", Styles.cleart, () -> {
                    String message = arcAtPlayer(user.name);
                    Call.sendChatMessage(message);
                }).size(buttonSize);

                button.button(String.valueOf(Iconc.lock), Styles.cleart, () -> {
                    if(follow != user){
                        follow = user;
                    }else {
                        follow = null;
                    }
                    if(control.input instanceof DesktopInput){
                        ((DesktopInput) control.input).panning = follow == user;
                        ui.arcInfo("追踪玩家：" + user.name);
                    }
                }).checked(b -> {
                    boolean checked = follow == user;
                    b.setText(checked ? String.valueOf(Iconc.lock) : String.valueOf(Iconc.lockOpen));
                    return checked;
                }).size(buttonSize);

                button.button("[coral]" + Iconc.planeOutline, Styles.cleart,
                        () -> {
                            ui.showConfirm("@confirm", Core.bundle.format("confirmvotekick",  user.name()), () -> {
                                Call.sendChatMessage("/votekick " + user.name());
                            });
                        }).size(buttonSize);

                if((net.server() || player.admin) && !user.isLocal() && (!user.admin || net.server())){
                    button.button("[gold]" + Iconc.zoom, Styles.cleart, () -> Call.adminRequest(user, AdminAction.trace)).size(buttonSize);
                    button.button("[gold]" + Iconc.cancel, Styles.cleart,
                            () -> ui.showConfirm("@confirm", Core.bundle.format("confirmkick",  user.name()), () -> Call.adminRequest(user, AdminAction.kick))).size(buttonSize);
                    button.button("[gold]" + Iconc.hammer, Styles.cleart,
                            () -> ui.showConfirm("@confirm", Core.bundle.format("confirmban",  user.name()), () -> Call.adminRequest(user, AdminAction.ban))).size(buttonSize);
                }
                if (teamMode) {
                    state.teams.getActive().each(teamData -> {
                        button.button("[#" + teamData.team.color + "]" + teamData.team.localized(), Styles.cleart,
                                () -> Call.sendChatMessage("/js Groups.player.find(e=>e.name== \"" + user.name + "\").team(Team.get(" + teamData.team.id + "))")).size(buttonSize);
                    });
                    button.button("[violet]+",Styles.cleart,()->{
                        new TeamSelectDialog(team -> Call.sendChatMessage("/js Groups.player.find(e=>e.name== \"" + user.name + "\").team(Team.get(" + team.id + "))"), user.team()).show();
                    }).size(buttonSize);
                }


            }
            //原版模式
            else{
                button.add(iconTable).size(h);
                button.labelWrap("[#" + user.color().toString().toUpperCase() + "]" + user.name()).width(170f).pad(10);
                button.add().grow();

                button.image(Icon.admin).visible(() -> user.admin && !(!user.isLocal() && net.server())).padRight(5).get().updateVisibility();

                if((net.server() || player.admin) && !user.isLocal() && (!user.admin || net.server())){
                    button.add().growY();

                    button.table(t -> {
                        t.defaults().size(bs);

                    t.button(Icon.hammerSmall, ustyle,
                    () -> ui.showConfirm("@confirm", Core.bundle.format("confirmban",  user.name()), () -> Call.adminRequest(user, AdminAction.ban)));
                    t.button(Icon.cancelSmall, ustyle,
                    () -> ui.showConfirm("@confirm", Core.bundle.format("confirmkick",  user.name()), () -> Call.adminRequest(user, AdminAction.kick)));

                    t.row();

                    t.button(Icon.adminSmall, style, () -> {
                        if(net.client()) return;

                            String id = user.uuid();

                            if(user.admin){
                                ui.showConfirm("@confirm", Core.bundle.format("confirmunadmin",  user.name()), () -> {
                                    netServer.admins.unAdminPlayer(id);
                                    user.admin = false;
                                });
                            }else{
                                ui.showConfirm("@confirm", Core.bundle.format("confirmadmin",  user.name()), () -> {
                                    netServer.admins.adminPlayer(id, user.usid());
                                    user.admin = true;
                                });
                            }
                        }).update(b -> b.setChecked(user.admin))
                            .disabled(b -> net.client())
                            .touchable(() -> net.client() ? Touchable.disabled : Touchable.enabled)
                            .checked(user.admin);

                    t.button(Icon.zoomSmall, ustyle, () -> Call.adminRequest(user, AdminAction.trace));

                    }).padRight(12).size(bs + 10f, bs);
                }else if(!user.isLocal() && !user.admin && net.client() && Groups.player.size() >= 3 && player.team() == user.team()){ //votekick
                    button.add().growY();

                button.button(Icon.hammer, ustyle,
                    () -> ui.showConfirm("@confirm", Core.bundle.format("confirmvotekick",  user.name()),
                    () -> Call.sendChatMessage("/votekick #" + user.id)))
                .size(h);
                }
            }
            content.add(button).padBottom(-6).width(700f).maxHeight(h + 14);
            content.row();
            content.image().height(4f).color(state.rules.pvp|| Core.settings.getBool("arcAlwaysTeamColor") ? user.team().color : Pal.gray).growX();
            content.row();
        }

        if(!found){
            content.add(Core.bundle.format("players.notfound")).padBottom(6).width(350f).maxHeight(h + 14);
        }

        content.marginBottom(5);
    }

    public void toggle(){
        visible = !visible;
        if(visible){
            rebuild();
        }else{
            Core.scene.setKeyboardFocus(null);
            search.clearText();
        }
    }

    private String arcAtPlayer(String name){
        StringBuilder builder = new StringBuilder(getPrefix("coral","AT"));
        builder.append("戳了").append(name).append("[white]一下，并提醒你留意对话框");
        return builder.toString();
    }
}
