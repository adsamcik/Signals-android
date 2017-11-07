package com.adsamcik.signalcollector.file;

import android.support.annotation.NonNull;
import android.util.MalformedJsonException;

import com.google.firebase.crash.FirebaseCrash;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.security.InvalidParameterException;

public class FileStore {

	/**
	 * Checks if file exists
	 *
	 * @param fileName file name
	 * @return existence of file
	 */
	public static @NonNull File file(@NonNull String parent, @NonNull String fileName) {
		return new File(parent, fileName);
	}

	/**
	 * Checks if file exists
	 *
	 * @param fileName file name
	 * @return existence of file
	 */
	public static @NonNull File file(@NonNull File parent, @NonNull String fileName) {
		return new File(parent, fileName);
	}

	public static @NonNull File dataFile(@NonNull File parent, @NonNull String dataFileName) {
		if(!parent.isDirectory())
			throw new InvalidParameterException("Parent must be directory");

		File[] files = parent.listFiles((file, s) -> s.startsWith(dataFileName));
		return files.length == 1 ? files[0] : new File(parent, dataFileName);
	}

	/**
	 * Saves string to file
	 *
	 * @param file file
	 * @param data string data
	 */
	public static boolean saveString(@NonNull File file, @NonNull String data, boolean append) {
		try (FileOutputStream outputStream = new FileOutputStream(file, append)) {
			outputStream.getChannel().lock();
			OutputStreamWriter osw = new OutputStreamWriter(outputStream);
			osw.write(data);
			osw.close();
		} catch (Exception e) {
			FirebaseCrash.report(e);
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * Appends string to file. If file does not exists, one is created. Should not be combined with other methods.
	 * Allows file overriding and custom empty array detection.
	 *
	 * @param file   file
	 * @param data   Json array to append
	 * @param append Should existing file be overriden with current data
	 * @return Failure
	 * @throws MalformedJsonException Thrown when json array is in incorrect format
	 */
	public static boolean saveAppendableJsonArray(@NonNull File file, @NonNull String data, boolean append) throws MalformedJsonException {
		return saveAppendableJsonArray(file, data, append, false);
	}

	/**
	 * Appends string to file. If file does not exists, one is created. Should not be combined with other methods.
	 * Allows file overriding and custom empty array detection.
	 *
	 * @param file   file
	 * @param data   Json array to append
	 * @param append Should existing file be overriden with current data
	 * @return Failure
	 * @throws MalformedJsonException Thrown when json array is in incorrect format
	 */
	public static boolean saveAppendableJsonArray(@NonNull File file, @NonNull String data, boolean append, boolean firstArrayItem) throws MalformedJsonException {
		StringBuilder sb = new StringBuilder(data);
		if (sb.charAt(0) == ',')
			throw new MalformedJsonException("Json starts with ','. That is not right.");
		char firstChar = !append || firstArrayItem || !file.exists() || file.length() == 0 ? '[' : ',';
		switch (firstChar) {
			case ',':
				if (sb.charAt(0) == '[')
					sb.setCharAt(0, ',');
				else
					sb.insert(0, ',');
				break;
			case '[':
				if (sb.charAt(0) == '{')
					sb.insert(0, '[');
		}

		if (sb.charAt(sb.length() - 1) == ']')
			sb.deleteCharAt(sb.length() - 1);

		data = sb.toString();
		return saveString(file, data, append);
	}


	/**
	 * Load string file as StringBuilder
	 *
	 * @param file file to load
	 * @return content of file as StringBuilder
	 */
	public static StringBuilder loadStringAsBuilder(@NonNull File file) {
		if (!file.exists())
			return null;

		try {
			FileInputStream fis = new FileInputStream(file);
			InputStreamReader isr = new InputStreamReader(fis);
			BufferedReader br = new BufferedReader(isr);
			String receiveString;
			StringBuilder stringBuilder = new StringBuilder();

			while ((receiveString = br.readLine()) != null)
				stringBuilder.append(receiveString);

			isr.close();
			return stringBuilder;
		} catch (Exception e) {
			FirebaseCrash.report(e);
			return null;
		}
	}

	/**
	 * Converts loadStringAsBuilder to string and handles nulls
	 *
	 * @param file file to load
	 * @return content of file (empty if file has no content or does not exists)
	 */
	public static String loadString(@NonNull File file) {
		StringBuilder sb = loadStringAsBuilder(file);
		if (sb != null)
			return sb.toString();
		else
			return null;
	}

	/**
	 * Loads json array that was saved with append method
	 *
	 * @param file file to load
	 * @return proper json array
	 */
	public static String loadAppendableJsonArray(@NonNull File file) {
		StringBuilder sb = loadStringAsBuilder(file);
		if (sb != null && sb.length() != 0) {
			if (sb.charAt(sb.length() - 1) != ']')
				sb.append(']');
			return sb.toString();
		}
		return null;
	}

	/**
	 * Loads whole json array and than finds last object and converts it to java object
	 *
	 * @param file file to load
	 * @param tClass   class of the resulting object
	 * @return last object of json array or null
	 */
	public static <T> T loadLastFromAppendableJsonArray(@NonNull File file, @NonNull Class<T> tClass) {
		StringBuilder sb = loadStringAsBuilder(file);
		if (sb == null)
			return null;
		for (int i = sb.length() - 1; i >= 0; i--) {
			if (sb.charAt(i) == '{') {
				try {
					return new Gson().fromJson(sb.substring(i), tClass);
				} catch (JsonSyntaxException e) {
					FirebaseCrash.report(e);
					return null;
				}
			}
		}
		return null;
	}

	public static String loadLastAscii(@NonNull File file, int n) throws InvalidParameterException, FileNotFoundException{
		byte[] bytes = loadLastBytes(file, n);
		return bytes != null ? new String(bytes) : null;
	}

	public static byte[] loadLastBytes(@NonNull File file, int n) throws InvalidParameterException, FileNotFoundException {
		if(n > file.length())
			throw new InvalidParameterException();

		try(RandomAccessFile raf = new RandomAccessFile(file, "r")) {
			raf.seek(file.length() - n);
			byte[] arr = new byte[n];
			raf.read(arr, 0, n);
			return arr;
		} catch (IOException e) {
			FirebaseCrash.report(e);
			return null;
		}
	}

	/**
	 * Tries to delete file 5 times.
	 * After every unsuccessfull try there is 50ms sleep so you should ensure that this function does not run on UI thread.
	 *
	 * @param file file to delete
	 * @return true if file was deleted, false otherwise
	 */
	public static boolean delete(File file) {
		return delete(file, 3);
	}

	/**
	 * Tries to delete file multiple times based on {@code maxRetryCount}.
	 * After every unsuccessfull try there is 50ms sleep so you should ensure that this function does not run on UI thread.
	 * If it is unsuccessfull after all tries it will attempt to mark the file for deletion but will return false at this time because it the deletation can't be guranteed
	 *
	 * @param file          file to delete
	 * @param maxRetryCount maximum retry count
	 * @return true if file was deleted, false otherwise
	 */
	public static boolean delete(File file, int maxRetryCount) {
		if (file == null)
			throw new InvalidParameterException("file is null");

		int retryCount = 0;
		for (; ; ) {
			if (!file.exists() || file.delete())
				return true;

			if (++retryCount < maxRetryCount)
				break;

			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				// Restore the interrupted done
				Thread.currentThread().interrupt();
			}
		}

		file.deleteOnExit();
		return false;
	}

	/**
	 * Recursively deletes all files in a directory
	 *
	 * @param file File or directory
	 * @return True if successfull
	 */
	public static boolean recursiveDelete(@NonNull File file) {
		if (file.isDirectory()) {
			for (File f : file.listFiles())
				if (!recursiveDelete(f))
					return false;
		}
		return delete(file);
	}

	public static boolean clearFolder(@NonNull File file) {
		if (file.isDirectory()) {
			for (File f : file.listFiles())
				if (!delete(f))
					return false;
		} else
			return false;
		return true;
	}

	/**
	 * Rename file
	 *
	 * @param file        file to rename
	 * @param newFileName new file name
	 * @return success
	 */
	public static boolean rename(File file, String newFileName) {
		return file.renameTo(new File(file.getParentFile(), newFileName));
	}
}
