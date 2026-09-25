import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public class DirectionalArmorPreview {
 record Part(String mesh,String atlas,float px,float py){}
 record Triangle(float[][] vertices,String atlas){}
 static final Part[] PARTS={new Part("head","helmet",0,0),new Part("chest","chestplate",0,0),new Part("left_arm","chestplate",5,2),new Part("right_arm","chestplate",-5,2),new Part("left_leg","leggings",1.9f,12),new Part("right_leg","leggings",-1.9f,12),new Part("left_foot","boots",1.9f,12),new Part("right_foot","boots",-1.9f,12)};
 static double[] project(float[] p,int ox,int oy){double x=p[0],y=.48-p[1],z=p[2];double a=.28,b=.10,c=Math.cos(a),s=Math.sin(a),d=s*x-c*z;return new double[]{ox+245*(c*x+s*z),oy-245*(Math.cos(b)*y-Math.sin(b)*d),Math.sin(b)*y+Math.cos(b)*d};}
 public static void main(String[] args)throws Exception{
  var triangles=new ArrayList<Triangle>();
  for(Part part:PARTS)try(var in=new DataInputStream(Files.newInputStream(Path.of(args[0],"meshes/armor/ascendance/"+part.mesh+".eamesh")))){
   if(in.readInt()!=0x45414D31)throw new IOException("Invalid EAM1");int count=in.readInt();for(int i=0;i<count;i++){float[][] t=new float[3][5];for(var v:t){for(int j=0;j<5;j++)v[j]=in.readFloat();for(int j=0;j<3;j++)in.readFloat();v[0]+=part.px/16;v[1]+=part.py/16;}triangles.add(new Triangle(t,part.atlas));}
  }
  int w=1200,h=1330;var out=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);var g=out.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(new Color(22,27,35));g.fillRect(0,0,w,h);g.setFont(new Font("SansSerif",Font.BOLD,25));g.setColor(new Color(238,240,245));g.drawString("Ascendance armor - directional shading",28,40);g.setFont(new Font("SansSerif",Font.PLAIN,15));g.setColor(new Color(170,182,197));g.drawString("Upper front-left light; corrected left knee; software preview, not an in-game screenshot",28,66);
  String[] tiers={"latent","dormant","awakened","resonant","ascendant","transcendent"};double[] depth=new double[w*h];Arrays.fill(depth,-100);
  for(int index=0;index<6;index++){
   int cellX=(index%3)*400,cellY=(index/3)*610+90;g.setColor(new Color(30,37,48));g.fillRoundRect(cellX+12,cellY,376,594,18,18);
   Map<String,BufferedImage> texs=new HashMap<>();for(String atlas:new String[]{"helmet","chestplate","leggings","boots"})texs.put(atlas,ImageIO.read(Path.of(args[0],"textures/armor/ascendance/generated/"+atlas+"_"+tiers[index]+".png").toFile()));
   for(var triangle:triangles){float[][] tri=triangle.vertices;var tex=texs.get(triangle.atlas);double[][] p=new double[3][];for(int i=0;i<3;i++)p[i]=project(tri[i],cellX+200,cellY+285);
    double det=(p[1][1]-p[2][1])*(p[0][0]-p[2][0])+(p[2][0]-p[1][0])*(p[0][1]-p[2][1]);if(Math.abs(det)<1e-7)continue;
    double ax=tri[1][0]-tri[0][0],ay=-(tri[1][1]-tri[0][1]),az=tri[1][2]-tri[0][2],bx=tri[2][0]-tri[0][0],by=-(tri[2][1]-tri[0][1]),bz=tri[2][2]-tri[0][2];
    double nx=-(ay*bz-az*by),ny=-(az*bx-ax*bz),nz=-(ax*by-ay*bx),len=Math.sqrt(nx*nx+ny*ny+nz*nz);
    double light=.74+.26*Math.max(0,(-.35*nx+.75*ny-.56*nz)/(len*Math.sqrt(.35*.35+.75*.75+.56*.56)));
    int minX=Math.max(0,(int)Math.floor(Math.min(p[0][0],Math.min(p[1][0],p[2][0])))),maxX=Math.min(w-1,(int)Math.ceil(Math.max(p[0][0],Math.max(p[1][0],p[2][0]))));
    int minY=Math.max(0,(int)Math.floor(Math.min(p[0][1],Math.min(p[1][1],p[2][1])))),maxY=Math.min(h-1,(int)Math.ceil(Math.max(p[0][1],Math.max(p[1][1],p[2][1]))));
    for(int y=minY;y<=maxY;y++)for(int x=minX;x<=maxX;x++){
     double a=((p[1][1]-p[2][1])*(x+.5-p[2][0])+(p[2][0]-p[1][0])*(y+.5-p[2][1]))/det,b=((p[2][1]-p[0][1])*(x+.5-p[2][0])+(p[0][0]-p[2][0])*(y+.5-p[2][1]))/det,c=1-a-b;if(Math.min(a,Math.min(b,c))<0)continue;
     double z=a*p[0][2]+b*p[1][2]+c*p[2][2];if(z<=depth[y*w+x]+1e-9)continue;
     int u=Math.clamp((int)((a*tri[0][3]+b*tri[1][3]+c*tri[2][3])*tex.getWidth()),0,tex.getWidth()-1),v=Math.clamp((int)((a*tri[0][4]+b*tri[1][4]+c*tri[2][4])*tex.getHeight()),0,tex.getHeight()-1);int pixel=tex.getRGB(u,v);if(pixel>>>24<128)continue;
     int red=(int)(((pixel>>16)&255)*light),green=(int)(((pixel>>8)&255)*light),blue=(int)((pixel&255)*light);out.setRGB(x,y,(red<<16)|(green<<8)|blue);depth[y*w+x]=z;
    }
   }
   g.setColor(new Color(237,239,244));g.setFont(new Font("SansSerif",Font.BOLD,20));String title=tiers[index].substring(0,1).toUpperCase()+tiers[index].substring(1);g.drawString(title,cellX+28,cellY+567);
  }
  g.dispose();ImageIO.write(out,"png",Path.of(args[1]).toFile());
 }
}
