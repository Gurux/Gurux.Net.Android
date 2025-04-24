//
// --------------------------------------------------------------------------
//  Gurux Ltd
//
//
//
// Filename:        $HeadURL$
//
// Version:         $Revision$,
//                  $Date$
//                  $Author$
//
// Copyright (c) Gurux Ltd
//
//---------------------------------------------------------------------------
//
//  DESCRIPTION
//
// This file is a part of Gurux Device Framework.
//
// Gurux Device Framework is Open Source software; you can redistribute it
// and/or modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; version 2 of the License.
// Gurux Device Framework is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
// See the GNU General Public License for more details.
//
// More information of Gurux products: http://www.gurux.org
//
// This code is licensed under the GNU General Public License v2.
// Full text may be retrieved at http://www.gnu.org/licenses/gpl-2.0.txt
//---------------------------------------------------------------------------

package gurux.net.android.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.Locale;

import gurux.common.GXCommon;
import gurux.common.IGXMediaListener;
import gurux.common.MediaStateEventArgs;
import gurux.common.PropertyChangedEventArgs;
import gurux.common.ReceiveEventArgs;
import gurux.common.TraceEventArgs;
import gurux.common.enums.MediaState;
import gurux.net.GXNet;
import gurux.net.android.R;
import gurux.net.android.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment implements IGXMediaListener {

    private FragmentHomeBinding binding;
    boolean bHex = false;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        final HomeViewModel homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        enableUI(false);
        final TextView info = binding.info;
        final EditText sendData = binding.sendData;
        final Button openBtn = binding.openBtn;
        final Button clearBtn = binding.clearBtn;
        final Button sendBtn = binding.sendBtn;
        final CheckBox hexCb = binding.hex;
        hexCb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            bHex = isChecked;
        });
        openBtn.setOnClickListener(v -> {
            try {
                final GXNet net = homeViewModel.getNet().getValue();
                if (net != null) {
                    if (net.isOpen()) {
                        net.close();
                    } else {
                        net.open();
                    }
                }
            } catch (Exception ex) {
                Log.e("Network", ex.getMessage());
                Toast.makeText(getContext(), ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        clearBtn.setOnClickListener(v -> {
            binding.receivedData.setText("");
        });

        sendBtn.setOnClickListener(v -> {
            try {
                final GXNet net = homeViewModel.getNet().getValue();
                if (net != null) {
                    if (bHex) {
                        net.send(GXCommon.hexToBytes(sendData.getText().toString()));
                    } else {
                        net.send(sendData.getText().toString());
                    }
                }
            } catch (Exception ex) {
                Log.e("Network", ex.getMessage());
                Toast.makeText(getContext(), ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        homeViewModel.getNet().observe(getViewLifecycleOwner(), net -> {
            //Remove old listener.
            net.removeListener(this);
            net.addListener(this);
            info.setText(String.format(Locale.getDefault(), "%s %s:%d", net.getProtocol(), net.getHostName(), net.getPort()));
        });
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onError(Object sender, RuntimeException ex) {

    }

    @Override
    public void onReceived(Object sender, ReceiveEventArgs e) {
        if (bHex) {
            binding.receivedData.append(GXCommon.bytesToHex((byte[]) e.getData()));
        } else {
            binding.receivedData.append(new String((byte[]) e.getData()));
        }
    }

    private void enableUI(boolean open) {
        final Button openBtn = binding.openBtn;
        final Button sendBtn = binding.sendBtn;
        if (open) {
            openBtn.setText(R.string.close);
        } else {
            openBtn.setText(R.string.open);
        }
        sendBtn.setEnabled(open);
    }

    @Override
    public void onMediaStateChange(Object sender, MediaStateEventArgs e) {
        if (e.getState() == MediaState.CLOSING) {
            enableUI(false);

        }
        if (e.getState() == MediaState.OPEN) {
            enableUI(true);
        }
    }

    @Override
    public void onTrace(Object sender, TraceEventArgs e) {

    }

    @Override
    public void onPropertyChanged(Object sender, PropertyChangedEventArgs e) {

    }
}