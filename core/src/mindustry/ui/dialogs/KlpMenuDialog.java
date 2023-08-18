package mindustry.ui.dialogs;

import arc.Core;
import arc.func.Cons;
import arc.scene.style.Drawable;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.Slider;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.editor.MapObjectivesCanvas;
import mindustry.gen.Iconc;
import mindustry.gen.Sounds;
import mindustry.input.Binding;

import static arc.Core.input;
import static mindustry.Vars.getThemeColor;
import static mindustry.Vars.port;
import static mindustry.core.NetClient.connect;

public class KlpMenuDialog extends BaseDialog{
    Table settings = new Table();
    ScrollPane pane = new ScrollPane(settings);

    public KlpMenuDialog(){
        super("快捷设置");
        addCloseKey();

        shouldPause = true;

        shown(this::rebuildMenu);
        onResize(this::rebuildMenu);
    }

    void rebuildMenu() {
        cont.clear();
        settings.clear();

        cont.add("[cyan]快捷功能菜单").top().row();
        cont.add("[lightgray]优先级从上到下").top().row();

        Table unitRotateTable = new Table();
        unitRotateTable.add("单位转向").color(getThemeColor()).colspan(4).pad(10).padTop(15).padBottom(4).row();
        addCheckSetting("[cyan]单位转向选项", "单位转向选项", null, unitRotateTable);
        if (getSettingB("单位转向选项")){
            addCheckSetting("强制看向鼠标", "强制看向鼠标", "我就看着你！", unitRotateTable);
            addCheckSetting("角度锁定", "角度锁定", "使你单位的角度锁定，只能由射击更改角度", unitRotateTable);
            addCheckSetting("建筑时不会面朝建筑", "建筑时不会面朝建筑", "船辅拯救者", unitRotateTable);
            addCheckSetting("瞬间转向", "瞬间转向", "使单位无需转头时间，瞬间转向", unitRotateTable);
            addCheckSetting("推进转向", "推进转向", "推进时看着鼠标方向", unitRotateTable);
            addSliderSetting("单位转向速度", "单位转向速度", 0f, 5f, 20, 1f, "单位转向速度倍率", unitRotateTable);
        }
        settings.add(unitRotateTable).row();

        Table unitMoveTable = new Table();
        unitMoveTable.add("单位移动").color(getThemeColor()).colspan(4).pad(10).padTop(15).padBottom(4).row();
        addCheckSetting("[cyan]单位移动选项", "单位移动选项", null, unitMoveTable);
        if (getSettingB("单位移动选项")){
            addCheckSetting("我爱角度移动", "我爱角度移动", "你是否喜爱anuke的坦克和船只移动？开启他！(优先级大于没有角度移动)", unitMoveTable);
            addCheckSetting("没有角度移动", "没有角度移动", "你是否痛恨anuke的坦克和船只移动？开启他！", unitMoveTable);
            addSliderSetting("单位移动速度", "单位移动速度", 0f, 4f, 20, 1f, "单位移动速度倍率", unitMoveTable);
        }
        settings.add(unitMoveTable).row();

        Table unitPhyTable = new Table();
        unitPhyTable.add("单位碰撞").color(getThemeColor()).colspan(4).pad(10).padTop(15).padBottom(4).row();
        addCheckSetting("[cyan]单位碰撞选项", "单位碰撞选项", null, unitPhyTable);
        if (getSettingB("单位碰撞选项")){
            addCheckSetting("刷怪圈也阻止不了我对你的爱", "刷怪圈也阻止不了我对你的爱", "刷怪圈不排斥单位", unitPhyTable);
            addCheckSetting("单位不碰撞", "单位不碰撞", "叠叠乐..?你不会用他玩除了叠叠乐以外的东西..对吧?", unitPhyTable);
            addCheckSetting("反向击退", "反向击退", "反着来!", unitPhyTable);
            addSliderSetting("被击退倍率", "被击退倍率", 0f, 2f, 8, 1f, "抗击退！", unitPhyTable);
            addCheckSetting("牵引光束反向拉扯", "牵引光束反向拉扯", "反着来!", unitPhyTable);
            addSliderSetting("牵引光束拉扯倍率", "牵引光束拉扯倍率", 0f, 2f, 8, 1f, "抗拉扯！", unitPhyTable);
            addSliderSetting("地图边界拓展", "地图边界拓展", 0f, 250f / 8, 30, 0f, "拓展后单位不会在拓展后边界受到神秘力量(31格外会瞬间死亡！)", unitPhyTable);
            addSliderSetting("屏障立场进入程度", "屏障立场进入程度", 0f, 1.5f, 30, 0f, "屏障立场可进入程度(单位碰撞箱)", unitPhyTable);

        }
        settings.add(unitPhyTable).row();

        Table playerCtrlTable = new Table();
        playerCtrlTable.add("玩家控制").color(getThemeColor()).colspan(4).pad(10).padTop(15).padBottom(4).row();
        addCheckSetting("[cyan]玩家控制选项", "玩家控制选项", null, playerCtrlTable);
        if (getSettingB("玩家控制选项")){
            addCheckSetting("强制控制单位", "强制控制单位", "抢我单位？", playerCtrlTable);
            addCheckSetting("一键装填-工厂", "一键装填-工厂", "使一键装填可以向工厂丢入物品", playerCtrlTable);
            addCheckSetting("一键装填-炮台", "一键装填-炮台", "使一键装填可以向炮台丢入物品", playerCtrlTable);
            addCheckSetting("按住一键装填", "按住一键装填", "按住装填按键 然后疯狂装填！", playerCtrlTable);
            addCheckSetting("自由鼠标", "自由鼠标", "开启后单位aim不会跟随鼠标位置", playerCtrlTable);
            addCheckSetting("按住一键装填自由鼠标", "按住一键装填自由鼠标", "开启后单位aim不会跟随鼠标位置", playerCtrlTable);
            if (getSettingB("自由鼠标") || getSettingB("按住一键装填自由鼠标")) {
                addCheckSetting("一键装填-wz模式", "一键装填-wz模式", "仅在鼠标附近装弹以绕过wz一键装弹，需开启自由鼠标！", playerCtrlTable);
            }
            addCheckSetting("残血丢下荷载", "残血丢下荷载", "残血会丢下荷载!", playerCtrlTable);
            if (getSettingB("残血丢下荷载")) {
                addSliderSetting("残载-血量阈值", "残载-血量阈值", 0f, 1f, 10, 0.25f, "低于血量阈值会丢下荷载!", playerCtrlTable);
            addCheckSetting("爬山无影脚", "爬山无影脚", "单位快速放下拿起荷载", playerCtrlTable);
            addCheckSetting("无迷雾", "无迷雾", "火眼金睛！", playerCtrlTable);    
            }
        }
        settings.add(playerCtrlTable).row();

        Table playerPlayTable = new Table();
        playerPlayTable.add("多人游戏").color(getThemeColor()).colspan(4).pad(10).padTop(15).padBottom(4).row();
        addCheckSetting("[cyan]多人游戏选项", "多人游戏选项", null, playerPlayTable);
        if (getSettingB("多人游戏选项")){
            playerPlayTable.table(table -> {
                playerPlayTable.add("存储名字和uuid").row();
                for (int i = 1; i <= 10; i++) {
                    int finalI = i;
                    table.button("Save Slot " + i, () -> {
                                Core.settings.put("nameSlot" + finalI, Core.settings.getString("name", "[lightgray]<None>"));
                                Core.settings.put("uuidSlot" + finalI, Core.settings.getString("uuid", "[lightgray]<None>"));
                                rebuildMenu();
                            }).tooltip(Core.settings.getString("nameSlot" + finalI, "[lightgray]<None>") + "[yellow]:[white]" + Core.settings.getString("uuidSlot" + finalI, "[lightgray]<None>"))
                            .width(128f).height(64f);
                    if (i == 5) table.row();
                }
            }).row();
            playerPlayTable.table(table -> {
                playerPlayTable.add("读取名字和uuid").row();
                for (int i = 1; i <= 10; i++) {
                    int finalI = i;
                    table.button("Load Slot " + i, () -> {
                                Core.settings.put("name", Core.settings.getString("nameSlot" + finalI, "[lightgray]<None>"));
                                Core.settings.put("uuid", Core.settings.getString("uuidSlot" + finalI, "[lightgray]<None>"));
                            }).tooltip(Core.settings.getString("nameSlot" + finalI, "[lightgray]<None>") + "[yellow]:[white]" + Core.settings.getString("uuidSlot" + finalI, "[lightgray]<None>"))
                            .width(128f).height(64f);
                    if (i == 5) table.row();
                }
            }).row();
            if(Vars.lastServer != null){
                playerPlayTable.add("服务器选项").row();
                playerPlayTable.table(table -> {
                    table.button("复制服务器Ip地址", () -> {
                        Core.app.setClipboardText(Vars.lastServer);
                    }).tooltip(Vars.lastServer).width(128f).height(64f);
                    table.button("重进服务器(非sync)", () -> {
                        String address = Vars.lastServer;
                        String resaddress = address.contains(":") ? address.split(":")[0] : address;
                        int resport = address.contains(":") ? Strings.parseInt(address.split(":")[1]) : port;
                        Vars.net.disconnect();
                        connect(resaddress,resport);
                    }).tooltip(Vars.lastServer).width(128f).height(64f);
                }).row();
            addSliderSetting("隐藏菜单保留时间", "隐藏菜单保留时间", Float.valueOf(0.0f), Float.valueOf(600.0f), 30, Float.valueOf(60.0f), "隐藏菜单后可被唤醒的时间(s)", playerPlayTable);
            addCheckSetting("[已强制开启]不发送ARC版本信息", "不发送ARC版本信息", "拒绝魔改端歧视,从开启功能做起", playerPlayTable);
            addCheckSetting("进服随机uuid与usid", "进服随机uuid与usid", "antiban!", playerPlayTable);
            addCheckSetting("模拟移动端", "模拟移动端", "让服务器认为你是手机版！", playerPlayTable);   
            }
        }
        settings.add(playerPlayTable).row();
        cont.add(pane).center();
    }

