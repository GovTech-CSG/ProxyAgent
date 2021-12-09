/*
 * Copyright (c) 2021 cloud_kh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kh.proxyagent.Fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kh.proxyagent.Foreground.ForegroundBuilder;
import com.kh.proxyagent.HttpAsync.CallbackFuture;
import com.kh.proxyagent.MainActivity;
import com.kh.proxyagent.R;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static android.content.Context.MODE_PRIVATE;

public class HomeFragment extends Fragment {

    private View view;
    private ImageButton powerButton;
    private TextView ipInterface;
    private CheckBox checkBox;
    private boolean toggle, variableSet, interfaceCheck;
    private String proxyAddress, port, interfaceValue;
    private final String VARIABLE_STATE = "variableSetState";
    private final String TOGGLE_STATE = "toggleState";
    private final String CHECK_STATE = "connectState";
    private final String INTERFACE_STATE = "interfaceState";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_home, container, false);

        powerButton = view.findViewById(R.id.powerButton);
        ipInterface = view.findViewById(R.id.ipInterface);
        checkBox = view.findViewById(R.id.checkBox);

        // Get saved state
        SharedPreferences preferences = this.getActivity().getPreferences(Context.MODE_PRIVATE);
        toggle = preferences.getBoolean(TOGGLE_STATE, false);
        variableSet = preferences.getBoolean(VARIABLE_STATE, false);
        interfaceCheck = preferences.getBoolean(CHECK_STATE, false);
        interfaceValue = preferences.getString(INTERFACE_STATE, "not connected");

        ipInterface.setText(interfaceValue);
        checkBox.setChecked(interfaceCheck);

        if (toggle)
            powerButton.setImageResource(R.drawable.stop_button);

        proxyAddress = preferences.getString("proxyAddress", "");
        port = preferences.getString("port", "");

        Bundle bundle = this.getArguments();
        if (proxyAddress.equals("") && bundle != null)
            proxyAddress = bundle.getString("proxyAddress");
        if (port.equals("") && bundle != null)
            port = bundle.getString("port");

        if(!proxyAddress.equals("") && !port.equals(""))
            variableSet = true;

        // Set power button click listener
        powerButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (variableSet) {
                        if(wifiConnected()) {
                            boolean hasConnection = testConnection();
                            if (!toggle) {
                                if(hasConnection) {
                                    if (MainActivity.executeCommand("settings put global http_proxy " + proxyAddress + ":" + port)) {
                                        powerButton.setImageResource(R.drawable.stop_button);
                                        toggle = true;
                                        interfaceCheck = true;
                                        startForegroundService();
                                    }
                                }
                                else {
                                    AlertDialog.Builder mBuilder = new AlertDialog.Builder(getContext());
                                    View mView = getLayoutInflater().inflate(R.layout.connection_dialog, null);

                                    Button yes = mView.findViewById(R.id.yesButton);

                                    mBuilder.setView(mView);
                                    AlertDialog dialog = mBuilder.create();

                                    yes.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            dialog.dismiss();
                                        }
                                    });
                                    dialog.show();
                                }
                            }
                            else {
                                if(MainActivity.executeCommand("settings put global http_proxy :0")) {
                                    powerButton.setImageResource(R.drawable.power);
                                    toggle = false;
                                    stopForegroundService();
                                    interfaceCheck = false;
                                }
                            }
                            checkBox.setChecked(interfaceCheck);

                            // Ensure to save state!
                            SharedPreferences preferences = getActivity().getPreferences(MODE_PRIVATE);
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putBoolean(CHECK_STATE, interfaceCheck);
                            editor.putBoolean(TOGGLE_STATE, toggle);
                            editor.putBoolean(VARIABLE_STATE, variableSet);
                            editor.commit();
                        }
                        else
                            Toast.makeText(getContext(), "Please connect to WiFi", Toast.LENGTH_SHORT).show();

                    }
                    else
                        Toast.makeText(getContext(), "Proxy settings not set!", Toast.LENGTH_SHORT).show();
                }
            }
        );

        return view;
    }

    private void proxySetting(boolean on) {
        if (on)
            MainActivity.executeCommand("settings put global http_proxy " + proxyAddress + ":" + port);
        else
            MainActivity.executeCommand("settings put global http_proxy :0");
    }

    private boolean testConnection() {
        try {
            proxySetting(true);
            String url = "http://burp";
            Request request = new Request.Builder().url(url).build();
            OkHttpClient client = new OkHttpClient();

            CallbackFuture future = new CallbackFuture();
            client.newCall(request).enqueue(future);
            Response response = future.get(2, TimeUnit.SECONDS); // To get async operation to sync operation

            if (response.isSuccessful()) {
                proxySetting(false);
                return true;
            }
            else {
                proxySetting(false);
                return false;
            }
        }
        catch (InterruptedException | ExecutionException | TimeoutException e) {
            proxySetting(false);
            return false;
        }
    }

    private boolean wifiConnected() {
        ConnectivityManager connManager = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        return mWifi.isConnected();
    }

    private void startForegroundService() {
        Intent serviceIntent = new Intent(getContext(), ForegroundBuilder.class);
        getContext().startService(serviceIntent);
    }

    private void stopForegroundService() {
        Intent serviceIntent = new Intent(getContext(), ForegroundBuilder.class);
        getContext().stopService(serviceIntent);
    }
}
