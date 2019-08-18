package com.dji.sdk.sample.demo.flightcontroller;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.os.Debug;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.controller.MainActivity;
import com.dji.sdk.sample.internal.utils.DialogUtils;
import com.dji.sdk.sample.internal.utils.ModuleVerificationUtil;
import com.dji.sdk.sample.internal.utils.OnScreenJoystick;
import com.dji.sdk.sample.internal.OnScreenJoystickListener;
import com.dji.sdk.sample.internal.utils.ToastUtils;
import com.dji.sdk.sample.internal.view.PresentableView;


import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;

import dji.common.error.DJIError;
import dji.common.flightcontroller.simulator.InitializationData;
import dji.common.flightcontroller.simulator.SimulatorState;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.model.LocationCoordinate2D;
import dji.common.util.CommonCallbacks;
import dji.keysdk.FlightControllerKey;
import dji.keysdk.KeyManager;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.flightcontroller.Simulator;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;


import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.InetAddress;


//TODO: Refactor needed

/**
 * Class for virtual stick.
 */
public class VirtualStickView extends RelativeLayout
        implements View.OnClickListener, CompoundButton.OnCheckedChangeListener, PresentableView {

    private boolean yawControlModeFlag = true;
    private boolean rollPitchControlModeFlag = true;
    private boolean verticalControlModeFlag = true;
    private boolean horizontalCoordinateFlag = true;

    private Button btnEnableVirtualStick;
    private Button btnDisableVirtualStick;
    private Button btnHorizontalCoordinate;
    private Button btnSetYawControlMode;
    private Button btnSetVerticalControlMode;
    private Button btnSetRollPitchControlMode;
    private ToggleButton btnSimulator;
    private Button btnTakeOff;

    private TextView textView;

    private OnScreenJoystick screenJoystickRight;
    private OnScreenJoystick screenJoystickLeft;

    private Timer sendVirtualStickDataTimer;
    private SendVirtualStickDataTask sendVirtualStickDataTask;

    private Context maincontext;

    public float pitch=0;
    public float roll=0;
    public float yaw=0;
    public float throttle=1;
    private FlightControllerKey isSimulatorActived;

    public float MaxRollSpeed=10;

    Server server;



    public VirtualStickView(Context context) throws IOException{
        super(context);
        init(context);
        maincontext =context;

    }


    @NonNull
    @Override
    public String getHint() {
        return this.getClass().getSimpleName() + ".java";
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setUpListeners();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (null != sendVirtualStickDataTimer) {
            if (sendVirtualStickDataTask != null) {
                sendVirtualStickDataTask.cancel();

            }
            sendVirtualStickDataTimer.cancel();
            sendVirtualStickDataTimer.purge();
            sendVirtualStickDataTimer = null;
            sendVirtualStickDataTask = null;
        }
        tearDownListeners();
        super.onDetachedFromWindow();
    }

    private void init(Context context)throws IOException {
        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Service.LAYOUT_INFLATER_SERVICE);
        layoutInflater.inflate(R.layout.view_virtual_stick, this, true);

        initAllKeys();
        initUI();





    }

    private void initAllKeys() {
        isSimulatorActived = FlightControllerKey.create(FlightControllerKey.IS_SIMULATOR_ACTIVE);
    }

    private void initUI() {
        btnEnableVirtualStick = (Button) findViewById(R.id.btn_enable_virtual_stick);
        btnDisableVirtualStick = (Button) findViewById(R.id.btn_disable_virtual_stick);
        btnHorizontalCoordinate = (Button) findViewById(R.id.btn_horizontal_coordinate);
        btnSetYawControlMode = (Button) findViewById(R.id.btn_yaw_control_mode);
        btnSetVerticalControlMode = (Button) findViewById(R.id.btn_vertical_control_mode);
        btnSetRollPitchControlMode = (Button) findViewById(R.id.btn_roll_pitch_control_mode);
        btnTakeOff = (Button) findViewById(R.id.btn_take_off);

        btnSimulator = (ToggleButton) findViewById(R.id.btn_start_simulator);

        textView = (TextView) findViewById(R.id.textview_simulator);

        screenJoystickRight = (OnScreenJoystick) findViewById(R.id.directionJoystickRight);
        screenJoystickLeft = (OnScreenJoystick) findViewById(R.id.directionJoystickLeft);



        btnEnableVirtualStick.setOnClickListener(this);
        btnDisableVirtualStick.setOnClickListener(this);
        btnHorizontalCoordinate.setOnClickListener(this);
        btnSetYawControlMode.setOnClickListener(this);
        btnSetVerticalControlMode.setOnClickListener(this);
        btnSetRollPitchControlMode.setOnClickListener(this);
        btnTakeOff.setOnClickListener(this);
        btnSimulator.setOnCheckedChangeListener(VirtualStickView.this);

        Boolean isSimulatorOn = (Boolean) KeyManager.getInstance().getValue(isSimulatorActived);
        if (isSimulatorOn != null && isSimulatorOn) {
            btnSimulator.setChecked(true);
            textView.setText("Simulator is On.");
        }
    }




    private void setUpListeners() {
        Simulator simulator = ModuleVerificationUtil.getSimulator();
        if (simulator != null) {
            simulator.setStateCallback(new SimulatorState.Callback() {
                @Override
                public void onUpdate(@NonNull final SimulatorState simulatorState) {
                    ToastUtils.setResultToText(textView,
                            "Yaw : "
                                    + simulatorState.getYaw()
                                    + ","
                                    + "X : "
                                    + simulatorState.getPositionX()
                                    + "\n"
                                    + "Y : "
                                    + simulatorState.getPositionY()
                                    + ","
                                    + "Z : "
                                    + simulatorState.getPositionZ());
                }
            });
        } else {
            ToastUtils.setResultToToast("Disconnected!");
        }




/*
        screenJoystickLeft.setJoystickListener(new OnScreenJoystickListener() {

            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                if (Math.abs(pX) < 0.02) {
                    pX = 0;
                }


                if (Math.abs(pY) < 0.02) {
                    pY = 0;
                }
                float pitchJoyControlMaxSpeed = 10;
                float rollJoyControlMaxSpeed = 10;

                if (horizontalCoordinateFlag) {
                    if (rollPitchControlModeFlag) {
                        pitch = (float) (pitchJoyControlMaxSpeed * pX);

                        roll = (float) (rollJoyControlMaxSpeed * pY);
                    } else {
                        pitch = -(float) (pitchJoyControlMaxSpeed * pY);

                        roll = (float) (rollJoyControlMaxSpeed * pX);
                    }
                }

                if (null == sendVirtualStickDataTimer) {
                    sendVirtualStickDataTask = new SendVirtualStickDataTask();
                    sendVirtualStickDataTimer = new Timer();
                    sendVirtualStickDataTimer.schedule(sendVirtualStickDataTask, 100, 200);
                }
            }
        });






        screenJoystickRight.setJoystickListener(new OnScreenJoystickListener() {

            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                if (Math.abs(pX) < 0.02) {
                    pX = 0;
                }

                if (Math.abs(pY) < 0.02) {
                    pY = 0;
                }
                float verticalJoyControlMaxSpeed = 2;
                float yawJoyControlMaxSpeed = 3;

                yaw = yawJoyControlMaxSpeed * pX;
                throttle = verticalJoyControlMaxSpeed * pY;

                if (null == sendVirtualStickDataTimer) {
                    sendVirtualStickDataTask = new SendVirtualStickDataTask();
                    sendVirtualStickDataTimer = new Timer();
                    sendVirtualStickDataTimer.schedule(sendVirtualStickDataTask, 0, 200);
                }
            }
        });
 */
    }



    private void tearDownListeners() {
        Simulator simulator = ModuleVerificationUtil.getSimulator();
        if (simulator != null) {
            simulator.setStateCallback(null);
        }
        screenJoystickLeft.setJoystickListener(null);
        screenJoystickRight.setJoystickListener(null);
    }

    @Override
    public void onClick(View v) {
        FlightController flightController = ModuleVerificationUtil.getFlightController();
        if (flightController == null) {
            return;
        }
        switch (v.getId()) {
            case R.id.btn_enable_virtual_stick:
                flightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        DialogUtils.showDialogBasedOnError(getContext(), djiError);
                    }
                });

                if (null == sendVirtualStickDataTimer) {
                    sendVirtualStickDataTask = new SendVirtualStickDataTask();
                    sendVirtualStickDataTimer = new Timer();
                    sendVirtualStickDataTimer.schedule(sendVirtualStickDataTask, 100, 200);
                }


                break;

            case R.id.btn_disable_virtual_stick:
                flightController.setVirtualStickModeEnabled(false, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        DialogUtils.showDialogBasedOnError(getContext(), djiError);
                    }
                });


                break;

            case R.id.btn_roll_pitch_control_mode:

                server = new Server((Activity) getContext());
                textView.setText(server.getIpAddress());
                Log.d("socket",server.getIpAddress());
                Log.d("socket",String.valueOf(server.getPort()));













                /*
                if (rollPitchControlModeFlag) {
                    flightController.setRollPitchControlMode(RollPitchControlMode.ANGLE);
                    rollPitchControlModeFlag = false;
                } else {
                    flightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
                    rollPitchControlModeFlag = true;
                }
                */

                /*
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            initserver();
                        }catch (IOException e){
                            e.printStackTrace();
                        }
                    }
                }).start();
                */



                /*
                try {
                    ToastUtils.setResultToToast(flightController.getRollPitchControlMode().name());
                } catch (Exception ex) {
                }*/
                break;

            case R.id.btn_yaw_control_mode:
                if (yawControlModeFlag) {
                    flightController.setYawControlMode(YawControlMode.ANGLE);
                    yawControlModeFlag = false;
                } else {
                    flightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
                    yawControlModeFlag = true;
                }
                try {
                    ToastUtils.setResultToToast(flightController.getYawControlMode().name());
                } catch (Exception ex) {
                }
                break;

            case R.id.btn_vertical_control_mode:
                if (verticalControlModeFlag) {
                    flightController.setVerticalControlMode(VerticalControlMode.POSITION);
                    verticalControlModeFlag = false;
                } else {
                    flightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
                    verticalControlModeFlag = true;
                }
                try {
                    ToastUtils.setResultToToast(flightController.getVerticalControlMode().name());
                } catch (Exception ex) {
                }
                break;

            case R.id.btn_horizontal_coordinate:
                if (horizontalCoordinateFlag) {
                    flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.GROUND);
                    horizontalCoordinateFlag = false;
                } else {
                    flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
                    horizontalCoordinateFlag = true;
                }
                try {
                    ToastUtils.setResultToToast(flightController.getRollPitchCoordinateSystem().name());
                } catch (Exception ex) {
                }
                break;

            case R.id.btn_take_off:

                flightController.startTakeoff(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        DialogUtils.showDialogBasedOnError(getContext(), djiError);
                    }
                });

                break;

            default:
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if (compoundButton == btnSimulator) {
            onClickSimulator(b);
        }
    }

    private void onClickSimulator(boolean isChecked) {
        Simulator simulator = ModuleVerificationUtil.getSimulator();
        if (simulator == null) {
            return;
        }
        if (isChecked) {

            textView.setVisibility(VISIBLE);

            simulator.start(InitializationData.createInstance(new LocationCoordinate2D(23, 113), 10, 10),
                    new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {

                        }
                    });
        } else {

            textView.setVisibility(INVISIBLE);

            simulator.stop(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {

                }
            });
        }
    }

    @Override
    public int getDescription() {
        return R.string.flight_controller_listview_virtual_stick;
    }

    private class SendVirtualStickDataTask extends TimerTask {

        @Override
        public void run() {
            if (ModuleVerificationUtil.isFlightControllerAvailable()) {
                DJISampleApplication.getAircraftInstance()
                        .getFlightController()
                        .sendVirtualStickFlightControlData(new FlightControlData(pitch,
                                        roll,
                                        yaw,
                                        throttle),
                                new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(DJIError djiError) {

                                    }
                                });
            }


            Log.d("pitch: ",String.valueOf(pitch));
            Log.d("roll: ",String.valueOf(roll));
            Log.d("yaw: ",String.valueOf(yaw));
            Log.d("throttle: ",String.valueOf(throttle));


        }
    }
    public float[] transfer(Double x,Double y){

        float[] axis = {x.floatValue(),y.floatValue()};
        return axis;
    };


    public class AndroidRunnable implements Runnable {
        public  Socket socket =null;

        public AndroidRunnable (Socket socket){
            this.socket = socket;

        }
        public void run(){
            System.out.println("客户端连入成功...");
            Log.d("socket","客户端连入成功");
            String line = "";
            InputStream input = null;
            OutputStream output = null;
            BufferedReader bff = null;
            JSONObject result = new JSONObject();

            try {
                // 向客户端发送信息
                output = socket.getOutputStream();
                input = socket.getInputStream();
                bff = new BufferedReader(new InputStreamReader(input));

                // 获取客户端的信息
                while ((line = bff.readLine()) != null) {
                    System.out.println("接收到客户端的消息为：" + line);
                    JSONObject jsonObject = new JSONObject(line);

                    Double x = jsonObject.getDouble("x");
                    Double y = jsonObject.getDouble("y");
                    roll = transfer(x,y)[0]; //横向移动
                    throttle = transfer(x,y)[1];//高度
                    System.out.println("向客户端发送的消息为：" + result.toString());
                    OutputStream out = socket.getOutputStream();
                    out.write(result.toString().getBytes());
                    out.flush();

                    //socket.shutdownInput();// 半闭socket
                    // 向客户端发送消息
                    //String username = jsonObject.getString("username");
                    //String password = jsonObject.getString("password");
                    // 验证用户名、密码
                    /*if (username.equals("123") && password.equals("456")) {

                        result.put("state", "0");

                        result.put("msg", "登录成功");
                    } else {
                        result.put("state", "1");
                        result.put("msg", "密码错误");
                    }*/

                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // 关闭输入输出流
                try {
                    output.close();
                    bff.close();
                    input.close();
                    socket.close();// socket最后关闭
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }





    public class Server {
        Activity activity;
        ServerSocket serverSocket;
        String message = "";
        static final int socketServerPORT = 8080;

        public Server(Activity activity) {
            this.activity = activity;
            Thread socketServerThread = new Thread(new SocketServerThread());
            socketServerThread.start();
            Log.d("socket","server初始化成功");
        }

        public int getPort() {
            return socketServerPORT;
        }

        public void onDestroy() {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }

        private class SocketServerThread extends Thread {

            int count = 0;

            @Override
            public void run() {
                try {
                    // create ServerSocket using specified port
                    serverSocket = new ServerSocket(socketServerPORT);

                    while (true) {
                        // block the call until connection is created and return
                        // Socket object
                        Socket socket = serverSocket.accept();

                        count++;
                        message += "#" + count + " from "
                                + socket.getInetAddress() + ":"
                                + socket.getPort() + "\n";


                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                textView.setText(message);
                            }
                        });

                        SocketServerReplyThread socketServerReplyThread =
                                new SocketServerReplyThread(socket, count);
                        socketServerReplyThread.run();

                    }
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }

        private class SocketServerReplyThread extends Thread {

            private Socket hostThreadSocket;
            int cnt;

            SocketServerReplyThread(Socket socket, int c) {
                hostThreadSocket = socket;
                cnt = c;
            }

            @Override
            public void run() {
                OutputStream outputStream;
                String msgReply = "Hello from Server, you are #" + cnt;
                String line = "";
                InputStream input = null;
                BufferedReader bff = null;
                JSONObject result = new JSONObject();
                int temp = 0;

                try {
                    outputStream = hostThreadSocket.getOutputStream();
                    input = hostThreadSocket.getInputStream();
                    bff = new BufferedReader(new InputStreamReader(input));


                    //PrintStream printStream = new PrintStream(outputStream);
                    //printStream.print(msgReply);
                    //printStream.close();

                    message += "replayed: " + msgReply + "\n";

                    activity.runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            textView.setText(message);
                        }
                    });

                    try {
                        while ((line = bff.readLine()) != null) {

                            try{
                                JSONObject jsonObject = new JSONObject(line);
                                DataOutputStream out = new DataOutputStream(hostThreadSocket.getOutputStream());

                                switch (jsonObject.getString("DataType")){
                                    case "DroneVector":
                                        float x = Float.valueOf(jsonObject.getJSONObject("position").getString("x"));
                                        float y = Float.valueOf(jsonObject.getJSONObject("position").getString("y"));
                                        if(x>0.3 ){
                                            roll = MaxRollSpeed;

                                        }else if(x<-0.3){
                                            roll = - MaxRollSpeed;

                                        }else if( y>0.3){
                                            throttle+=0.1;
                                        }else if (y<-0.3 && throttle>0){
                                            throttle-=0.1;
                                        }
                                        break;



                                }



                                PrintStream out1 = new PrintStream(out);
                                out1.println("已接收");


                                out1.flush();

                                message += jsonObject+"\n";
                                activity.runOnUiThread(new Runnable() {

                                    @Override
                                    public void run() {
                                        textView.setText(message);
                                    }
                                });
                            }catch (JSONException e){
                                e.printStackTrace();
                            }





                            //PrintStream printStream = new PrintStream(outputStream);
                            //printStream.print(jsonObject);
                            //printStream.flush();

                        }
                    }catch (IOException e){
                        message +="readline empty! \n";
                        e.printStackTrace();
                        hostThreadSocket.close();
                    }finally {

                        hostThreadSocket.close();
                        message +="socket关闭！ \n";
                    }






                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    message += "Something wrong! " + e.toString() + "\n";

                }



                activity.runOnUiThread(new Runnable() {


                    @Override
                    public void run() {
                        textView.setText(message);
                    }
                });
            }

        }

        public String getIpAddress() {
            String ip = "";
            try {
                Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface
                        .getNetworkInterfaces();
                while (enumNetworkInterfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = enumNetworkInterfaces
                            .nextElement();
                    Enumeration<InetAddress> enumInetAddress = networkInterface
                            .getInetAddresses();
                    while (enumInetAddress.hasMoreElements()) {
                        InetAddress inetAddress = enumInetAddress
                                .nextElement();

                        if (inetAddress.isSiteLocalAddress()) {
                            ip += "Server running at : "
                                    + inetAddress.getHostAddress();
                        }
                    }
                }

            } catch (SocketException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                ip += "Something Wrong! " + e.toString() + "\n";
            }
            return ip;
        }
    }





























}
