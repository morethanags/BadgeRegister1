package com.colp.badgeregister;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
	public static final String MIME_TEXT_PLAIN = "text/plain";
	private EditText mCardId;
	private EditText mCredentialId;
	private NfcAdapter mNfcAdapter;

	public void setNewValue(String newValue) {
		mCredentialId.setText(newValue);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		mCardId = (EditText) findViewById(R.id.editText_CardId);
		mCredentialId = (EditText) findViewById(R.id.editText_CredentialId);
		mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
		if (mNfcAdapter == null) {
			Toast.makeText(this, "This device doesn't support NFC.",
					Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		if (!mNfcAdapter.isEnabled()) {
			Toast.makeText(this, "Please enable NFC.", Toast.LENGTH_LONG)
					.show();
		}
		ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo[] netInfo = connectivityManager.getAllNetworkInfo();
		for (NetworkInfo ni : netInfo) {
			if (ni.getTypeName().equalsIgnoreCase("WIFI"))
				if (!ni.isConnected()) {
					Toast.makeText(this, "Please connect to PeruLNG network.",
							Toast.LENGTH_LONG).show();
					finish();
					return;
				}
		}
		handleIntent(getIntent());
	}

	public void registerBadge(View view) {
		if (mCardId.getText().toString().matches("")
				|| mCredentialId.getText().toString().matches("")) {
			Toast.makeText(this, "Tap a bagde and enter credential id",
					Toast.LENGTH_LONG).show();
			return;
		} else {
			try {
				String cardId = mCardId.getText().toString();
				String credentialId = mCredentialId.getText().toString();
				String serverURL = getResources().getString(
						R.string.server_url)+"/Access/PostBadge/"
						+ cardId
						+ "/"
						+ credentialId;
				new QueryOperation().execute(serverURL);
			} catch (Exception e) {
				Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
			}
		}
		mCardId.setText("");
		mCredentialId.setText("");
	}

	private void handleIntent(Intent intent) {
		String action = intent.getAction();
		if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
			Time now = new Time();
			now.setToNow();
			Parcelable[] rawMsgs = intent
					.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
			NdefMessage[] msgs;
			if (rawMsgs != null) {
				mCardId.append("Raw Msgs:" + rawMsgs.length + "\r\n");
				msgs = new NdefMessage[rawMsgs.length];
				for (int i = 0; i < rawMsgs.length; i++) {
					msgs[i] = (NdefMessage) rawMsgs[i];
				}
			} else {
				Parcelable parcelable = intent
						.getParcelableExtra(NfcAdapter.EXTRA_TAG);
				Tag tag = (Tag) parcelable;
				// MifareClassic mifareClassic = MifareClassic.get(tag);

				byte[] id = tag.getId();
				mCardId.setText(getDec(id) + "");
				mCredentialId.setText("");
				mCredentialId.requestFocus();
				// Toast.makeText(this, "Badge found.",
				// Toast.LENGTH_LONG).show();
			}
		}
	}

	private long getDec(byte[] bytes) {
		long result = 0;
		long factor = 1;
		for (int i = 0; i < bytes.length; ++i) {
			long value = bytes[i] & 0xffl;
			result += value * factor;
			factor *= 256l;
		}
		return result;
	}

	protected void onResume() {
		super.onResume();
		setupForegroundDispatch(this, mNfcAdapter);
	}

	@Override
	protected void onPause() {
		stopForegroundDispatch(this, mNfcAdapter);
		super.onPause();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		
		handleIntent(intent);
	}

	public static void setupForegroundDispatch(final Activity activity,
			NfcAdapter adapter) {
		final Intent intent = new Intent(activity.getApplicationContext(),
				activity.getClass());
		intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		final PendingIntent pendingIntent = PendingIntent.getActivity(
				activity.getApplicationContext(), 0, intent, 0);
		IntentFilter[] filters = new IntentFilter[1];
		String[][] techList = new String[][] {};
		filters[0] = new IntentFilter();
		filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
		filters[0].addCategory(Intent.CATEGORY_DEFAULT);
		try {
			filters[0].addDataType(MIME_TEXT_PLAIN);
		} catch (MalformedMimeTypeException e) {
			throw new RuntimeException("Check your mime type.");
		}
		adapter.enableForegroundDispatch(activity, pendingIntent, filters,
				techList);
	}

	public static void stopForegroundDispatch(final Activity activity,
			NfcAdapter adapter) {
		adapter.disableForegroundDispatch(activity);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		// getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	private class QueryOperation extends AsyncTask<String, String, String> {


		protected String doInBackground(String... arg0) {
			HttpURLConnection urlConnection;

			StringBuilder result = new StringBuilder();
			try {

				URL url = new URL(arg0[0]);
				urlConnection = (HttpURLConnection) url.openConnection();
				urlConnection.setRequestMethod("POST");
				InputStream in = new BufferedInputStream(urlConnection.getInputStream());
				BufferedReader reader = new BufferedReader(new InputStreamReader(in));
				String line;
				while ((line = reader.readLine()) != null) {
					result.append(line);
				}
			}
			catch(Exception e){
				Log.d("Exception", e.toString());
			}

			return result.toString();
		}

		protected void onPostExecute(String result) {

			try {
			
				JSONObject jsonResponse = new JSONObject(result);
				result = jsonResponse.optString("records")
						+ " Badge registered";
				Toast.makeText(MainActivity.this, result, Toast.LENGTH_SHORT)
						.show();
				
			} catch (JSONException e) {
				Log.d("Exception", e.toString());
			}

		}
	}

}
