package com.demo.heshan.resonancedemo;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.SmsManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.vr.sdk.audio.GvrAudioEngine;
import com.google.vr.sdk.base.GvrActivity;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends GvrActivity implements TextToSpeech.OnInitListener {

    private static final int MIN_LENGTH = 0;
    private BluetoothAdapter bluetoothAdapter;

    private static boolean bluetoothReady = false;
    private static final String HEADBAND_NAME = "HC-05";
    private static final String WALKING_STICK_NAME = "STICK";
    private BluetoothDevice headBand;
    private BluetoothDevice walkingStick;
    private Handler headbandHandler;
    private Handler walkingStickHandler;

    private GvrAudioEngine gvrAudioEngine;
    private static final String SUCCESS_SOUND_FILE = "success.wav";

    /**
     *
     */
    private Button btnSpeak;

    private ListView lstPaired;

    private TextView txtHeadbandReadings;
    private TextView txtHeadbandBattery;
    private TextView txtStickDistance;
    private TextView txtStickBattery;

    private TextToSpeech textToSpeech;

    private static double[] angles = {135,180,270,0,45};
    private static final double MAX_LENGTH = 90;
    private byte[] headbandDistances;
    private long stickDistance;
    private long headbandBattery;
    private long stickBattery;

    private FirebaseFirestore database;
    private LocationManager locationManager;

    private static final int ENABLE_BLUETOOTH_REQUEST = 1;
    private static final int LOCATION_PERMISSION_REQUEST = 2;
    private static final int SMS_PERMISSION_REQUEST = 3;

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

    /*
    Return x, y, z where z = 0
     */
    private static float[] polarToCartesian(double radius, double angle){
        float[] values = new float[3];
        values[0] = (float) (radius*Math.cos(angle));
        values[1] = (float) (radius*Math.sin(angle));
        return values;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        gvrAudioEngine = new GvrAudioEngine(this, GvrAudioEngine.RenderingMode.BINAURAL_HIGH_QUALITY);
        gvrAudioEngine.setHeadPosition(0, 0, 0);

        headbandDistances = new byte[5];
        textToSpeech = new TextToSpeech(this, this);
        Button btnRight = findViewById(R.id.btn_right);
        Button btnLeft = findViewById(R.id.btn_left);
        Button btnForward = findViewById(R.id.btn_forward);
        Button btnBackward = findViewById(R.id.btn_backward);
        Button btnSms = findViewById(R.id.btn_sms);
        btnSpeak = findViewById(R.id.btn_speak);
        btnSpeak.setEnabled(false);

        lstPaired = findViewById(R.id.lst_paird);

        txtHeadbandReadings = findViewById(R.id.txt_headbandreadings);
        txtHeadbandBattery = findViewById(R.id.txt_headband_battery);
        txtStickBattery = findViewById(R.id.txt_stick_battery);
        txtStickDistance = findViewById(R.id.txt_stick_distance);


        headbandBattery = 0;
        stickDistance = 0;
        stickBattery = 0;
        // Preload the sound file
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        gvrAudioEngine.preloadSoundFile(SUCCESS_SOUND_FILE);
                    }
                }
        ).start();

        // set-up bluetooth connection
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothReady = bluetoothAdapter.isEnabled();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Device don't support bluetooth", Toast.LENGTH_LONG).show();
        } else {
            // enable bluetooth
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBluetoothIntent, ENABLE_BLUETOOTH_REQUEST);
            }
        }
        if (bluetoothReady) {
            onBluetoothReady();
        }

        // set-up action listeners for buttons
        btnRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                moveSoundSource(0,10, 0, 0);
            }
        });

        btnLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                moveSoundSource(0,-10, 0, 0);
            }
        });

        btnForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                moveSoundSource(0,0, 10, 0);
            }
        });

        btnBackward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                moveSoundSource(0,0, -10, 0);
            }
        });

        btnSpeak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String text = "Ha ha ha";
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            }
        });

        btnSms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendSMS();
            }
        });
        database = FirebaseFirestore.getInstance();

        // setup location services
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            enableLocationListener();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        }
    }

    private void sendSMS(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED){
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage("+94766041559",null,"Test",null,null);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS},
                    SMS_PERMISSION_REQUEST);
        }
    }

    private void speak(String text){
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
    }

    private void enableLocationListener() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            locationManager = this.getSystemService(LocationManager.class);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10 * 60000, 5, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    if (location != null) {
                        // Logic to handle location object
                        database.collection("locations").add(location);
                    }
                }

                @Override
                public void onStatusChanged(String s, int i, Bundle bundle) {

                }

                @Override
                public void onProviderEnabled(String s) {
                    speak("Location saving enabled");
                }

                @Override
                public void onProviderDisabled(String s) {
                    speak("Location saving disabled");
                }
            });

        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        }

    }

    private synchronized void onBluetoothReady(){
        updatePairedDevicesList();
        headbandHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what){
                    case BluetoothMessage.HEADBAND_DISTANCE:
                        byte[] temp =(byte[]) msg.obj;
                        for (int i =0 ; i < temp.length; i++){
                            temp[i] = (byte) ((temp[i] - 48) * 10);
                        }
                        System.out.println("Calculated :" + Arrays.toString(temp));
                        headbandDistances = temp;
                        updateSoundPositions();
                        txtHeadbandReadings.setText(Arrays.toString(temp));
                        break;
                    case BluetoothMessage.HEADBAND_BATTERY:
                        setHeadbandBattery(msg.arg1);
                        txtHeadbandBattery.setText(Long.toString(getHeadbandBattery()));
                        speak("Headband battery level " + headbandBattery);
                        break;
                    case BluetoothMessage.STICK_BATTERY:
                        setStickBattery(msg.arg1);
                        txtStickBattery.setText(Long.toString(getStickBattery()));
                        speak("Walking stick battery level " + stickBattery);
                        break;
                    case BluetoothMessage.STICK_DISTANCE:
                        setStickDistance(msg.arg1);
                        txtStickDistance.setText(Long.toString(getStickDistance()));
                        speak(stickDistance + " away");
                        break;
                    case BluetoothMessage.IR_READING:
                        System.out.println("IR message: " + (int) msg.obj);
                        switch ((int) msg.obj){
                            case 1:
                                speak("Pedestrian Crossing Red");
                                break;
                            case 2:
                                speak("Pedestrian Crossing Green");
                                break;
                            case 3 :
                                speak("177 Bus");
                                break;
                            case 4:
                                speak("Pedestrian Crossing ahead");
                                break;
                            case 5:
                                speak("Railway station ahead");
                                break;
                            case 6:
                                speak("Bus stand ahead");
                                break;
                            case 7:
                                speak("Danger! pavement under construction");
                                break;
                            case 8:
                                speak("Hospital ahead");
                                break;
                        }
                        break;
                    default:
                        super.handleMessage(msg);
                }
            }
        };

        walkingStickHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what){
                    case BluetoothMessage.STICK_DISTANCE:
                        int distance = msg.arg1;
                        if (distance <= 0){
                            speak("out of range");
                        } else {
                            speak(distance + "centimeters away");
                        }
                        break;
                }
            }
        };

        // Make sure the devices have been paired before
        if (walkingStick != null){
            ReadThread walkingStickReadThread = new ReadThread(walkingStick, walkingStickHandler);
            walkingStickReadThread.start();
        } else {
            Toast.makeText(this,"walking stick not detected",Toast.LENGTH_LONG).show();
        }

        if (headBand != null){
            ReadThread headBandReadThread = new ReadThread(headBand,headbandHandler);
            headBandReadThread.start();
        } else {
            Toast.makeText(this,"head band not detected",Toast.LENGTH_LONG).show();
        }

    }

    private synchronized void updateSoundPositions() {
        for (int i = 0; i < 5; i++){
            if ((headbandDistances[i] >= MAX_LENGTH ) || (headbandDistances[i] <= MIN_LENGTH)){
                continue;
            }
            float[] coordinates = polarToCartesian(headbandDistances[i],angles[i]);
            moveSoundSource(i,coordinates[0],coordinates[1],coordinates[2]);
        }
    }

    public synchronized void moveSoundSource(int id,float x, float y, float z){
        int sourceId = gvrAudioEngine.createSoundObject(SUCCESS_SOUND_FILE);
        gvrAudioEngine.setSoundObjectPosition(sourceId,x,y,z);
        gvrAudioEngine.playSound(sourceId,false);
        gvrAudioEngine.update();
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

    private void updatePairedDevicesList(){
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        ArrayList<String> deviceNames = new ArrayList<>();
        if (pairedDevices.size() > 0){
            for (BluetoothDevice device:pairedDevices){
                String deviceName = device.getName();
                if (device.getName().equals(HEADBAND_NAME)){
                    this.headBand = device;
                } else if (device.getName().equals(WALKING_STICK_NAME)){
                    this.walkingStick = device;
                }
                deviceNames.add(deviceName);
            }
            String[] nameArr = deviceNames.toArray(new String[0]);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,nameArr);
            lstPaired.setAdapter(adapter);
        } else {
            Toast.makeText(this,"No bluetooth headBand has been paired yet",Toast.LENGTH_LONG).show();
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case LOCATION_PERMISSION_REQUEST:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    enableLocationListener();
                }
                break;
            case SMS_PERMISSION_REQUEST:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    sendSMS();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
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
                byte[] header = new byte[1];
                while (true){
                    try {
                        int t1 = inputStream.read(header,0,1);
                        if (header[0] == 'H') {
                            byte[] distances = new byte[5];
                            int temp = inputStream.read(distances,0,5);
                            System.out.println(Arrays.toString(distances));
                            Message.obtain(handler,BluetoothMessage.HEADBAND_DISTANCE,distances).sendToTarget();
                        } else if (header[0] == 'S'){
                            byte[] reading = new byte[1];
                            int temp = inputStream.read(reading,0,1);
                            int irReading = reading[0]-48;
                            Message.obtain(handler,BluetoothMessage.IR_READING,irReading).sendToTarget();
                        } else if (header[0] == 'D'){
                            byte[] distance = new byte[1];
                            int reading = inputStream.read(distance,0,1);
                            System.out.println(distance[0]);
                            Message.obtain(handler,BluetoothMessage.STICK_DISTANCE,distance[0],0).sendToTarget();
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
