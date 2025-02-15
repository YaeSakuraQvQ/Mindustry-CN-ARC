package mindustry.ui.dialogs;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.files.ZipFi;
import arc.func.Boolc;
import arc.func.Cons;
import arc.graphics.Texture;
import arc.graphics.Texture.TextureFilter;
import arc.input.KeyCode;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.*;
import arc.scene.ui.TextButton.TextButtonStyle;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.OS;
import arc.util.Scaling;
import arc.util.Strings;
import arc.util.io.Streams;
import mindustry.content.TechTree;
import mindustry.content.TechTree.TechNode;
import mindustry.core.GameState;
import mindustry.core.Version;
import mindustry.ctype.UnlockableContent;
import mindustry.game.EventType.Trigger;
import mindustry.game.Schematics;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Shaders;
import mindustry.input.DesktopInput;
import mindustry.input.MobileInput;
import mindustry.ui.Styles;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static arc.Core.*;
import static mindustry.Vars.*;

public class SettingsMenuDialog extends BaseDialog{
    public SettingsTable graphics;
    public SettingsTable game;
    public SettingsTable sound;
    public SettingsTable arc;
    public SettingsTable forcehide;
    public SettingsTable specmode;
    public SettingsTable cheating;
    public SettingsTable main;

    private Table prefs;
    private Table menu;
    private BaseDialog dataDialog;
    private Seq<SettingsCategory> categories = new Seq<>();

    public SettingsMenuDialog(){
        super(bundle.get("settings", "Settings"));
        addCloseButton();

        cont.add(main = new SettingsTable());
        shouldPause = true;

        shown(() -> {
            back();
            rebuildMenu();
        });

        onResize(() -> {
            graphics.rebuild();
            sound.rebuild();
            game.rebuild();
            updateScrollFocus();
        });

        cont.clearChildren();
        cont.remove();
        buttons.remove();

        menu = new Table(Tex.button);

        game = new SettingsTable();
        graphics = new SettingsTable();
        sound = new SettingsTable();
        arc = new SettingsTable();
        forcehide = new SettingsTable();
        specmode = new SettingsTable();
        cheating = new SettingsTable();

        prefs = new Table();
        prefs.top();
        prefs.margin(14f);

        rebuildMenu();

        prefs.clearChildren();
        prefs.add(menu);

        dataDialog = new BaseDialog("@settings.data");
        dataDialog.addCloseButton();

        dataDialog.cont.table(Tex.button, t -> {
            t.defaults().size(280f, 60f).left();
            TextButtonStyle style = Styles.flatt;

            t.button("@settings.cleardata", Icon.trash, style, () -> ui.showConfirm("@confirm", "@settings.clearall.confirm", () -> {
                ObjectMap<String, Object> map = new ObjectMap<>();
                for(String value : Core.settings.keys()){
                    if(value.contains("usid") || value.contains("uuid")){
                        map.put(value, Core.settings.get(value, null));
                    }
                }
                Core.settings.clear();
                Core.settings.putAll(map);

                for(Fi file : dataDirectory.list()){
                    file.deleteDirectory();
                }

                Core.app.exit();
            })).marginLeft(4);

            t.row();

            t.button("@settings.clearsaves", Icon.trash, style, () -> {
                ui.showConfirm("@confirm", "@settings.clearsaves.confirm", () -> {
                    control.saves.deleteAll();
                });
            }).marginLeft(4);

            t.row();

            t.button("@settings.clearresearch", Icon.trash, style, () -> {
                ui.showConfirm("@confirm", "@settings.clearresearch.confirm", () -> {
                    universe.clearLoadoutInfo();
                    for(TechNode node : TechTree.all){
                        node.reset();
                    }
                    content.each(c -> {
                        if(c instanceof UnlockableContent u){
                            u.clearUnlock();
                        }
                    });
                    settings.remove("unlocks");
                });
            }).marginLeft(4);

            t.row();

            t.button("@settings.clearcampaignsaves", Icon.trash, style, () -> {
                ui.showConfirm("@confirm", "@settings.clearcampaignsaves.confirm", () -> {
                    for(var planet : content.planets()){
                        for(var sec : planet.sectors){
                            sec.clearInfo();
                            if(sec.save != null){
                                sec.save.delete();
                                sec.save = null;
                            }
                        }
                    }

                    for(var slot : control.saves.getSaveSlots().copy()){
                        if(slot.isSector()){
                            slot.delete();
                        }
                    }
                });
            }).marginLeft(4);

            t.row();

            t.button("@data.export", Icon.upload, style, () -> {
                if(ios){
                    Fi file = Core.files.local("mindustry-data-export.zip");
                    try{
                        exportData(file);
                    }catch(Exception e){
                        ui.showException(e);
                    }
                    platform.shareFile(file);
                }else{
                    platform.showFileChooser(false, "zip", file -> {
                        try{
                            exportData(file);
                            ui.showInfo("@data.exported");
                        }catch(Exception e){
                            e.printStackTrace();
                            ui.showException(e);
                        }
                    });
                }
            }).marginLeft(4);

            t.row();

            t.button("@data.import", Icon.download, style, () -> ui.showConfirm("@confirm", "@data.import.confirm", () -> platform.showFileChooser(true, "zip", file -> {
                try{
                    importData(file);
                    control.saves.resetSave();
                    state = new GameState();
                    Core.app.exit();
                }catch(IllegalArgumentException e){
                    ui.showErrorMessage("@data.invalid");
                }catch(Exception e){
                    e.printStackTrace();
                    if(e.getMessage() == null || !e.getMessage().contains("too short")){
                        ui.showException(e);
                    }else{
                        ui.showErrorMessage("@data.invalid");
                    }
                }
            }))).marginLeft(4);

            if(!mobile){
                t.row();
                t.button("@data.openfolder", Icon.folder, style, () -> Core.app.openFolder(Core.settings.getDataDirectory().absolutePath())).marginLeft(4);
            }

            t.row();

            t.button("@crash.export", Icon.upload, style, () -> {
                if(settings.getDataDirectory().child("crashes").list().length == 0 && !settings.getDataDirectory().child("last_log.txt").exists()){
                    ui.showInfo("@crash.none");
                }else{
                    if(ios){
                        Fi logs = tmpDirectory.child("logs.txt");
                        logs.writeString(getLogs());
                        platform.shareFile(logs);
                    }else{
                        platform.showFileChooser(false, "txt", file -> {
                            try{
                                file.writeBytes(getLogs().getBytes(Strings.utf8));
                                app.post(() -> ui.showInfo("@crash.exported"));
                            }catch(Throwable e){
                                ui.showException(e);
                            }
                        });
                    }
                }
            }).marginLeft(4);
        });

        row();
        pane(prefs).grow().top();
        row();
        add(buttons).fillX();

        addSettings();
    }

