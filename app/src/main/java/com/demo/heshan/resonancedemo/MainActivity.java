package com.demo.heshan.resonancedemo;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.google.vr.sdk.audio.GvrAudioEngine;
import com.google.vr.sdk.base.GvrActivity;

public class MainActivity extends GvrActivity {

    private GvrAudioEngine gvrAudioEngine;
    private volatile int sourceId = GvrAudioEngine.INVALID_ID;
    private volatile int successSourceId = GvrAudioEngine.INVALID_ID;

    private Button btnRight;
    private Button btnLeft;
    private Button btnForward;
    private Button btnBackward;

    private static final String SUCCESS_SOUND_FILE = "success.wav";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        gvrAudioEngine = new GvrAudioEngine(this,GvrAudioEngine.RenderingMode.BINAURAL_HIGH_QUALITY);
        gvrAudioEngine.setHeadPosition(0,0,0);

        btnRight = findViewById(R.id.btn_right);
        btnLeft = findViewById(R.id.btn_left);
        btnForward = findViewById(R.id.btn_forward);
        btnBackward = findViewById(R.id.btn_backward);

        // Preload the sound file
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        gvrAudioEngine.preloadSoundFile(SUCCESS_SOUND_FILE);
                        sourceId = gvrAudioEngine.createSoundObject(SUCCESS_SOUND_FILE);
                    }
                }
        ).start();

        btnRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                moveSoundSource(10,0,0);
            }
        });

        btnLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                moveSoundSource(-10,0,0);
            }
        });

        btnForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                moveSoundSource(0,10,0);
            }
        });

        btnBackward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                moveSoundSource(0,-10,0);
            }
        });
    }

    public synchronized void moveSoundSource(float x, float y, float z){
        if (sourceId != GvrAudioEngine.INVALID_ID) {
            gvrAudioEngine.setSoundObjectPosition(sourceId,x,y,z);
            gvrAudioEngine.playSound(sourceId,true);
            gvrAudioEngine.update();
        }

    }

    @Override
    public void onPause() {
        gvrAudioEngine.pause();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        gvrAudioEngine.resume();
    }

}
