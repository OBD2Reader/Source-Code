package com.scandroid_dtc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import android.app.Activity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import android.content.Intent;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import android.graphics.Color;
import android.graphics.Typeface;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import android.util.Log;

import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;

import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	Button btnSelectAndConnect;
	Button btnCheckDTC;
	Button btnTest;

	Spinner spinnerMake;
	Spinner spinnerModel;
	Spinner spinnerYear;

	TextView textViewDTC;
	TextView textViewVIN;

	StringBuilder sb = new StringBuilder();
	StringBuilder sbtemp = new StringBuilder();

	Handler h;

	WriteThread mWriteThread;
	ReadThread mReadThread;

	Worker worker;

	private final int RECEIVE_MESSAGE = 1;
	private static final int REQUEST_ENABLE_BT = 2;
	private static final int REQUEST_PAIRED_DEVICE = 3;

	private BluetoothAdapter btAdapter = null;
	private BluetoothSocket btSocket = null;

	// SPP UUID Service
	private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	private boolean read = false;
	private boolean connected = false;

	private SQLiteDatabase dbCars = null;
	private SQLiteDatabase dbDtc = null;

	DatabaseHelper mCarsDatabaseHelper = null;
	DatabaseHelper mDtcDatabaseHelper = null;

	boolean firstClick = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);

		btnSelectAndConnect = (Button) findViewById(R.id.btnConnect);
		btnSelectAndConnect.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setClass(getApplicationContext(), ListPairedDevicesActivity.class);
				startActivityForResult(intent, REQUEST_PAIRED_DEVICE);
			}
		});

		btnCheckDTC = (Button) findViewById(R.id.btnTroubleCodes);
		btnCheckDTC.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				sb.delete(0, sb.length());
				mWriteThread.write("03" + '\r');
			}
		});

		textViewVIN = (TextView) findViewById(R.id.textViewVIN);

		/***
		 * --------------------------------------
		 * 
		 * The spinners and cars database section
		 * 
		 * --------------------------------------
		 ***/

		// Try to create and open the existent car database
		mCarsDatabaseHelper = new DatabaseHelper(this, "cars.sql");
		try {
			mCarsDatabaseHelper.createDatabase();
			mCarsDatabaseHelper.openDatabase();
		} catch (SQLException sqle) {
			throw new Error("Unable to open database");
		} catch (IOException e) {
			throw new Error("Unable to create database");
		}
		// That’s it, the database is open!

		// Try to create and open the existent dtc database
		mDtcDatabaseHelper = new DatabaseHelper(this, "obd_dtc.sql");
		try {
			mDtcDatabaseHelper.createDatabase();
			mDtcDatabaseHelper.openDatabase();
		} catch (SQLException sqle) {
			throw new Error("Unable to open database");
		} catch (IOException e) {
			throw new Error("Unable to create database");
		}
		// That’s it, the database is open!

		spinnerMake = (Spinner) findViewById(R.id.spinnerMake);
		spinnerModel = (Spinner) findViewById(R.id.spinnerModel);
		spinnerYear = (Spinner) findViewById(R.id.spinnerYear);

		List<String> selMake = new ArrayList<String>();
		selMake.add("- Select Make -");
		List<String> selModel = new ArrayList<String>();
		selModel.add("- Select Model -");
		List<String> selYear = new ArrayList<String>();
		selYear.add("- Select Year -");

		// Creating adapter for spinner
		ArrayAdapter<String> dataAdapterMake = new ArrayAdapter<String>(getBaseContext(), android.R.layout.simple_spinner_item, selMake);
		ArrayAdapter<String> dataAdapterModel = new ArrayAdapter<String>(getBaseContext(), android.R.layout.simple_spinner_item, selModel);
		ArrayAdapter<String> dataAdapterYear = new ArrayAdapter<String>(getBaseContext(), android.R.layout.simple_spinner_item, selYear);

		// Drop down layout style - list view with radio button
		dataAdapterMake.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		dataAdapterModel.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		dataAdapterYear.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

		// attaching data adapter to spinner
		spinnerMake.setAdapter(dataAdapterMake);
		spinnerModel.setAdapter(dataAdapterModel);
		spinnerYear.setAdapter(dataAdapterYear);

		spinnerMake.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				// if is the first time the user clicks looking for a car
				if (!firstClick) {
					dbCars = mCarsDatabaseHelper.getReadableDatabase();
					Cursor c = dbCars.rawQuery("SELECT DISTINCT make FROM VehicleModelYear", null);
					// Spinner Drop down elements
					List<String> values = new ArrayList<String>();
					if (c.moveToFirst())
						do {
							values.add(c.getString(c.getColumnIndex("make")));
						} while (c.moveToNext());
					// Creating adapter for spinner
					ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(getBaseContext(), android.R.layout.simple_spinner_item, values);
					// Drop down layout style - list view with radio button
					dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
					// attaching data adapter to spinner
					spinnerMake.setAdapter(dataAdapter);
					firstClick = true;
				}
				return false;
			}

		});

		spinnerMake.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
				if (firstClick) {
					// On selecting a spinner item
					String item = parent.getItemAtPosition(position).toString();
					dbCars = mCarsDatabaseHelper.getReadableDatabase();
					Cursor c = dbCars.rawQuery("SELECT DISTINCT model FROM VehicleModelYear WHERE make='" + item + "'", null);
					// Spinner Drop down elements
					List<String> values = new ArrayList<String>();
					if (c.moveToFirst())
						do {
							values.add(c.getString(c.getColumnIndex("model")));
						} while (c.moveToNext());
					// Creating adapter for spinner
					ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(getBaseContext(), android.R.layout.simple_spinner_item, values);
					// Drop down layout style - list view with radio button
					dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
					// attaching data adapter to spinner
					spinnerModel.setAdapter(dataAdapter);
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				// Do nothing
			}

		});

		spinnerModel.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
				if (firstClick) {
					// On selecting a spinner item
					String item = parent.getItemAtPosition(position).toString();
					dbCars = mCarsDatabaseHelper.getReadableDatabase();
					Cursor cursor = dbCars.rawQuery("SELECT DISTINCT year FROM VehicleModelYear WHERE model='" + item + "'", null);
					// Spinner Drop down elements
					List<String> values = new ArrayList<String>();
					if (cursor.moveToFirst())
						do {
							values.add(cursor.getString(cursor.getColumnIndex("year")));
						} while (cursor.moveToNext());
					// Creating adapter for spinner
					ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(getBaseContext(), android.R.layout.simple_spinner_item, values);
					// Drop down layout style - list view with radio button
					dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
					// attaching data adapter to spinner
					spinnerYear.setAdapter(dataAdapter);
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				// Do nothing
			}
		});

		/***
		 * ---------------------------------------------
		 * 
		 * End of the spinners and cars dabatase section
		 * 
		 * ---------------------------------------------
		 ***/

		btAdapter = BluetoothAdapter.getDefaultAdapter();
		checkBluetoothState();

		// ((Button)
		// findViewById(R.id.btnConnect)).setBackground(getResources().getDrawable(R.drawable.buttonshape_connected));
		// ((Button) findViewById(R.id.btnConnect)).setText("CONNECTED");
	}

	private void showDataTroubleCode(String pid, String make, boolean danger) {
		if (danger) {
			ImageView imageView = new ImageView(this);
			imageView.setImageResource(R.drawable.attention_red);
			imageView.setScaleType(ScaleType.MATRIX);

			TextView errorCode = new TextView(this);
			errorCode.setTextSize(20);
			errorCode.setTypeface(Typeface.DEFAULT_BOLD);
			errorCode.setTextColor(Color.BLACK);
			errorCode.setText(pid);

			TextView errorExplanation = new TextView(this);
			errorExplanation.setTextSize(14);
			errorExplanation.setTextColor(Color.BLACK);

			dbDtc = mDtcDatabaseHelper.getReadableDatabase();
			Cursor cursor = dbDtc.rawQuery("SELECT data, make FROM DataTroubleCodes WHERE code='" + pid + "' AND make='" + make + "'", null);
			if (cursor.moveToFirst()) {
				errorExplanation.setText(cursor.getString(cursor.getColumnIndex("data")));
			} else {
				cursor = dbDtc.rawQuery("SELECT data, make FROM DataTroubleCodes WHERE code='" + pid + "' AND make='" + make + "'", null);
				if (cursor.moveToFirst()) {
					errorExplanation.setText(cursor.getString(cursor.getColumnIndex("data")));
				}
			}

			LinearLayout linearLayoutErrorCode = new LinearLayout(this);
			linearLayoutErrorCode.setOrientation(LinearLayout.VERTICAL);
			linearLayoutErrorCode.addView(errorCode);
			linearLayoutErrorCode.addView(errorExplanation);

			LinearLayout test = new LinearLayout(this);
			test.setBackgroundResource(R.color.LightSalmon);
			test.addView(imageView);
			test.addView(linearLayoutErrorCode);

			LinearLayout linearLayout = (LinearLayout) findViewById(R.id.linearLayoutDTC);
			linearLayout.addView(test);
		}
	}

	private static String hexToASCII(String hexValue) {
		StringBuilder output = new StringBuilder("");
		for (int i = 0; i < hexValue.length(); i += 2) {
			String str = hexValue.substring(i, i + 2);
			output.append((char) Integer.parseInt(str, 16));
		}
		return output.toString();
	}

	@Override
	public void onPause() {
		super.onPause();
		if (connected) {
			connected = false;
			try {
				btSocket.close();
				((Button) findViewById(R.id.btnConnect)).setBackground(getResources().getDrawable(R.drawable.buttonshape_connect));
				((Button) findViewById(R.id.btnConnect)).setText("CONNECT");
			} catch (IOException e) {
				errorExit("Fatal Error", "In 'onPause()' and failed to close socket" + e.getMessage() + ".");
			}
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_ENABLE_BT) {
			checkBluetoothState();
		}
		if (requestCode == REQUEST_PAIRED_DEVICE) {
			if (resultCode == RESULT_OK) {
				Log.i("Mac of the Bluetooth Device", data.getStringExtra("mac"));
				connect(data.getStringExtra("mac"));
				if (connected) {
					btnCheckDTC.setEnabled(true);
					// Trouble Codes Button
					((Button) findViewById(R.id.btnTroubleCodes)).setVisibility(View.VISIBLE);
					// Connect Button
					((Button) findViewById(R.id.btnConnect)).setBackground(getResources().getDrawable(R.drawable.buttonshape_connected));
					((Button) findViewById(R.id.btnConnect)).setText("CONNECTED");
				} else
					((Button) findViewById(R.id.btnTroubleCodes)).setVisibility(View.INVISIBLE);
			}
		}
	}

	private void checkBluetoothState() {
		// Check for Bluetooth support and then check to make sure it is turned on.
		if (btAdapter == null) {
			errorExit("Fatal Error", "Bluetooth not supported.");
		} else if (!btAdapter.isEnabled()) {
			// Prompt user to turn on Bluetooth
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivity(enableBtIntent);
		}
	}

	private void connect(String address) {
		// Set up a pointer to the remote node using it's address
		BluetoothDevice device = btAdapter.getRemoteDevice(address);

		// Two things are needed to make a connection:
		// A MAC address, which we got above
		// A Service ID or UUID. In this case we are using the UUID for SPP

		try {
			btSocket = createBluetoothSocket(device);
		} catch (IOException e) {
			errorExit("Fatal Error", "In 'onResume()' and socket create failed: " + e.getMessage() + ".");
		}

		// Discovery is resource intensive. Make sure it isn't going on ehtn you attempt to connect and pass your
		// message
		btAdapter.cancelDiscovery();

		// Establish the connection. This will block until it connects
		Toast.makeText(this, "..Connecting..", Toast.LENGTH_SHORT).show();
		try {
			btSocket.connect();
			Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show();
		} catch (IOException e) {
			try {
				btSocket.close();
			} catch (IOException e2) {
				errorExit("Fatal Error", "In 'onResume()' and unable to close socket during connection failure" + e2.getMessage() + ".");
			}
		}

		// Create a data stream so we can talk with the device.
		mWriteThread = new WriteThread(btSocket);
		mReadThread = new ReadThread(btSocket);
		mReadThread.start();

		worker = new Worker("Worker");
		worker.start();
		worker.waitUntilReady();

		mWriteThread.write("atz" + '\r');
		mWriteThread.write("ate0" + '\r');
		mWriteThread.write("atsp0" + '\r');

		// I send something to avoid the "SEARCHING" message and wait until the message is readed
		mWriteThread.write("03" + '\r');

		// We must get the VIN
		mWriteThread.write("0902" + '\r');

		if (btSocket.isConnected())
			connected = true;
		else
			connected = false;
	}

	private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
		if (Build.VERSION.SDK_INT >= 10) {
			try {
				final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
				return (BluetoothSocket) m.invoke(device, MY_UUID);
			} catch (Exception e) {
				errorExit("Fatal Error", "Could not create Insecure RFComm Connection" + e.getMessage() + ".");
			}
		}
		return device.createRfcommSocketToServiceRecord(MY_UUID);
	}

	private void errorExit(String title, String message) {
		// Prints an error message and finish the application
		Toast.makeText(getBaseContext(), title + " - " + message, Toast.LENGTH_LONG).show();
		finish();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will automatically handle clicks on the Home/Up button, so
		// long as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * ==============================================================
	 * 
	 * Thread to write data in the bt device
	 * 
	 * ==============================================================
	 **/

	private class WriteThread extends Thread {
		private final OutputStream mmOutStream;

		public WriteThread(BluetoothSocket socket) {
			OutputStream tmpOut = null;
			// Get the output stream, using a temp object because member stream is final
			try {
				tmpOut = socket.getOutputStream();
			} catch (IOException e) {
				e.getMessage();
			}
			mmOutStream = tmpOut;
		}

		// Call this from the main activity to send data to the remote device
		public synchronized void write(String message) {
			byte[] msgBuffer = message.getBytes();
			try {
				mmOutStream.write(msgBuffer);
				read = false;
				while (!read) {
					// wait
				}
			} catch (IOException e) {
				Toast.makeText(getApplicationContext(), "Error sending data: " + e.getMessage() + "..", Toast.LENGTH_SHORT).show();
			}
		}
	}

	/***
	 * ==============================================================
	 * 
	 * Thread to read data from the bt device
	 * 
	 * ==============================================================
	 ***/

	private class ReadThread extends Thread {
		private final InputStream mmInStream;

		public ReadThread(BluetoothSocket socket) {
			InputStream tmpIn = null;
			// Get the input stream, using a temp object because member stream is final
			try {
				tmpIn = socket.getInputStream();
			} catch (IOException e) {
				e.getMessage();
			}
			mmInStream = tmpIn;
		}

		@Override
		public void run() {
			byte[] buffer = new byte[255]; // buffer store for the stream
			int bytes; // bytes returned from read()
			// Keep listening to the InputStream until an exception occurs
			while (true) {
				try { // Read from the InputStream
					bytes = mmInStream.read(buffer);
					// Get the number of bytes and message in "buffer"
					if (bytes != -1)
						h.obtainMessage(RECEIVE_MESSAGE, bytes, -1, buffer).sendToTarget();
				} // Send to message queue Handler
				catch (IOException e) {
					e.getMessage();
					break;
				}
			}
		}
	}

	/***
	 * ==============================================================
	 * 
	 * This class is used to decode the response and update the GUI
	 * 
	 * ==============================================================
	 ***/

	private class Worker extends HandlerThread {

		private final char[] dtcLetters = { 'P', 'C', 'B', 'U' };
		// P: Powertrain, C: Chassis, B: Body, N: Network
		private final char[] hexArray = "0123456789ABCDEF".toCharArray();

		public Worker(String name) {
			super(name);
		}

		public synchronized void waitUntilReady() {
			h = new Handler(getLooper(), new Handler.Callback() {

				@Override
				public boolean handleMessage(Message msg) {
					switch (msg.what) {
					case RECEIVE_MESSAGE:
						if (msg.obj != null) {
							byte[] readBuf = (byte[]) msg.obj;
							String strIncom = new String(readBuf, 0, msg.arg1);
							sbtemp.append(strIncom);
						}
						int endOfLineIndex = sbtemp.indexOf(">");
						if (endOfLineIndex > 0) {
							String sbaux = sbtemp.substring(0, endOfLineIndex).toUpperCase(Locale.US).replaceAll("[?> \r\n\t\b]", "");
							// Example: sbaux = '43C12101130118'
							// Toast.makeText(getApplicationContext(), sbaux, Toast.LENGTH_LONG).show();

							String firstCode = "", secondCode = "", thirdCode = "";
							// The idea here is that a frame error code has 7 bytes, that means 14 hex values. The first
							// two are '43' indicating a correct response to the '03' request command; the rest of the
							// frame has the information about the error codes. If one of the error code is equal to
							// 'P0000' then that means that there no more error codes. With the first byte we deduce the
							// first 2 values of the error code, the first two bits are representing the type of error
							// {P, C, B, U} and the last two the second character of the error code. The other three
							// values of the error code are defined by the next 3 hex values
							if (sbaux.length() > 13 && !sbaux.contains("SEARCHING") && !sbaux.contains("STOPPED") && !sbaux.contains("ELM")) {

								if (!sbaux.substring(0, 2).equals("49")) {
									byte b = hexStringToByteArray(sbaux.charAt(2));
									int ch1 = ((b & 0xC0) >> 6);
									int ch2 = ((b & 0x30) >> 4);
									firstCode += dtcLetters[ch1];
									firstCode += hexArray[ch2] + sbaux.substring(3, 6);
									if (!firstCode.equals("P0000")) {
										sb.append(firstCode + "\n");
									}

									b = hexStringToByteArray(sbaux.charAt(6));
									ch1 = ((b & 0xC0) >> 6);
									ch2 = ((b & 0x30) >> 4);
									secondCode += dtcLetters[ch1];
									secondCode += hexArray[ch2] + sbaux.substring(7, 10);
									if (!secondCode.equals("P0000")) {
										sb.append(firstCode + "\n");
									}

									b = hexStringToByteArray(sbaux.charAt(10));
									ch1 = ((b & 0xC0) >> 6);
									ch2 = ((b & 0x30) >> 4);
									thirdCode += dtcLetters[ch1];
									thirdCode += hexArray[ch2] + sbaux.substring(11, 14);
									if (!thirdCode.equals("P0000")) {
										sb.append(thirdCode + "\n");
									}

									// sb.append("No more error codes" + '\n');

									runOnUiThread(new Runnable() {
										@Override
										public void run() {
											String[] codes = sb.toString().split("\n");
											for (String s : codes)
												showDataTroubleCode(s, spinnerMake.getSelectedItem().toString(), true);
										}
									});
								} else {
									for (int i = 12; i < sbaux.length(); i++) {
										if (sbaux.charAt(i) == '4' && sbaux.charAt(i + 1) == '9') {
											i = i + 5;
										} else {
											String s = "" + sbaux.charAt(i) + sbaux.charAt(i + 1);
											sb.append(hexToASCII(s));
											i++;
										}
									}
									runOnUiThread(new Runnable() {
										@Override
										public void run() {
											textViewVIN.setText(sb);
										}
									});
								}
							} else {
								sb.delete(0, sb.length());
							}
							sbtemp.delete(0, sbtemp.length());

							read = true;
						}
						break;
					}
					return false;
				}
			});
		}

		private byte hexStringToByteArray(char s) {
			// It converts an hex char value to a byte array and displaces it to fit a byte. For example hex C = 1100 in
			// bit = 12 in decimal, this function returns 110000 in bit = 192 in decimal
			return (byte) ((Character.digit(s, 16) << 4));
		}
	}

}