    String getLogs(){
        Fi log = settings.getDataDirectory().child("last_log.txt");

        StringBuilder out = new StringBuilder();
        for(Fi fi : settings.getDataDirectory().child("crashes").list()){
            out.append(fi.name()).append("\n\n").append(fi.readString()).append("\n");
        }

        if(log.exists()){
            out.append("\nlast log:\n").append(log.readString());
        }

        return out.toString();
    }

    /** Adds a custom settings category, with the icon being the specified region. */
    public void addCategory(String name, @Nullable String region, Cons<SettingsTable> builder){
        categories.add(new SettingsCategory(name, region == null ? null : new TextureRegionDrawable(atlas.find(region)), builder));
    }

    /** Adds a custom settings category, for use in mods. The specified consumer should add all relevant mod settings to the table. */
    public void addCategory(String name, @Nullable Drawable icon, Cons<SettingsTable> builder){
        categories.add(new SettingsCategory(name, icon, builder));
    }

    /** Adds a custom settings category, for use in mods. The specified consumer should add all relevant mod settings to the table. */
    public void addCategory(String name, Cons<SettingsTable> builder){
        addCategory(name, (Drawable)null, builder);
    }

    public Seq<SettingsCategory> getCategories(){
        return categories;
    }

    void rebuildMenu(){
        menu.clearChildren();

        TextButtonStyle style = Styles.flatt;

        float marg = 8f, isize = iconMed;

        menu.defaults().size(300f, 60f);
            menu.button("@settings.game", Icon.settings, style, isize, () -> visible(0)).marginLeft(marg).row();
            menu.row();
            menu.button("@settings.graphics", Icon.image, style, isize, () -> visible(1)).marginLeft(marg).row();
            menu.row();
            menu.button("@settings.sound", Icon.filters, style, isize, () -> visible(2)).marginLeft(marg).row();
            menu.row();
            menu.button("@settings.arc", Icon.star,style,isize, () -> visible(3)).marginLeft(marg).row();
            menu.row();
            menu.button("@settings.forcehide", Icon.eyeSmall,style,isize, () -> visible(4)).marginLeft(marg).row();
            menu.row();
            menu.button("@settings.specmode", Icon.info,style,isize, () -> visible(5)).marginLeft(marg).row();
            menu.row();
            menu.button("@settings.cheating", Icon.lock,style,isize, () -> visible(6)).marginLeft(marg).row();
            menu.row();
            menu.button("@settings.language", Icon.chat, style, isize, ui.language::show).marginLeft(marg).row();
            if(!mobile || Core.settings.getBool("keyboard")){
            menu.button("@settings.controls", Icon.move, style, isize, ui.controls::show).marginLeft(marg).row();
            }
        menu.button("@settings.data", Icon.save, style, isize, () -> dataDialog.show()).marginLeft(marg).row();

        int i = Core.settings.getInt("changelogreaded") == changeLogRead ? 7 : 1;
        for(var cat : categories){
            int index = i;
            if(cat.icon == null){
                menu.button(cat.name, style, () -> visible(index)).marginLeft(marg).row();
            }else{
                menu.button(cat.name, cat.icon, style, isize, () -> visible(index)).with(b -> ((Image)b.getChildren().get(1)).setScaling(Scaling.fit)).marginLeft(marg).row();
            }
            i++;
        }
        }

