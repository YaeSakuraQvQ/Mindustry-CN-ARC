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
        group.update(() -> group.toFront());
    }

    public Window createWindow() {
        Window w = new Window(this);
        w.add();
        return w;
    }

    public void addWindow(Window w) {
        if (windows.contains(w)) return;
        windows.add(w);
        group.addChild(w.table);
        w.center();
    }

    public void removeWindow(Window w) {
        if (!w.removed) w.remove();
        windows.remove(w);
    }

    public void closeAll() {
        while (!windows.isEmpty()) {
            windows.get(0).remove();
        }
    }
}
