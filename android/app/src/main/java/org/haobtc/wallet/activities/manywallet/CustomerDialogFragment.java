package org.haobtc.wallet.activities.manywallet;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.chaquo.python.Kwarg;
import com.chaquo.python.PyObject;

import org.haobtc.wallet.R;
import org.haobtc.wallet.activities.ActivatedProcessing;
import org.haobtc.wallet.activities.PinSettingActivity;
import org.haobtc.wallet.activities.TransactionDetailsActivity;
import org.haobtc.wallet.activities.WalletUnActivatedActivity;
import org.haobtc.wallet.activities.onlywallet.CreateOnlyChooseActivity;
import org.haobtc.wallet.activities.onlywallet.CreatePersonalWalletActivity;
import org.haobtc.wallet.activities.set.UpgradeBixinKEYActivity;
import org.haobtc.wallet.activities.set.VersionUpgradeActivity;
import org.haobtc.wallet.activities.set.recovery_set.Backup_recoveryActivity;
import org.haobtc.wallet.fragment.BleDeviceRecyclerViewAdapter;
import org.haobtc.wallet.fragment.BluetoothConnectingFragment;
import org.haobtc.wallet.fragment.BluetoothFragment;
import org.haobtc.wallet.fragment.ReadingPubKeyDialogFragment;
import org.haobtc.wallet.fragment.ReadingPubKeyFailedDialogFragment;
import org.haobtc.wallet.utils.CommonUtils;
import org.haobtc.wallet.utils.Daemon;
import org.haobtc.wallet.utils.Global;
import org.haobtc.wallet.utils.NfcUtils;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import cn.com.heaton.blelibrary.ble.Ble;
import cn.com.heaton.blelibrary.ble.callback.BleConnectCallback;
import cn.com.heaton.blelibrary.ble.callback.BleNotiftCallback;
import cn.com.heaton.blelibrary.ble.callback.BleScanCallback;
import cn.com.heaton.blelibrary.ble.callback.BleWriteCallback;
import cn.com.heaton.blelibrary.ble.model.BleDevice;


public class CustomerDialogFragment extends DialogFragment implements View.OnClickListener {

    // private static final int PIN_New_SECOND = 3;
    public static final String TAG = "BLE";
    public static final int PIN_REQUEST = 5;
    public static final int REQUEST_ACTIVE = 4;
    public static final int SHOW_PROCESSING = 7;
    public static final int PIN_CURRENT = 1;
    public static final int PIN_NEW_FIRST = 2;
    private static final int REQUEST_ENABLE_BT = 1;
    public static volatile String pin = "";
    public static PyObject pyHandler, customerUI;
    public static MyHandler handler;
    public static FutureTask<PyObject> futureTask;
    public static ExecutorService executorService = Executors.newSingleThreadExecutor();
    public static String xpub;
    public static volatile boolean isActive = false;
    private final IntentFilter bondFilter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
    private BleDeviceRecyclerViewAdapter adapter;
    private RelativeLayout relativeLayout;
    private String tag;
    private String extras;
    private List<Runnable> runnables;
    private BluetoothConnectingFragment fragment;
    private Ble<BleDevice> mBle;
    private ReadingPubKeyDialogFragment dialogFragment;
    private BluetoothFragment bleFragment;
    public static volatile boolean isNFC;
    private final BleScanCallback<BleDevice> scanCallback = new BleScanCallback<BleDevice>() {
        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void onLeScan(final BleDevice device, int rssi, byte[] scanRecord) {
            synchronized (mBle.getLocker()) {
                Log.i(TAG, "BLE Device Find====" + device.getBleName());
                Activity activity = getActivity();
                if (activity != null) {
                    getActivity().runOnUiThread(() -> adapter.add(device));
                } else {
                    mBle.stopScan();
                }
            }
        }
    };
    private final BleWriteCallback<BleDevice> writeCallBack = new BleWriteCallback<BleDevice>() {

        @Override
        public void onWriteSuccess(BleDevice device, BluetoothGattCharacteristic characteristic) {
            Log.i(TAG, "发送数据成功:" + CommonUtils.bytes2hex(characteristic.getValue()));
        }

        @Override
        public void onWiteFailed(BleDevice device, String message) {
            super.onWiteFailed(device, message);
            Log.i(TAG, "发送数据失败:" + message);
        }
    };
    private boolean isBonded;
    private static boolean isErrorOccurred;