    void addSettings(){
        game.checkPref("uuidShow", true);

            sound.sliderPref("musicvol", 100, 0, 100, 1, i -> i + "%");
            sound.sliderPref("sfxvol", 100, 0, 100, 1, i -> i + "%");
            sound.sliderPref("ambientvol", 100, 0, 100, 1, i -> i + "%");

            game.addCategory("arcCSave");
            game.checkPref("savecreate", true);
            game.checkPref("save_more_map", false);
            game.sliderPref("saveinterval", 60, 10, 5 * 120, 10, i -> Core.bundle.format("setting.seconds", i));

            game.addCategory("arcCAssist");
            game.checkPref("autotarget", true);
            if (mobile) {
                if (!ios) {
                    game.checkPref("keyboard", false, val -> {
                        control.setInput(val ? new DesktopInput() : new MobileInput());
                        input.setUseKeyboard(val);
                    });
                    if (Core.settings.getBool("keyboard")) {
                        control.setInput(new DesktopInput());
                        input.setUseKeyboard(true);
                    }
                } else {
                    Core.settings.put("keyboard", false);
                }
            }
            //the issue with touchscreen support on desktop is that:
            //1) I can't test it
            //2) the SDL backend doesn't support multitouch
        /*else{
            game.checkPref("touchscreen", false, val -> control.setInput(!val ? new DesktopInput() : new MobileInput()));
            if(Core.settings.getBool("touchscreen")){
                control.setInput(new MobileInput());
            }
        }*/

            if (!mobile) {
                game.checkPref("crashreport", true);
            }

            game.sliderPref("maxSchematicSize", 32, 32, 256, 1, v -> v == 256 ? "无限" : String.valueOf(v));
            game.checkPref("savecreate", true);
            game.checkPref("blockreplace", true);
            game.checkPref("conveyorpathfinding", true);
            game.checkPref("shiftCopyIcon", true);

            game.checkPref("backgroundpause", true);
            game.checkPref("buildautopause", false);

            game.checkPref("doubletapmine", false);
            game.checkPref("commandmodehold", true);

            if (!ios) {
                game.checkPref("modcrashdisable", true);
            }

            if (steam) {
                game.sliderPref("playerlimit", 16, 2, 32, i -> {
                    platform.updateLobby();
                    return i + "";
                });

                if (!Version.modifier.contains("beta")) {
                    game.checkPref("steampublichost", false, i -> {
                        platform.updateLobby();
                    });
                }
            }

            game.addCategory("arcCHint");
            game.checkPref("hints", true);
            game.checkPref("logichints", true);
            game.checkPref("console", false);

            graphics.sliderPref("menuPic", 1, 1, 5, 1, s -> {
                if(s==1){return "klp-1";}
                else if(s==2){return "虚无-1";}
                else if(s==3){return "klp-3";}
                else if(s==4){return "klp-4";}
                else{return "klp-5";}
            });

            graphics.addCategory("arcCOverview");

            graphics.sliderPref("fpscap", 240, 10, 245, 5, s -> (s > 240 ? Core.bundle.get("setting.fpscap.none") : Core.bundle.format("setting.fpscap.text", s)));
            int[] lastUiScale = {settings.getInt("uiscale", 100)};

            graphics.sliderPref("uiscale", 100, 25, 300, 5, s -> {
                //if the user changed their UI scale, but then put it back, don't consider it 'changed'
                Core.settings.put("uiscalechanged", s != lastUiScale[0]);
                return s + "%";
            });

            graphics.sliderPref("chatopacity", 100, 0, 100, 5, s -> s + "%");

            if (!mobile) {
                graphics.checkPref("vsync", true, b -> Core.graphics.setVSync(b));
                graphics.checkPref("fullscreen", false, b -> {
                    if (b && settings.getBool("borderlesswindow")) {
                        Core.graphics.setWindowedMode(Core.graphics.getWidth(), Core.graphics.getHeight());
                        settings.put("borderlesswindow", false);
                        graphics.rebuild();
                    }

                    if (b) {
                        Core.graphics.setFullscreen();
                    } else {
                        Core.graphics.setWindowedMode(Core.graphics.getWidth(), Core.graphics.getHeight());
                    }
                });

                graphics.checkPref("borderlesswindow", false, b -> {
                    if (b && settings.getBool("fullscreen")) {
                        Core.graphics.setWindowedMode(Core.graphics.getWidth(), Core.graphics.getHeight());
                        settings.put("fullscreen", false);
                        graphics.rebuild();
                    }
                    Core.graphics.setBorderless(b);
                });

                Core.graphics.setVSync(Core.settings.getBool("vsync"));

                if (Core.settings.getBool("fullscreen")) {
                    Core.app.post(() -> Core.graphics.setFullscreen());
                }

                if (Core.settings.getBool("borderlesswindow")) {
                    Core.app.post(() -> Core.graphics.setBorderless(true));
                }
            } else if (!ios) {
                graphics.checkPref("landscape", false, b -> {
                    if (b) {
                        platform.beginForceLandscape();
                    } else {
                        platform.endForceLandscape();
                    }
                });

                if (Core.settings.getBool("landscape")) {
                    platform.beginForceLandscape();
                }
            }

            graphics.addCategory("arcCgamewindow");
            graphics.checkPref("fps", false);
            graphics.checkPref("override_boss_shown", false);

            graphics.checkPref("minimap", !mobile);
            graphics.sliderPref("minimapSize", 140, 40, 400, 10, i -> i + "");
            graphics.checkPref("minimapTools", !mobile);
            graphics.checkPref("position", false);
            graphics.checkPref("mouseposition", false);
            graphics.sliderPref("chatopacity", 100, 0, 100, 5, i -> i > 0 ? i + "%" : "关闭");

            graphics.addCategory("arcCgameview");
            graphics.checkPref("blockstatus", false);
            graphics.checkPref("playerchat", true);
            graphics.checkPref("alwaysshowdropzone", false);
            graphics.checkPref("showFlyerSpawn", false);
            graphics.checkPref("showFlyerSpawnLine", false);
            graphics.sliderPref("lasersopacity", 100, 0, 100, 5, s -> {
                if (ui.settings != null) {
                    Core.settings.put("preferredlaseropacity", s);
                }
                return s + "%";
            });
            graphics.sliderPref("bridgeopacity", 100, 0, 100, 5, i -> i > 0 ? i + "%" : "关闭");
            graphics.sliderPref("HiddleItemTransparency", 0, 0, 100, 2, i -> i > 0 ? i + "%" : "关闭");
            graphics.checkPref("playerindicators", true);
            graphics.checkPref("indicators", true);

            graphics.addCategory("arcCGraphicsOther");
            graphics.checkPref("smoothcamera", true);
            graphics.sliderPref("screenshake", 4, 0, 8, i -> (i / 4f) + "x");
            graphics.checkPref("skipcoreanimation", false);
            if (!mobile) {
                Core.settings.put("swapdiagonal", false);
            }

            arc.addCategory("arcHudToolbox");
            arc.sliderPref("AuxiliaryTable", 0, 0, 3, 1, s -> {
                if (s == 0) {
                    return "关闭";
                } else if (s == 1) {
                    return "左上-右";
                } else if (s == 2) {
                    return "左上-下";
                } else if (s == 3) {
                    return "右上-下";
                } else {
                    return "";
                }
            });
            arc.checkPref("showAdvanceToolTable", false);
            arc.checkPref("arcSpecificTable", true);
            arc.checkPref("logicSupport", true);
            arc.checkPref("powerStatistic", true);
            arc.sliderPref("arccoreitems", 3, 0, 3, 1, s -> {
                if (s == 0) {
                    return "不显示";
                } else if (s == 1) {
                    return "资源状态";
                } else if (s == 2) {
                    return "兵种状态";
                } else {
                    return "显示资源和兵种";
                }
            });
            arc.sliderPref("arcCoreItemsCol", 5, 4, 15, 1, i -> i + "列");
            arc.checkPref("showFloatingSettings", false);
            arc.sliderPref("arcDetailInfo", 1, 0, 1, 1, s -> {
                if (s == 0) {
                    return "详细模式";
                } else if (s == 1) {
                    return "简略模式";
                } else {
                    return s + "";
                }
            });

            arc.addCategory("arcAddBlockInfo");
            arc.sliderPref("overdrive_zone", 0, 0, 100, 2, i -> i > 0 ? i + "%" : "关闭");
            arc.sliderPref("mend_zone", 0, 0, 100, 2, i -> i > 0 ? i + "%" : "关闭");
            arc.checkPref("blockdisabled", false);
            arc.checkPref("blockBars", false);
            arc.sliderPref("blockbarminhealth", 0, 0, 4000, 50, i -> i + "[red]HP");
            arc.checkPref("blockBars_mend", false);
            arc.checkPref("arcdrillmode", false);
            arc.checkPref("arcDrillProgress", false);
            arc.checkPref("arcchoiceuiIcon", false);
            arc.checkPref("hidedisplays", false);
            arc.checkPref("arclogicbordershow", true);
            arc.checkPref("arcPlacementEffect", false);

            arc.addCategory("arcMassDriverInfo");
            arc.sliderPref("mass_driver_line_alpha", 100, 0, 100, 1, i -> i > 0 ? i + "%" : "关闭");
            arc.sliderPref("mass_driver_line_interval", 40, 8, 400, 4, i -> i / 8f + "格");
            arc.stringInput("mass_driver_line_color", "ff8c66");

            arc.addCategory("arcAddTurretInfo");
            arc.checkPref("showTurretAmmo", false);
            arc.checkPref("showTurretAmmoAmount", false);
            arc.checkPref("arcTurretPlacementItem", false);
            arc.checkPref("arcTurretPlaceCheck", false);
            arc.sliderPref("turretShowRange", 0, 0, 3, 1, s -> {
                if (s == 0) {
                    return "关闭";
                } else if (s == 1) {
                    return "仅对地";
                } else if (s == 2) {
                    return "仅对空";
                } else if (s == 3) {
                    return "全部";
                } else {
                    return "";
                }
            });
            arc.checkPref("forcenofog", false);
            arc.checkPref("ignoreNoAmmo", true);
            arc.sliderPref("turretAlertRange", 0, 0, 30, 1, i -> i > 0 ? i + "格" : "关闭");
            arc.checkPref("blockWeaponTargetLine", false);
            arc.checkPref("blockWeaponTargetLineWhenIdle", false);

            arc.addCategory("arcAddUnitInfo");
            arc.sliderPref("unitweapon_range", 0, 0, 100, 1, i -> i > 0 ? i + "%" : "关闭");
            arc.sliderPref("unitAlertRange", 0, 0, 30, 1, s -> {
                if (s == 0) {
                    return "关闭";
                } else if (s == 30) {
                    return "一直开启";
                } else {
                    return s + "格";
                }
            });
            arc.checkPref("unitWeaponTargetLine", false);

            arc.checkPref("unitItemCarried", false);
            arc.checkPref("unithitbox", false);


            arc.checkPref("unitLogicMoveLine", false);
            arc.checkPref("unitLogicTimerBars", false);
            arc.checkPref("arcBuildInfo",false);
            arc.checkPref("unitbuildplan", false);

            arc.addCategory("arcRTSSupporter");
            arc.checkPref("arcCommandTable", true);
            arc.checkPref("alwaysShowUnitRTSAi", false);
            arc.sliderPref("rtsWoundUnit", 0, 0, 100, 2, s -> s + "%");

            arc.addCategory("arcShareinfo");
            arc.sliderPref("chatValidType", 0, 0, 3, 1, s -> {
                if (s == 0) {
                    return "原版模式";
                } else if (s == 1) {
                    return "纯净聊天";
                } else if (s == 2) {
                    return "服务器记录";
                } else if (s == 3) {
                    return "全部记录";
                } else {
                    return s + "";
                }
            });
            arc.checkPref("arcPlayerList", true);
            arc.checkPref("ShowInfoPopup", true);
            arc.checkPref("arcAutoGG", false);
            arc.checkPref("arcShareWaveInfo", false);
            arc.checkPref("arcAlwaysTeamColor", false);
            arc.checkPref("arcSelfName", false);

            arc.addCategory("arcPlayerEffect");
            arc.stringInput("playerEffectColor", "ffd37f");
            arc.sliderPref("unitTargetType", 0, 0, 5, 1, s -> {
                if (s == 0) {
                    return "关闭";
                } else if (s == 1) {
                    return "虚圆";
                } else if (s == 2) {
                    return "攻击";
                } else if (s == 3) {
                    return "攻击去边框";
                } else if (s == 4) {
                    return "圆十字";
                } else if (s == 5) {
                    return "十字";
                } else {
                    return s + "";
                }
            });
            arc.sliderPref("superUnitEffect", 0, 0, 2, 1, s -> {
                if (s == 0) {
                    return "关闭";
                } else if (s == 1) {
                    return "独一无二";
                } else if (s == 2) {
                    return "全部玩家";
                } else {
                    return s + "";
                }
            });
            arc.sliderPref("playerEffectCurStroke", 0, 1, 30, 1, i -> (float) i / 10f + "Pixel(s)");

            arc.addCategoryS("雷达扫描设置 [lightgray](PC按键，手机辅助器)");
            arc.sliderPref("radarMode", 0, 0, 30, 1, s -> {
                if (s == 0) return "关闭";
                else if (s == 30) return "一键开关";
                else {
                    return "[lightgray]x[white]" + Strings.autoFixed(s * 0.2f, 1) + "倍搜索";
                }
            });
            arc.sliderPref("radarSize", 0, 0, 50, 1, s -> {
                if (s == 0) return "固定大小";
                else {
                    return "[lightgray]x[white]" + Strings.autoFixed(s * 0.1f, 1) + "倍";
                }
            });

            arc.addCategory("developerMode");
            arc.checkPref("arcDisableModWarning", false);
            arc.sliderPref("menuFlyersCount", 0, -15, 50, 5, i -> i + "");
            arc.checkPref("menuFlyersRange", false);
            arc.checkPref("menuFlyersFollower", false);
            arc.checkPref("menuFloatText", true);
            arc.checkPref("showUpdateDialog", true);
            arc.checkPref("arcInfSchem", false);

            //////////forcehide
            forcehide.addCategory("arcCDisplayBlock");
            forcehide.sliderPref("blockRenderLevel", 2, 0, 2, 1, s -> {
                if (s == 0) {
                    return "隐藏全部建筑";
                } else if (s == 1) {
                    return "只显示建筑状态";
                } else if (s == 2) {
                    return "全部显示";
                } else {
                    return s + "";
                }
            });
            forcehide.checkPref("displayblock", true);
            forcehide.addCategory("arcCDisplayUnit");
            forcehide.checkPref("unitHealthBar", false);
            forcehide.checkPref("alwaysShowPlayerUnit", false);
            forcehide.checkPref("showminebeam", true);
            forcehide.sliderPref("unitTransparency", 100, 0, 100, 5, i -> i > 0 ? i + "%" : "关闭");
            forcehide.sliderPref("minhealth_unitshown", 0, 0, 2500, 50, i -> i + "[red]HP");
            forcehide.sliderPref("minhealth_unithealthbarshown", 0, 0, 2500, 100, i -> i + "[red]HP");
            forcehide.addCategory("arcCDisplayEffect");
            forcehide.checkPref("bulletShow", true);
            forcehide.checkPref("drawlight", true);
            forcehide.checkPref("effects", true);
            forcehide.checkPref("bloom", true, val -> renderer.toggleBloom(val));
            forcehide.sliderPref("bloomintensity", 6, 0, 16, i -> (int) (i / 4f * 100f) + "%");
            forcehide.sliderPref("bloomblur", 2, 1, 16, i -> i + "x");
            forcehide.checkPref("forceEnableDarkness", true);
            forcehide.checkPref("destroyedblocks", true);
            forcehide.checkPref("showweather", true);
            forcehide.checkPref("animatedwater", true);

            if (Shaders.shield != null) {
                forcehide.checkPref("animatedshields", !mobile);
                forcehide.checkPref("staticShieldsBorder", false);
            }

            forcehide.checkPref("atmosphere", !mobile);

            if (!mobile) {
                forcehide.checkPref("vsync", true, b -> Core.graphics.setVSync(b));
            }
            Core.graphics.setVSync(Core.settings.getBool("vsync"));

            //iOS (and possibly Android) devices do not support linear filtering well, so disable it
            if (!ios) {
                graphics.checkPref("linear", !mobile, b -> {
                    for (Texture tex : Core.atlas.getTextures()) {
                        TextureFilter filter = b ? TextureFilter.linear : TextureFilter.nearest;
                        tex.setFilter(filter, filter);
                    }
                });
            } else {
                settings.put("linear", false);
            }

            if (Core.settings.getBool("linear")) {
                for (Texture tex : Core.atlas.getTextures()) {
                    TextureFilter filter = TextureFilter.linear;
                    tex.setFilter(filter, filter);
                }
            }

            forcehide.checkPref("pixelate", false, val -> {
                if (val) {
                    Events.fire(Trigger.enablePixelation);
                }
            });

            //////////specmode
            specmode.addCategory("moreContent");
            specmode.checkPref("modMode", false);
            specmode.sliderPref("itemSelectionHeight", 4, 4, 12, i -> i + "行");
            specmode.sliderPref("itemSelectionWidth", 4, 4, 12, i -> i + "列");
            specmode.sliderPref("blockInventoryWidth", 3, 3, 16, i -> i + "");
            specmode.sliderPref("editorBrush", 4, 3, 12, i -> i + "");

            specmode.addCategory("personalized");
            specmode.checkPref("colorizedContent", false);
            specmode.sliderPref("fontSet", 0, 0, 2, 1, s -> {
                if (s == 0) {
                    return "原版字体";
                } else if (s == 1) return "[violet]LC[white]の[cyan]萌化字体包";
                else if (s == 2) return "[violet]9527[white]の[cyan]楷体包";
                else {
                    return s + "";
                }
            });
            specmode.sliderPref("fontSize", 10, 5, 25, 1, i -> "x " + Strings.fixed(i * 0.1f, 1));
            specmode.stringInput("themeColor", "ffd37f");
            specmode.addCategory("specGameMode");
            specmode.checkPref("autoSelSchematic", false);
            specmode.checkPref("researchViewer", false);
            specmode.checkPref("bossKeyValid",false);
            specmode.checkPref("arcShareMedia",true);
            specmode.checkPref("developMode", false);
            //////////cheating
            cheating.addCategory("arcWeakCheat");
            cheating.checkPref("forceIgnoreAttack", false);
            cheating.checkPref("allBlocksReveal", false);
            cheating.checkPref("worldCreator", false);
            cheating.checkPref("overrideSkipWave", false);
            cheating.checkPref("forceConfigInventory", false);
            cheating.checkPref("alwaysPickup", false);
            cheating.addCategory("arcStrongCheat");
            cheating.checkPref("showOtherTeamResource", false);
            cheating.checkPref("showOtherTeamState", false);
            cheating.checkPref("selectTeam", false);
            cheating.checkPref("playerNeedShooting", false);
            cheating.checkPref("otherCheat", false);
            if (OS.isMac) {
                graphics.checkPref("macnotch", false);
            }

            if (!mobile) {
                Core.settings.put("swapdiagonal", false);
            }

    }

