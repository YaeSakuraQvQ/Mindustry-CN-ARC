package mindustry.arcModule.ui.dialogs;

import arc.Core;
import arc.Events;
import arc.audio.Music;
import arc.files.Fi;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.graphics.Texture;
import arc.graphics.g2d.TextureRegion;
import arc.scene.ui.Button;
import arc.scene.ui.Slider;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.util.Http;
import arc.util.Log;
import arc.util.Nullable;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Icon;
import mindustry.gen.Player;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import java.security.MessageDigest;
import java.util.Date;

import static mindustry.Vars.platform;
import static mindustry.Vars.ui;
import static mindustry.arcModule.RFuncs.getPrefix;

public class MusicDialog extends BaseDialog{
    public static final String version = "1.0.2";
    public static final String ShareType = "[pink]<Music>";
    private MusicApi api;
    private MusicApi[] apis;
    private Music player;
    private String search = "";
    private Runnable loadSearch, loadStatus;
    private MusicSet searchResult;
    private int searchPage, allPage;
    private float progress, vol;
    private Slider progressBar;
    private MusicInfo nowMusic;
    private boolean loaded, updating, paused;
    private BaseDialog switchDialog;
    public MusicDialog() {
        super("松鼠音乐");
        try {
            searchResult = new MusicSet((byte)0);
            nowMusic = new MusicInfo();
            searchPage = allPage = 1;
            progress = 0;
            apis = new MusicApi[]{null, new Squirrel(), new KuGouWeb()};
            api = apis[2];
            setup();
            addCloseButton();
            button(Icon.info, () -> ui.showInfo("[pink]松鼠音乐v" + version + "\n[gold]松鼠制作\n[cyan]松鼠站:squi2rel.tk"));
            buttons.button("切换api", this::switchApi);
            buttons.button("上传本地音乐", this::upload).disabled(b -> !api.canUpload());
            onResize(this::setup);
            shown(() -> {
                if(check()) {
                    setup();
                }
            });
            player = new Music();
            vol = 100;
            loaded = true;
            switchDialog = new BaseDialog("切换api");
            switchDialog.cont.label(() -> "当前api: (" + api.getId() + ")" + api.getName());
            switchDialog.cont.row();
            switchDialog.cont.pane(p -> {
                for(MusicApi capi : apis) {
                    if(capi != null) {
                        byte id = capi.getId();
                        String name = capi.getName();
                        p.button("(" + id + ")" + name, () -> {
                            api = apis[id];
                            setup();
                            switchDialog.hide();
                        }).width(200f);
                        p.row();
                    }
                }
            }).growX().growY();
            switchDialog.addCloseButton();
            Events.run(EventType.Trigger.update, this::updateProgress);
        } catch (Exception err) {
            Events.on(EventType.ClientLoadEvent.class, e -> ui.showException("松鼠音乐加载失败!", err));
        }
    }
    private void switchApi(){
        switchDialog.show();
    }
    private boolean check() {
        if(!loaded) {
            cont.clear();
            cont.add("[red]松鼠音乐加载失败");
        }
        return loaded;
    }
    private void search() {
        api.search(search, searchPage, res -> {
            searchResult = res;
            Core.app.post(() -> loadSearch.run());
        });
    }
    private void play(MusicInfo info) {
        player.stop();
        nowMusic = info;
        try {
            Http.get(info.url, r -> {
                Fi tmp = new Fi("/tmp/squirrel.mp3");
                tmp.writeBytes(r.getResult());
                player.stop();
                player.pause(false);
                player.load(tmp);
                player.play();
                loadStatus.run();
            });
        } catch (Exception e) {
            ui.showException("发生了一个错误", e);
        }
    }
    private void playDirectly(Fi f) throws Exception {
        player.stop();
        player.pause(false);
        player.load(f);
        player.play();
        loadStatus.run();
    }
    private void share(MusicInfo info) {
        Vars.ui.showConfirm("分享","确认分享到聊天框?",() -> api.getMusicInfo(info.id, fullinfo -> Call.sendChatMessage(getPrefix("pink", "Music") + " " + fullinfo.src + "M" + fullinfo.id)));
    }
    private void setup() {
        cont.top();
        cont.clear();
        if(api.canSearch()) {
            cont.table(s -> {
                s.left();
                s.field(search, res -> search = res).growX().get();
                s.button(Icon.zoom, () -> {
                    searchPage = 1;
                    search();
                });
            }).fillX().padBottom(4);
            cont.row();
            cont.pane(t -> {
                t.top();
                loadSearch = () -> {
                    if (searchResult.count > 0) {
                        float width = Core.graphics.getWidth() / Scl.scl() * 0.9f;
                        t.clear();
                        for (byte i = 0; i < searchResult.count; i++) {
                            MusicInfo info = searchResult.list[i];
                            Button[] buttons = {null};//Variable 'button' might not have been initialized
                            Button button = buttons[0] = t.button(b -> {
                            }, () -> {
                                if (!buttons[0].childrenPressed()) {
                                    player.stop();
                                    api.getMusicInfo(info.id, this::play);
                                }
                            }).width(width).pad(2f).get();
                            Table inner = new Table(Tex.whiteui);
                            inner.setColor(Color.black);
                            button.clearChildren();
                            button.add(inner).growX();
                            inner.add(info.author + " - " + info.name).left().padLeft(10f).wrap().style(Styles.outlineLabel).growX();
                            inner.button(Icon.download, Styles.emptyi, () -> download(info)).margin(3f).pad(2).padTop(6f).top().right();
                            inner.button(Icon.link, Styles.emptyi, () -> share(info)).margin(3f).pad(2).pad(6).top().right();
                            button.row();
                            t.row();
                        }
                        t.table(tt -> {
                            tt.button(Icon.left, () -> {
                                searchPage--;
                                search();
                            }).height(50f).width(150f).disabled(b -> searchPage <= 1);
                            tt.add(searchPage + "/" + allPage);
                            tt.button(Icon.right, () -> {
                                searchPage++;
                                search();
                            }).height(50f).width(150f).disabled(b -> searchPage >= allPage);
                        });
                    }
                };
            }).growY();
            cont.row();
            loadSearch.run();
        } else {
            loadSearch = () -> {};
        }
        cont.bottom();
        cont.table(t -> loadStatus = () -> {
            t.clear();
            t.bottom();
            float width = Core.graphics.getWidth() / Scl.scl() * 0.9f;
            t.table(tt -> {
                tt.left();
                Table[] img = {null};
                tt.table(ttt -> img[0] = ttt).size(64).pad(2f);
                if (nowMusic.img != null) Http.get(nowMusic.img, r -> {
                    Pixmap pix = new Pixmap(r.getResult());
                    Core.app.post(() -> {
                        TextureRegion cache = new TextureRegion(new Texture(pix));
                        pix.dispose();
                        img[0].image(cache).size(64);
                    });
                });
                tt.table(ms -> {
                    ms.table(ttt -> {
                        ttt.add(nowMusic.name == null ? "松鼠音乐" : (nowMusic.author + " - " + nowMusic.name)).left().wrap().style(Styles.outlineLabel).growX();
                        ttt.button(Icon.play, Styles.emptyi, () -> {
                            if (!player.isPlaying()) {
                                if (paused) {
                                    player.pause(false);
                                    paused = false;
                                } else {
                                    if (nowMusic.url != null) {
                                        Fi f = new Fi("/tmp/squirrel.mp3");
                                        if (f.exists()) {
                                            try {
                                                playDirectly(f);
                                            } catch (Exception ignored) {

                                            }
                                        }
                                    }
                                }
                            }
                        }).margin(3f).padTop(6f).top().right();
                        ttt.button(Icon.pause, Styles.emptyi, () -> {
                            player.pause(true);
                            paused = true;
                        }).margin(3f).pad(2).padTop(6f).top().right();
                        ttt.button(Icon.downloadSmall, Styles.emptyi, () -> {
                            if (nowMusic.url != null) {
                                download(nowMusic);
                            }
                        }).margin(3f).pad(2).padTop(6f).top().right();
                        ttt.label(() -> "音量:" + (byte) vol);
                        ttt.button(Icon.upSmall, Styles.emptyi, () -> {
                            vol = Math.min(vol + 10, 100);
                            player.setVolume(vol / 100);
                        }).margin(3f).pad(2).padTop(6f).top().right();
                        ttt.button(Icon.downSmall, Styles.emptyi, () -> {
                            vol = Math.max(vol - 10, 0);
                            player.setVolume(vol / 100);
                        }).margin(3f).pad(2).padTop(6f).top().right();
                        ttt.button(Icon.refreshSmall, Styles.emptyi, () -> {
                            player.setLooping(!player.isLooping());
                            ui.announce("单曲循环已" + (player.isLooping() ? "开启" : "关闭"), 1f);
                        }).margin(3f).pad(2).padTop(6f).top().right().checked(b -> player.isLooping());
                        ttt.button(Icon.linkSmall, Styles.emptyi, () -> {
                            if (nowMusic.url != null) {
                                share(nowMusic);
                            }
                        }).margin(3f).pad(2).pad(6).top().right();
                    }).growX();
                    ms.row();
                    progressBar = ms.slider(0f, nowMusic.length, 0.01f, f -> {
                        if (!updating) {
                            player.setPosition(0);
                            player.setPosition(f);
                            player.pause(false);
                            paused = false;
                        }
                    }).growX().disabled(s -> nowMusic.length == 0).get();
                    ms.row();
                    ms.label(() -> "" + (int) (progress / 60) + ":" + (int) (progress % 60) + (nowMusic.length == 0 ? "" : "/" + (nowMusic.length / 60) + ":" + (nowMusic.length % 60)));
                }).growX().growY();
            }).width(width).height(100f);
        });
        loadStatus.run();
    }
    private void updateProgress() {
        updating = true;
        progress = player.getPosition();
        progressBar.setValue(progress);
        updating = false;
    }
    private void download(MusicInfo info) {
        platform.showFileChooser(false, "下载音乐", "mp3", fi -> api.getMusicInfo(info.id, fullinfo -> Http.get(fullinfo.url, r -> {
            fi.writeBytes(r.getResult());
            Core.app.post(() -> Vars.ui.showInfo("下载成功"));
        })));
    }
    private void upload(){
        platform.showFileChooser(true, "选择音乐文件", "mp3", f -> {
            ui.announce("正在上传...\n很慢!(1-2分钟)\n上传完成后会自动播放");
            api.upload(f, info -> api.getMusicInfo(info.id, this::play));
        });
    }
    public boolean resolveMsg(String msg) {
        return resolveMsg(msg, null);
    }
    public boolean resolveMsg(String msg, @Nullable Player sender) {
        if((!msg.contains(ShareType))||(!loaded)||(!MessageDialog.arcMsgType.music.show)) {
            return false;
        }
        try {
            MessageDialog.addMsg(new MessageDialog.advanceMsg(MessageDialog.arcMsgType.music, msg));
            int start = msg.indexOf(ShareType) + ShareType.length() + 8;
            int split = msg.indexOf("M", start);
            byte src = Byte.parseByte(msg.substring(start, split));
            String id = msg.substring(split + 1);
            MusicApi current = apis[src];
            current.getMusicInfo(id, info -> Core.app.post(() -> ui.showConfirm("松鼠音乐", (sender == null ? "" : sender.name) + "分享了一首来自" + current.getName() + "的音乐" + (info.name == null ? "" : ":\n" + info.author + " - " + info.name) + "\n播放?", () -> current.getMusicInfo(info.id, this::play))), true);
            return true;
        } catch (Exception e) {
            Log.err(e);
            Core.app.post(() -> ui.arcInfo("[orange]音乐读取失败"));
        }
        return false;
    }