    private void dealWithBusiness() {
        boolean isInit = false; //should be false
        try {
            Log.i(TAG, "java ==== isInitialized");
            isInit = isInitialized();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage() == null ? "unknown error" : e.getMessage());
            Toast.makeText(getContext(), "communication error, check init status", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isInit) {
            boolean pinCached = false;
            try {
                Log.i(TAG, "java ==== get_pin_status");
                pinCached = executorService.submit(() -> Daemon.commands.callAttr("get_pin_status", "bluetooth")).get().toBoolean();
            } catch (Exception e) {
                e.printStackTrace();
                dismiss();
            }
            if (ManyWalletTogetherActivity.TAG.equals(tag) || CreatePersonalWalletActivity.TAG.equals(tag) || CreateOnlyChooseActivity.TAG.equals(tag)) {
                dialogFragment = showReadingDialog();
                // todo: get xpub
                Log.i(TAG, "java ==== get_xpub_from_hw");
                if (CreatePersonalWalletActivity.TAG.equals(tag)) {
                    futureTask = new FutureTask<>(() -> Daemon.commands.callAttr("get_xpub_from_hw", "bluetooth", new Kwarg("_type", "p2wpkh")));
                } else {
                    futureTask = new FutureTask<>(() -> Daemon.commands.callAttr("get_xpub_from_hw", "bluetooth"));
                }
                executorService.submit(futureTask);
                if (pinCached) {
                    try {
                        xpub = futureTask.get(5, TimeUnit.SECONDS).toString();
                        Objects.requireNonNull(getActivity()).runOnUiThread(runnables.get(1));
                        dialogFragment.dismiss();
                    } catch (ExecutionException | InterruptedException e) {
                        dialogFragment.dismiss();
                        showReadingFailedDialog();
                    } catch (TimeoutException e) {
                        e.printStackTrace();
                        dismiss();
                    }
                }
            } else if (TransactionDetailsActivity.TAG.equals(tag)) {
                // todo： sign
                Log.i(TAG, "java ==== sign_tx");
                futureTask = new FutureTask<>(() -> Daemon.commands.callAttr("sign_tx", extras));
                executorService.submit(futureTask);
                if (pinCached) {
                    Objects.requireNonNull(getActivity()).runOnUiThread(runnables.get(0));
                    dismiss();
                }
            } else if (Backup_recoveryActivity.TAG.equals(tag)) {
                // todo： backup
                if (TextUtils.isEmpty(extras)) {
                    Log.i(TAG, "java ==== backup");
                    futureTask = new FutureTask<>(() -> Daemon.commands.callAttr("backup_wallet"));
                    executorService.submit(futureTask);
                    try {
                        String privateKey = futureTask.get().toString();
                        Objects.requireNonNull(getActivity()).runOnUiThread(() -> Toast.makeText(getActivity(), String.format("备份成功: %s", privateKey), Toast.LENGTH_SHORT).show());
                        dismiss();
                    } catch (ExecutionException | InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    // todo: recovery
                    Log.i(TAG, "java ==== recovery");
                    futureTask = new FutureTask<>(() -> Daemon.commands.callAttr("wallet_recovery", extras));
                    executorService.submit(futureTask);
                }

            } else if (VersionUpgradeActivity.TAG.equals(tag)) {
                Intent intent = new Intent(getActivity(), UpgradeBixinKEYActivity.class);
                intent.putExtra("way", "nfc");
                if ("hardware".equals(extras)) {
                    intent.putExtra("tag", 1);
                } else if ("ble".equals(extras)) {
                    intent.putExtra("tag", 2);
                }
                startActivity(intent);

            }

        } else {
            // todo: Initialized
            Intent intent = new Intent(getContext(), WalletUnActivatedActivity.class);
            startActivityForResult(intent, REQUEST_ACTIVE);
        }


    }

    private final BleConnectCallback<BleDevice> connectCallback = new BleConnectCallback<BleDevice>() {
        @Override
        public void onConnectionChanged(BleDevice device) {

            if (device.isConnectting()) {
                Log.i(TAG, "正在连接---" + device.getBleName());
                showConnecting();
            }
            if (BleDeviceRecyclerViewAdapter.device.getBondState() != BluetoothDevice.BOND_BONDED) {
                return;
            }
            if (device.isConnected()) {
                Log.i(TAG, "以连接至---" + device.getBleName());
            }

        }

        @Override
        public void onServicesDiscovered(BleDevice device) {
            super.onServicesDiscovered(device);
            setNotify(device);
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onReady(BleDevice device) {
            super.onReady(device);
            showConnecting();
            isNFC = false;
            pyHandler.put("BLE", mBle);
            pyHandler.put("BLE_DEVICE", device);
            pyHandler.put("CALL_BACK", writeCallBack);
            Instant begin = Instant.now();
            while (true) {
                BluetoothGattCharacteristic characteristic = mBle.getBleRequest().getReadCharacteristic(BleDeviceRecyclerViewAdapter.device.getAddress());
                BluetoothGattDescriptor notify = null;
                if (characteristic != null) {
                    notify = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                }
                boolean isNotify = false;
                if (notify != null) {
                    isNotify = Arrays.equals(notify.getValue(), BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                }
                if (isNotify) {
                    new Handler().postDelayed(() -> dealWithBusiness(), isBonded ? 3000 : 1000);
                    break;
                } else {
                    setNotify(BleDeviceRecyclerViewAdapter.mBleDevice);
                }
                if (Duration.between(begin, Instant.now()).toMillis() > 10000) {
                    showReadingFailedDialog();
                    break;
                }
                if (isErrorOccurred) {
                    isErrorOccurred = false;
                    dismiss();
                    break;
                }
            }
        }

        @Override
        public void onConnectException(BleDevice device, int errorCode) {
            super.onConnectException(device, errorCode);
            Log.e(TAG, String.format("连接异常，异常状态码: %d", errorCode));
            isErrorOccurred = true;
            switch (errorCode) {
                case 2523:
                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() -> Toast.makeText(getContext(), "蓝牙连接异常", Toast.LENGTH_LONG).show());
                    }
                    dismiss();
                    break;
                case 133:
                    mBle.disconnect(device);
                    mBle.connect(device, connectCallback);
                    break;
                case 2520:

                    break;
                case 8:

                    break;
                default:
                    dismiss();
            }
        }

        @Override
        public void onConnectCancel(BleDevice device) {
            super.onConnectCancel(device);
            Log.i(TAG, "正在取消连接");

        }

        @Override
        public void onConnectTimeOut(BleDevice device) {
            super.onConnectTimeOut(device);
            Log.e(TAG, String.format("连接设备==%s超时", device.getBleName()));
        }
    };
    private final Runnable retry = new Runnable() {
        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void run() {
            if (BleDeviceRecyclerViewAdapter.device.getBondState() == BluetoothDevice.BOND_BONDED) {
                if (BleDeviceRecyclerViewAdapter.mBleDevice.isConnected()) {
                    connectCallback.onReady(BleDeviceRecyclerViewAdapter.mBleDevice);
                } else {
                    mBle.connect(BleDeviceRecyclerViewAdapter.mBleDevice, connectCallback);
                }
            } else {
                BleDeviceRecyclerViewAdapter.device.createBond();
                isBonded = true;
            }
        }
    };

    private void showConnecting() {
        Objects.requireNonNull(getActivity()).runOnUiThread(() -> {
            if (fragment == null) {
                fragment = new BluetoothConnectingFragment();
            }
            getChildFragmentManager().beginTransaction().replace(R.id.ble_device, fragment, "connecting").commitNow();
            relativeLayout.setVisibility(View.GONE);
        });
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i(TAG, "receive broadcast===" + action);
            if (fragment == null && BleDeviceRecyclerViewAdapter.device.getBondState() == BluetoothDevice.BOND_BONDING) {
                fragment = new BluetoothConnectingFragment();
                getChildFragmentManager().beginTransaction().replace(R.id.ble_device, fragment).commitNow();
                relativeLayout.setVisibility(View.GONE);
            }
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                if (BleDeviceRecyclerViewAdapter.device != null) {
                    if (BleDeviceRecyclerViewAdapter.device.getBondState() == BluetoothDevice.BOND_BONDED &&
                            !BleDeviceRecyclerViewAdapter.mBleDevice.isConnected()) {
                        mBle.connect(BleDeviceRecyclerViewAdapter.mBleDevice, connectCallback);
                        isBonded = true;
                    }
                }
            }

        }
    };

    public CustomerDialogFragment(String tag, @Nullable List<Runnable> runnables, String extra) {
        this.tag = tag;
        this.runnables = runnables;
        this.extras = extra;
    }

    private void setNotify(BleDevice device) {
        /*Set up notifications when the connection is successful*/
        mBle.startNotify(device, new BleNotiftCallback<BleDevice>() {
            @Override
            public void onChanged(BleDevice device, BluetoothGattCharacteristic characteristic) {
                Log.e(TAG, "receive from hardware: " + CommonUtils.bytes2hex(characteristic.getValue()));
                pyHandler.put("RESPONSE", characteristic.getValue());
            }

            @Override
            public void onNotifySuccess(BleDevice device, BluetoothGatt gatt) {
                super.onNotifySuccess(device, gatt);
                Log.i(TAG, "onNotifySuccess: " + gatt.getDevice().getName());
            }

            @Override
            public void onNotifyCanceled(BleDevice device) {
                super.onNotifyCanceled(device);
            }
        });
    }

    private boolean isInitialized() throws Exception {
        boolean isInitialized = false;
        try {
            isInitialized = executorService.submit(() -> Daemon.commands.callAttr("is_initialized", "bluetooth")).get().toBoolean();
        } catch (Exception e) {
            Toast.makeText(getContext(), "communication error", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            throw e;
        }
        return isInitialized;
    }

    public ReadingPubKeyDialogFragment showReadingDialog() {
        getChildFragmentManager().beginTransaction().replace(R.id.ble_device, bleFragment).commit();
        ReadingPubKeyDialogFragment fragment = new ReadingPubKeyDialogFragment();
        fragment.show(getChildFragmentManager(), "");
        return fragment;
    }

    public void showReadingFailedDialog() {
        ReadingPubKeyFailedDialogFragment fragment = new ReadingPubKeyFailedDialogFragment();
        fragment.setRunnable(retry);
        fragment.show(getChildFragmentManager(), "");
    }

    private void turnOnBlueTooth() {
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBle.isBleEnable()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ImageView imageViewCancel;
        View view = inflater.inflate(R.layout.bluetooth_nfc, null);
        ImageView imageView = view.findViewById(R.id.touch_nfc);
        TextView textView = view.findViewById(R.id.text_prompt);
        FrameLayout frameLayout = view.findViewById(R.id.ble_device);
        RadioGroup radioGroup = view.findViewById(R.id.radio_group);
        imageViewCancel = view.findViewById(R.id.img_cancel);
        TextView textViewInputByHand = view.findViewById(R.id.text_input_publickey_by_hand);
        relativeLayout = view.findViewById(R.id.input_layout);
        textViewInputByHand.setOnClickListener(this);
        imageViewCancel.setOnClickListener(this);
        mBle = Ble.getInstance();
        adapter = new BleDeviceRecyclerViewAdapter(this.getActivity());
        adapter.setConnectCallback(connectCallback);
        bleFragment = new BluetoothFragment(adapter);
        pyHandler = Global.py.getModule("trezorlib.transport.bluetooth").get("BlueToothHandler");
        customerUI = Global.py.getModule("trezorlib.customer_ui").get("CustomerUI");
        handler = MyHandler.getInstance(getActivity());
        customerUI.put("handler", handler);
        NfcUtils.nfc(getActivity());
        if (!ManyWalletTogetherActivity.TAG.equals(tag)) {
            relativeLayout.setVisibility(View.GONE);
        }
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            switch (checkedId) {
                case R.id.radio_ble:
                    group.check(R.id.radio_ble);
                    frameLayout.setVisibility(View.VISIBLE);
                    imageView.setVisibility(View.GONE);
                    textView.setVisibility(View.GONE);
                    turnOnBlueTooth();
                    refreshDeviceList(true);
                    getChildFragmentManager().beginTransaction().replace(R.id.ble_device, bleFragment).commit();
                    break;
                case R.id.radio_nfc:
                    group.check(R.id.radio_nfc);
                    mBle.getConnetedDevices().forEach(bleDevice -> mBle.disconnect(bleDevice));
                    refreshDeviceList(false);
                    imageView.setVisibility(View.VISIBLE);
                    textView.setVisibility(View.VISIBLE);
                    frameLayout.setVisibility(View.GONE);
            }
        });
        Window window = Objects.requireNonNull(getDialog()).getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(R.color.transparent);
            window.getDecorView().setPadding(0, 0, 0, 0);
            WindowManager.LayoutParams wlp = window.getAttributes();
            wlp.gravity = Gravity.BOTTOM;
            wlp.width = WindowManager.LayoutParams.MATCH_PARENT;
            wlp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(wlp);
        }
        return view;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void refreshDeviceList(boolean start) {
        if (mBle.isScanning()) {
            mBle.stopScan();
        }
        BleDeviceRecyclerViewAdapter.mValues.clear();
        adapter.notifyDataSetChanged();
        if (start) {
            mBle.startScan(scanCallback);
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.img_cancel:
                refreshDeviceList(false);
                dismiss();
                break;
            case R.id.text_input_publickey_by_hand:
                refreshDeviceList(false);
                Activity activity = getActivity();
                if (activity != null) getActivity().runOnUiThread(runnables.get(0));
                dismiss();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(getContext(), getString(R.string.open_bluetooth), Toast.LENGTH_LONG).show();
        } else if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) {
            refreshDeviceList(true);
        } else if (requestCode == REQUEST_ACTIVE && resultCode == Activity.RESULT_OK) { // Bluetooth activation
            if (data != null) {
                isActive = data.getBooleanExtra("isActive", false);
                if (isActive) {
                    executorService.execute(() -> {
                        try {
                            Daemon.commands.callAttr("init", "bluetooth");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Objects.requireNonNull(getActivity()).registerReceiver(broadcastReceiver, bondFilter);
        if (NfcUtils.mNfcAdapter != null && NfcUtils.mNfcAdapter.isEnabled()) {
            // enable nfc discovery for the app
            Log.i("NFC", "为本App启用NFC感应");
            NfcUtils.mNfcAdapter.enableForegroundDispatch(getActivity(), NfcUtils.mPendingIntent, NfcUtils.mIntentFilter, NfcUtils.mTechList);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (dialogFragment != null) {
            dialogFragment.dismiss();
        }
        if (NfcUtils.mNfcAdapter != null && NfcUtils.mNfcAdapter.isEnabled()) {
            // disable nfc discovery for the app
            NfcUtils.mNfcAdapter.disableForegroundDispatch(getActivity());
            Log.i("NFC", "禁用本App的NFC感应");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Objects.requireNonNull(getActivity()).unregisterReceiver(broadcastReceiver);
    }

    public static class MyHandler extends Handler {
        private static volatile MyHandler myHandler;
        private WeakReference<FragmentActivity> reference;

        private MyHandler(FragmentActivity activity) {
            this.reference = new WeakReference<>(activity);
        }

        static MyHandler getInstance(FragmentActivity fragmentActivity) {
            if (myHandler == null) {
                synchronized (MyHandler.class) {
                    if (myHandler == null) {
                        myHandler = new MyHandler(fragmentActivity);
                    }
                }
            }
            return myHandler;
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case PIN_CURRENT:
                    Intent intent = new Intent(reference.get(), PinSettingActivity.class);
                    intent.putExtra("pin", PIN_CURRENT);
                    reference.get().startActivityForResult(intent, PIN_REQUEST);
                    break;
                case PIN_NEW_FIRST:
                    Intent intent1 = new Intent(reference.get(), PinSettingActivity.class);
                    intent1.putExtra("pin", PIN_NEW_FIRST);
                    reference.get().startActivityForResult(intent1, PIN_REQUEST);
                    break;
                case SHOW_PROCESSING:
                    Intent intent2 = new Intent(reference.get(), ActivatedProcessing.class);
                    reference.get().startActivity(intent2);
            }
        }
    }
}
