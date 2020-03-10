package com.getbouncer.cardscan.base;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;


public class Api {
    @NonNull public static String baseUrl = "https://api.getbouncer.com";
    @Nullable public static String apiKey = null;

    @NonNull
    static private JSONObject getUnknownErrorResponse() {
        JSONObject response = new JSONObject();
        try {
            response.put("status", "error");
            response.put("error_code", "network_error");
            response.put("error_message", "CardNetwork error");
        } catch (JSONException e1) {
            e1.printStackTrace();
        }
        return response;
    }

    @NonNull
    static private JSONObject getApiUrlNotSet() {
        JSONObject response = new JSONObject();
        try {
            response.put("status", "error");
            response.put("error_code", "api_baseurl_not_set");
            response.put("error_message", "Your API.baseUrl or token isn't set");
        } catch (JSONException e1) {
            e1.printStackTrace();
        }
        return response;
    }

    static void scanStats(@NonNull Context context, @NonNull ScanStats scanStats) {
        try {
            JSONObject args = new JSONObject();
            args.put("platform", "android");
            args.put("scan_stats", scanStats.toJson());

            makeApiCallPost(Api.baseUrl + "/scan_stats", args);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @NonNull
    static private String downloadString(@NonNull HttpURLConnection urlConnection) throws IOException, JSONException {
        InputStreamReader in = new InputStreamReader(urlConnection.getInputStream());
        int contentLen = urlConnection.getContentLength();
        StringWriter responseBody = (contentLen > 0) ? new StringWriter(contentLen) : new StringWriter();
        String data;
        do {
            data = readIt2(in, 4096);
            if (!TextUtils.isEmpty(data)) {
                responseBody.append(data);
            }
        } while (data != null);

        in.close();
        return responseBody.toString();
    }

    static private void makeApiCallPost(@NonNull final String url, @NonNull final JSONObject args) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                makeApiCallPostImplementation(url, args);
            }
        }).start();
    }

    @NonNull
    static private JSONObject makeApiCallPostImplementation(@NonNull String url, @NonNull JSONObject args) {
        HttpURLConnection urlConnection;
        try {
            if (TextUtils.isEmpty(Api.baseUrl) || TextUtils.isEmpty(Api.apiKey)) {
                return getApiUrlNotSet();
            }

            urlConnection = (HttpURLConnection) new URL(url).openConnection();

            if (!TextUtils.isEmpty(apiKey)) {
                urlConnection.setRequestProperty("x-bouncer-auth", apiKey);
            }
            urlConnection.setDoOutput(true);
            byte[] content = args.toString().getBytes(StandardCharsets.UTF_8);
            urlConnection.setFixedLengthStreamingMode(content.length);

            OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
            out.write(content);
            out.flush();

            return new JSONObject(downloadString(urlConnection));
        } catch (Exception e) {
            e.printStackTrace();
            return getUnknownErrorResponse();
        }
    }

    // Reads an InputStream and converts it to a String.
    @Nullable
    static private String readIt2(@NonNull InputStreamReader stream, int len) throws IOException {
        char[] buffer = new char[len];
        int ret = stream.read(buffer);
        if (ret < 0) {
            return null;
        }
        return new String(buffer, 0, ret);
    }
}
