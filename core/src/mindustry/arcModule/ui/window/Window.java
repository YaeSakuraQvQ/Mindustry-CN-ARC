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
import arc.util.Align;
import arc.util.Log;
import arc.util.Tmp;
import mindustry.gen.Icon;
import mindustry.logic.LCanvas;
import mindustry.ui.Styles;
import mindustry.Vars;

public class Window {
    public WindowTable table;
    WindowManager manager;
    String title;
    Table controlBar, cont;
    TextureRegion icon;
    Image iconImage;
    boolean removed = false;

    Window() {
        this(Vars.ui.WindowManager);
    }

    Window(WindowManager manager) {
        this("Title", manager);
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

    public Window setBody(Table body) {
        cont.clear();
        cont.add(body);
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
        if (removed) return;
        removed = true;
        table.remove();
    }

    private enum ResizeDirection {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        LEFT,
        RIGHT,
        TOP,
        BOTTOM
    }

    private class WindowTable extends Table {
        private float offsetX, offsetY, dragStartX, dragStartY;
        private boolean dragging, resizing;
        private ResizeDirection resizeDirection;
        public WindowTable(float width, float height) {
            setColor(new Color(127, 127, 127, 255));
            margin(0f);
            touchable = Touchable.childrenOnly;
            setBackground(Styles.black3);
            table(t -> {
                controlBar = t;
                iconImage = new Image(icon);
                t.add(iconImage).size(12).pad(10, 12, 10, 12);
                t.table(tt -> {
                    tt.label(() -> title).grow();
                    tt.addListener(new InputListener(){
                        float lastx, lasty;

                        @Override
                        public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                            Vec2 v = localToParentCoordinates(Tmp.v1.set(x, y));
                            lastx = v.x;
                            lasty = v.y;
                            toFront();
                            return true;
                        }

                        @Override
                        public void touchDragged(InputEvent event, float x, float y, int pointer){
                            Vec2 v = localToParentCoordinates(Tmp.v1.set(x, y));
                            translation.add(v.x - lastx, v.y - lasty);
                            lastx = v.x;
                            lasty = v.y;
                        }
                    });
                }).grow();
                t.button("一", Styles.cleart, this::remove).size(46, 28).pad(1).right();
                t.button(Icon.copySmall, Styles.clearNonei, () -> {
                }).size(46, 28).pad(1).right();
                t.button(Icon.cancelSmall, Styles.clearNonei, this::remove).size(46, 28).pad(1).right();
                t.touchable = Touchable.enabled;
            }).height(32).top().growX().row();
            pane(t -> {
                t.setFillParent(true);
                t.add().grow();
                t.label(() -> String.valueOf(resizeDirection)).growX();
                cont = t;
                t.touchable = Touchable.enabled;
            }).grow();
            /*addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                    offsetX = x;
                    offsetY = y;
                    dragStartX = getX(Align.bottomLeft);
                    dragStartY = getY(Align.bottomLeft);

                    if (x < 10 && y < 10) {
                        resizing = true;
                        resizeDirection = ResizeDirection.BOTTOM_LEFT;
                    } else if (x > getWidth() - 10 && y < 10) {
                        resizing = true;
                        resizeDirection = ResizeDirection.BOTTOM_RIGHT;
                    } else if (x < 10 && y > getHeight() - 10) {
                        resizing = true;
                        resizeDirection = ResizeDirection.TOP_LEFT;
                    } else if (x > getWidth() - 10 && y > getHeight() - 10) {
                        resizing = true;
                        resizeDirection = ResizeDirection.TOP_RIGHT;
                    } else if (x < 10) {
                        resizing = true;
                        resizeDirection = ResizeDirection.LEFT;
                    } else if (x > getWidth() - 10) {
                        resizing = true;
                        resizeDirection = ResizeDirection.RIGHT;
                    } else if (y < 10) {
                        resizing = true;
                        resizeDirection = ResizeDirection.BOTTOM;
                    } else if (y > getHeight() - 10) {
                        resizing = true;
                        resizeDirection = ResizeDirection.TOP;
                    } else {
                        dragging = true;
                    }
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    if (dragging) {
                        setPosition(dragStartX + x - offsetX, dragStartY + y - offsetY);
                    } else if (resizing) {
                        float dx = x - offsetX;
                        float dy = y - offsetY;

                        switch (resizeDirection) {
                            case TOP_LEFT -> {
                                setSize(getWidth() - dx, getHeight() - dy);
                                setPosition(getX(Align.bottomLeft) + dx, getY(Align.bottomLeft) + dy);
                            }
                            case TOP_RIGHT -> {
                                setSize(getWidth() + dx, getHeight() - dy);
                                setPosition(getX(Align.bottomLeft), getY(Align.bottomLeft) + dy);
                            }
                            case BOTTOM_LEFT -> {
                                setSize(getWidth() - dx, getHeight() + dy);
                                setPosition(getX(Align.bottomLeft) + dx, getY(Align.bottomLeft));
                            }
                            case BOTTOM_RIGHT -> setSize(getWidth() + dx, getHeight() + dy);
                            case LEFT -> {
                                setSize(getWidth() - dx, getHeight());
                                setPosition(getX(Align.bottomLeft) + dx, getY(Align.bottomLeft));
                            }
                            case RIGHT -> setSize(getWidth() + dx, getHeight());
                            case TOP -> {
                                setSize(getWidth(), getHeight() - dy);
                                setPosition(getX(Align.bottomLeft), getY(Align.bottomLeft) + dy);
                            }
                            case BOTTOM -> setSize(getWidth(), getHeight() + dy);
                        }
                    }
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                    dragging = false;
                    resizing = false;
                }
            });*/
            setWidth(width);
            setHeight(height);
        }

        @Override
        public boolean remove() {
            manager.removeWindow(Window.this);
            return super.remove();
        }
    }
}

