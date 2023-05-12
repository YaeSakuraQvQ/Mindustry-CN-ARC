package mindustry.world.blocks.power;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.annotations.Annotations.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.logic.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class NuclearReactor extends PowerGenerator{
    public final int timerFuel = timers++;

    public final Vec2 tr = new Vec2();

    public Color lightColor = Color.valueOf("7f19ea");
    public Color coolColor = new Color(1, 1, 1, 0f);
    public Color hotColor = Color.valueOf("ff9575a3");
    /** ticks to consume 1 fuel */
    public float itemDuration = 120;
    /** heating per frame * fullness */
    public float heating = 0.01f;
    /** threshold at which block starts smoking */
    public float smokeThreshold = 0.3f;
    /** heat threshold at which lights start flashing */
    public float flashThreshold = 0.46f;

    /** heat removed per unit of coolant */
    public float coolantPower = 0.5f;

    public Item fuelItem = Items.thorium;

    public @Load("@-top") TextureRegion topRegion;
    public @Load("@-lights") TextureRegion lightsRegion;

    public NuclearReactor(String name){
        super(name);
        itemCapacity = 30;
        liquidCapacity = 30;
        hasItems = true;
        hasLiquids = true;
        rebuildable = false;
        flags = EnumSet.of(BlockFlag.reactor, BlockFlag.generator);
        schematicPriority = -5;
        envEnabled = Env.any;

        explosionShake = 6f;
        explosionShakeDuration = 16f;

        explosionRadius = 19;
        explosionDamage = 1250 * 4;

        explodeEffect = Fx.reactorExplosion;
        explodeSound = Sounds.explosionbig;
    }

    @Override
    public void setStats(){
        super.setStats();

        if(hasItems){
            stats.add(Stat.productionTime, itemDuration / 60f, StatUnit.seconds);
        }
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("heat", (NuclearReactorBuild entity) -> new Bar("bar.heat", Pal.lightOrange, () -> entity.heat));
    }

    public class NuclearReactorBuild extends GeneratorBuild{
        public float heat;
        public float flash;
        public float smoothLight;

        public Vec2[] hexPos = new Vec2[0];
        
        @Override
        public void updateTile(){
            int fuel = items.get(fuelItem);
            float fullness = (float)fuel / itemCapacity;
            productionEfficiency = fullness;

            if(fuel > 0 && enabled){
                heat += fullness * heating * Math.min(delta(), 4f);

                if(timer(timerFuel, itemDuration / timeScale)){
                    consume();
                }
            }else{
                productionEfficiency = 0f;
            }

            if(heat > 0){
                float maxUsed = Math.min(liquids.currentAmount(), heat / coolantPower);
                heat -= maxUsed * coolantPower;
                liquids.remove(liquids.current(), maxUsed);
            }

            if(heat > smokeThreshold){
                float smoke = 1.0f + (heat - smokeThreshold) / (1f - smokeThreshold); //ranges from 1.0 to 2.0
                if(Mathf.chance(smoke / 20.0 * delta())){
                    Fx.reactorsmoke.at(x + Mathf.range(size * tilesize / 2f),
                    y + Mathf.range(size * tilesize / 2f));
                }
            }

            heat = Mathf.clamp(heat);

            if(heat >= 0.999f){
                Events.fire(Trigger.thoriumReactorOverheat);
                kill();
            }
        }

        @Override
        public double sense(LAccess sensor){
            if(sensor == LAccess.heat) return heat;
            return super.sense(sensor);
        }

        @Override
        public void createExplosion(){
            if(items.get(fuelItem) >= 5 || heat >= 0.5f){
                super.createExplosion();
            }
        }

        @Override
        public void drawLight(){
            float fract = productionEfficiency;
            smoothLight = Mathf.lerpDelta(smoothLight, fract, 0.08f);
            Drawf.light(x, y, 12f * smoothLight, Tmp.c1.set(lightColor).lerp(Color.scarlet, heat), 0.6f * smoothLight);
        }

        @Override
        public void draw(){
            super.draw();

            Draw.color(coolColor, hotColor, heat);
            Fill.rect(x, y, size * tilesize, size * tilesize);

            Draw.color(liquids.current().color);
            Draw.alpha((liquids.currentAmount() / liquidCapacity)* 0.6f);
            Draw.rect(topRegion, x, y);

            if(heat > flashThreshold){
                flash += (1f + ((heat - flashThreshold) / (1f - flashThreshold)) * 5.4f) * Time.delta;
                Draw.color(Color.red, Color.yellow, Mathf.absin(flash, 9f, 1f));
                Draw.alpha(0.3f);
                Draw.rect(lightsRegion, x, y);
            }


            drawFuelRods();
            Draw.reset();
        }

        public void drawFuelRods(){
            Item item = Items.thorium;
            int fuel = items.get(fuelItem);
            Draw.color(item.color);
            //Drawf.light(x, y, 12f, liquids.current().color, ((float)fuel / (float)itemCapacity));

            float dx,dy;
            for(int i = 0; i < fuel; i++){
                dx = getHexPos(i + 6, 2f).x;
                dy = getHexPos(i + 6, 2f).y;
                Draw.alpha(0.8f);
                Fill.poly(x + dx, y + dy, 6, 0.7f);
                //Log.infoTag("Reactor", i + " | " + dx + "," + dy);
                //Drawf.light(x + getHexPos(i + 6, 2f).x, y + getHexPos(i + 6, 2f).y, 4f, liquids.current().color, ((float)fuel / (float)itemCapacity) * 0.6f);
            }
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(heat);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            heat = read.f();
        }

        //fuel pods position
        public Vec2 getHexPos(int posId, float gap){
            if(hexPos.length != itemCapacity + 6){
                genHexPos();
            }
            if(posId >= itemCapacity + 6) return new Vec2(0f, 0f);
            Vec2 v = new Vec2(hexPos[posId].x, hexPos[posId].y);
            //Log.infoTag("Reactor", posId + " | " + v.x + "," + v.y);
            v.scl(gap);
            return v;
        } 

        public void genHexPos(){
            hexPos = new Vec2[itemCapacity + 6];

            float turnId;
            float lineId;
            float linepos;
            Vec2 v = new Vec2();
            for(int posId = 0; posId < itemCapacity + 6; posId++){
                turnId = 0;
                while(turnId * (turnId + 1f) * 3f <= (float)posId){
                    turnId += 1f;
                }
                lineId = Mathf.floor(((float)posId - turnId * (turnId - 1f) * 3f) / (turnId));
                linepos = Mathf.mod((float)posId - turnId * (turnId - 1f) * 3f, turnId);

                switch((int)lineId){

                    case 0:
                    v.set(turnId / -2f + linepos, turnId / 2f * Mathf.sqrt3);
                    break;
                    case 1:
                    v.set(turnId / 2f + linepos / 2f, (turnId - linepos) * Mathf.sqrt3 / 2f);
                    break;
                    case 2:
                    v.set(turnId - linepos / 2f, (-linepos) * Mathf.sqrt3 / 2f);
                    break;

                    case 3:
                    v.set(turnId / 2f - linepos, -turnId / 2f * Mathf.sqrt3);
                    break;
                    case 4:
                    v.set(-turnId / 2f - linepos / 2f, (-turnId + linepos) * Mathf.sqrt3 / 2f);
                    break;
                    case 5:
                    v.set(- turnId + linepos / 2f, linepos * Mathf.sqrt3 / 2f);
                }
                hexPos[posId] = v.cpy();
                //Log.infoTag("ReactorHex", posId + " | " + hexPos[posId].x + "," + hexPos[posId].y);
            }

        }
    }
}
