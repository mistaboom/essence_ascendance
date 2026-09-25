import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
public class RepaintHelmet {
 public static void main(String[] args) throws Exception {
  var base=ImageIO.read(Path.of(args[0]).toFile());var accent=ImageIO.read(Path.of(args[1]).toFile());
  var generated=ImageIO.read(Path.of(args[2]).toFile());
  int w=base.getWidth(),h=base.getHeight();boolean[] painted=new boolean[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++)painted[y*w+x]=((base.getRGB(x,y)|accent.getRGB(x,y))>>>24)>0;
  int[] labels=new int[w*h];Arrays.fill(labels,-1);List<int[]> bounds=new ArrayList<>();
  for(int start=0;start<w*h;start++)if(painted[start]&&labels[start]<0){
   int id=bounds.size();int[] box={w,h,0,0};var q=new ArrayDeque<Integer>();q.add(start);labels[start]=id;
   while(!q.isEmpty()){int k=q.removeFirst(),x=k%w,y=k/w;box[0]=Math.min(box[0],x);box[1]=Math.min(box[1],y);box[2]=Math.max(box[2],x);box[3]=Math.max(box[3],y);
    for(int[] d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}){int nx=x+d[0],ny=y+d[1];if(nx<0||ny<0||nx>=w||ny>=h)continue;int n=ny*w+nx;if(painted[n]&&labels[n]<0){labels[n]=id;q.add(n);}}
   }bounds.add(box);
  }
  for(int layer=0;layer<2;layer++){
   var original=layer==0?base:accent;var result=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);int changed=0;
   for(int y=0;y<h;y++)for(int x=0;x<w;x++){
    int old=original.getRGB(x,y);if(old>>>24==0){result.setRGB(x,y,old);continue;}
    int[] box=bounds.get(labels[y*w+x]);
    // Transfer the generated plate material into each ORIGINAL island, snapping shades to UV-pixel clusters.
    double u=Math.clamp((x/4*4-box[0]+2.0)/(box[2]-box[0]+1),0,1);
    double v=Math.clamp((y/4*4-box[1]+2.0)/(box[3]-box[1]+1),0,1);
    int sample=generated.getRGB(333+(int)(u*77),367+(int)(v*64));
    int gray=(((sample>>16)&255)*54+((sample>>8)&255)*183+(sample&255)*19)/256;
    int shade=Math.clamp(160+(gray-137)*88/118,160,248)+(layer==1?6:0);
    boolean top=!at(painted,w,h,x,y-2),left=!at(painted,w,h,x-2,y);
    boolean bottom=!at(painted,w,h,x,y+2),right=!at(painted,w,h,x+2,y);
    if(top||left)shade=255;
    else if(bottom||right)shade=layer==0?190:210;
    // Restrained palette and hard-edged transitions keep the material tintable and Minecraft-like.
    int[] palette=layer==0?new int[]{176,192,208,224,240,255}:new int[]{192,208,224,240,255};
    int nearest=palette[0];for(int value:palette)if(Math.abs(value-shade)<Math.abs(nearest-shade))nearest=value;
    int color=(old&0xff000000)|(nearest<<16)|(nearest<<8)|nearest;
    result.setRGB(x,y,color);if(old!=color)changed++;
   }
   String name=layer==0?"Head_Base":"Head_Accent";
   ImageIO.write(result,"png",Path.of(args[3],name+".png").toFile());
   System.out.println(name+": "+changed+" colors changed; all alpha bytes and unpainted pixels preserved");
  }
 }
 static boolean at(boolean[] pixels,int w,int h,int x,int y){return x>=0&&y>=0&&x<w&&y<h&&pixels[y*w+x];}
}

