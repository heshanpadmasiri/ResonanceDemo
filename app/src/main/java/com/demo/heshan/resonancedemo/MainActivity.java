package com.demo.heshan.resonancedemo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.vr.sdk.audio.GvrAudioEngine;
import com.google.vr.sdk.base.GvrActivity;

import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends GvrActivity {

    private BluetoothAdapter bluetoothAdapter;
    private static final int ENABLE_BLUETOOTH_REQUEST = 1;
    private static boolean bluetoothReady = false;

    private GvrAudioEngine gvrAudioEngine;
    private volatile int sourceId = GvrAudioEngine.INVALID_ID;
    private volatile int successSourceId = GvrAudioEngine.INVALID_ID;
    private static final String SUCCESS_SOUND_FILE = "success.wav";

    private Button btnRight;
    private Button btnLeft;
    private Button btnForward;
    private Button btnBackward;

    private ListView lstPaird;

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

        lstPaird = findViewById(R.id.lst_paird);

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

        // set-up bluetooth connection
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothReady = bluetoothAdapter.isEnabled();
        if (bluetoothAdapter == null){
            Toast.makeText(this,"Device don't support bluetooth",Toast.LENGTH_LONG).show();
        } else {
            // enable bluetooth
            if (!bluetoothAdapter.isEnabled()){
                Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBluetoothIntent, ENABLE_BLUETOOTH_REQUEST);
            }
        }
        if (bluetoothReady){
            updatePairdDevicesList();
        }

        // set-up action listeners for buttons
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


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ENABLE_BLUETOOTH_REQUEST){
            if (resultCode == RESULT_OK){
                Toast.makeText(this,"Bluetooth connection received",Toast.LENGTH_LONG).show();
                bluetoothReady = true;
            } else {
                Toast.makeText(this,"Bluetooth connection failed",Toast.LENGTH_LONG).show();
            }
        } else {
            // Not something we asked for
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void updatePairdDevicesList(){
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        ArrayList<String> deviceNames = new ArrayList<>();
        if (pairedDevices.size() > 0){
            for (BluetoothDevice device:pairedDevices){
                String deviceName = device.getName();
                deviceNames.add(deviceName);
            }
            String[] nameArr = deviceNames.toArray(new String[0]);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,nameArr);
            lstPaird.setAdapter(adapter);
        } else {
            Toast.makeText(this,"No bluetooth device has been paired yet",Toast.LENGTH_LONG).show();
        }
    }
}
