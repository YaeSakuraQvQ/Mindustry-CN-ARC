package mindustry.arcModule.ui.window;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.Texture;
import arc.graphics.g2d.TextureRegion;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import arc.scene.event.HandCursorListener;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Image;
import arc.scene.ui.layout.Table;
import arc.util.Tmp;
import mindustry.gen.Icon;
import mindustry.ui.Styles;

public class Window {
    public WindowTable table;
    WindowManager manager;
    String title;
    Table controlBar, cont;
    TextureRegion icon;
    Image iconImage;
    boolean removed = false;
    Window(WindowManager manager) {
        this("", manager);
    }
    Window(String title, WindowManager manager) {
        this(title, 600, 400, manager);
    }
    Window(String title, float width, float height, WindowManager manager) {
        this(title, width, height, new TextureRegion((Texture) Core.assets.get("sprites/error.png")), manager);
    }
    Window(String title, float width, float height, TextureRegion icon, WindowManager manager) {
        this.manager = manager;
        this.title = title;
        this.icon = icon;
        table = new WindowTable(width, height);
    }
    public Window setIcon(TextureRegion icon) {
        iconImage.setDrawable(icon);
        return this;
    }
    public Window setTitle(String title) {
        this.title = title;
        return this;
    }
    public void posX(float x) {
        table.x = x;
    }
    public float posX() {
        return table.x;
    }
    public void posY(float y) {
        table.y = y;
    }
    public float posY() {
        return table.y;
    }
    public void pos(float x, float y) {
        table.x = x;
        table.y = y;
    }
    public void center() {
        pos((table.parent.getWidth() - table.getWidth()) / 2, (table.parent.getHeight() - table.getHeight()) / 2);
    }
    public void remove() {
        if(removed) return;
        removed = true;
        table.remove();
    }
    private class WindowTable extends Table {
        public WindowTable(float width, float height) {
            setColor(new Color(127, 127, 127, 255));
            margin(0f);
            touchable = Touchable.enabled;
            table(t -> {
                t.setBackground(Styles.black3);
                t.addListener(new HandCursorListener());
                t.margin(6f);
                t.table(tt -> {
                    controlBar = tt;
                    buildControl(tt);
                    tt.addListener(new InputListener() {
                        float lastx, lasty;
                        @Override
                        public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                            Vec2 v = localToParentCoordinates(Tmp.v1.set(x, y));
                            lastx = v.x;
                            lasty = v.y;
                            toFront();
                            return true;
                        }

                        @Override
                        public void touchDragged(InputEvent event, float x, float y, int pointer) {
                            Vec2 v = localToParentCoordinates(Tmp.v1.set(x, y));
                            translation.add(v.x - lastx, v.y - lasty);
                            Core.settings.put("lrcX", translation.x);
                            Core.settings.put("lrcY", translation.y);
                            lastx = v.x;
                            lasty = v.y;
                        }
                    });
                }).height(32).top();
                cont = table(tt -> tt.pane(ttt -> {}).height(height - 32)).get();
            }).width(width).height(height);
        }
        @Override
        public boolean remove() {
            manager.removeWindow(Window.this);
            return super.remove();
        }
        private void buildControl(Table tt) {
            iconImage = new Image(icon);
            tt.add(iconImage).size(12).pad(10, 12, 10, 12);
            tt.label(() -> title).width(width - 180).get();
            tt.button("一", Styles.cleart, this::remove).size(46,28).pad(1).right();
            tt.button(Icon.copySmall, Styles.clearNonei, () -> {}).size(46,28).pad(1).right();
            tt.button(Icon.cancelSmall, Styles.clearNonei, this::remove).size(46,28).pad(1).right();
            tt.touchable = Touchable.enabled;
        }
    }
}

