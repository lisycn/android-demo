package cn.vfinance.demo;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.*;
import cn.vfinance.demo.util.DisplayUtils;
import cn.vfinance.wallet.VFQuery;
import cn.vfinance.wallet.async.VFCallback;
import cn.vfinance.wallet.async.VFResult;
import cn.vfinance.wallet.entity.VFBillOrder;
import cn.vfinance.wallet.entity.VFQueryBillsResult;
import cn.vfinance.wallet.entity.VFQueryCountResult;
import cn.vfinance.wallet.entity.VFReqParams;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 用于展示订单查询
 */
public class BillListActivity extends Activity {

    public static final String TAG = "BillListActivity";

    Spinner channelChooser;
    ListView listViewOrder;

    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == 1) {
                BillListAdapter adapter = new BillListAdapter(BillListActivity.this, bills);
                listViewOrder.setAdapter(adapter);
            }
            return true;
        }
    });
    private List<VFBillOrder> bills;

    private ProgressDialog loadingDialog;

    //回调入口
    final VFCallback vfCallback = new VFCallback() {
        @Override
        public void done(VFResult vfResult) {

            //此处关闭loading界面
            loadingDialog.dismiss();

            final VFQueryBillsResult vfQueryResult = (VFQueryBillsResult) vfResult;

            // resultCode为0表示请求成功
            // count包含返回的订单个数
            if (vfQueryResult.getResultCode() == 0) {

                //订单列表
                bills = vfQueryResult.getBills();

                Log.i(BillListActivity.TAG, "bill count: " + vfQueryResult.getCount());

            } else {
                //订单列表
                bills = null;

                BillListActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(BillListActivity.this, "err code:" + vfQueryResult.getResultCode() +
                                "; err msg: " + vfQueryResult.getResultMsg() +
                                "; err detail: " + vfQueryResult.getErrDetail(), Toast.LENGTH_LONG).show();
                    }
                });
            }

            Message msg = mHandler.obtainMessage();
            msg.what = 1;
            mHandler.sendMessage(msg);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bill_list);

        loadingDialog = new ProgressDialog(BillListActivity.this);
        loadingDialog.setMessage("正在请求服务器, 请稍候...");
        loadingDialog.setIndeterminate(true);
        loadingDialog.setCancelable(true);

        DisplayUtils.initBack(this);

        listViewOrder = (ListView) findViewById(R.id.listViewOrder);

        initSpinner();
    }

    void initSpinner() {
        channelChooser = (Spinner) this.findViewById(R.id.channelChooser);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"微信", "支付宝", "银联", "百度", "PayPal", "全渠道", "订单总数"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        channelChooser.setAdapter(adapter);

        channelChooser.setSelected(false);

        channelChooser.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // 如果调起支付太慢，可以在这里开启动画，表示正在loading
                //以progressdialog为例
                loadingDialog.show();

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);

                Date startTime;
                //Date endTime;
                try {
                    startTime = sdf.parse("2015-10-01 00:00");
                    //endTime = sdf.parse("2015-12-01 23:59");
                } catch (ParseException e) {
                    startTime = new Date();
                    //endTime = new Date();
                    e.printStackTrace();
                }

                VFQuery.QueryParams params;

                switch (position) {
                    case 0: //微信
                        VFQuery.getInstance().queryBillsAsync(
                                VFReqParams.VFChannelTypes.WX,  //此处表示微信支付的查询
                                vfCallback);
                        break;
                    case 1: //支付宝
                        VFQuery.getInstance().queryBillsAsync(
                                VFReqParams.VFChannelTypes.ALI_APP, //渠道，此处表示ALI客户端渠道
                                //"20150820102712150", //此处表示限制订单号
                                vfCallback);
                        break;
                    case 2: //银联
                        VFQuery.getInstance().queryBillsAsync(
                                VFReqParams.VFChannelTypes.UN_APP, //渠道, 此处表示银联手机APP客户端支付
                                null,                //订单号
                                startTime.getTime(), //起始时间
                                null,                //结束时间
                                2,                   //跳过满足条件的前2条数据
                                15,                  //最多返回满足条件的15条数据
                                vfCallback);
                        break;
                    case 3: //百度
                        //以下演示通过PayParams发起请求
                        params = new VFQuery.QueryParams();
                        params.channel = VFReqParams.VFChannelTypes.BD;

                        //以下参数按需设置，都是‘且’的关系

                        //限制支付订单号
                        //params.billNum = null;

                        //只返回成功的订单
                        params.payResult = Boolean.TRUE;

                        //限制起始时间
                        params.startTime = startTime.getTime();

                        //限制结束时间
                        //params.endTime = endTime.getTime();

                        //跳过满足条件的数目
                        //params.skip = 10;

                        //最多返回的数目
                        params.limit = 20;

                        //是否获取渠道返回的详细信息
                        params.needDetail = true;

                        VFQuery.getInstance().queryBillsAsync(params, vfCallback);

                        break;
                    case 4: //PayPal
                        params = new VFQuery.QueryParams();
                        params.channel = VFReqParams.VFChannelTypes.PAYPAL;
                        VFQuery.getInstance().queryBillsAsync(params, vfCallback);

                        break;
                    case 5: //全部的渠道类型
                        params = new VFQuery.QueryParams();
                        params.channel = VFReqParams.VFChannelTypes.ALL;

                        //跳过满足条件的数目
                        params.skip = 10;

                        //最多返回的数目
                        params.limit = 20;

                        VFQuery.getInstance().queryBillsAsync(params, vfCallback);
                        break;

                    case 6:
                        params = new VFQuery.QueryParams();

                        //以下为可用的限制参数
                        //渠道类型
                        params.channel = VFReqParams.VFChannelTypes.ALL;

                        //支付单号
                        //params.billNum = "your bill number";

                        //订单是否支付成功
                        params.payResult = Boolean.TRUE;

                        //限制起始时间
                        params.startTime = startTime.getTime();

                        //限制结束时间
                        //params.endTime = endTime.getTime();

                        VFQuery.getInstance().queryBillsCountAsync(params, new VFCallback() {
                            @Override
                            public void done(VFResult result) {
                                if (loadingDialog.isShowing())
                                    loadingDialog.dismiss();

                                final VFQueryCountResult countResult = (VFQueryCountResult) result;

                                if (countResult.getResultCode() == 0) {
                                    //显示获取到的订单总数
                                    BillListActivity.this.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(BillListActivity.this,
                                                    "订单总数:" + countResult.getCount(),
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    });
                                } else {
                                    BillListActivity.this.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(BillListActivity.this,
                                                    "err code:" + countResult.getResultCode() +
                                                            "; err msg: " + countResult.getResultMsg() +
                                                            "; err detail: " + countResult.getErrDetail(),
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    });
                                }
                            }
                        });
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

}