    public void exportData(Fi file) throws IOException{
        Seq<Fi> files = new Seq<>();
        files.add(Core.settings.getSettingsFile());
        files.addAll(customMapDirectory.list());
        files.addAll(saveDirectory.list());
        files.addAll(modDirectory.list());
        files.addAll(schematicDirectory.list());
        String base = Core.settings.getDataDirectory().path();

        //add directories
        for(Fi other : files.copy()){
            Fi parent = other.parent();
            while(!files.contains(parent) && !parent.equals(settings.getDataDirectory())){
                files.add(parent);
            }
        }

        try(OutputStream fos = file.write(false, 2048); ZipOutputStream zos = new ZipOutputStream(fos)){
            for(Fi add : files){
                String path = add.path().substring(base.length());
                if(add.isDirectory()) path += "/";
                //fix trailing / in path
                path = path.startsWith("/") ? path.substring(1) : path;
                zos.putNextEntry(new ZipEntry(path));
                if(!add.isDirectory()){
                    try(var stream = add.read()){
                        Streams.copy(stream, zos);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    public void importData(Fi file){
        Fi dest = Core.files.local("zipdata.zip");
        file.copyTo(dest);
        Fi zipped = new ZipFi(dest);

        Fi base = Core.settings.getDataDirectory();
        if(!zipped.child("settings.bin").exists()){
            throw new IllegalArgumentException("Not valid save data.");
        }

        //delete old saves so they don't interfere
        saveDirectory.deleteDirectory();

        //purge existing tmp data, keep everything else
        tmpDirectory.deleteDirectory();

        zipped.walk(f -> f.copyTo(base.child(f.path())));
        dest.delete();

        //clear old data
        settings.clear();
        //load data so it's saved on exit
        settings.load();
    }

    private void back(){
        rebuildMenu();
        prefs.clearChildren();
        prefs.add(menu);
    }

    private void visible(int index){
        prefs.clearChildren();
        Seq<Table> tables = new Seq<>();
           tables.addAll(game, graphics, sound, arc,forcehide,specmode, cheating);
        for(var custom : categories){
            tables.add(custom.table);
        }
        prefs.add(tables.get(index));
    }

    @Override
    public void addCloseButton(){
        buttons.button("@back", Icon.left, () -> {
            if(prefs.getChildren().first() != menu){
                back();
            }else{
                hide();
            }
        }).size(210f, 64f);

        keyDown(key -> {
            if(key == KeyCode.escape || key == KeyCode.back){
                if(prefs.getChildren().first() != menu){
                    back();
                }else{
                    hide();
                }
            }
        });
    }

    public interface StringProcessor{
        String get(int i);
    }

    public static class SettingsCategory{
        public String name;
        public @Nullable Drawable icon;
        public Cons<SettingsTable> builder;
        public SettingsTable table;

        public SettingsCategory(String name, Drawable icon, Cons<SettingsTable> builder){
            this.name = name;
            this.icon = icon;
            this.builder = builder;

            table = new SettingsTable();
            builder.get(table);
        }
    }

    public static class SettingsTable extends Table{
        protected Seq<Setting> list = new Seq<>();

        public SettingsTable(){
            left();
        }

        public Seq<Setting> getSettings(){
            return list;
        }

        public void pref(Setting setting){
            list.add(setting);
            rebuild();
        }

        public SliderSetting sliderPref(String name, int def, int min, int max, StringProcessor s){
            return sliderPref(name, def, min, max, 1, s);
        }

        public SliderSetting sliderPref(String name, int def, int min, int max, int step, StringProcessor s){
            SliderSetting res;
            list.add(res = new SliderSetting(name, def, min, max, step, s));
            settings.defaults(name, def);
            rebuild();
            return res;
        }

        public void checkPref(String name, boolean def){
            list.add(new CheckSetting(name, def, null));
            settings.defaults(name, def);
            rebuild();
        }

        public void checkPref(String name, boolean def, Boolc changed){
            list.add(new CheckSetting(name, def, changed));
            settings.defaults(name, def);
            rebuild();
        }

        public void addCategory(String name){
            list.add(new Divider(name, bundle.get("category." + name + ".name")));
            rebuild();
        }

        public void addCategoryS(String name){
            list.add(new Divider(name, name));
            rebuild();
        }

        public void stringInput(String name, String def){
            list.add(new StringSetting(name, def, def));
            settings.defaults(name, def);
            rebuild();
        }

        public void textPref(String name, String def){
            list.add(new TextSetting(name, def, null));
            settings.defaults(name, def);
            rebuild();
        }

        public void textPref(String name, String def, Cons<String> changed){
            list.add(new TextSetting(name, def, changed));
            settings.defaults(name, def);
            rebuild();
        }

        public void areaTextPref(String name, String def){
            list.add(new AreaTextSetting(name, def, null));
            settings.defaults(name, def);
            rebuild();
        }

        public void areaTextPref(String name, String def, Cons<String> changed){
            list.add(new AreaTextSetting(name, def, changed));
            settings.defaults(name, def);
            rebuild();
        }

        public void rebuild(){
            clearChildren();

            for(Setting setting : list){
                setting.add(this);
            }

            button(bundle.get("settings.reset", "Reset to Defaults"), () -> {
                for(Setting setting : list){
                    if(setting.name == null || setting.title == null) continue;
                    settings.remove(setting.name);
                }
                rebuild();
            }).margin(14).width(240f).pad(6);
        }

        public abstract static class Setting{
            public String name;
            public String title;
            public @Nullable String description;

            public Setting(String name){
                this.name = name;
                String winkey = "setting." + name + ".name.windows";
                title = OS.isWindows && bundle.has(winkey) ? bundle.get(winkey) : bundle.get("setting." + name + ".name", name);
                description = bundle.getOrNull("setting." + name + ".description");
            }

            public abstract void add(SettingsTable table);

            public void addDesc(Element elem){
                ui.addDescTooltip(elem, description);
            }
        }

        public static class CheckSetting extends Setting{
            boolean def;
            Boolc changed;

            public CheckSetting(String name, boolean def, Boolc changed){
                super(name);
                this.def = def;
                this.changed = changed;
            }

            @Override
            public void add(SettingsTable table){
                CheckBox box = new CheckBox(title);

                box.update(() -> box.setChecked(settings.getBool(name)));

                box.changed(() -> {
                    settings.put(name, box.isChecked());
                    if(changed != null){
                        changed.get(box.isChecked());
                    }
                });

                box.left();
                addDesc(table.add(box).left().padTop(3f).get());
                table.row();
            }
        }

        public static class SliderSetting extends Setting{
            int def, min, max, step;
            StringProcessor sp;

            public SliderSetting(String name, int def, int min, int max, int step, StringProcessor s){
                super(name);
                this.def = def;
                this.min = min;
                this.max = max;
                this.step = step;
                this.sp = s;
            }

            @Override
            public void add(SettingsTable table){
                Slider slider = new Slider(min, max, step, false);

                slider.setValue(settings.getInt(name));

                Label value = new Label("", Styles.outlineLabel);
                Table content = new Table();
                content.add(title, Styles.outlineLabel).left().growX().wrap();
                content.add(value).padLeft(10f).right();
                content.margin(3f, 33f, 3f, 33f);
                content.touchable = Touchable.disabled;

                slider.changed(() -> {
                    settings.put(name, (int)slider.getValue());
                    value.setText(sp.get((int)slider.getValue()));
                });

                slider.change();

                addDesc(table.stack(slider, content).width(Math.min(Core.graphics.getWidth() / 1.2f, 460f)).left().padTop(4f).get());
                table.row();
            }
        }

        public static class Divider extends Setting {

            Divider(String name, String title) {
                super(name);
                this.title = title;
            }

            @Override
            public void add(SettingsTable table) {
                table.add(title).color(getThemeColor()).colspan(4).pad(10).padTop(15).padBottom(4).row();
                table.image().color(getThemeColor()).fillX().height(3).colspan(4).padTop(0).padBottom(10).row();
            }
        }

        public static class StringSetting extends Setting {
            String def, value;

            StringSetting(String name, String def, String value) {
                super(name);
                this.def = def;
                this.value = value;
            }

            @Override
            public void add(SettingsTable table) {
                value = settings.getString(name);
                Table field = new Table();
                field.add(bundle.get("setting."+name+".name"));
                field.field(value, text -> {
                    settings.put(name, text);
                    value = text;
                }).padLeft(30);
                table.add(field).left().pad(10).padTop(15).padBottom(4).row();
            }
        }

        public static class TextSetting extends Setting{
            String def;
            Cons<String> changed;

            public TextSetting(String name, String def, Cons<String> changed){
                super(name);
                this.def = def;
                this.changed = changed;
            }

            @Override
            public void add(SettingsTable table){
                TextField field = new TextField();

                field.update(() -> field.setText(settings.getString(name)));

                field.changed(() -> {
                    settings.put(name, field.getText());
                    if(changed != null){
                        changed.get(field.getText());
                    }
                });

                Table prefTable = table.table().left().padTop(3f).get();
                prefTable.add(field);
                prefTable.label(() -> title);
                addDesc(prefTable);
                table.row();
            }
        }

        public static class AreaTextSetting extends TextSetting{
            public AreaTextSetting(String name, String def, Cons<String> changed){
                super(name, def, changed);
            }

            @Override
            public void add(SettingsTable table){
                TextArea area = new TextArea("");
                area.setPrefRows(5);

                area.update(() -> {
                    area.setText(settings.getString(name));
                    area.setWidth(table.getWidth());
                });

                area.changed(() -> {
                    settings.put(name, area.getText());
                    if(changed != null){
                        changed.get(area.getText());
                    }
                });

                addDesc(table.label(() -> title).left().padTop(3f).get());
                table.row().add(area).left();
                table.row();
            }
        }
    }
}