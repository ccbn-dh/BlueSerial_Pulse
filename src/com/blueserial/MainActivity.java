/*
 * Released under MIT License http://opensource.org/licenses/MIT
 * Copyright (c) 2013 Plasty Grove
 * Refer to file LICENSE or URL above for full text 
 */

package com.blueserial;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.UUID;

import com.blueserial.R;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	private static final String TAG = "BlueTest5-MainActivity";
	private int mMaxChars = 50000;// Default
	private UUID mDeviceUUID;
	private BluetoothSocket mBTSocket;
	private ReadInput mReadThread = null;

	private boolean mIsUserInitiatedDisconnect = false;

	// All controls here
	private TextView mTxtReceive;
	private TextView mTxtAverage;
	private Button mBtnDisconnect;
	private Button mBtnSend;
	private Button mBtnClear;
	private Button mBtnClearInput;
	private Button mBtnSync;
	private ScrollView scrollView;
	private CheckBox chkScroll;
	private CheckBox chkReceiveText;
	private String strReceive;

	private boolean mIsBluetoothConnected = false;

	private BluetoothDevice mDevice;

	private ProgressDialog progressDialog;

	final Context context = this;
	private String strTimes = "";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		ActivityHelper.initialize(this);

		Intent intent = getIntent();
		Bundle b = intent.getExtras();
		mDevice = b.getParcelable(Homescreen.DEVICE_EXTRA);
		mDeviceUUID = UUID.fromString(b.getString(Homescreen.DEVICE_UUID));
		mMaxChars = b.getInt(Homescreen.BUFFER_SIZE);

		Log.d(TAG, "Ready");

		mBtnSend = (Button) findViewById(R.id.btnSend);
		mBtnClear = (Button) findViewById(R.id.btnClear);
		mBtnSync = (Button) findViewById(R.id.btnSync);
		mTxtReceive = (TextView) findViewById(R.id.txtReceive);
		mTxtAverage = (TextView) findViewById(R.id.txtAverage);
		scrollView = (ScrollView) findViewById(R.id.viewScroll);
		chkScroll = (CheckBox) findViewById(R.id.chkScroll);
		chkReceiveText = (CheckBox) findViewById(R.id.chkReceiveText);

		mTxtReceive.setMovementMethod(new ScrollingMovementMethod());

		mBtnSend.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {

				// get prompts.xml view
				LayoutInflater li = LayoutInflater.from(context);
				View promptsView = li.inflate(R.layout.prompts, null);

				AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
						context);

				// set prompts.xml to alertdialog builder
				alertDialogBuilder.setView(promptsView);

				final EditText userInput = (EditText) promptsView
						.findViewById(R.id.editTextDialogUserInput);

				// set dialog message
				alertDialogBuilder
						.setCancelable(false)
						.setPositiveButton("OK",
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int id) {
										// get user input and set it to result
										// edit text
										strTimes = userInput.getText()
												.toString();

										try {
											mBTSocket
													.getOutputStream()
													.write(("times " + strTimes + "\n")
															.getBytes());
										} catch (IOException e) {
											e.printStackTrace();
										}
									}
								})
						.setNegativeButton("Cancel",
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int id) {
										dialog.cancel();
									}
								});

				// create alert dialog
				AlertDialog alertDialog = alertDialogBuilder.create();

				// show it
				alertDialog.show();
			}
		});

		mBtnSync.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				try {
					String str = "sync ";

					Calendar cal = Calendar.getInstance();

					str += String.format("%tH %tM %tS %td %tm %ty\n", cal, cal,
							cal, cal, cal, cal);

					mBTSocket.getOutputStream().write(str.getBytes());

				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});

		mBtnClear.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				mTxtReceive.setText("");
				try {
					mBTSocket.getOutputStream().write("clear\n".getBytes());
				} catch (IOException e) {
					e.printStackTrace();
				}

			}
		});

	}

	private class ReadInput implements Runnable {

		private boolean bStop = false;
		private Thread t;

		public ReadInput() {
			t = new Thread(this, "Input Thread");
			t.start();
		}

		public boolean isRunning() {
			return t.isAlive();
		}

		@Override
		public void run() {
			InputStream inputStream;

			try {
				inputStream = mBTSocket.getInputStream();
				while (!bStop) {
					byte[] buffer = new byte[256];
					if (inputStream.available() > 0) {
						inputStream.read(buffer);
						int i = 0;
						/*
						 * This is needed because new String(buffer) is taking
						 * the entire buffer i.e. 256 chars on Android 2.3.4
						 * http://stackoverflow.com/a/8843462/1287554
						 */
						for (i = 0; i < buffer.length && buffer[i] != 0; i++) {
						}
						final String strInput = new String(buffer, 0, i);

						/*
						 * If checked then receive text, better design would
						 * probably be to stop thread if unchecked and free
						 * resources, but this is a quick fix
						 */

						if (chkReceiveText.isChecked()) {
							mTxtReceive.post(new Runnable() {
								@SuppressLint("ShowToast")
								@Override
								public void run() {

									//mTxtReceive.setText("");
									mTxtReceive.append("");
									strReceive += strInput;

									if (strReceive.indexOf("average") >= 0) {
										String strAverage[] = strReceive
												.split("average");

										int pos = strAverage[1].indexOf("\n");
										 if(pos >= 0)
										 strAverage[1] =
										 strAverage[1].substring(0, pos);

										Calendar cal = Calendar.getInstance();

										mTxtAverage.setText("Average "
												+ strAverage[1]
												+ String.format(
														"\n%tH : %tM : %tS \n %td / %tm / %ty\n",
														cal, cal, cal, cal,
														cal, cal));

										strReceive = "";
									} else if (strReceive.indexOf("sync") >= 0) {
										String strEEPROM[] = strInput
												.split("sync");

										String strEEPROM2 = "";
										if (strEEPROM.length > 0) {

											String arrStr[] = strEEPROM[1]
													.split(" ");

											if (arrStr.length >= 6
													&& arrStr.length % 7 == 0) {
												int k = 0;
												while (k < arrStr.length) {
													strEEPROM2 += "Average: "
															+ arrStr[k++]
															+ "\n";
													strEEPROM2 += arrStr[k++]
															+ ":";
													strEEPROM2 += arrStr[k++]
															+ ":";
													strEEPROM2 += arrStr[k++]
															+ "\n";
													strEEPROM2 += arrStr[k++]
															+ "/";
													strEEPROM2 += arrStr[k++]
															+ "/";
													strEEPROM2 += arrStr[k++]
															+ "\n";

												}
												mTxtReceive.setText("");
												mTxtReceive.setText(strEEPROM2);
											}else{
												mTxtReceive.setText("");
												mTxtReceive.setText("Sync missing data...\n Please press Sync again");
											}

										}

										Toast.makeText(getApplicationContext(),
												"Synced", Toast.LENGTH_SHORT)
												.show();

										strReceive = "";
									} else if (strReceive.indexOf("clear") >= 0) {
										// mTxtAverage.setText("Cleaned");
										Toast.makeText(getApplicationContext(),
												"Cleaned", Toast.LENGTH_SHORT)
												.show();

										strReceive = "";
									} else if (strReceive.indexOf("times") >= 0) {

										Toast.makeText(getApplicationContext(),
												"Set times", Toast.LENGTH_SHORT)
												.show();
										strReceive = "";
									}

									int txtLength = mTxtReceive
											.getEditableText().length();
									if (txtLength > mMaxChars) {
										mTxtReceive.getEditableText().delete(0,
												txtLength - mMaxChars);
									}

									if (chkScroll.isChecked()) { // Scroll only
																	// if this
																	// is
																	// checked
										scrollView.post(new Runnable() { // Snippet
													// from
													// http://stackoverflow.com/a/4612082/1287554
													@Override
													public void run() {
														scrollView
																.fullScroll(View.FOCUS_DOWN);
													}
												});
									}
								}
							});
						}

					}
					Thread.sleep(500);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

		public void stop() {
			bStop = true;
		}

	}

	private class DisConnectBT extends AsyncTask<Void, Void, Void> {

		@Override
		protected void onPreExecute() {
		}

		@Override
		protected Void doInBackground(Void... params) {

			if (mReadThread != null) {
				mReadThread.stop();
				while (mReadThread.isRunning())
					; // Wait until it stops
				mReadThread = null;

			}

			try {
				mBTSocket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			mIsBluetoothConnected = false;
			if (mIsUserInitiatedDisconnect) {
				finish();
			}
		}

	}

	private void msg(String s) {
		Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
	}

	@Override
	protected void onPause() {
		if (mBTSocket != null && mIsBluetoothConnected) {
			new DisConnectBT().execute();
		}
		Log.d(TAG, "Paused");
		super.onPause();
	}

	@Override
	protected void onResume() {
		if (mBTSocket == null || !mIsBluetoothConnected) {
			new ConnectBT().execute();
		}
		Log.d(TAG, "Resumed");
		super.onResume();
	}

	@Override
	protected void onStop() {
		Log.d(TAG, "Stopped");
		super.onStop();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		// TODO Auto-generated method stub
		super.onSaveInstanceState(outState);
	}

	private class ConnectBT extends AsyncTask<Void, Void, Void> {
		private boolean mConnectSuccessful = true;

		@Override
		protected void onPreExecute() {
			progressDialog = ProgressDialog.show(MainActivity.this, "Hold on",
					"Connecting");// http://stackoverflow.com/a/11130220/1287554
		}

		@Override
		protected Void doInBackground(Void... devices) {

			try {
				if (mBTSocket == null || !mIsBluetoothConnected) {
					mBTSocket = mDevice
							.createInsecureRfcommSocketToServiceRecord(mDeviceUUID);
					BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
					mBTSocket.connect();
				}
			} catch (IOException e) {
				// Unable to connect to device
				e.printStackTrace();
				mConnectSuccessful = false;
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			if (!mConnectSuccessful) {
				Toast.makeText(
						getApplicationContext(),
						"Could not connect to device. Is it a Serial device? Also check if the UUID is correct in the settings",
						Toast.LENGTH_LONG).show();
				finish();
			} else {
				msg("Connected to device");
				mIsBluetoothConnected = true;
				mReadThread = new ReadInput(); // Kick off input reader

				try {
					String str = "settime ";

					Calendar cal = Calendar.getInstance();

					str += String.format("%tH %tM %tS %td %tm %ty\n", cal, cal,
							cal, cal, cal, cal);

					mBTSocket.getOutputStream().write(str.getBytes());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}

			progressDialog.dismiss();
		}

	}

}
