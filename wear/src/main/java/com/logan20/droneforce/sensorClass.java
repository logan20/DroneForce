package com.logan20.droneforce;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.text.DecimalFormat;

/**
 * Created by kwasi on 3/7/2016.
 * Hello
 */
public class sensorClass implements SensorEventListener{
    SensorManager sm;  //Sensor manager
    Sensor sGyro, sAccel; //Gyroscope and Accelerometer sensors
    float[][] readings; //0th row is accelerometer's current reading
                        //1st row is gyroscope's current reading
                        //2th row is peak accelerometer reading
                        //3th row is min accelerometer reading
                        //4th row is peak accelerometer time
                        //5th row is min accelerometer time
                        //6th row is velocity (vector quantity) in each axis
    private final float ACCEL_DELTA = 1.0f;//minimum change needed before changing of values on accelerometer
    private final float ACCEL_ZERO_POINT = 0.5f;//noise level eliminator for accelerometer
    private final float GYRO_DELTA = 0.1f; //minimum change needed before changing of values on gyroscope
    private final float GYRO_ZERO_POINT = 0.05f; //noise level eliminator for gyroscope
    private final float MIN_MOTION_DELTA=8.0f;//minimum distance between peaks and trough in order to record an axis' speed
    private final long MIN_MOTION_TIME = 200; //minimum length of time user must move on a particular axis before reading is recorded
    private final long ZERO_TIME = 300;//if this time passes and there's no update of either min or max of an axis, reset that axis
    private Thread zeroThd;

    private void setSensorManager(SensorManager x){
        sm = x;
    }

    private void initArr(){
        readings = new float[7][3]; //initialise the readings matrix
    }

    public sensorClass(SensorManager x){
        setSensorManager(x);//set the sensor manager to the manager passed in from the main class
        initArr();//initialise the arrays that hold data
        setSensors(); //sets the sensors
        registerListeners(); //register the sensors
        zeroThread();
    }

    private void zeroThread() {
        zeroThd = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.interrupted()){
                    //if the latest peak/min was set a long time ago, zero the axis
                    long currTime = Math.abs(System.currentTimeMillis())%10000000;
                    for (int a=0;a<3;a++){
                        if (currTime>readings[4][a]+ZERO_TIME&&currTime>readings[5][a]+ZERO_TIME){
                            zeroAxis(a);
                        }
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
        zeroThd.start();
    }

    public void registerListeners(){
        sm.registerListener(this, sGyro, SensorManager.SENSOR_DELAY_FASTEST); //registers the listeners for the gyroscope
        sm.registerListener(this, sAccel, SensorManager.SENSOR_DELAY_FASTEST);//register the listeners for the accelerometer
    }
    public void unregisterListeners(){ //unregister listeners to save battery when sensors not in use
        sm.unregisterListener(this, sGyro);
        sm.unregisterListener(this, sAccel);
        if (zeroThd!=null)
            zeroThd.interrupt();
    }

    private void setSensors(){//sets the sensors
        sGyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sAccel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        updateSensors(event);
    }

    public void resetPeakMin() {//needed to reset the values of each axis so that new readings can be obtained, values cannot be defaulted to 0
        for (int a=0;a<3;a++){
            readings[2][a]=readings[0][a];
            readings[3][a]=readings[0][a];
        }
    }


    private void updateSensors(SensorEvent event){
        boolean t=false;//variable to see if any peak / min values was updated

        if (event.sensor.getType()==Sensor.TYPE_ACCELEROMETER){//for the accelerometer
            for (int a=0;a<3;a++){//for each of the x, y and z axis
                if (Math.abs(Math.abs(readings[0][a])-Math.abs(event.values[a]))>ACCEL_DELTA){//once the change in the readings is significant
                    readings[0][a]=Math.abs(event.values[a])<ACCEL_ZERO_POINT?0.00f:event.values[a];//set the value appropriately, (0 if the reading is approximately 0 else current sensor value
                    if (event.values[a]>readings[2][a]){//if the new value is greater than the peak
                        readings[2][a]=event.values[a]; //update the peak accelerometer reading
                        readings[4][a]=Math.abs(System.currentTimeMillis())%10000000;//update the time that the reading was taken
                    }
                    if (event.values[a]<readings[3][a]){//if the new value is smaller than the min
                        readings[3][a]=event.values[a];//update the min accelerometer reading
                        readings[5][a]=Math.abs(System.currentTimeMillis())%10000000;//update the time that the reading was taken
                    }
                    t=true;//set the flag that an update of peak / min was done
                }
            }
            if (t){//once an update of peak / min was done
                for (int a='x';a<='z';a++){//for each of the x,y and z axis
                    float motionMagnitude =readings[2][a-'x']-readings[3][a-'x'];
                    if (motionMagnitude>MIN_MOTION_DELTA){//once the movement's significant enough
                        if (Math.abs(Math.abs(readings[4][a-'x'])-Math.abs(readings[5][a-'x']))>MIN_MOTION_TIME){//once the time difference is significant enough or movement is a strong movement
                            readings[6][a-'x']=readings[2][a-'x']-readings[3][a-'x'];//set speed
                            readings[6][a-'x']=readings[4][a-'x']<readings[5][a-'x']?readings[6][a-'x']:-readings[6][a-'x'];//set direction
                            //Log.d(String.valueOf((char)a).toUpperCase()+" axis","Speed: "+readings[6][a-'x']);
                        }

                    }
                }
            }
        }
        else if (event.sensor.getType()==Sensor.TYPE_GYROSCOPE){//for the gyroscope
            for (int a=0;a<3;a++){//for each of the axis
                if (Math.abs(Math.abs(readings[1][a])-Math.abs(event.values[a]))>GYRO_DELTA){//once the movement is significant enough
                    readings[1][a]=Math.abs(event.values[a])<GYRO_ZERO_POINT?0.00f:event.values[a];//update readings
                    t=true;//set the flag
                }
            }
            if (t){//once the flag has been triggered
                //need to finish code out gyroscope section...
                //accelerometer is used to give direction
                //rotational information from gyroscope will be used for when the user shakes his hand, that will stop all movement of the drone
                //
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void zeroAxis(int i) {
        //Log.d("ZERO","Zeroing the "+i+"th axis");
        for (int a=2;a<7;a++){
            readings[a][i]=0.0f;//reset the peak, min, peaktime, mintime, and speed of the ith axis
        }
    }

    public float[] getOrientationRelativeSpeedReadings() {
        float[] ret=new float[3];
        int highest=1;
        for (int a=0;a<3;a++){
            ret[a]=0.0f;
        }
        highest = readings[6][0]>readings[6][1]&&readings[6][0]>readings[6][2]?0:readings[6][2]>readings[6][0]&&readings[6][2]>readings[6][1]?2:1;
        ret[highest]=readings[6][highest];
        return ret;
    }

}
