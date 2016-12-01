package com.example.stevennl.tastysnake.ui.game;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.example.stevennl.tastysnake.Constants;
import com.example.stevennl.tastysnake.R;
import com.example.stevennl.tastysnake.util.CommonUtil;
import com.example.stevennl.tastysnake.util.bluetooth.BluetoothManager;
import com.example.stevennl.tastysnake.util.bluetooth.listener.OnDiscoverListener;
import com.example.stevennl.tastysnake.util.bluetooth.listener.OnStateChangedListener;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

/**
 * Device connection page.
 */
public class ConnectFragment extends Fragment {
    private static final String TAG = "ConnectFragment";
    private static final int REQ_BLUETOOTH_ENABLED = 1;
    private static final int REQ_BLUETOOTH_DISCOVERABLE = 2;

    private GameActivity act;

    private SafeHandler handler;
    private BluetoothManager manager;
    private OnStateChangedListener stateListener;

    private ArrayList<BluetoothDevice> devices;

    private TextView titleTxt;
    private ListViewAdapter adapter;
    private SwipeRefreshLayout refreshLayout;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        act = (GameActivity)context;
        handler = new SafeHandler(this);
        manager = BluetoothManager.getInstance();
        devices = new ArrayList<>();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initStateListener();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_connect, container, false);
        initTitleTxt(v);
        initListView(v);
        initRefreshLayout(v);
        startConnect();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        registerDiscoveryReceiver();
    }

    @Override
    public void onPause() {
        super.onPause();
        manager.unregisterDiscoveryReceiver(act);
        manager.cancelDiscovery();
    }

    /**
     * Initialize the {@link OnStateChangedListener}
     */
    private void initStateListener() {
        stateListener = new OnStateChangedListener() {
            @Override
            public void onClientSocketEstablished() {
                Log.d(TAG, "Client socket established.");
            }

            @Override
            public void onServerSocketEstablished() {
                Log.d(TAG, "Server socket established.");
            }

            @Override
            public void onDataChannelEstablished() {
                Log.d(TAG, "Data channel established.");
                manager.stopServer();
                if (isAdded()) {
                    act.replaceFragment(new BattleFragment(), true);
                }
            }

            @Override
            public void onError(int code, Exception e) {
                Log.e(TAG, "Error code: " + code, e);
                errHandle(code);
            }
        };
    }

    /**
     * Initialize title TextView.
     *
     * @param v The root view
     */
    private void initTitleTxt(View v) {
        titleTxt = (TextView) v.findViewById(R.id.connect_titleTxt);
    }

    /**
     * Initialize device list.
     *
     * @param v The root view
     */
    private void initListView(View v) {
        ListView listView = (ListView) v.findViewById(R.id.connect_device_listView);
        adapter = new ListViewAdapter(act, devices);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BluetoothDevice device = devices.get(position);
                titleTxt.setText(getString(R.string.connecting));
                manager.connectDeviceAsync(device, stateListener);
                refreshLayout.setRefreshing(true);
            }
        });
    }

    /**
     * Initialize {@link SwipeRefreshLayout}
     *
     * @param v The root view
     */
    private void initRefreshLayout(View v) {
        refreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.connect_swipe_layout);
        refreshLayout.setColorSchemeResources(R.color.colorPrimary);
        refreshLayout.setProgressBackgroundColorSchemeColor(getResources().getColor(R.color.white));
        refreshLayout.setDistanceToTriggerSync(30);  // dips
        refreshLayout.setSize(SwipeRefreshLayout.DEFAULT);
        refreshLayout.setNestedScrollingEnabled(true);
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (!manager.isEnabled()) {
                    startConnect();
                } else {
                    startDiscover();
                }
            }
        });
    }

    /**
     * Start connection procedure.
     */
    private void startConnect() {
        if (!manager.isEnabled()) {
            startActivityForResult(manager.getEnableIntent(), REQ_BLUETOOTH_ENABLED);
        } else {
            reqDiscoverable();
        }
        refreshLayout.setRefreshing(false);
    }

    /**
     * Request device discoverable.
     */
    private void reqDiscoverable() {
        Intent i = manager.getDiscoverableIntent(Constants.DEVICE_DISCOVERABLE_TIME);
        startActivityForResult(i, REQ_BLUETOOTH_DISCOVERABLE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQ_BLUETOOTH_ENABLED:
                if (resultCode == RESULT_OK) {
                    Log.d(TAG, "Bluetooth enabled.");
                    reqDiscoverable();
                } else if (resultCode == RESULT_CANCELED) {
                    Log.d(TAG, "Fail to enable bluetooth.");
                    CommonUtil.showToast(act, getString(R.string.bluetooth_unable));
                }
                break;
            case REQ_BLUETOOTH_DISCOVERABLE:
                if (resultCode == Constants.DEVICE_DISCOVERABLE_TIME) {
                    Log.d(TAG, "Your device can be discovered in " + Constants.DEVICE_DISCOVERABLE_TIME + " seconds.");
                } else if (resultCode == RESULT_CANCELED) {
                    Log.d(TAG, "Device cannot be discovered.");
                }
                startDiscover();
                break;
            default:
                break;
        }
    }

    /**
     * Start device discovery.
     */
    private void startDiscover() {
        runServer();
        refreshLayout.setRefreshing(true);
        devices.clear();
        manager.cancelDiscovery();
        if (manager.startDiscovery()) {
            Log.d(TAG, "Discovering...");
            scheduleCancelDiscover();
        } else {
            CommonUtil.showToast(act, getString(R.string.device_discover_fail));
            refreshLayout.setRefreshing(false);
        }
    }

    /**
     * Cancel discovery after a period of time.
     */
    private void scheduleCancelDiscover() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                manager.cancelDiscovery();
                refreshLayout.setRefreshing(false);
                Log.d(TAG, "Discover finished.");
                if (devices.isEmpty()) {
                    Log.d(TAG, "No device discovered.");
                    if (isAdded()) {
                        CommonUtil.showToast(act, getString(R.string.device_discover_empty));
                    }
                }
            }
        }, Constants.DEVICE_DISCOVER_TIME);
    }

    /**
     * Register device discovery receiver.
     */
    private void registerDiscoveryReceiver() {
        manager.registerDiscoveryReceiver(act, new OnDiscoverListener() {
            @Override
            public void onDiscover(BluetoothDevice device) {
                Log.d(TAG, "Device discovered: " + device.getName() + " " + device.getAddress());
                devices.add(device);
                adapter.notifyDataSetChanged();
            }
        });
    }

    /**
     * Return the title TextView.
     */
    public TextView getTitleTxt() {
        return titleTxt;
    }

    /**
     * Return the SwipeRefreshLayout.
     */
    public SwipeRefreshLayout getRefreshLayout() {
        return refreshLayout;
    }

    /**
     * Handle errors.
     * Notice that this method is called in a sub-thread.
     *
     * @param code The error code
     */
    private void errHandle(int code) {
        handler.obtainMessage(SafeHandler.MSG_ERR).sendToTarget();
        switch (code) {
            case OnStateChangedListener.ERR_SOCKET_CREATE:
                break;
            case OnStateChangedListener.ERR_SOCKET_CLOSE:
                break;
            case OnStateChangedListener.ERR_SERVER_SOCKET_ACCEPT:
                break;
            case OnStateChangedListener.ERR_CLIENT_SOCKET_CONNECT:
                break;
            case OnStateChangedListener.ERR_STREAM_CREATE:
                break;
            case OnStateChangedListener.ERR_STREAM_READ:
                break;
            case OnStateChangedListener.ERR_STREAM_WRITE:
                break;
            default:
                break;
        }
    }

    /**
     * Run a bluetooth server.
     */
    private void runServer() {
        manager.runServerAsync(stateListener);
        Log.d(TAG, "Bluetooth server thread starts working...");
    }

    /**
     * A safe handler that circumvents memory leaks.
     * Author: LCY
     */
    private static class SafeHandler extends Handler {
        private static final int MSG_ERR = 1;
        private WeakReference<ConnectFragment> fragment;

        private SafeHandler(ConnectFragment fragment) {
            this.fragment = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            ConnectFragment f = fragment.get();
            switch (msg.what) {
                case MSG_ERR:
                    if (f.isAdded()) {
                        f.getRefreshLayout().setRefreshing(false);
                        f.getTitleTxt().setText(f.getString(R.string.select_device));
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Device ListView adapter.
     * Author: LCY
     */
    private class ListViewAdapter extends ArrayAdapter<BluetoothDevice> {
        private LayoutInflater inflater;

        private ListViewAdapter(Context context, ArrayList<BluetoothDevice> devList) {
            super(context, 0, devList);
            this.inflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_list_device, parent, false);
                holder = new ListViewAdapter.ViewHolder();
                holder.devNameTxt = (TextView) convertView.findViewById(R.id.item_list_device_nameTxt);
                convertView.setTag(holder);
            } else {
                holder = (ListViewAdapter.ViewHolder)convertView.getTag();
            }
            BluetoothDevice device = getItem(position);
            if (device != null) {
                holder.devNameTxt.setText(device.getName());
            }
            return convertView;
        }

        private class ViewHolder {
            private TextView devNameTxt;
        }
    }
}
