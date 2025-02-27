package com.example.firstpage;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class GameView1 extends SurfaceView implements SurfaceHolder.Callback {

    SurfaceHolder holder;
    GameThread gameThread;
    Game1 game1;

    public GameView1(Context context, AttributeSet attrs) {
        super(context, attrs);
        holder = getHolder();
        holder.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d("GAMEVIEW", "created");
        game1 = new Game1(
                getContext(),
                new Rect(0, 0, getWidth(), getHeight()),
                holder,
                getResources());
        gameThread = new GameThread(game1);
        gameThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d("GAMEVIEW", "changed");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d("GAMEVIEW", "destroyed");
        if(gameThread != null) {
            gameThread.shutdown();

            while(gameThread != null) {
                try {
                    gameThread.join();
                    gameThread = null;
                } catch (InterruptedException e) {
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        game1.onTouchEvent(event);
        return true;
    }
}
