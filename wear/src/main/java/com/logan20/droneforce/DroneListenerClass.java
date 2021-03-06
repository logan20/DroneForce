package com.logan20.droneforce;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.SensorManager;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DICTIONARY_KEY_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_ERROR_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerArgumentDictionary;
import com.parrot.arsdk.arcontroller.ARControllerDictionary;
import com.parrot.arsdk.arcontroller.ARDeviceController;
import com.parrot.arsdk.arcontroller.ARDeviceControllerListener;
import com.parrot.arsdk.arcontroller.ARFeatureARDrone3;
import com.parrot.arsdk.arcontroller.ARFeatureCommon;
import com.parrot.arsdk.arcontroller.ARFeatureMiniDrone;
import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_ENUM;


public class DroneListenerClass implements ARDeviceControllerListener{
    private Enum flyingState;
    private sensorClass sensor;
    private static final long THREAD_TIMEOUT = 50;
    private Context context;
    private Activity activity;
    private ARDeviceController deviceController;
    private Thread pilotThread;
    private volatile float[] currReadings,myReadings;
    private float STRONG_MOVEMENT = 18.0f;
    public volatile int toggle;
    private int batVal;
    private String droneName;
    private String droneType;
    private final int BTNEMERGENCYLAND = 123;
    private final int BTNAUTOTAKEOFF = 124;
    private final long FLYTIMEBEFORESTABILIZATION = 10000; //10 seconds before stabilising
    private final int BTNEXIT = 125;
    private Thread stabilise;

    public DroneListenerClass(Activity act, Context ctx, ARDeviceController dvc, String droneName, String droneType){
        deviceController=dvc;
        toggle=0;
        context=ctx;
        activity=act;
        this.droneName=droneName;
        this.droneType=droneType;
        myReadings = new float[4];
        myReadings[3]=Math.abs(System.currentTimeMillis())%10000000;
        pilotFromSensor();
        initProgress();
    }

    private void initProgress() {
        Toast.makeText(context.getApplicationContext(),"Waiting on OK from drone, Please wait",Toast.LENGTH_LONG).show();
    }

    public void toggleAutoTakeoff(){
        if (deviceController!=null){
            toggle=1-toggle;
            if (context!=null){
                if (toggle == 1) {
                    Toast.makeText(context.getApplicationContext(),"Auto Take-off Mode Enabled",Toast.LENGTH_LONG).show();
                    activity.findViewById(BTNAUTOTAKEOFF).setBackgroundResource(R.drawable.auto_hover_enabled);
                }
                else{
                    Toast.makeText(context.getApplicationContext(),"Auto Take-off Mode Disabled",Toast.LENGTH_LONG).show();
                    activity.findViewById(BTNAUTOTAKEOFF).setBackgroundResource(R.drawable.auto_hover_disabled);
                }
                autoTakeoff(toggle);
            }
        }
    }

