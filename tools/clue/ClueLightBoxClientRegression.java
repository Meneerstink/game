import com.jagex.core.io.Packet;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.rsmod.game.tools.importer.*;
import java.nio.file.*;
import java.io.File;
import java.util.*;

/** Actual native Component/ClientScript decoding, then execute the ported draw program on two viewport sizes. */
class ClueLightBoxClientRegression {
    static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    static class Lamp {int x,y,w,h,item,outline;}
    static List<Lamp> draw(ClientScript script,int width,int height,int bits,int control) {
        int[] local=new int[script.intVarCount];local[0]=(1234<<16)|3;
        List<Integer> stack=new ArrayList<>();List<Lamp> lamps=new ArrayList<>();Lamp active=null;
        for(int pc=0,budget=10000;pc<script.opcodes.length && budget-->0;pc++) {
            int op=script.opcodes[pc],value=script.intOperands[pc];
            switch(op) {
                case 0:stack.add(value);break;
                case 1:check(value==control,"private light varp");stack.add(bits);break;
                case 21:return lamps;
                case 33:stack.add(local[value]);break;
                case 34:local[value]=pop(stack);break;
                case 6:pc+=value;break;
                case 8:{int b=pop(stack),a=pop(stack);if(a==b)pc+=value;break;}
                case 9:{int b=pop(stack),a=pop(stack);if(a<b)pc+=value;break;}
                case 152:pop(stack);lamps.clear();break;
                case 150:{int child=pop(stack),type=pop(stack);pop(stack);check(type==5 && child==lamps.size(),"native three-arg icon create");active=new Lamp();lamps.add(active);break;}
                case 2502:pop(stack);stack.add(width);break;
                case 2503:pop(stack);stack.add(height);break;
                case 4000:{int b=pop(stack),a=pop(stack);stack.add(a+b);break;}
                case 4001:{int b=pop(stack),a=pop(stack);stack.add(a-b);break;}
                case 4002:{int b=pop(stack),a=pop(stack);stack.add(a*b);break;}
                case 4003:{int b=pop(stack),a=pop(stack);stack.add(a/b);break;}
                case 4010:{int b=pop(stack),a=pop(stack);stack.add((a&(1<<b))==0?0:1);break;}
                case 4011:{int b=pop(stack),a=pop(stack);stack.add(a%b);break;}
                case 1000:{int ym=pop(stack),xm=pop(stack),y=pop(stack),x=pop(stack);check(xm==0&&ym==0,"source absolute draw positions");active.x=x;active.y=y;break;}
                case 1001:{int hm=pop(stack),wm=pop(stack),h=pop(stack),w=pop(stack);check(wm==0&&hm==0,"source icon size");active.w=w;active.h=h;break;}
                case 1205:check(pop(stack)==0,"source non-numbered icon quantity");active.item=pop(stack);break;
                case 1116:active.outline=pop(stack);break;
                default:throw new AssertionError("unexpected draw op"+op);
            }
        }
        throw new AssertionError("draw loop did not return");
    }
    static int pop(List<Integer> values) {return values.remove(values.size()-1);}
    public static void main(String[] args) throws Exception {
        Path folder=Path.of(args[0]);var json=new ObjectMapper();
        var root=json.readTree(folder.resolve("lightbox-interface.json").toFile());
        var sources=json.readTree(folder.resolve("lightbox-scripts.json").toFile());
        var hover=json.readTree(folder.resolve("hover.json").toFile());
        byte[] template=Base64.getDecoder().decode(hover.get("targetBytes").asText());
        Map<Integer,Integer> scripts=new LinkedHashMap<>(),sprites=new LinkedHashMap<>();
        int next=5500;for(int id:ClueLightBoxCacheTool.INSTANCE.getSCRIPTS())scripts.put(id,next++);
        next=9000;for(int id:ClueLightBoxCacheTool.INSTANCE.getSPRITES())sprites.put(id,next++);
        Map<Integer,Integer> items=Map.of(20356,30501,20357,30502);
        int group=1234,control=2408;
        int scriptCount=0;
        ClientScript drawing=null;
        for(int id:scripts.keySet()) {
            byte[] raw=ClueLightBoxCacheTool.INSTANCE.script(id,sources.get(""+id),template,scripts,sprites,items,control,576);
            ClientScript script=ClientScript.decode(raw);
            check(script.intArgCount==sources.get(""+id).get("intStackCount").asInt(),"source argument count"+id);
            for(int i=0;i<script.opcodes.length;i++) {
                int op=script.opcodes[i];
                check(op!=100&&op!=102,"no OSRS create/delete opcode leaked"+id);
                if(op==40)check(scripts.containsValue(script.intOperands[i]),"call remapped"+id);
            }
            if(id==1339)drawing=script;
            scriptCount++;
        }
        int count=0,hookCount=0;
        for(var fixture:ClueLightBoxCacheTool.INSTANCE.components(root,Map.of(495,999,496,1000),sprites)) {
            var hooks=ClueLightBoxCacheTool.INSTANCE.hooks(fixture.getId(),group,scripts,sprites);
            byte[] raw=ClueLightBoxCacheTool.INSTANCE.encode(fixture,hooks,fixture.getId()==3?List.of(control):List.of());
            var packet=new Packet(raw);var c=new Component();c.slot=(group<<16)|fixture.getId();c.decode(packet);
            check(packet.pos==raw.length,"component alignment"+fixture.getId());
            check(c.layer==(fixture.getParent()<0?-1:(group<<16)|fixture.getParent()),"component parent"+fixture.getId());
            check(c.type==fixture.getType(),"component type"+fixture.getId());
            if(fixture.getId()==3) {
                check(Arrays.equals(c.onLoad,new Object[]{scripts.get(1338),(group<<16)|3}),"draw init hook");
                check(Arrays.equals(c.varpTriggers,new int[]{control}),"private varp trigger");
                check(Arrays.equals(c.onVarTransmit,new Object[]{scripts.get(1339),(group<<16)|3}),"source draw on varp");
            }
            if(fixture.getId()>=8&&fixture.getId()<=15)check(c.onOp[0].equals(scripts.get(1336)),"source press+15cycle reset");
            hookCount+=hooks.size();count++;
        }
        check(count==44&&scriptCount==10,"complete source UI/CS2 cohort");
        for(int[] size:new int[][]{{286,176},{386,276}})for(int bits:new int[]{0,33554431,0x1555555}) {
            List<Lamp> lamps=draw(drawing,size[0],size[1],bits,control);check(lamps.size()==25,"all lamps");
            // Source1339 reserves an8px top inset before calculating vertical cell spacing.
            int gapX=(size[0]-36*5)/6,gapY=(size[1]-32*5-8)/6;
            for(int cell=0;cell<25;cell++) {
                Lamp lamp=lamps.get(cell);
                check(lamp.w==36&&lamp.h==32&&lamp.outline==1,"actual item icon geometry+outline");
                check(lamp.x==1+gapX+(cell%5)*(36+gapX)&&lamp.y==8+(cell/5)*(32+gapY),
                    "source integer margins cell"+cell+" at"+lamp.x+","+lamp.y);
                check(lamp.item==((bits&(1<<cell))!=0?30501:30502),"correct mapped on/off icon");
            }
        }
        System.out.println("Native lightbox decoder: "+count+" components, "+scriptCount+" CS2 programs, "+hookCount+" hooks;150 item icons across two sizes/three states OK");
    }
}
