package cn.vfinance.demo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import cn.vfinance.demo.R;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import cn.vfinance.demo.util.BillUtils;
import cn.vfinance.demo.util.DisplayUtils;

import cn.vfinance.wallet.VFOfflinePay;
import cn.vfinance.wallet.VFQuery;
import cn.vfinance.wallet.async.VFCallback;
import cn.vfinance.wallet.async.VFResult;
import cn.vfinance.wallet.entity.VFBillStatus;
import cn.vfinance.wallet.entity.VFPayResult;
import cn.vfinance.wallet.entity.VFReqParams;

public class PayViaAuthCodeActivity extends Activity {
    private static final String TAG = "PayViaAuthCodeActivity";

    private static final int REQ_OFF_PAY_SUCC=1;
    private static final int NOTIFY_RESULT = 10;
    private static final int ERR_CODE = 99;

    private ProgressDialog loadingDialog;

    TextView authCodeView;
    Button payBtn;
    Button scanBtn;
    Button queryBtn;

    VFReqParams.VFChannelTypes channelType;
    String type;
    String billTitle;
    String billNum;
    String authCode;
    String errMsg;
    String notify;

    private Handler mHandler= new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {

                case REQ_OFF_PAY_SUCC:

                    Toast.makeText(PayViaAuthCodeActivity.this,
                            "发起支付成功，请通过查询API确认",
                            Toast.LENGTH_SHORT).show();

                    break;

                case NOTIFY_RESULT:
                    Toast.makeText(PayViaAuthCodeActivity.this,
                            notify,
                            Toast.LENGTH_SHORT).show();

                    break;
                case ERR_CODE:

                    Toast.makeText(PayViaAuthCodeActivity.this, errMsg, Toast.LENGTH_LONG).show();
                    //finish();
            }

