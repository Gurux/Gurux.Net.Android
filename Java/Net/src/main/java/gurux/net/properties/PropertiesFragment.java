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

package gurux.net.properties;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;

import gurux.net.GXNet;
import gurux.net.R;
import gurux.net.databinding.FragmentPropertiesBinding;
import gurux.net.enums.NetworkType;

public class PropertiesFragment extends Fragment {

    private GXNet mNet;
    private FragmentPropertiesBinding binding;
    private ListView listView;

    private final List<String> rows = new ArrayList<>();

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        PropertiesViewModel propertiesViewModel =
                new ViewModelProvider(requireActivity()).get(PropertiesViewModel.class);

        binding = FragmentPropertiesBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        mNet = (GXNet) propertiesViewModel.getMedia();
        if (mNet != null) {
            rows.add(getProtocol());
            rows.add(getHostName());
            rows.add(getPort());
            listView = binding.properties;
            ArrayAdapter<String> adapter = new ArrayAdapter<>(container.getContext(),
                    android.R.layout.simple_list_item_1, rows);
            listView.setAdapter(adapter);
                listView.setOnItemClickListener((parent, view, position, id) -> {
                    //User can't change the settings when the connection is open.
                    if (!mNet.isOpen()) {
                            switch (position) {
                                case 0:
                                    updateProtocol();
                                    break;
                                case 1:
                                    updateHost();
                                    break;
                                case 2:
                                    updatePort();
                                    break;
                                default:
                                    //Do nothing.
                            }
                        }
                    else {
                        Toast.makeText(getActivity(), R.string.connectionEstablished, Toast.LENGTH_SHORT).show();
                    }
                });
        }
        return root;
    }

    private String getPort() {
        return getString(R.string.port) + System.lineSeparator() + mNet.getPort();
    }

    private String getHostName() {
        return getString(R.string.host) + System.lineSeparator() + mNet.getHostName();
    }

    private String getProtocol() {
        return getString(R.string.protocol) + System.lineSeparator() + mNet.getProtocol();
    }

    /**
     * Update Protocol.
     */
    private void updateProtocol() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        String[] values = new String[]{"UDP", "TCP/IP"};
        int actual = 0;
        if (mNet.getProtocol() == NetworkType.TCP) {
            actual = 1;
        }
        builder.setTitle(R.string.protocol)
                .setSingleChoiceItems(values, actual, (dialog, which) -> {
                    NetworkType tmp;
                    if (which == 0) {
                        tmp = NetworkType.UDP;
                    } else {
                        tmp = NetworkType.TCP;
                    }
                    mNet.setProtocol(tmp);
                    rows.set(0, getProtocol());
                    ((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel())
                .show();
    }

    /**
     * Update host name.
     */
    private void updateHost() {
        final EditText input = new EditText(getActivity());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        input.setText(mNet.getHostName());
        //Set the cursor at the end of the text.
        input.setSelection(input.getText().length());
        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    mNet.setHostName(input.getText().toString());
                    rows.set(1, getHostName());
                    ((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
                })
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.cancel()).create();
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
        dialog.show();
    }

    /**
     * Update port number.
     */
    private void updatePort() {
        final EditText input = new EditText(getActivity());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(mNet.getPort()));
        //Set the cursor at the end of the text.
        input.setSelection(input.getText().length());
        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    String value = input.getText().toString();
                    if (!value.isEmpty()) {
                        try {
                            mNet.setPort(Integer.parseInt(value));
                            rows.set(2, getPort());
                            ((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
                        } catch (Exception ex) {
                            Toast.makeText(getActivity(), "Invalid number", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.cancel())
                .create();
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
        dialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}