    public void autoTakeoff(int enabled){
        switch (droneType){
            case "minidrone":
                deviceController.getFeatureMiniDrone().sendPilotingAutoTakeOffMode((byte)enabled);
                break;
            default:
                Log.d("UNSUPOTED","Unsupported");
                break;
        }


    }
    private void pilotFromSensor() {
        pilotThread=new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.interrupted()) {
                    if (sensor != null) {
                        compareReadings();
                        switch (getState()) {
                            case "landed state":
                                //the only thing you can do when landed is take off
                                if (Math.abs(myReadings[2])>STRONG_MOVEMENT||Math.abs(myReadings[1])>STRONG_MOVEMENT||Math.abs(myReadings[0])>STRONG_MOVEMENT){
                                    takeOff();
                                }
                                break;
                            case "emergency state":
                                Log.d("Emergency", "Emergency state");
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(activity.getApplicationContext(),"Battery for the drone is critical. Please recharge!",Toast.LENGTH_LONG).show();
                                    }
                                });
                                stopThread();
                                break;
                            default:
                                toggle=0;
                                pilot();
                                break;
                        }
                        try{
                            Thread.sleep(THREAD_TIMEOUT);
                        }
                        catch (Exception e){
                            e.printStackTrace();
                            Thread.currentThread().interrupt();
                        }
                        Log.d("State", flyingState + " ");
                    }
                }
            }
        });
        pilotThread.start();
    }

    private void compareReadings() {
        currReadings = sensor.getOrientationRelativeSpeedReadings();
        boolean updated=false;
        for (int a=0;a<3;a++)
            if (currReadings[a]!=myReadings[a]){
                if (currReadings[a]==0 && Math.abs(System.currentTimeMillis())%10000000-myReadings[3]>FLYTIMEBEFORESTABILIZATION){
                    myReadings[a]=0;
                }
                else{
                    myReadings[a]=currReadings[a];
                }
                updated=true;
            }
        if (updated){
            myReadings[3]= Math.abs(System.currentTimeMillis())%10000000;
            Log.d("TAG", "compareReadings: "+String.format("%.2f\t%.2f\t%.2f",myReadings[0],myReadings[1],myReadings[2]));
        }

    }

    private void pilot() {
        float hSpeed = myReadings[2];
        float vSpeed = myReadings[1];
        float dSPeed = myReadings[0];
        int timestamp = (int)System.currentTimeMillis();

        switch (droneType){
            case "minidrone":
                deviceController.getFeatureMiniDrone().setPilotingPCMD((byte)1,(byte)hSpeed,(byte)dSPeed,(byte)0,(byte)vSpeed,timestamp);
                break;
            default:
                Log.d("UNSUPORTED DRONE","Add functionality for "+droneType);
                break;
        }

    }

    private void emergencyLand() {
        switch (droneType){
            case "minidrone":
                deviceController.getFeatureMiniDrone().sendPilotingEmergency();
                break;
            default:
                Log.d("UNSUPORTED DRONE","Add functionality for "+droneType);
                break;
        }
    }

    private void takeOff() {
        switch (droneType){
            case "minidrone":
                deviceController.getFeatureMiniDrone().sendPilotingTakeOff();
                break;
            default:
                Log.d("UNSUPORTED DRONE","Add functionality for "+droneType);
                break;
        }

    }

    @NonNull
    private String getState(){
        if (flyingState!=null){
            return (flyingState+"").toLowerCase();
        }
        return "";
    }
    @Override
    public void onStateChanged(ARDeviceController deviceController, ARCONTROLLER_DEVICE_STATE_ENUM newState, ARCONTROLLER_ERROR_ENUM error) {
        switch (newState){
            case ARCONTROLLER_DEVICE_STATE_RUNNING:
                Log.d("STATE","Controller is RUNNING");
                //only when the controller is started, the create the sensor
                sensor=new sensorClass((SensorManager)context.getApplicationContext().getSystemService(Context.SENSOR_SERVICE));
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context.getApplicationContext(),"Ready to fly!",Toast.LENGTH_LONG).show();
                        activity.findViewById(BTNAUTOTAKEOFF).setClickable(true);
                        activity.findViewById(BTNEMERGENCYLAND).setClickable(true);
                        activity.findViewById(BTNEMERGENCYLAND).setBackgroundResource(R.drawable.emergency_enabled);
                    }
                });
                break;
            case ARCONTROLLER_DEVICE_STATE_STOPPED:
                Log.d("STATE","Controller is STOPPED");
                stopThread();
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(activity.getApplicationContext(),"Drone has been disconnected from the device",Toast.LENGTH_SHORT).show();
                    }
                });
                break;
            case ARCONTROLLER_DEVICE_STATE_STARTING:
                Log.d("STATE","Controller is STARTING");
                //create icons on the screen that shows drone's batt percentage
                final TextView df=(TextView)activity.findViewById(R.id.tv_mainTxt);
                final Button btnConnect=(Button)activity.findViewById(R.id.btnConnect);
                final ViewGroup buttonPanel = (ViewGroup) btnConnect.getParent();
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnConnect.setVisibility(View.GONE); //hide the "connect" button from the screen


                        //change the text of the header
                        df.setTextSize(18);
                        df.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL);
                        df.setText("DroneForce:\nConnected to "+droneName+"\nBattery Level: "+batVal+"%");

                        //define button parameters
                        RelativeLayout.LayoutParams param1 = new RelativeLayout.LayoutParams(100,100);
                        RelativeLayout.LayoutParams param2 = new RelativeLayout.LayoutParams(100,100);
                        RelativeLayout.LayoutParams param3 = new RelativeLayout.LayoutParams(100,100);
                        param2.addRule(RelativeLayout.RIGHT_OF,BTNEMERGENCYLAND);
                        param3.addRule(RelativeLayout.RIGHT_OF,BTNAUTOTAKEOFF);

                        //add buttons for drone's functionality
                        Button emLand = new Button(context);
                        emLand.setBackgroundResource(R.drawable.emergency_disabled);
                        emLand.setId(BTNEMERGENCYLAND);
                        emLand.setLayoutParams(param1);
                        emLand.setClickable(false);
                        emLand.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                //when emergency land is pressed, emergency land the drone
                                emergencyLand();
                            }
                        });

                        Button autoTakeoff = new Button(context);
                        autoTakeoff.setBackgroundResource(R.drawable.auto_hover_disabled);
                        autoTakeoff.setId(BTNAUTOTAKEOFF);
                        autoTakeoff.setLayoutParams(param2);
                        autoTakeoff.setClickable(false);
                        autoTakeoff.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                //when autoTakeoff is clicked, toggle the autotakeoff
                                toggleAutoTakeoff();
                            }
                        });

                        Button exit = new Button(context);
                        exit.setBackgroundResource(android.R.drawable.ic_menu_close_clear_cancel);
                        exit.setId(BTNEXIT);
                        exit.setLayoutParams(param3);
                        exit.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        new AlertDialog.Builder(context)
                                                .setMessage("Do you want to disconnect from the drone?")
                                                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        //end the current activity and restart
                                                        stopThread();
                                                        activity.finish();
                                                        activity.startActivity(activity.getBaseContext().getPackageManager().getLaunchIntentForPackage(activity.getBaseContext().getPackageName()).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                                                    }
                                                })
                                                .setNegativeButton(android.R.string.no,null)
                                                .show();
                                    }
                                });
                            }
                        });
                        //add created buttons to layout
                        buttonPanel.addView(emLand);
                        buttonPanel.addView(autoTakeoff);
                        buttonPanel.addView(exit);
                    }
                });
                break;
            case ARCONTROLLER_DEVICE_STATE_STOPPING:
                Log.d("STATE","Controller is STOPPING");
                break;
            default:
                break;
        }
    }

    private void stopThread() {
        if (pilotThread!=null){
            pilotThread.interrupt();
        }
        //when thread is stopped, the sensor should also release control of the accelerometer and gyroscope
        if (sensor!=null){
            sensor.unregisterListeners();
        }

        if (stabilise!=null){
            stabilise.interrupt();
        }
    }

    @Override
    public void onExtensionStateChanged(ARDeviceController deviceController, ARCONTROLLER_DEVICE_STATE_ENUM newState, ARDISCOVERY_PRODUCT_ENUM product, String name, ARCONTROLLER_ERROR_ENUM error) {

    }

    @Override
    public void onCommandReceived(ARDeviceController deviceController, ARCONTROLLER_DICTIONARY_KEY_ENUM commandKey, ARControllerDictionary elementDictionary) {
        if (elementDictionary!=null){
            //if the command recieved is battery update
            if (commandKey==ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED){
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args!=null){
                    batVal = (Integer) args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED_PERCENT);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ((TextView)activity.findViewById(R.id.tv_mainTxt)).setText(("DroneForce:\nConnected to "+droneName+"\nBattery Level: "+batVal));
                        }

                    });

                }
            }
            else if (commandKey==ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED){
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args!=null){
                    Integer state = (Integer) args.get(ARFeatureMiniDrone.ARCONTROLLER_DICTIONARY_KEY_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE);
                    if (ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM.getFromValue(state)!=flyingState){
                        Log.d("STATE",flyingState+" ");
                    }

                    flyingState = ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM.getFromValue(state);
                }
            }
            else if (commandKey==ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED){
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args!=null){
                    Integer state = (Integer) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE);
                    flyingState = ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM.getFromValue(state);
                }
            }
        }
        else{
            Log.d("ERR","Element Dictionary is null");
        }
    }

    public void stopAll() {
        stopThread();
    }
}
