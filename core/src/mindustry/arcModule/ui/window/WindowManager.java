package mindustry.arcModule.ui.window;

import arc.Core;
import arc.scene.Group;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.WidgetGroup;

import java.util.ArrayList;

public class WindowManager {
    public Group group = new WidgetGroup();
    ArrayList<Window> windows = new ArrayList<>();
    public WindowManager() {
        Core.scene.add(group);
        group.setFillParent(true);
        group.setTransform(true);
        group.touchable = Touchable.childrenOnly;
    }
    public Window createWindow() {
        Window w = new Window(this);
        Core.scene.add(w.table);
        windows.add(w);
        w.center();
        return w;
    }
    public void removeWindow(Window w) {
        w.remove();
        windows.remove(w);
    }
}
