package com.demo.heshan.resonancedemo;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.vr.sdk.audio.GvrAudioEngine;
import com.google.vr.sdk.base.GvrActivity;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends GvrActivity implements TextToSpeech.OnInitListener{

    private BluetoothAdapter bluetoothAdapter;
    private static final int ENABLE_BLUETOOTH_REQUEST = 1;
    private static boolean bluetoothReady = false;
    private static final String DEVICE_NAME = "HC-06";
    private BluetoothDevice device;
    private BluetoothServer bluetoothServer;

    private Handler mHandler;

    private GvrAudioEngine gvrAudioEngine;
    private volatile int sourceId = GvrAudioEngine.INVALID_ID;
    private volatile int successSourceId = GvrAudioEngine.INVALID_ID;
    private static final String SUCCESS_SOUND_FILE = "success.wav";

    private Button btnRight;
    private Button btnLeft;
    private Button btnForward;
    private Button btnBackward;
    private Button btnSpeak;

    private ListView lstPaired;

    private TextView txtHeadbandReadings;
    private TextView txtHeadbandBattery;
    private TextView txtStickDistance;
    private TextView txtStickBattery;

    private TextToSpeech textToSpeech;

    private long[] headbandDistances;
    private long stickDistance;
    private long headbandBattery;
    private long stickBattery;

    public long[] getHeadbandDistances() {
        return headbandDistances;
    }

    public void setHeadbandDistances(long[] headbandDistances) {
        this.headbandDistances = headbandDistances;
    }

    public long getStickDistance() {
        return stickDistance;
    }

    public void setStickDistance(long stickDistance) {
        this.stickDistance = stickDistance;
    }

    public long getHeadbandBattery() {
        return headbandBattery;
    }

    public void setHeadbandBattery(long headbandBattery) {
        this.headbandBattery = headbandBattery;
    }

    public long getStickBattery() {
        return stickBattery;
    }

    public void setStickBattery(long stickBattery) {
        this.stickBattery = stickBattery;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        gvrAudioEngine = new GvrAudioEngine(this,GvrAudioEngine.RenderingMode.BINAURAL_HIGH_QUALITY);
        gvrAudioEngine.setHeadPosition(0,0,0);

        textToSpeech = new TextToSpeech(this,this);

        btnRight = findViewById(R.id.btn_right);
        btnLeft = findViewById(R.id.btn_left);
        btnForward = findViewById(R.id.btn_forward);
        btnBackward = findViewById(R.id.btn_backward);

        btnSpeak = findViewById(R.id.btn_speak);
        btnSpeak.setEnabled(false);

        lstPaired = findViewById(R.id.lst_paird);

        txtHeadbandReadings = findViewById(R.id.txt_headbandreadings);
        txtHeadbandBattery = findViewById(R.id.txt_headband_battery);
        txtStickBattery = findViewById(R.id.txt_stick_battery);
        txtStickDistance = findViewById(R.id.txt_stick_distance);

        headbandDistances = new long[8];
        headbandBattery = 0;
        stickDistance = 0;
        stickBattery = 0;
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
            onBluetoothReady();
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

        btnSpeak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String text = "Ha ha ha";
                textToSpeech.speak(text,TextToSpeech.QUEUE_FLUSH,null);
            }
        });
    }

    private synchronized void onBluetoothReady(){
        updatePairdDevicesList();
        mHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what){
                    case BluetoothMessage.HEADBAND_DISTANCE:
                        getHeadbandDistances()[msg.arg1] = msg.arg2;
                        txtHeadbandReadings.setText(Arrays.toString(getHeadbandDistances()));
                        break;
                    case BluetoothMessage.HEADBAND_BATTERY:
                        setHeadbandBattery(msg.arg1);
                        txtHeadbandBattery.setText(Long.toString(getHeadbandBattery()));
                        break;
                    case BluetoothMessage.STICK_BATTERY:
                        setStickBattery(msg.arg1);
                        txtStickBattery.setText(Long.toString(getStickBattery()));
                        break;
                    case BluetoothMessage.STICK_DISTANCE:
                        setStickDistance(msg.arg1);
                        txtStickDistance.setText(Long.toString(getStickDistance()));
                        break;
                    default:
                        super.handleMessage(msg);
                }
            }
        };
        // Make sure the devices have been paired before
        if (device != null){
            ReadThread readThread = new ReadThread(device,mHandler);
            readThread.start();
        }

    }

    public synchronized void moveSoundSource(float x, float y, float z){
        if (sourceId != GvrAudioEngine.INVALID_ID) {
            gvrAudioEngine.setSoundObjectPosition(sourceId,x,y,z);
            gvrAudioEngine.playSound(sourceId,true);
            gvrAudioEngine.update();
        }

    }

    private synchronized void connect(boolean secure){
        assert device != null && bluetoothServer != null;
        bluetoothServer.connect(device,secure);
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
                onBluetoothReady();
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
                if (deviceName.equals(DEVICE_NAME)){
                    this.device = device;
                }
                deviceNames.add(deviceName);
            }
            String[] nameArr = deviceNames.toArray(new String[0]);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,nameArr);
            lstPaired.setAdapter(adapter);
        } else {
            Toast.makeText(this,"No bluetooth device has been paired yet",Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null){
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS){
            int result = textToSpeech.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED){
                Toast.makeText(this,"TTS not availble",Toast.LENGTH_LONG).show();
            } else {
                btnSpeak.setEnabled(true);
                Toast.makeText(this,"TTS ready",Toast.LENGTH_LONG).show();
            }
        }
    }

    public Activity getActivity() {
        return this;
    }

    private class ReadThread extends Thread{
        BluetoothDevice device;
        InputStream inputStream;
        BluetoothSocket socket;
        Activity activity;
        Handler handler;

        public ReadThread(BluetoothDevice device, Handler handler) {
            this.handler = handler;
            this.device = device;
            try {
                socket = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                if (socket != null){
                    socket.connect();
                    inputStream = socket.getInputStream();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            if (inputStream != null){
                byte[] header = new byte[3];
                byte[] data = new byte[2];
                while (true){
                    try {
                        int t1 = inputStream.read(header,0,3);
                        if (header[0] == 'H'){
                            // headband message
                            if (header[2] == 'B'){
                                // headband battery message
                                int t2 = inputStream.read(data,0,1);
                                handler.obtainMessage(BluetoothMessage.HEADBAND_BATTERY,data[0]).sendToTarget();
                            } else {
                                // headband distance message
                                int t2 = inputStream.read(data,0,2);
                                int distance = ((data[0] & 0xff) << 8) | (data[1] & 0xff);
                                handler.obtainMessage(BluetoothMessage.HEADBAND_DISTANCE,header[2],distance).sendToTarget();
                            }
                        } else {
                            // stick message
                            if (header[2] == 'B'){
                                //stick battery message
                                int t2 = inputStream.read(data,0,1);
                                handler.obtainMessage(BluetoothMessage.STICK_BATTERY,data[0]).sendToTarget();
                            } else {
                                int t2 = inputStream.read(data,0,2);
                                int distance = ((data[0] & 0xff) << 8) | (data[1] & 0xff);
                                handler.obtainMessage(BluetoothMessage.STICK_DISTANCE,distance).sendToTarget();
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        break;
                    }
                }
            }
        }
    }
}
