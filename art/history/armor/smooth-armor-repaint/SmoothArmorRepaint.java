import com.google.gson.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public class SmoothArmorRepaint {
 static final int GRID=24;
 static double[][] material=new double[GRID][GRID];
 static double materialMin=255,materialMax=0;
 public static void main(String[] args)throws Exception{
  var project=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
  var reference=ImageIO.read(Path.of(args[1]).toFile());
  // Low-frequency material only: spatial averaging removes any generated grain or fine streaks.
  for(int gy=0;gy<GRID;gy++)for(int gx=0;gx<GRID;gx++){
   long sum=0,count=0;
   for(int y=gy*reference.getHeight()/GRID;y<(gy+1)*reference.getHeight()/GRID;y++)
    for(int x=gx*reference.getWidth()/GRID;x<(gx+1)*reference.getWidth()/GRID;x++){
     int p=reference.getRGB(x,y);sum+=((p>>16)&255)*54+((p>>8)&255)*183+(p&255)*19;count++;
    }
   material[gy][gx]=(double)sum/count/256;materialMin=Math.min(materialMin,material[gy][gx]);materialMax=Math.max(materialMax,material[gy][gx]);
  }
  Map<String,JsonObject> textures=new LinkedHashMap<>();for(var t:project.getAsJsonArray("textures")){var o=t.getAsJsonObject();textures.put(o.get("name").getAsString(),o);}
  Path out=Path.of(args[2]);Files.createDirectories(out);
  for(var element:project.getAsJsonArray("elements")){
   var mesh=element.getAsJsonObject();String name=mesh.get("name").getAsString();
   var baseMeta=textures.get(name+"_Base");var accentMeta=textures.get(name+"_Accent");
   var base=decode(baseMeta);var accent=decode(accentMeta);int width=base.getWidth(),height=base.getHeight();
   double scaleX=width/baseMeta.get("uv_width").getAsDouble(),scaleY=height/baseMeta.get("uv_height").getAsDouble();
   double minX=Double.POSITIVE_INFINITY,maxX=-minX,minZ=minX,maxZ=-minX;
   for(var entry:mesh.getAsJsonObject("vertices").entrySet()){
    var p=entry.getValue().getAsJsonArray();double x=p.get(0).getAsDouble(),z=p.get(2).getAsDouble();
    minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
   }
   double[] fields=new double[width*height];int[] samples=new int[fields.length];
   for(var entry:mesh.getAsJsonObject("faces").entrySet()){
    var face=entry.getValue().getAsJsonObject();var ids=face.getAsJsonArray("vertices");if(ids.size()!=3)throw new IllegalArgumentException("Expected triangle");
    double[][] p=new double[3][3];double[] u=new double[3],v=new double[3];
    for(int i=0;i<3;i++){
     String id=ids.get(i).getAsString();var point=mesh.getAsJsonObject("vertices").getAsJsonArray(id);var uv=face.getAsJsonObject("uv").getAsJsonArray(id);
     for(int j=0;j<3;j++)p[i][j]=point.get(j).getAsDouble();u[i]=uv.get(0).getAsDouble()*scaleX;v[i]=uv.get(1).getAsDouble()*scaleY;
    }
    double den=(v[1]-v[2])*(u[0]-u[2])+(u[2]-u[1])*(v[0]-v[2]);if(Math.abs(den)<1e-9)continue;
    int loX=Math.max(0,(int)Math.floor(Math.min(u[0],Math.min(u[1],u[2])))),hiX=Math.min(width,(int)Math.ceil(Math.max(u[0],Math.max(u[1],u[2]))));
    int loY=Math.max(0,(int)Math.floor(Math.min(v[0],Math.min(v[1],v[2])))),hiY=Math.min(height,(int)Math.ceil(Math.max(v[0],Math.max(v[1],v[2]))));
    for(int y=loY;y<hiY;y++)for(int x=loX;x<hiX;x++){
     double a=((v[1]-v[2])*(x+.5-u[2])+(u[2]-u[1])*(y+.5-v[2]))/den,b=((v[2]-v[0])*(x+.5-u[2])+(u[0]-u[2])*(y+.5-v[2]))/den,c=1-a-b;
     if(Math.min(a,Math.min(b,c)) < -1e-7)continue;
     double px=a*p[0][0]+b*p[1][0]+c*p[2][0],pz=a*p[0][2]+b*p[1][2]+c*p[2][2];
     // Position-based material projection keeps coplanar triangles continuous and mirrored pieces consistent.
     double mx=.5+.42*Math.abs((px-(minX+maxX)/2)/Math.max(.001,(maxX-minX)/2));
     double my=(maxZ-pz)/Math.max(.001,maxZ-minZ);
     fields[y*width+x]+=sample(mx,my);samples[y*width+x]++;
    }
   }
   // Unused painted pixels and triangle-edge texels get the nearest mapped material value, never new alpha.
   for(int i=0;i<fields.length;i++)if(samples[i]>0)fields[i]/=samples[i];
   for(int layer=0;layer<2;layer++){
    BufferedImage original=layer==0?base:accent;var result=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);long total=0;int painted=0,lowest=255,highest=0;
    for(int y=0;y<height;y++)for(int x=0;x<width;x++){
     int old=original.getRGB(x,y);if(old>>>24==0){result.setRGB(x,y,old);continue;}
     double f=fields[y*width+x];if(samples[y*width+x]==0){
      double nearest=Double.MAX_VALUE;f=.7;
      for(int dy=-6;dy<=6;dy++)for(int dx=-6;dx<=6;dx++){
       int nx=x+dx,ny=y+dy;double d=dx*dx+dy*dy;
       if(nx>=0&&ny>=0&&nx<width&&ny<height&&samples[ny*width+nx]>0&&d<nearest){f=fields[ny*width+nx];nearest=d;}
      }
     }
     int floor=layer==0?236:244;
     int gray=(int)Math.round(floor+(255-floor)*Math.pow(Math.clamp(f,0,1),.55));
     int color=(old&0xff000000)|(gray<<16)|(gray<<8)|gray;result.setRGB(x,y,color);
     total+=gray;painted++;lowest=Math.min(lowest,gray);highest=Math.max(highest,gray);
    }
    String textureName=name+(layer==0?"_Base":"_Accent");ImageIO.write(result,"png",out.resolve(textureName+".png").toFile());
    System.out.printf(Locale.ROOT,"%s: %d painted pixels, gray %d..%d, mean %.2f; alpha unchanged%n",textureName,painted,lowest,highest,(double)total/painted);
   }
  }
 }
 static double sample(double u,double v){
  double gx=Math.clamp(u,0,1)*(GRID-1),gy=Math.clamp(v,0,1)*(GRID-1);int x=(int)gx,y=(int)gy;double fx=gx-x,fy=gy-y;
  double a=material[y][x]*(1-fx)+material[y][Math.min(GRID-1,x+1)]*fx,b=material[Math.min(GRID-1,y+1)][x]*(1-fx)+material[Math.min(GRID-1,y+1)][Math.min(GRID-1,x+1)]*fx;
  return (a*(1-fy)+b*fy-materialMin)/Math.max(1,materialMax-materialMin);
 }
 static BufferedImage decode(JsonObject t)throws Exception{String s=t.get("source").getAsString();return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(s.substring(s.indexOf(',')+1))));}
}
