package com.andromeda.locationsender;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SmsManager;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    //------------------- Variables for the View widgets ----------------//
    TextView locationInfo, map, send, saveNumber, changeNumber;
    ListView pairedDevicesList;
    Switch btSwitch, atSwitch;
    EditText editText, defaultNumber;

    //------------------ Objects declarations ------------------//
    public FusedLocationProviderClient mFusedLocationClient;
    public BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private SharedPreferences sharedPreferences;

    //--------------------- Variable Declarations ------------------------//
    public static final UUID mUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public String locationData, geocode, number, smsBody, address;
    public String receivedMessage;
    public boolean isBtConnected = false;
    public static int REQUEST_ENABLE_BT = 1;

    //----------- Handle incoming messages -----------------//
    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() { ///////////////////////////////////////////////////// handle received messages
        @Override
        public void handleMessage(Message msg) {
            byte[] buffer = (byte[]) msg.obj;
            int begin = msg.arg1;
            int end = msg.arg2;

            switch (msg.what) {
                case 1:
                    receivedMessage = new String(buffer);
                    receivedMessage = receivedMessage.substring(begin, end);
                    onBluetoothMessageReceive();
                    break;
            }
        }
    };

    //----------------------- OnCreate method call --------------------------//
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        sharedPreferences = MainActivity.this.getPreferences(Context.MODE_PRIVATE);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // ----------------- initialization -----------------------//
        locationInfo = findViewById(R.id.location_info);
        map = findViewById(R.id.button_map);
        send = findViewById(R.id.button_send);
        editText = findViewById(R.id.editText);
        defaultNumber = findViewById(R.id.def_num);
        btSwitch = findViewById(R.id.bt_switch);
        atSwitch = findViewById(R.id.at_switch);
        saveNumber = findViewById(R.id.save_num);
        changeNumber = findViewById(R.id.change_num);
        pairedDevicesList = findViewById(R.id.paired_device_list);

        defaultNumber.setText(getDefaultNumber());
        editText.setText(getDefaultNumber());

        //----------------- if bluetooth is on load paired devices -------------//
        if (bluetoothAdapter != null) {
            if (bluetoothAdapter.isEnabled()) {
                btSwitch.setChecked(true);
                pairedDevicesList();
            }

        }

        // ----------------- turn on bluetooth ---------------------------//
        btSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (bluetoothAdapter != null) {
                        if (!bluetoothAdapter.isEnabled()) {
                            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                        }
                    }
                }
            }
        });

        //--------------------------------------------- load location data -------------------------------------------------//
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            geocode = location.getLatitude() + "," + location.getLongitude();
                            locationData = "geo:0,0?q=" + geocode + "(Help)";
                            locationInfo.setText(String.format(Locale.US, "Latitude :  %f\n" + "Longitude : %f", location.getLatitude(), location.getLongitude()));
                        } else {
                            locationInfo.setText("Location Data is Not Available");
                        }
                    }
                });


        //-------------------------- open google map ------------------------------//
        map.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "Opening Maps", Toast.LENGTH_SHORT).show();
                Uri gmmIntentUri = Uri.parse(locationData);
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                if (mapIntent.resolveActivity(getPackageManager()) != null && locationData != null) {
                    startActivity(mapIntent);
                }
            }
        });

        // ------------------------ save a number --------------------------//
        saveNumber.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("Default_Number", defaultNumber.getText().toString());
                editor.apply();
                defaultNumber.setEnabled(false);
            }
        });

        // ----------------- change a number -----------------------//
        changeNumber.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                defaultNumber.setEnabled(true);
            }
        });

        // ---------------- send sms -----------------------//
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSms();
            }
        });
    }

    //--------------------------------------- sending geocode via sms --------------------------------------//
    public void sendSms() {
        if (!editText.getText().toString().equals(getDefaultNumber())) {
            number = editText.getText().toString();
            smsBody = "Help Me ! I am Here : https://www.google.com/maps/?q=" + geocode;
            SmsManager sms = SmsManager.getDefault();
            sms.sendTextMessage(number, null, smsBody, null, null);
            //Toast.makeText(MainActivity.this, "Message Sent to " + number, Toast.LENGTH_SHORT).show();
        } else {
            number = getDefaultNumber();///////////////////////////////////////////////////////////////////////////////////////////// put number here
            smsBody = "Help Me ! I am Here : https://www.google.com/maps/?q=" + geocode; ////////////////////////////////////////// edit sms body
            SmsManager sms = SmsManager.getDefault();
            sms.sendTextMessage(number, null, smsBody, null, null);
            //Toast.makeText(MainActivity.this, "Message Sent to Default Number", Toast.LENGTH_SHORT).show();
        }
    }

    // --------------- check if bluetooth is turned on ------------------//
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                pairedDevicesList();
            }
        }
    }

    // ----------------------- load paired devices ----------------------//
    public void pairedDevicesList() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        ArrayList<String> list = new ArrayList<>();

        int count = 1;

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice bt : pairedDevices) {
                String name = bt.getName().replace('\n', ' ').replace('\r', ' ');
                list.add(name + '\n' + bt.getAddress());
                count++;
            }
        } else {
            Toast.makeText(getApplicationContext(), "No Paired Bluetooth Devices Found.", Toast.LENGTH_LONG).show();
        }

        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, list);
        ViewGroup.LayoutParams params = pairedDevicesList.getLayoutParams();
        int unit = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, getResources().getDisplayMetrics());
        params.height = count * unit;
        pairedDevicesList.setLayoutParams(params);
        pairedDevicesList.setAdapter(adapter);
        pairedDevicesList.setOnItemClickListener(mListClickListener);
    }

    // -------------------------- list click listener ---------------------------//
    private AdapterView.OnItemClickListener mListClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView av, View v, int arg2, long arg3) {
            String info = ((TextView) v).getText().toString();
            address = info.substring(info.length() - 17);
            ConnectBluetooth connectBluetooth = new ConnectBluetooth();
            connectBluetooth.execute();
        }
    };

    // ------------------------------ connect app to bluetooth device ----------------------------//
    @SuppressLint("StaticFieldLeak")
    class ConnectBluetooth extends AsyncTask<Void, Void, Void> {
        private boolean ConnectSuccess = true;

        @Override
        protected Void doInBackground(Void... devices) {
            try {
                if (bluetoothSocket == null || !isBtConnected) {
                    BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);
                    bluetoothSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(mUUID);
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    bluetoothSocket.connect();
                }
            } catch (IOException e) {
                ConnectSuccess = false;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);

            if (ConnectSuccess) {
                isBtConnected = true;
                Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();
                ConnectedThread connectedThread = new ConnectedThread(bluetoothSocket);
                connectedThread.start();
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private class ConnectedThread extends Thread {
        private final InputStream mInStream;

        ConnectedThread(BluetoothSocket socket) {
            bluetoothSocket = socket;
            InputStream tmpIn = null;
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException ignored) {
            }
            mInStream = tmpIn;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int begin = 0;
            int bytes = 0;
            while (true) {
                try {
                    bytes += mInStream.read(buffer, bytes, buffer.length - bytes);
                    for (int i = begin; i < bytes; i++) {
                        if (buffer[i] == "\n".getBytes()[0]) { ///////////////////////////////////// read data until new line
                            mHandler.obtainMessage(1, begin, i, buffer).sendToTarget();
                            begin = i + 1;
                            if (i == bytes - 1) {
                                bytes = 0;
                                begin = 0;
                            }
                        }
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }
    }

    // ------------------ when a message is received ---------------//
    public void onBluetoothMessageReceive() {
        //btText.setText(receivedMessage);
        if (atSwitch.isChecked()) {
            sendSms();
            Toast.makeText(getApplicationContext(), "Sending Location...", Toast.LENGTH_SHORT).show();
            sendMessage("Location Sent");
        } else {
            Toast.makeText(getApplicationContext(), "Location not sent", Toast.LENGTH_SHORT).show();
            sendMessage("Location Not Sent");
        }
    }

    // ---------------- load default number ------------------------//
    public String getDefaultNumber() {
        return sharedPreferences.getString("Default_Number", "+8801914997809");
    }


    // --------------------- send bluetooth message -----------------------//
    public void sendMessage(String message) {
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.getOutputStream().write(message.getBytes());
            } catch (IOException ignored) {

            }
        }
    }

}
