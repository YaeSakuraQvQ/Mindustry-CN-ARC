package mindustry.arcModule.ui.dialogs;

import arc.Core;
import arc.Events;
import arc.scene.ui.TextArea;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.ui.dialogs.BaseDialog;

import java.util.ArrayList;

import static mindustry.Vars.mods;

public class ConsoleDialog extends BaseDialog {
    TextArea area;
    public Table pane;
    ArrayList<Cell<?>> needsUpdate = new ArrayList<>();
    ArrayList<String> temp = new ArrayList<>();
    boolean loaded = false;
    public ConsoleDialog(){
        super("控制台");
        build();
        Events.on(EventType.ClientLoadEvent.class, e -> {
            loaded = true;
            for(String str : temp) {
                pane.add(str).left().top();
            }
        });
        addCloseButton();
        build();
        onResize(this::rebuild);
        shown(this::rebuild);
    }
    private void rebuild(){
        float w = Core.graphics.getWidth() / Scl.scl() * 0.9f;
        for(Cell<?> c : needsUpdate) {
            c.width(w);
        }
    }
    private void build(){
        cont.clear();
        cont.table(t -> {
            needsUpdate.add(t.pane(tt -> pane = tt).growY());
            Cell<TextArea> a = cont.area("", tt -> {
            }).growY();
            needsUpdate.add(a);
            area = a.get();
        });
        cont.row();
        cont.table(t -> {
            t.button(Icon.play, () -> {
                pane.add(mods.getScripts().runConsole(area.getText()).replace("[", "[[")).left().top();
                pane.row();
            });
            t.button(Icon.trash, () -> pane.clear());
        });
    }
    public void addMessage(String str){
        if(!loaded) {
            temp.add(str);
            return;
        }
        pane.add(str).left();
    }
}
