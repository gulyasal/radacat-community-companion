package com.mapmates.radacat;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Eight-tooth settings cog, drawn consistently across Android fonts. */
public final class SettingsIcon extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    public SettingsIcon(int color){paint.setColor(color);}
    @Override public void draw(Canvas canvas){
        float size=Math.min(getBounds().width(),getBounds().height());
        float cx=getBounds().exactCenterX(),cy=getBounds().exactCenterY();
        Path cog=new Path();cog.setFillType(Path.FillType.EVEN_ODD);
        for(int tooth=0;tooth<8;tooth++){
            float[] offsets={-22.5f,-13f,-10f,10f,13f,22.5f};
            float[] radii={0.34f,0.34f,0.46f,0.46f,0.34f,0.34f};
            for(int edge=0;edge<offsets.length;edge++){
                double angle=Math.toRadians(tooth*45+offsets[edge]);
                float x=cx+(float)Math.cos(angle)*size*radii[edge];
                float y=cy+(float)Math.sin(angle)*size*radii[edge];
                if(tooth==0 && edge==0)cog.moveTo(x,y);else cog.lineTo(x,y);
            }
        }
        cog.close();cog.addCircle(cx,cy,size*0.15f,Path.Direction.CW);
        canvas.drawPath(cog,paint);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
