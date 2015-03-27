package com.scandroid_dtc;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
	// The Android's default system path of your application database
	private static String DB_PATH;
	private static String DB_NAME;

	private SQLiteDatabase mDatabase;
	private final Context mContext;

	public DatabaseHelper(Context context, String dbName) {
		super(context, dbName, null, 1);
		mContext = context;
		DB_PATH = "/data/data/" + context.getPackageName() + "/databases/";
		DB_NAME = dbName;
	}

	public void createDatabase() throws IOException {
		boolean existDatabase = checkDatabase();
		if (!existDatabase) {
			// By calling this method an empty database will be created into the
			// default system path of your application so we are gonna be able
			// to overwrite that database with our database
			this.getReadableDatabase();
			copyDatabase();
		}
	}

	public boolean checkDatabase() {
		String path = DB_PATH + DB_NAME;
		SQLiteDatabase checkDb = null;
		try {
			checkDb = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);
		} catch (SQLiteException sqle) {
			// Do nothing;
		}
		if (checkDb != null)
			checkDb.close();
		return checkDb != null;
	}

	public void copyDatabase() throws IOException {
		// Open your local database as the input stream
		InputStream in = mContext.getAssets().open(DB_NAME);
		// Path to the just created empty database
		String path = DB_PATH + DB_NAME;
		// Open the empty database as the output stream
		OutputStream out = new FileOutputStream(path);

		// Transfer bytes from the inputfile to the outputfile
		int length = 0;
		byte[] buffer = new byte[1024];
		while ((length = in.read(buffer)) > 0)
			out.write(buffer, 0, length);

		// Close the streams
		out.flush();
		out.close();
		in.close();
	}

	public void openDatabase() throws SQLException {
		// Open the database
		String path = DB_PATH + DB_NAME;
		mDatabase = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);
	}

	@Override
	public synchronized void close() {
		if (mDatabase != null)
			mDatabase.close();
		super.close();
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	}

	// Add your public helper methods to access and get content from the
	// database. You could return cursors by doing "return mDatabase.query(...)"
	// so it'd be easy to you to create adapters for your views

}