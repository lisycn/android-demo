package cn.vfinance.demo;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;
import cn.vfinance.demo.R;
import cn.vfinance.demo.util.BillUtils;
import cn.vfinance.demo.util.DisplayUtils;

import cn.vfinance.wallet.VFOfflinePay;
import cn.vfinance.wallet.VFQuery;
import cn.vfinance.wallet.async.VFCallback;
import cn.vfinance.wallet.async.VFResult;
import cn.vfinance.wallet.entity.VFBillStatus;
import cn.vfinance.wallet.entity.VFQRCodeResult;
import cn.vfinance.wallet.entity.VFReqParams;
import cn.vfinance.wallet.entity.VFRevertStatus;

public class GenQRCodeActivity extends Activity {

    private static final int REQ_QRCODE_CODE=1;
    private static final int NOTIFY_RESULT = 10;
    private static final int ERR_CODE = 99;

    ProgressDialog loadingDialog;
    String billNum;
    String type;
    VFReqParams.VFChannelTypes channelType;
    String billTitle;
    Bitmap qrCodeBitMap;
    String notify;
    String errMsg;
    ImageView qrcodeImg;
    Button btnQueryResult;
    Button btnRevert;

    private Handler mHandler= new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case REQ_QRCODE_CODE:
                    qrcodeImg.setImageBitmap(qrCodeBitMap);
                    break;

                case NOTIFY_RESULT:
                    Toast.makeText(GenQRCodeActivity.this, notify, Toast.LENGTH_LONG).show();
                    break;

                case ERR_CODE:
                    Toast.makeText(GenQRCodeActivity.this, errMsg, Toast.LENGTH_LONG).show();
            }
            return true;
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gen_qrcode);

        Intent intent = getIntent();
        type = intent.getStringExtra("type");

        // 对于二维码，微信使用 WX_NATIVE 作为 channel 参数
        // 支付宝使用 ALI_OFFLINE_QRCODE
        switch (type) {
            case "WX":
                channelType = VFReqParams.VFChannelTypes.WX_NATIVE;
                billTitle = "安卓微信二维码测试";
                break;
            case "ALI":
                channelType = VFReqParams.VFChannelTypes.ALI_OFFLINE_QRCODE;
                billTitle = "安卓支付宝线下二维码测试";
                break;
            default:
                Toast.makeText(this, "invalid!", Toast.LENGTH_SHORT).show();
                finish();
        }

        DisplayUtils.initBack(this);

        loadingDialog = new ProgressDialog(this);
        loadingDialog.setMessage("处理中，请稍候...");
        loadingDialog.setIndeterminate(true);
        loadingDialog.setCancelable(true);

        qrcodeImg = (ImageView) this.findViewById(R.id.qrcodeImg);
        btnQueryResult = (Button) findViewById(R.id.btnQueryResult);

        initQueryButton();

        btnRevert = (Button) findViewById(R.id.btnRevert);

        initRevertBtn();

        reqQrCode();
    }

    void reqQrCode(){

        loadingDialog.show();

        Map<String, String> optional=new HashMap<String, String>();
        optional.put("用途", "测试二维码");
        optional.put("testEN", "value哈哈");

        //初始化回调入口
        VFCallback callback = new VFCallback() {
            @Override
            public void done(VFResult vfResult) {

                //此处关闭loading界面
                loadingDialog.dismiss();

                final VFQRCodeResult vfqrCodeResult = (VFQRCodeResult) vfResult;

                Message msg = mHandler.obtainMessage();

                // resultCode为0, 表示请求成功
                if (vfqrCodeResult.getResultCode() == 0) {
                    //如果你设置了生成二维码参数为true那么此处可以获取二维码
                    qrCodeBitMap = vfqrCodeResult.getQrCodeBitmap();

                    //否则通过 vfqrCodeResult.getQrCodeRawContent() 获取二维码的内容，自己去生成对应的二维码

                    msg.what = REQ_QRCODE_CODE;
                } else {
                    errMsg = "err code:" + vfqrCodeResult.getResultCode() +
                            "; err msg: " + vfqrCodeResult.getResultMsg() +
                            "; err detail: " + vfqrCodeResult.getErrDetail();
                    msg.what = ERR_CODE;
                }
                mHandler.sendMessage(msg);
            }
        };

        billNum = BillUtils.genBillNum();

        //你可以任选一种方法请求微信和支付宝二维码
        //此处的判断只是示例和测试需要，并没有实际的逻辑意义
        if (channelType == VFReqParams.VFChannelTypes.WX_NATIVE) {
            VFOfflinePay.getInstance().reqQRCodeAsync(
                    channelType,
                    billTitle,  //商品描述
                    1,          //总金额, 以分为单位, 必须是正整数
                    billNum,    //流水号
                    optional,   //扩展参数
                    true,       //是否生成二维码的bitmap
                    380,        //二维码的尺寸, 以px为单位, 如果为null则默认为360
                    callback);
        } else {
            VFOfflinePay.PayParams payParam = new VFOfflinePay.PayParams();

            payParam.channelType = channelType;
            payParam.billTitle = billTitle; //商品描述
            payParam.billTotalFee = 1; //总金额, 以分为单位, 必须是正整数
            payParam.billNum = billNum;         //流水号
            payParam.optional = optional;   //扩展参数
            payParam.genQRCode = true;      //是否生成二维码的bitmap
            payParam.qrCodeWidth = 380;                   //二维码的尺寸, 以px为单位, 如果为null则默认为360

            VFOfflinePay.getInstance().reqQRCodeAsync(
                    payParam,
                    callback
            );
        }
    }

    void initQueryButton() {
        btnQueryResult.setOnClickListener(new View.OnClickListener() {
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

    void initRevertBtn() {
        btnRevert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //微信二维码无法撤销
                if (channelType == VFReqParams.VFChannelTypes.WX_NATIVE) {
                    Message msg = mHandler.obtainMessage();
                    msg.what = NOTIFY_RESULT;
                    notify = "微信二维码无法撤销";
                    mHandler.sendMessage(msg);
                    return;
                }

                loadingDialog.setMessage("订单撤销中，请稍候...");
                loadingDialog.show();

                VFOfflinePay.getInstance().reqRevertBillAsync(
                        channelType,
                        billNum,
                        new VFCallback() {
                            @Override
                            public void done(VFResult result) {
                                loadingDialog.dismiss();

                                VFRevertStatus revertStatus = (VFRevertStatus) result;

                                Message msg = mHandler.obtainMessage();

                                if (revertStatus.getResultCode() == 0 &&
                                        revertStatus.getRevertStatus()) {
                                    msg.what = NOTIFY_RESULT;
                                    notify = "撤销成功";
                                } else {

                                    msg.what = ERR_CODE;
                                    errMsg = "撤销失败：" + revertStatus.getResultCode() + " # " +
                                            revertStatus.getResultMsg() + " # " +
                                            revertStatus.getErrDetail();
                                }

                                mHandler.sendMessage(msg);
                            }
                        }
                );
            }
        });
    }
}