    public static class MusicInfo{
        public String name;
        public String author;
        public String url;
        public String img;
        public String id;
        public byte src;
        public int length;
    }

    public static class MusicSet{
        public byte count;
        public MusicInfo[] list;
        public MusicSet(byte length) {
            list = new MusicInfo[length];
        }
        public void add(MusicInfo m) {
            list[count++] = m;
        }
    }

    public interface MusicApi{
        String getName();
        byte getId();
        boolean canSearch();
        boolean canUpload();
        void getMusicInfo(String id, Cons<MusicInfo> callback);
        void getMusicInfo(String id, Cons<MusicInfo> callback, boolean noTip);
        void search(String name, int page, Cons<MusicSet> callback);
        void upload(Fi file, Cons<MusicInfo> callback);
    }
    private class Squirrel implements MusicApi{//松鼠站
        byte num = 1;
        public String getName() {
            return "松鼠站";
        }
        public byte getId() {
            return num;
        }
        public boolean canSearch() {
            return false;
        }
        public boolean canUpload() {
            return true;
        }
        public void getMusicInfo(String id, Cons<MusicInfo> callback) {
            getMusicInfo(id, callback, false);
        }
        public void getMusicInfo(String rid, Cons<MusicInfo> callback, boolean noTip) {
            callback.get(new MusicInfo() {{
                src = num;
                url = "http://squirrel.gq/api/get?id=" + rid;
                id = rid;
            }});
        }
        public void search(String name, int page, Cons<MusicSet> callback) {
            Vars.ui.showInfo("松鼠站不支持搜索");
        }
        public void upload(Fi file, Cons<MusicInfo> callback) {
            Http.HttpRequest post = Http.post("http://squirrel.gq/api/upload");
            post.contentStream=file.read();
            post.header("filename", file.name());
            post.header("size", String.valueOf(file.length()));
            post.header("token", "3ab6950d5970c57f938673911f42fd32");
            post.timeout = 10000;
            post.error(e -> Core.app.post(() -> ui.showException("上传失败", e)));
            post.submit(r -> {
                Core.app.post(() -> ui.announce("上传成功"));
                callback.get(new MusicInfo(){{
                    src = num;
                    id = r.getResultAsString();
                }});
            });
        }
    }
    private class KuGouWeb implements MusicApi{//酷狗网页版api
        byte num = 2;
        public String getName() {
            return "酷狗音乐";
        }
        public byte getId() {
            return num;
        }
        public boolean canSearch(){
            return true;
        }
        public boolean canUpload() {
            return false;
        }
        private final MessageDigest md5;
        public KuGouWeb() throws Exception{
            md5 = MessageDigest.getInstance("MD5");
        }
        public void getMusicInfo(String id, Cons<MusicInfo> callback) {
            getMusicInfo(id, callback, false);
        }
        public void getMusicInfo(String id, Cons<MusicInfo> callback, boolean noTip) {
            Http.HttpRequest req = Http.get("https://wwwapi.kugou.com/yy/index.php?r=play/getdata&encode_album_audio_id="+id);
            req.header("Cookie","kg_mid=b9b171b4281dedf26f547fff82c55a71");
            req.submit(res -> {
                JsonValue j = new JsonReader().parse(res.getResultAsString());
                if(j.getByte("status") == 0) {
                    Core.app.post(() -> {
                        Vars.ui.showErrorMessage("此歌曲无法播放:\nKuGou Error: (" + j.getLong("err_code") + ")");//用throw抓不到
                    });
                    return;
                }
                JsonValue data = j.get("data");
                if(data.getString("play_url").contains("clip")&&!noTip) {
                    Core.app.post(() -> Vars.ui.showConfirm("此歌曲为vip歌曲 仅支持播放部分", () -> callback.get(new MusicInfo() {{
                        name = data.getString("song_name");
                        author = data.getString("author_name");
                        url = data.getString("play_url");
                        img = data.getString("img");
                        id = data.getString("encode_album_audio_id");
                        src = num;
                        length = data.getInt("timelength") / 1000;
                    }})));
                } else {
                    callback.get(new MusicInfo() {{
                        name = data.getString("song_name");
                        author = data.getString("author_name");
                        url = data.getString("play_url");
                        img = data.getString("img");
                        id = data.getString("encode_album_audio_id");
                        src = num;
                        length = data.getInt("timelength") / 1000;
                    }});
                }
            });
        }
        public void search(String name, int page, Cons<MusicSet> callback) {
            long timestamp = new Date().getTime();
            String data = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwtappid=1014bitrate=0clienttime=" + timestamp + "clientver=1000dfid=-filter=10inputtype=0iscorrection=1isfuzzy=0keyword=" + name + "mid=" + timestamp + "page=" + page + "pagesize=10platform=WebFilterprivilege_filter=0srcappid=2919userid=0uuid=" + timestamp + "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt";
            byte[] result = md5.digest(data.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : result) {
                sb.append(String.format("%02x", b));
            }
            Http.get("https://complexsearch.kugou.com/v2/search/song?appid=1014&bitrate=0&clienttime=" + timestamp + "&clientver=1000&dfid=-&filter=10&inputtype=0&iscorrection=1&isfuzzy=0&keyword=" + name + "&mid=" + timestamp + "&page=" + page + "&pagesize=10&platform=WebFilter&privilege_filter=0&srcappid=2919&userid=0&uuid=" + timestamp + "&signature=" + sb, res -> {
                JsonValue j = new JsonReader().parse(res.getResultAsString());
                if(j.getByte("status") == 0) {
                    Core.app.post(() -> Vars.ui.showErrorMessage("搜索出错:\nKuGou Error: (" + j.getLong("error_code") + ") " + j.getString("error_msg")));
                    return;
                }
                JsonValue lists = j.get("data").get("lists");
                allPage = j.get("data").getInt("total") / 10 + 1;
                MusicSet set = new MusicSet((byte)10);
                for(byte i = 0; i < 10; i++) {
                    JsonValue thisMusic = lists.get(i);
                    if(thisMusic == null) {
                        break;
                    }
                    set.add(new MusicInfo() {{
                        name = thisMusic.getString("SongName");
                        author = thisMusic.getString("SingerName");
                        id = thisMusic.getString("EMixSongID");
                        src = num;
                    }});
                }
                callback.get(set);
            });
        }
        public void upload(Fi file, Cons<MusicInfo> callback) {

        }
    }
}