    boolean getSettingB(String setting){
        return Core.settings.getBool(setting, false);
    }

    private void addCheckSetting(String text, String setting, @Nullable String desc){
        addCheckSetting(text, setting, desc, settings);
    }

    private void addCheckSetting(String text, String setting, @Nullable String desc, Table settingsTable){
        if (desc == null) desc = text;
        settingsTable.check(text, Core.settings.getBool(setting, false), b -> {
            Core.settings.put(setting, !Core.settings.getBool(setting,false));
            rebuildMenu();
         }).tooltip(desc).row();
    }

    private void addSliderSetting(String text, String setting, Float min, Float max, Integer steps, Float def, @Nullable String desc){
        addSliderSetting(text, setting, min, max, steps, def, desc, settings);
    }
    private void addSliderSetting(String text, String setting, Float min, Float max, Integer steps, Float def, @Nullable String desc, Table settingsTable){
        if (desc == null) desc = text;
        String finalDesc = desc;
        settingsTable.table(table -> {
            table.add(text);
            TextField field = table.field(String.valueOf(Core.settings.getFloat(setting, def)) , t -> {
                if(Strings.canParsePositiveFloat(t)) Core.settings.put(setting, Float.parseFloat(t));
            }).tooltip(finalDesc).valid(Strings::canParsePositiveFloat).get();
            Slider slider = table.slider(min, max, (max - min) / steps, Core.settings.getFloat(setting, def), n -> {
                Core.settings.put(setting, n);
                field.setText(String.valueOf(Core.settings.getFloat(setting, def)));
            }).get();
            table.button("重置", () -> slider.setValue(def));
        });
        settingsTable.row();
    }

        public void addCloseKey(){
        keyDown(key -> {
            if(input.keyDown(Binding.klpMenu) || input.keyDown(Binding.menu)){
                hide();
            }
        });
    }
}

