package mindustry.world.blocks.defense;

import arc.*;
import arc.Core;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.annotations.Annotations.*;
import mindustry.content.*;
import mindustry.core.UI;
import mindustry.entities.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.logic.*;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class ForceProjector extends Block{
    public final int timerUse = timers++;
    public float phaseUseTime = 350f;

    public float phaseRadiusBoost = 80f;
    public float phaseShieldBoost = 400f;
    public float radius = 101.7f;
    public int sides = 6;
    public float shieldRotation = 0f;
    public float shieldHealth = 700f;
    public float cooldownNormal = 1.75f;
    public float cooldownLiquid = 1.5f;
    public float cooldownBrokenBase = 0.35f;
    public float coolantConsumption = 0.1f;
    public boolean consumeCoolant = true;
    public Effect absorbEffect = Fx.absorb;
    public Effect shieldBreakEffect = Fx.shieldBreak;
    public @Load("@-top") TextureRegion topRegion;

    //TODO json support
    public @Nullable Consume itemConsumer, coolantConsumer;

    protected static ForceBuild paramEntity;
    protected static Effect paramEffect;
    protected static final Cons<Bullet> shieldConsumer = bullet -> {
        if(bullet.team != paramEntity.team && bullet.type.absorbable && Intersector.isInRegularPolygon(((ForceProjector)(paramEntity.block)).sides, paramEntity.x, paramEntity.y, paramEntity.realRadius() * 2f, ((ForceProjector)(paramEntity.block)).shieldRotation, bullet.x, bullet.y)){
            bullet.absorb();
            paramEffect.at(bullet);
            paramEntity.hit = 1f;
            paramEntity.buildup += bullet.damage;
        }
    };

    public ForceProjector(String name){
        super(name);
        update = true;
        solid = true;
        group = BlockGroup.projectors;
        hasPower = true;
        hasLiquids = true;
        hasItems = true;
        envEnabled |= Env.space;
        ambientSound = Sounds.shield;
        ambientSoundVolume = 0.08f;

        if(consumeCoolant){
            consume(coolantConsumer = new ConsumeCoolant(coolantConsumption)).boost().update(false);
        }
    }

    @Override
    public void init(){
        updateClipRadius(radius + phaseRadiusBoost + 3f);
        super.init();
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("shield", (ForceBuild entity) -> new Bar(() ->  UI.simpleFormat("盾容", shieldHealth + phaseShieldBoost * entity.phaseHeat - entity.buildup,shieldHealth + phaseShieldBoost * entity.phaseHeat),
                () -> Pal.accent,
                () -> entity.broken ? 0f : 1f - entity.buildup / (shieldHealth + phaseShieldBoost * entity.phaseHeat))
                .blink(Color.white));
    }

    @Override
    public boolean outputsItems(){
        return false;
    }

    @Override
    public void setStats(){
        boolean consItems = itemConsumer != null;

        if(consItems) stats.timePeriod = phaseUseTime;
        super.setStats();
        stats.add(Stat.shieldHealth, shieldHealth, StatUnit.none);
        stats.add(Stat.cooldownTime, (int) (shieldHealth / cooldownBrokenBase / 60f), StatUnit.seconds);
        stats.add(Stat.regenSpeed, cooldownNormal * Time.toSeconds, StatUnit.perSecond);
        if (consumeCoolant || consItems) {
            stats.add(Stat.booster, table -> {
                table.row();
                table.table(c -> {
                    if (consumeCoolant) {
                        for(Liquid liquid : content.liquids()){
                            if(!consumesLiquid(liquid)) continue;

                            c.image(liquid.uiIcon).size(3 * 8).padRight(4).right().top();
                            c.add(liquid.localizedName).padRight(10).left().top();
                            c.table(Tex.underline, bt -> {
                                bt.left().defaults().padRight(3).left();
                                bt.add(Core.bundle.format("shield.regenspeed", cooldownLiquid * (1f + (liquid.heatCapacity - 0.4f) * 0.9f)));
                            }).left().padTop(-9);
                            c.row();
                        }
                    }
                    if (consItems) {
                        if (itemConsumer instanceof ConsumeItems ci) {
                            Item item = ci.items[0].item;
                            c.image(item.uiIcon).size(3 * 8).padRight(4).right().top();
                            c.add(item.localizedName).padRight(10).left().top();
                            c.table(Tex.underline, bt -> {
                                bt.left().defaults().padRight(3).left();
                                bt.add(Core.bundle.format("shield.phaseboost", phaseRadiusBoost / tilesize, phaseShieldBoost));
                            }).left().padTop(-9);
                            c.row();
                        }
                    }
                }).colspan(table.getColumns());
            });
        }
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);

        Draw.color(Pal.gray);
        Lines.stroke(3f);
        Lines.poly(x * tilesize + offset, y * tilesize + offset, sides, radius, shieldRotation);
        Draw.color(player.team().color);
        Lines.stroke(1f);
        Lines.poly(x * tilesize + offset, y * tilesize + offset, sides, radius, shieldRotation);
        Draw.color();
    }

    public class ForceBuild extends Building implements Ranged{
        public boolean broken = true;
        public float buildup, radscl, hit, warmup, phaseHeat;

        @Override
        public float range(){
            return realRadius();
        }

        @Override
        public boolean shouldAmbientSound(){
            return !broken && realRadius() > 1f;
        }

        @Override
        public void onRemoved(){
            float radius = realRadius();
            if(!broken && radius > 1f) Fx.forceShrink.at(x, y, radius, team.color);
            super.onRemoved();
        }

        @Override
        public void pickedUp(){
            super.pickedUp();
            radscl = warmup = 0f;
        }

        @Override
        public boolean inFogTo(Team viewer){
            return false;
        }

        @Override
        public void updateTile(){
            boolean phaseValid = itemConsumer != null && itemConsumer.efficiency(this) > 0;

            phaseHeat = Mathf.lerpDelta(phaseHeat, Mathf.num(phaseValid), 0.1f);

            if(phaseValid && !broken && timer(timerUse, phaseUseTime) && efficiency > 0){
                consume();
            }

            radscl = Mathf.lerpDelta(radscl, broken ? 0f : warmup, 0.05f);

            if(Mathf.chanceDelta(buildup / shieldHealth * 0.1f)){
                Fx.reactorsmoke.at(x + Mathf.range(tilesize / 2f), y + Mathf.range(tilesize / 2f));
            }

            warmup = Mathf.lerpDelta(warmup, efficiency, 0.1f);

            if(buildup > 0){
                float scale = !broken ? cooldownNormal : cooldownBrokenBase;

                //TODO I hate this system
                if(coolantConsumer != null){
                    if(coolantConsumer.efficiency(this) > 0){
                        coolantConsumer.update(this);
                        scale *= (cooldownLiquid * (1f + (liquids.current().heatCapacity - 0.4f) * 0.9f));
                    }
                }

                buildup -= delta() * scale;
            }

            if(broken && buildup <= 0){
                broken = false;
            }

            if(buildup >= shieldHealth + phaseShieldBoost * phaseHeat && !broken){
                broken = true;
                buildup = shieldHealth;
                shieldBreakEffect.at(x, y, realRadius(), team.color);
                if(team != state.rules.defaultTeam){
                    Events.fire(Trigger.forceProjectorBreak);
                }
            }

            if(hit > 0f){
                hit -= 1f / 5f * Time.delta;
            }

            deflectBullets();
        }

        public void deflectBullets(){
            float realRadius = realRadius();

            if(realRadius > 0 && !broken){
                paramEntity = this;
                paramEffect = absorbEffect;
                Groups.bullet.intersect(x - realRadius, y - realRadius, realRadius * 2f, realRadius * 2f, shieldConsumer);
            }
        }

        public float realRadius(){
            return (radius + phaseHeat * phaseRadiusBoost) * radscl;
        }

        @Override
        public double sense(LAccess sensor){
            if(sensor == LAccess.heat) return buildup;
            return super.sense(sensor);
        }

        @Override
        public void draw(){
            super.draw();

            if(buildup > 0f){
                Draw.alpha(buildup / shieldHealth * 0.75f);
                Draw.z(Layer.blockAdditive);
                Draw.blend(Blending.additive);
                Draw.rect(topRegion, x, y);
                Draw.blend();
                Draw.z(Layer.block);
                Draw.reset();
            }
            
            drawShield();
        }

        public void drawShield(){
            if(!broken){
                float radius = realRadius();

                Draw.color(team.color, Color.white, Mathf.clamp(hit));

                if(renderer.animateShields){
                    Draw.z(Layer.shields + 0.001f * hit);
                    Fill.poly(x, y, sides, radius, shieldRotation);
                }else{
                    Draw.z(Layer.shields);
                    Lines.stroke(1.5f);
                    Draw.alpha(0.09f + Mathf.clamp(0.08f * hit));
                    Fill.poly(x, y, sides, radius, shieldRotation);
                    Draw.alpha(1f);
                    Lines.poly(x, y, sides, radius, shieldRotation);
                    Draw.reset();
                }
            }

            Draw.reset();
        }

        @Override
        public void drawBars() {
            super.drawBars();
            if (Core.settings.getBool("blockBars_mend") && buildup > 0) {
                Draw.color(Color.black, 0.3f);
                Lines.stroke(4f);
                Lines.line(x - block.size * tilesize / 2f * 0.6f, y + block.size * tilesize / 2.5f,
                        x + block.size * tilesize / 2f * 0.6f, y + block.size * tilesize / 2.5f);
                Draw.color(broken ? Pal.remove : Pal.stat, 1f);
                Lines.stroke(2f);
                Lines.line(x - block.size * tilesize / 2f * 0.6f, y + block.size * tilesize / 2.5f,
                        x + 0.6f * (0.5f - buildup / (shieldHealth + phaseShieldBoost * phaseHeat)) * block.size * tilesize, y + block.size * tilesize / 2.5f);
                Draw.color();
            }
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.bool(broken);
            write.f(buildup);
            write.f(radscl);
            write.f(warmup);
            write.f(phaseHeat);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            broken = read.bool();
            buildup = read.f();
            radscl = read.f();
            warmup = read.f();
            phaseHeat = read.f();
        }
    }
}
