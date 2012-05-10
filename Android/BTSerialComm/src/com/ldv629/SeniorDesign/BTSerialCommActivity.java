/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Created by: Lee VanGundy
 * Last Modified: 5/2/2012
 * Part of Team MobileComm Senior Design project
 * 
 * This code has been modified from publicly available code published by
 * Google according to the above licensing. 
 * 
 * Designed and implemented to work with serial over bluetooth, specifically
 * devices made by Decagon Devices, Inc.
 */

package com.ldv629.SeniorDesign;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This is the main Activity that displays the current chat session.
 */
public class BTSerialCommActivity extends Activity {
    // Debugging
    private static final String TAG = "BTSerial";
    private static final boolean D = true;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int LOG_READ = 6;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    // Name of the connected device
    public String mConnectedDeviceName = null;
    // Array adapter for the conversation thread
    private ArrayAdapter<String> mConversationArrayAdapter;
    // String buffer for outgoing messages
    //private StringBuffer mOutStringBuffer;
    // Local Bluetooth adapter
    public static BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    public static BluetoothChatService mChatService = null;

    static String totalRead = null;
    static String justRead = null;
    String logName = null;
    
    
    public static int counter = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.e(TAG, "+++ ON CREATE +++");
        
        // Set up the window layout
        setContentView(R.layout.main);

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        //Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(this, mHandler);
        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        //setContentView(R.layout.main);
    }

    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");
        
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the chat session
        } else {
            if (mChatService == null) home_options();
        }
    	home_options();
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+ ON RESUME +");

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
              // Start the Bluetooth chat services
              mChatService.start();
            }
        }
    }

    private void home_options() {
        Log.d(TAG, "home_options()");

            
        Button ConnectDeviceButton = (Button) findViewById(R.id.button1);
        ConnectDeviceButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	Intent serverIntent = null;
            	serverIntent = new Intent(v.getContext(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
            }
        });
        
        Button GetLogButton = (Button) findViewById(R.id.button2);
        GetLogButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	counter = 0;
            	createLog();
            	getLog();
            	
            	}
        });

        Button TerminalButton = (Button) findViewById(R.id.button3);
        TerminalButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	Intent serverIntent = null;
            	serverIntent = new Intent(v.getContext(), BluetoothChat.class);
            	mChatService.stop();
                startActivityForResult(serverIntent, 10);
            }
        });

        // Initialize the buffer for outgoing messages
        //mOutStringBuffer = new StringBuffer("");
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (mChatService != null) mChatService.stop();
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }


    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send;
			
            message += '\r';
			send = message.getBytes();
			
            mChatService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            //mOutStringBuffer.setLength(0);
            //mOutEditText.setText(mOutStringBuffer);
        }
    }



    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothChatService.STATE_CONNECTED:
                    //setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                    //mConversationArrayAdapter.clear();
                    break;
                case BluetoothChatService.STATE_CONNECTING:
                    //setStatus(R.string.title_connecting);
                    break;
                case BluetoothChatService.STATE_LISTEN:
                case BluetoothChatService.STATE_NONE:
                    //setStatus(R.string.title_not_connected);
                    break;
                }
                break;
            case MESSAGE_WRITE:
                //byte[] writeBuf = (byte[]) msg.obj;
                // construct a string from the buffer
                //String writeMessage = new String(writeBuf);
                /*if(mConversationArrayAdapter == null){
                	mConversationArrayAdapter = BluetoothChat.getConversationArrayAdapter();
                }
                mConversationArrayAdapter.add("Me:  " + writeMessage);*/
                break;
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                //Log.e(TAG,"READING?");
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                //Write to a file here?
                if(readMessage.indexOf(">") == -1){
                	Log.e(TAG,"Continue Reading");
                	if(totalRead == null){
                		totalRead = readMessage;
                	}
                	else{
                		totalRead += readMessage;
                	}
                }
                else{
                	if(totalRead == null){
                		totalRead = readMessage;
                	}
                	
                	Log.e(TAG,"Total: " + totalRead);
                	if(mConversationArrayAdapter == null){
                    	mConversationArrayAdapter = BluetoothChat.getConversationArrayAdapter();
                    }
                	//Log.e(TAG,"FOOOFOOBOISOIERUSDFLKJAWSOIEURLKJSDFUIASLDKJURWOIEUR");
                	//mConversationArrayAdapter.add(mConnectedDeviceName+":  " + totalRead);
                	TextView temp = (TextView) findViewById(R.id.output);
                	Log.e(TAG,((String) totalRead.subSequence(0,totalRead.length() - 1)));
                	temp.append(totalRead.subSequence(0,totalRead.length() - 1) );
                	temp.append("\n");
                	//justRead = totalRead;
                	justRead = (String) totalRead.replace(">", "");
                	justRead = justRead.replace("\r", " ");
                	justRead = justRead.trim();
                	totalRead = null;
                	
                	if(counter != -1){
                		counter += 1;
                		getLog();
                	}
                	else{
                		counter = 0;
                	}
                }
                
                //mConversationArrayAdapter.add(mConnectedDeviceName+":  " + readMessage);
                break;
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE_SECURE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                connectDevice(data, true);
            }
            break;
        case REQUEST_CONNECT_DEVICE_INSECURE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                connectDevice(data, false);
            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session
                //home_options();
            } else {
                // User did not enable Bluetooth or an error occurred
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device);
    }
    
    private void createLog(){
    	Date d = new Date();
    	logName = (new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss'.xml'")).format(d); 
        
    	File outfile = Environment.getExternalStorageDirectory();
    	//File path = new File(outfile + "/" + logName);
    	
        //if(!path.exists()){
        	//Log.e(TAG,"path Doesn't exist");
        	try {
        		Log.e(TAG,"WRITING LOG");
    			FileWriter out = new FileWriter(new File(outfile,logName), true);
    			out.write("<?DOCTYPE DataTrac_XML_Data>\n"); //Create header
    			out.write("<ECH20_Devices creator=\"dtm v1.06\" format=\"1.0\" "); //Create header
    			out.write("created=\"" + (new SimpleDateFormat("dd MMMM yyyy HH:mm:ss")).format(d));
    			out.write("\">\n");
    			out.write("\t<Device name=\"");
    			out.close();
    		} catch (IOException e) {
    			Log.e(TAG,"Write to Log Failed");
    			e.printStackTrace();
    		}
        //}
    }
    
    private void getLog(){
    	Log.d(TAG,"Get Device Log and Generate File");
    	if(mChatService.getState() != BluetoothChatService.STATE_CONNECTED){
    		Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
    	}
    	
    	
    	File outfile = Environment.getExternalStorageDirectory();
		FileWriter out;
    	switch(counter){
    		case 0:
    			Log.d(TAG,"counter: 0");
    			sendMessage("scan");
    			break;
    		case 1:
    			Log.d(TAG,"counter: 1");
    			sendMessage("get -i");
    			break;
    		case 2:
    			Log.d(TAG,"counter: 2");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\" type=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("get -ver");
    			
    			break;
    		case 3:
    			Log.d(TAG,"counter: 3");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\" sn=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("get -sn");
    			break;
    		case 4:
    			Log.d(TAG,"counter: 4");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\" tn=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("get -tn");
    			
    			break;
    		case 5:
    			Log.d(TAG,"counter: 5");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\" ver=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("get -ver");
    			break;
    		case 6:
    			Log.d(TAG,"counter: 6");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\" version=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("get -ver");
    			break;
    		case 7:
    			Log.d(TAG,"counter: 7");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\" old_name=\"\">\n");
    				out.write("\t\t<Configuration time=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("get -time");
    			break;
    		case 8:
    			Log.d(TAG,"counter: 8");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\" trait=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("get -trait");
    			break;
    		case 9:
    			Log.d(TAG,"counter: 9");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\">\n");
    				out.write("\t\t\t<Measurement wake=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("get -w");
    			break;
    		case 10:
    			Log.d(TAG,"counter: 10");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\" padc=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("get -padc");
    			break;
    		case 11:
    			Log.d(TAG,"counter: 11");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\">\n");
    				out.write("\t\t\t\t<Ports>\n");
    				//out.write("\t\t\t\t\t<Port number=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("get -ports");
    			break;
    		case 12:
    			Log.d(TAG,"counter: 12");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				String[] temp = justRead.split(" ");
    				for(int i=1; i<temp.length;++i){
    					out.write("\t\t\t\t\t<Port number=\"");
    					out.write(i + "\" value=\"" + temp[i-1] + "\" sensor=\"None Selected\"/>\n");
    					
    				}
    				out.write("\t\t\t\t</Ports>\n");
    				out.write("\t\t\t</Measurement>\n");
    				out.write("\t\t\t<Telemetry>\n");
    				out.write("\t\t\t\t<MSradio rmode=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("get -r");
    			break;
    		case 13:
    			Log.d(TAG,"counter: 13");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\" rch=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("get -rch");
    			break;
    		case 14:
    			Log.d(TAG,"counter: 14");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\" rsch=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("get -rsch");
    			break;
    		case 15:
    			Log.d(TAG,"counter: 15");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\" rver=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("get -rver");
    			break;
    		case 16:
    			Log.d(TAG,"counter: 16");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\" />\n");
    				out.write("\t\t\t</Telemetry>\n");
    				out.write("\t\t</Configuration>\n");
    				out.write("\t\t<Status time=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("get -time");
    			break;
    		case 17:
    			Log.d(TAG,"counter: 17");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\" batt=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("get -batt");
    			break;
    		case 18:
    			Log.d(TAG,"counter: 18");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\" rssi=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("get -rssi");
    			break;
    		case 19:
    			Log.d(TAG,"counter: 19");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\" errors=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("get -error");
    			break;
    		case 20:
    			Log.d(TAG,"counter: 20");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\" fwOK=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("get -fwOK");
    			break;
    		case 21:
    			Log.d(TAG,"counter: 21");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\"/>\n");
    				out.write("\t\t<Data datatype=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("scan");
    			break;
    		case 22:
    			Log.d(TAG,"counter: 22");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write("SI 3.0" + "\" scans=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("dump -enum -all 65535");
    			break;
    		case 23:
    			Log.d(TAG,"counter: 23");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\" start=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("dump -telrid");
    			break;
    		case 24:
    			Log.d(TAG,"counter: 24");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\" stop=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("get -stop");
    			break;
    		case 25:
    			Log.d(TAG,"counter: 25");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\" rid=\"");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("dump -rid");
    			break;
    		case 26:
    			Log.d(TAG,"counter: 26");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				out.write(justRead + "\">\n");
    				
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			sendMessage("dump -rid");
    			break;
    		case 27:
    			Log.d(TAG,"counter: 27");
    			Log.e(TAG,"rid = " + justRead);
    			sendMessage("dump -range 0 " + justRead);
    			justRead = null;
    			break;
    		case 28:
    			Log.d(TAG,"counter: 28");
    			try {
    				out = new FileWriter(new File(outfile,logName), true);
    				String[] token = justRead.split(" ");
    				for(int i=0; i<token.length;i++){
    					out.write("\t\t\t" + token[i] + "\n");
    				}
    				out.write("\t\t</Data>\n");
    				out.write("\t</Device>\n");
    				out.write("</ECH20_Devices>");
    				out.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			justRead = null;
    			
    			TextView done = (TextView) findViewById(R.id.output);
    			done.append("Log Written to " + logName);
    			counter = -1;
    			break;
    	}

    }

}