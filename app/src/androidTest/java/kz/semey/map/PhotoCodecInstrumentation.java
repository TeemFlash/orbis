package kz.semey.map;
import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Base64;
import java.io.*;
public class PhotoCodecInstrumentation extends Instrumentation {
  @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
  @Override public void onStart(){Bundle result=new Bundle();File file=new File(getTargetContext().getCacheDir(),"photo-codec-test.jpg");try{
    Bitmap image=Bitmap.createBitmap(2200,1600,Bitmap.Config.ARGB_8888);int[] line=new int[2200];java.util.Random random=new java.util.Random(7);
    for(int y=0;y<1600;y++){for(int x=0;x<2200;x++)line[x]=0xff000000|random.nextInt(0xffffff);image.setPixels(line,0,2200,0,y,2200,1);}
    try(FileOutputStream out=new FileOutputStream(file)){image.compress(Bitmap.CompressFormat.JPEG,98,out);}image.recycle();
    String encoded=PhotoCodec.encode(getTargetContext().getContentResolver(),Uri.fromFile(file));
    if(Base64.decode(encoded,Base64.NO_WRAP).length>102400)throw new AssertionError("JPEG exceeds 100 KiB");
    Bitmap decoded=PhotoCodec.decode(encoded);if(decoded==null||Math.max(decoded.getWidth(),decoded.getHeight())>960)throw new AssertionError("Invalid dimensions");decoded.recycle();
    if(PhotoCodec.decode("invalid")!=null)throw new AssertionError("Invalid image accepted");
    if(PhotoCodec.decode(new String(new char[136537]))!=null)throw new AssertionError("Oversize data accepted");
    Bitmap solid=Bitmap.createBitmap(1600,1600,Bitmap.Config.ARGB_8888);
    ByteArrayOutputStream compressed=new ByteArrayOutputStream();solid.compress(Bitmap.CompressFormat.JPEG,85,compressed);solid.recycle();
    Bitmap thumbnail=PhotoCodec.decode(Base64.encodeToString(compressed.toByteArray(),Base64.NO_WRAP));
    if(thumbnail==null||thumbnail.getByteCount()>1024*1024)throw new AssertionError("Display thumbnail exceeds 1 MiB");thumbnail.recycle();
    kz.semey.map.profile.ProfilePhotoChecks.run(getTargetContext());
    result.putString("stream","PASS: noisy large image compressed <=100 KiB; dimensions <=960; invalid and oversized data rejected; eight EXIF orientations normalized.\n");finish(Activity.RESULT_OK,result);
  }catch(Throwable e){result.putString("stream","FAIL: "+e);finish(Activity.RESULT_CANCELED,result);}finally{file.delete();}}
}