            return true;
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pay_via_auth_code);

        Intent intent = getIntent();
        type = intent.getStringExtra("type");

        //对于二维码，微信使用 WX_NATIVE 作为channel参数
        //支付宝使用ALI_OFFLINE_QRCODE
        if (type.equals("WX")) {
            channelType = VFReqParams.VFChannelTypes.WX_SCAN;
            billTitle = "安卓通过扫描微信付款码支付测试";
        } else if (type.equals("ALI")) {
            channelType = VFReqParams.VFChannelTypes.ALI_SCAN;
            billTitle = "安卓通过扫描支付宝付款码支付测试";
        } else {
            Toast.makeText(this, "invalid!", Toast.LENGTH_SHORT).show();
            finish();
        }

        DisplayUtils.initBack(this);

        loadingDialog = new ProgressDialog(this);
        loadingDialog.setMessage("处理中，请稍候...");
        loadingDialog.setIndeterminate(true);
        loadingDialog.setCancelable(true);

        authCodeView = (TextView) findViewById(R.id.authCodeView);

        initScanBtn();
        initPayBtn();
        initQueryBtn();
    }

    void initScanBtn () {
        scanBtn = (Button) findViewById(R.id.scanBtn);
        scanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!preCheck())
                    return;

                IntentIntegrator integrator = new IntentIntegrator(PayViaAuthCodeActivity.this);
                integrator.initiateScan();
            }
        });
    }

    void initPayBtn() {
        payBtn = (Button) findViewById(R.id.payBtn);
        payBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                loadingDialog.show();

                billNum = BillUtils.genBillNum();

                Map<String, String> optional=new HashMap<String, String>();
                optional.put("用途", "测试扫码支付");
                optional.put("testEN", "value恩恩");

                VFCallback callback = new VFCallback() {     //回调入口
                    @Override
                    public void done(VFResult vfResult) {

                        //此处关闭loading界面
                        loadingDialog.dismiss();

                        final VFPayResult payResult = (VFPayResult) vfResult;

                        Message msg = mHandler.obtainMessage();

                        //RESULT_SUCCESS表示请求成功
                        if (payResult.getResult().equals(VFPayResult.RESULT_SUCCESS)) {
                            msg.what = REQ_OFF_PAY_SUCC;
                        } else {

                            errMsg = "支付失败，请重试；错误信息：" +
                                    "err code:" + payResult.getResult() +
                                    "; err msg: " + payResult.getErrMsg() +
                                    "; err detail: " + payResult.getDetailInfo();
                            msg.what = ERR_CODE;
                        }

                        mHandler.sendMessage(msg);
                    }
                };

                //你可以任选一种方法请求微信和支付宝二维码
                //此处的判断只是示例和测试需要，并没有实际的逻辑意义
                if (channelType == VFReqParams.VFChannelTypes.WX_SCAN) {
                    VFOfflinePay.getInstance().reqOfflinePayAsync(
                            channelType,
                            "安卓微信扫码方法支付测试", //商品描述
                            1,                 //总金额, 以分为单位, 必须是正整数
                            billNum,          //流水号
                            optional,            //扩展参数
                            authCode,           //付款码
                            "fake-terminalId",  //若机具商接入terminalId(机具终端编号)必填
                            null,               //若系统商接入，storeId(商户门店编号)必填
                            callback);
                } else {
                    VFOfflinePay.PayParams payParam = new VFOfflinePay.PayParams();
                    payParam.channelType = channelType;
                    payParam.billTitle = "安卓支付宝扫码方法支付测试";  //商品描述
                    payParam.billTotalFee = 1;  //总金额, 以分为单位, 必须是正整数
                    payParam.billNum = billNum; //流水号
                    payParam.optional = optional;   //扩展参数
                    payParam.authCode = authCode;   //付款码
                    payParam.terminalId = "fake-terminalId";    //若机具商接入terminalId(机具终端编号)必填

                    VFOfflinePay.getInstance().reqOfflinePayAsync(
                            payParam,
                            callback);

                }
            }
        });
    }

    void initQueryBtn() {
        queryBtn = (Button) findViewById(R.id.queryBtn);

        queryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadingDialog.setMessage("订单查询中，请稍候...");
                loadingDialog.show();

                VFQuery.getInstance().queryOfflineBillStatusAsync(
                        channelType,
                        billNum,
                        new VFCallback() {
                            @Override
                            public void done(VFResult result) {
                                loadingDialog.dismiss();

                                VFBillStatus billStatus = (VFBillStatus) result;

                                Message msg = mHandler.obtainMessage();

                                //表示支付成功
                                if (billStatus.getResultCode() == 0 &&
                                        billStatus.getPayResult()) {
                                    msg.what = NOTIFY_RESULT;
                                    notify = "支付成功";
                                } else {

                                    msg.what = ERR_CODE;
                                    errMsg = "支付失败：" + billStatus.getResultCode() + " # " +
                                            billStatus.getResultMsg() + " # " +
                                            billStatus.getErrDetail();
                                }

                                mHandler.sendMessage(msg);
                            }
                        }
                );
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode==IntentIntegrator.REQUEST_CODE)
        {
            IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
            if (scanResult != null)
            {
                // handle scan result
                Log.d(TAG, scanResult.toString());

                authCode = scanResult.getContents();
                authCodeView.setText("收款码：" + authCode);
            }
            else
            {
                // else continue with any other code you need in the method
                Toast.makeText(PayViaAuthCodeActivity.this, "无法获取收款码，请重试", Toast.LENGTH_LONG).show();
            }
        }
    }

    public boolean preCheck() {
        boolean res = appInstalledOrNot("com.google.zxing.client.android");

        if (!res){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("提示");
            builder.setMessage("本示例依赖ZXing扫码APP，是否安装？");

            builder.setNegativeButton("确定",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            installScannerPlugin();
                            dialog.dismiss();
                        }
                    });

            builder.setPositiveButton("取消",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            builder.create().show();
        }

        return res;
    }

    private void installScannerPlugin() {
        AssetManager assetManager = getAssets();

        InputStream in;
        OutputStream out;

        try {
            in = assetManager.open("BarcodeScanner.apk");
            out = new FileOutputStream(Environment.getExternalStorageDirectory()
                    + File.separator + "BarcodeScanner.apk");

            byte[] buffer = new byte[1024];

            int len;
            while((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }

            in.close();

            out.flush();
            out.close();

            Intent intent = new Intent(Intent.ACTION_VIEW);

            intent.setDataAndType(Uri.fromFile(new File(Environment.getExternalStorageDirectory()
                            + File.separator + "BarcodeScanner.apk")),
                    "application/vnd.android.package-archive");

            startActivity(intent);

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private boolean appInstalledOrNot(String uri) {
        PackageManager pm = getPackageManager();
        boolean appInstalled;
        try {
            pm.getPackageInfo(uri, 0);
            appInstalled = true;
        }
        catch (PackageManager.NameNotFoundException e) {
            appInstalled = false;
        }
        return appInstalled;
    }
}
