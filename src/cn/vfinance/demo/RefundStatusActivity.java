package cn.vfinance.demo;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.TextView;
import android.widget.Toast;
import cn.vfinance.demo.R;
import cn.vfinance.demo.util.DisplayUtils;

import cn.vfinance.wallet.VFQuery;
import cn.vfinance.wallet.async.VFCallback;
import cn.vfinance.wallet.async.VFResult;
import cn.vfinance.wallet.entity.VFRefundStatus;
import cn.vfinance.wallet.entity.VFReqParams;

/**
 * 用于展示退款状态
 */
public class RefundStatusActivity extends Activity {
    public static final String TAG = "BillListActivity";

    TextView txtRefundStatus;

    private ProgressDialog loadingDialog;
    private Handler mHandler;
    private String refundStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refund_status);

        DisplayUtils.initBack(this);

        txtRefundStatus = (TextView) findViewById(R.id.txtRefundStatus);

        loadingDialog = new ProgressDialog(RefundStatusActivity.this);
        loadingDialog.setMessage("正在请求服务器, 请稍候...");
        loadingDialog.setIndeterminate(true);
        loadingDialog.setCancelable(true);

        // Defines a Handler object that's attached to the UI thread.
        // 通过Handler.Callback()可消除内存泄漏警告
        mHandler = new Handler(new Handler.Callback() {
            /**
             * Callback interface you can use when instantiating a Handler to
             * avoid having to implement your own subclass of Handler.
             *
             * handleMessage() defines the operations to perform when the
             * Handler receives a new Message to process.
             */
            @Override
            public boolean handleMessage(Message msg) {
                if (msg.what == 1) {

                    txtRefundStatus.setText(
                            VFRefundStatus.RefundStatus.getTranslatedRefundStatus(refundStatus));
                }
                return true;
            }
        });

        //回调入口
        final VFCallback vfCallback = new VFCallback() {
            @Override
            public void done(VFResult vfResult) {

                //此处关闭loading界面
                loadingDialog.dismiss();

                final VFRefundStatus vfQueryResult = (VFRefundStatus) vfResult;

                //resultCode为0表示请求成功
                if (vfQueryResult.getResultCode() == 0) {

                    //返回的退款信息
                    refundStatus = vfQueryResult.getRefundStatus();

                    if (refundStatus == null){

                        RefundStatusActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(RefundStatusActivity.this, "没有查询到相关信息", Toast.LENGTH_LONG).show();
                            }
                        });

                    }else{
                        Message msg = mHandler.obtainMessage();
                        msg.what = 1;
                        mHandler.sendMessage(msg);
                    }

                } else {

                    RefundStatusActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(RefundStatusActivity.this, "err code:" + vfQueryResult.getResultCode() +
                                    "; err msg: " + vfQueryResult.getResultMsg() +
                                    "; err detail: " + vfQueryResult.getErrDetail(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        };

        loadingDialog.show();
        VFQuery.getInstance().queryRefundStatusAsync(
                VFReqParams.VFChannelTypes.WX,     //目前仅支持WX、YEE、KUAIQIAN、BD
                "20150812436857",                   //必须是微信的退款单号
                vfCallback);                            //回调入口
    }

}
