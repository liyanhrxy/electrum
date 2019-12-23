package org.haobtc.wallet.activities;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chaquo.python.PyObject;
import com.google.gson.Gson;

import org.acra.data.StringFormat;
import org.haobtc.wallet.R;
import org.haobtc.wallet.activities.base.BaseActivity;
import org.haobtc.wallet.adapter.ChoosePayAddressAdapetr;
import org.haobtc.wallet.bean.AddressEvent;
import org.haobtc.wallet.bean.MainWheelBean;
import org.haobtc.wallet.utils.CommonUtils;
import org.haobtc.wallet.utils.Daemon;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class SendOne2ManyMainPageActivity extends BaseActivity {


    @BindView(R.id.wallet_name)
    TextView walletName;
    @BindView(R.id.lin_chooseAddress)
    LinearLayout linChooseAddress;
    @BindView(R.id.address_count)
    TextView addressCount;
    @BindView(R.id.linearLayout10)
    LinearLayout linearLayout10;
    @BindView(R.id.tv_amount)
    TextView tvAmount;
    @BindView(R.id.fee_select)
    LinearLayout feeSelect;
    @BindView(R.id.edit_Remarks)
    EditText editRemarks;
    @BindView(R.id.tet_textNum)
    TextView tetTextNum;
    @BindView(R.id.create_trans_one2many)
    Button createTransOne2many;
    private ArrayList<AddressEvent> dataListName;
    private Dialog dialogBtom;
    private RecyclerView recyPayaddress;
    private ChoosePayAddressAdapetr choosePayAddressAdapetr;
    private String wallet_name;

    public int getLayoutId() {
        return R.layout.send_one2many_main;
    }

    public void initView() {
        // TODO: add setContentView(...) invocation
        ButterKnife.bind(this);
        CommonUtils.enableToolBar(this, R.string.send);
        Intent intent = getIntent();
        wallet_name = intent.getStringExtra("wallet_name");
        int addressNum = intent.getIntExtra("addressNum", 0);
        int totalAmount = intent.getIntExtra("totalAmount", 0);
        walletName.setText(wallet_name);
        addressCount.setText(String.valueOf(addressNum)+getResources().getString(R.string.to_num));
        tvAmount.setText(String.format("%d BTC",totalAmount));

    }

    @Override
    public void initData() {
        dataListName = new ArrayList<>();
        //getMorepayAddress
        payAddressMore();

    }

    //getMorepayAddress
    private void payAddressMore() {
        PyObject get_wallets_list_info = Daemon.commands.callAttr("get_wallets_list_info");
        String toString = get_wallets_list_info.toString();
        Log.i("payAddressMore", "pay---: " + toString);
        Gson gson = new Gson();
        MainWheelBean mainWheelBean = gson.fromJson(toString, MainWheelBean.class);
        List<MainWheelBean.WalletsBean> wallets = mainWheelBean.getWallets();
        for (int i = 0; i < wallets.size(); i++) {
            String wallet_type = wallets.get(i).getWalletType();
            String name = wallets.get(i).getName();
            AddressEvent addressEvent = new AddressEvent();
            addressEvent.setName(name);
            addressEvent.setType(wallet_type);
            dataListName.add(addressEvent);
        }
    }


    @OnClick({R.id.lin_chooseAddress, R.id.linearLayout10, R.id.fee_select})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.lin_chooseAddress:
                //check wallet
                showDialogs(SendOne2ManyMainPageActivity.this, R.layout.select_send_wallet_popwindow);
                break;
            case R.id.linearLayout10:
                break;
            case R.id.fee_select:
                break;
        }
    }

    private void showDialogs(Context context, @LayoutRes int resource) {
        //set see view
        View view = View.inflate(context, resource, null);
        dialogBtom = new Dialog(context, R.style.dialog);
        //cancel dialog
        view.findViewById(R.id.cancel_select_wallet).setOnClickListener(v -> {
            dialogBtom.cancel();
        });
        view.findViewById(R.id.bn_select_wallet).setOnClickListener(v -> {
//            Daemon.commands.callAttr("load_wallet", wallet_name);
//            Daemon.commands.callAttr("select_wallet", wallet_name);

            walletName.setText(wallet_name);
            dialogBtom.cancel();
        });
        recyPayaddress = view.findViewById(R.id.recy_payAdress);
        recyPayaddress.setLayoutManager(new LinearLayoutManager(SendOne2ManyMainPageActivity.this));
        choosePayAddressAdapetr = new ChoosePayAddressAdapetr(SendOne2ManyMainPageActivity.this, dataListName);
        recyPayaddress.setAdapter(choosePayAddressAdapetr);
        recyclerviewOnclick();


        dialogBtom.setContentView(view);
        Window window = dialogBtom.getWindow();
        //set pop_up size
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        //set locate
        window.setGravity(Gravity.BOTTOM);
        //set animal
        window.setWindowAnimations(R.style.AnimBottom);
        dialogBtom.show();
    }

    private void recyclerviewOnclick() {
        choosePayAddressAdapetr.setmOnItemClickListener(new ChoosePayAddressAdapetr.OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                wallet_name = dataListName.get(position).getName();

            }
        });
    }


}
