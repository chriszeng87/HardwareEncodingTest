package com.java.openwatch;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.util.Log;

/**
 * Created by kevenwu on 13-9-24.
 */
public class MD5FileUtil {

	private static String TAG = "MD5FileUtil";

	protected static char hexDigits[] = { '0', '1', '2', '3', '4', '5', '6',
			'7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
	protected static MessageDigest messagedigest = null;

	static {
		try {
			messagedigest = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			Log.e(TAG, "MD5FileUtil messagedigest初始化失败");
		}
	}

	public static String getFileMD5String(File file) {
		FileInputStream in = null;
		try {
			in = new FileInputStream(file);
			byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) != -1) {
            	messagedigest.update(buffer, 0, length);
            }
			return bufferToHex(messagedigest.digest());
		} catch (IOException e) {
			Log.e(TAG, e.getStackTrace().toString());
			return null;
		} catch (OutOfMemoryError e) {
			Log.e(TAG, e.getStackTrace().toString());
			return null;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					Log.e(TAG, e.getStackTrace().toString());
				}
			}
		}
	}

	public static String getFileMD5StringByRandomAccessFile(
			RandomAccessFile file) {
		FileChannel ch = null;
		try {
			ch = file.getChannel();
			MappedByteBuffer byteBuffer = ch.map(FileChannel.MapMode.READ_ONLY,
					0, file.length());
			messagedigest.update(byteBuffer);
			return bufferToHex(messagedigest.digest());
		} catch (IOException e) {
			return null;
		} catch (OutOfMemoryError e) {
			return null;
		} finally {
			if (ch != null) {
				try {
					ch.close();
				} catch (IOException e) {
					Log.e(TAG, e.getStackTrace().toString());
				}
			}
		}
	}

	public static String getMD5String(String s) {
		return getMD5String(s.getBytes());
	}

	public static String getMD5String(byte[] bytes) {
		messagedigest.update(bytes);
		return bufferToHex(messagedigest.digest());
	}

	private static String bufferToHex(byte bytes[]) {
		return bufferToHex(bytes, 0, bytes.length);
	}

	private static String bufferToHex(byte bytes[], int m, int n) {
		StringBuffer stringbuffer = new StringBuffer(2 * n);
		int k = m + n;
		for (int l = m; l < k; l++) {
			appendHexPair(bytes[l], stringbuffer);
		}
		return stringbuffer.toString();
	}

	private static void appendHexPair(byte bt, StringBuffer stringbuffer) {
		char c0 = hexDigits[(bt & 0xf0) >> 4];
		char c1 = hexDigits[bt & 0xf];
		stringbuffer.append(c0);
		stringbuffer.append(c1);
	}

	public static boolean checkPassword(String password, String md5PwdStr) {
		String s = getMD5String(password);
		return s.equals(md5PwdStr);
	}
}
