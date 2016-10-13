package cn.vfinance.demo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import cn.vfinance.wallet.*;
import cn.vfinance.wallet.async.VFCallback;
import cn.vfinance.wallet.async.VFResult;
import cn.vfinance.wallet.entity.*;
import com.unionpay.UPPayAssistEx;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.*;

public class ShoppingCartActivity extends Activity {

    private static final String TAG = "ShoppingCartActivity";
    private ProgressDialog loadingDialog;
    private ListView payMethod;

    // 微信开放平台分配给商户的 appid
    private final String wxAppId = "wx678ad9de0bf9d684";

    // 维金分配给商户的 appKey
    private final String appId = "100120161013100063";

    // 维金分配给商户的公钥
    private final String secret = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC+8bLQ9baW63/7A7FLfEqf8daWg7TY+hFRZB0zTK/tJy/NzN3L3zOCs3VuhlucMSyKLNb69M05MNk/FVHmRxL0lU8DusMRYs21FMk0SFfFjArW2nGE9r+vVyhxzrIc3fvOsWhAA5arh9yX4h4H5PBloXE2rhxXGiOHgzxxOSx+BwIDAQAB";

    // 下单成功后, 调用维金的同步接口, 更新订单状态
    private final String notifyUrl = "http://func68.vfinance.cn/gateway-mobile/vfinance/syn_trade";

    private String channelCode = "";

    private PayMethodListItem adapter;

    Integer[] payIcons = new Integer[]{
            R.drawable.wechat,
            R.drawable.alipay,
            R.drawable.unionpay
    };
    final String[] payNames = new String[]{"微信支付", "支付宝支付", "银联在线"};
    String[] payDescs = new String[]{"使用微信支付，以人民币CNY计费", "使用支付宝支付，以人民币CNY计费", "使用银联在线支付，以人民币CNY计费"};

    //支付结果返回入口
    VFCallback vfCallback = new VFCallback() {
        @Override
        public void done(final VFResult vfResult) {

            final VFPayResult vfPayResult = (VFPayResult) vfResult;

            //此处关闭loading界面
            loadingDialog.dismiss();

            //根据你自己的需求处理支付结果
            //需要注意的是，此处如果涉及到UI的更新，请在UI主进程或者Handler操作
            ShoppingCartActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    String result = vfPayResult.getResult();
                    /*
                      注意！
                      所有支付渠道建议以服务端的状态金额为准，此处返回的 RESULT_SUCCESS 仅仅代表手机端支付成功
                    */
                    if (result.equals(VFPayResult.RESULT_SUCCESS)) {
                        Toast.makeText(ShoppingCartActivity.this, "用户支付成功", Toast.LENGTH_LONG).show();
                        allPayNotify();

                    } else if (result.equals(VFPayResult.RESULT_CANCEL))
                        Toast.makeText(ShoppingCartActivity.this, "用户取消支付", Toast.LENGTH_LONG).show();
                    else if (result.equals(VFPayResult.RESULT_FAIL)) {
                        String toastMsg = "支付失败, 原因: " + vfPayResult.getErrCode() +
                                " # " + vfPayResult.getErrMsg() +
                                " # " + vfPayResult.getDetailInfo();
                        /**
                         * 你发布的项目中不应该出现如下错误，此处由于支付宝政策原因，
                         * 不再提供支付宝支付的测试功能，所以给出提示说明
                         */
                        if (vfPayResult.getErrMsg().equals("PAY_FACTOR_NOT_SET") &&
                                vfPayResult.getDetailInfo().startsWith("支付宝参数")) {
                            toastMsg = "支付失败：由于支付宝政策原因，故不再提供支付宝支付的测试功能，给您带来的不便，敬请谅解";
                        }

                        /**
                         * 以下是正常流程，请按需处理失败信息
                         */
                        Toast.makeText(ShoppingCartActivity.this, toastMsg, Toast.LENGTH_LONG).show();
                        Log.e(TAG, toastMsg);

                        if (vfPayResult.getErrMsg().equals(VFPayResult.FAIL_PLUGIN_NOT_INSTALLED)) {
                            //银联需要重新安装控件
                            Message msg = mHandler.obtainMessage();
                            msg.what = 1;
                            mHandler.sendMessage(msg);
                        }

                    } else if (result.equals(VFPayResult.RESULT_UNKNOWN)) {
                        //可能出现在支付宝8000返回状态
                        Toast.makeText(ShoppingCartActivity.this, "订单状态未知", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(ShoppingCartActivity.this, "invalid return", Toast.LENGTH_LONG).show();
                    }

                    if (vfPayResult.getId() != null) {
                        //你可以把这个id存到你的订单中，下次直接通过这个id查询订单
                        Log.w(TAG, "bill id retrieved : " + vfPayResult.getId());

                        //根据ID查询，此处只是演示如何通过id查询订单，并非支付必要部分
                        getBillInfoByID(vfPayResult.getId());
                    }
                }
            });
        }
    };

    // Defines a Handler object that's attached to the UI thread.
    // 通过Handler.Callback()可消除内存泄漏警告
    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == 1) {
                //如果用户手机没有安装银联支付控件,则会提示用户安装
                AlertDialog.Builder builder = new AlertDialog.Builder(ShoppingCartActivity.this);
                builder.setTitle("提示");
                builder.setMessage("完成支付需要安装或者升级银联支付控件，是否安装？");

                builder.setNegativeButton("确定",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                UPPayAssistEx.installUPPayPlugin(ShoppingCartActivity.this);
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
            return true;
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shopping_cart);

        // 推荐在主 Activity 或 application 里的 onCreate 函数中初始化 VFinance.
        VFinance.setSandbox(false);
        VFinance.setAppIdAndSecret(appId, secret);

        // 如果用到微信支付，在用到微信支付的Activity的onCreate函数里调用以下函数.
        // 第二个参数需要换成你自己的微信AppID.
        String initInfo = VFPay.initWechatPay(
                ShoppingCartActivity.this,
                wxAppId
        );
        if (initInfo != null) {
            Toast.makeText(this, "微信初始化失败：" + initInfo, Toast.LENGTH_LONG).show();
        }

        payMethod = (ListView) this.findViewById(R.id.payMethod);
        adapter = new PayMethodListItem(this, channels);
        payMethod.setAdapter(adapter);

        // 如果调起支付太慢, 可以在这里开启动画, 以 progressdialog 为例
        loadingDialog = new ProgressDialog(ShoppingCartActivity.this);
        loadingDialog.setMessage("启动第三方支付，请稍候...");
        loadingDialog.setIndeterminate(true);
        loadingDialog.setCancelable(true);

        payMethod.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                // 支付金额(元)
                String billTotalFee = ((EditText) findViewById(R.id.billTotalFee)).getText().toString().trim();

                // 转成分
//                Double totalFee = Double.parseDouble(billTotalFee) * 100;
                int totalFee = VFValidationUtil.formatTotalFee(billTotalFee);

                // 商品标题
                String billTitle = ((EditText) findViewById(R.id.billTitle)).getText().toString();

                // 商户订单号
                String billNum = ((EditText) findViewById(R.id.billNum)).getText().toString();

                Log.i(TAG, "setOnItemClickListener,  billTotalFee=" + billTotalFee + ", totalFee=" + totalFee + ", billTitle=" + billTitle + ", billNum=" + billNum);

                Map<String, String> mapOptional = new HashMap<String, String>();

                ChannelInfoEntity item = adapter.getItem(position);
                switch (item.payIcon) {

                    case R.drawable.wechat: //微信
                        loadingDialog.show();
                        // 对于微信支付, 手机内存太小会有 OutOfResourcesException 造成的卡顿, 以致无法完成支付, 这个是微信自身存在的问题

                        // 微信需要手机设备 ip 地址
                        mapOptional.put("ip", getIpAddress());

                        // 商品描述
//                        mapOptional.put("proDesc", billTitle);

                        if (VFPay.isWXAppInstalledAndSupported() && VFPay.isWXPaySupported()) {
                            channelCode = "WXAPPPAY";
                            VFPay.getInstance(ShoppingCartActivity.this).reqWXPaymentAsync(
                                    billTitle, // 订单标题
                                    totalFee, // 订单金额(分)
                                    billNum, // 订单流水号, BillUtils.genBillNum()
                                    mapOptional, // 扩展参数 (可以null)
                                    vfCallback); // 支付完成后回调入口
                        } else {
                            Toast.makeText(ShoppingCartActivity.this, "您尚未安装微信或者安装的微信版本不支持", Toast.LENGTH_LONG).show();
                            loadingDialog.dismiss();
                        }
                        break;

                    case R.drawable.alipay: //支付宝支付
                        loadingDialog.show();
                        channelCode = "ALIAPPPAY";
                        VFPay.getInstance(ShoppingCartActivity.this).reqAliPaymentAsync(
                                billTitle,
                                totalFee,
                                billNum,
                                mapOptional,
                                vfCallback);
                        break;

                    case R.drawable.unionpay: //银联支付
                        loadingDialog.show();
                        channelCode = "UNIONPAY";
                        //  你可以通过如下方法发起支付，或者 PayParams 的方式
                        VFPay.getInstance(ShoppingCartActivity.this).reqUnionPaymentAsync(
                                billTitle,
                                totalFee,
                                billNum,
                                mapOptional,
                                vfCallback);
                        break;
                }
            }
        });

        getChannelData();
    }

    private ArrayList<ChannelInfoEntity> channels = new ArrayList<ChannelInfoEntity>();
    private void getChannelData() {
        HttpsTrustManager.allowAllSSL();
        VFQuery.getInstance().queryValidChannel(appId, new VFCallback() {
            @Override
            public void done(VFResult vfResult) {
                VFChannelEntity vfChannelEntity = ((VFChannelEntity) vfResult);
                if(vfChannelEntity.getChannel() == null){
                    return;
                }
                for(String s : vfChannelEntity.getChannel()){
                    ChannelInfoEntity channelInfoEntity = new ChannelInfoEntity();
                    if("ALIAPPPAY".equals(s)){
                        channelInfoEntity.payName = payNames[1];
                        channelInfoEntity.payDesc = payDescs[1];
                        channelInfoEntity.payIcon = payIcons[1];
                    }else if("WXAPPPAY".equals(s)){
                        channelInfoEntity.payName = payNames[0];
                        channelInfoEntity.payDesc = payDescs[0];
                        channelInfoEntity.payIcon = payIcons[0];
                    }else if("UNIONPAY".equals(s)){
                        channelInfoEntity.payName = payNames[2];
                        channelInfoEntity.payDesc = payDescs[2];
                        channelInfoEntity.payIcon = payIcons[2];
                    }
                    channels.add(channelInfoEntity);
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        });
    }

    void getBillInfoByID(String id) {

        VFQuery.getInstance().queryBillByIDAsync(id,
                new VFCallback() {
                    @Override
                    public void done(VFResult result) {
                        VFQueryBillResult billResult = (VFQueryBillResult) result;

                        Log.d(TAG, "------ response info ------");
                        Log.d(TAG, "------getResultCode------" + billResult.getResultCode());
                        Log.d(TAG, "------getResultMsg------" + billResult.getResultMsg());
                        Log.d(TAG, "------getErrDetail------" + billResult.getErrDetail());

                        if (billResult.getResultCode() != 0)
                            return;

                        Log.d(TAG, "------- bill info ------");
                        VFBillOrder billOrder = billResult.getBill();
                        Log.d(TAG, "订单唯一标识符：" + billOrder.getId());
                        Log.d(TAG, "订单号:" + billOrder.getBillNum());
                        Log.d(TAG, "订单金额, 单位为分:" + billOrder.getTotalFee());
                        Log.d(TAG, "渠道类型:" + VFReqParams.VFChannelTypes.getTranslatedChannelName(billOrder.getChannel()));
                        Log.d(TAG, "子渠道类型:" + VFReqParams.VFChannelTypes.getTranslatedChannelName(billOrder.getSubChannel()));
                        Log.d(TAG, "订单是否成功:" + billOrder.getPayResult());

                        if (billOrder.getPayResult())
                            Log.d(TAG, "渠道返回的交易号，未支付成功时，是不含该参数的:" + billOrder.getTradeNum());
                        else
                            Log.d(TAG, "订单是否被撤销，该参数仅在线下产品（例如二维码和扫码支付）有效:"
                                    + billOrder.getRevertResult());

                        Log.d(TAG, "订单创建时间:" + new Date(billOrder.getCreatedTime()));
                        Log.d(TAG, "扩展参数:" + billOrder.getOptional());
                        Log.w(TAG, "订单是否已经退款成功(用于后期查询): " + billOrder.getRefundResult());
                        Log.w(TAG, "渠道返回的详细信息，按需处理: " + billOrder.getMessageDetail());

                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //清理当前的activity引用
        VFPay.clear();

        //使用微信的，在initWechatPay的activity结束时detach
        VFPay.detachWechat();

        //使用百度支付的，在activity结束时detach
        VFPay.detachBaiduPay();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100) {
            loadingDialog.hide();
        }
    }

    // 订单支付成功后, 调用维金同步接口, 及时更新订单状态
    private void allPayNotify() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String instOrderNo = ((EditText) findViewById(R.id.billNum)).getText().toString();
                try {
                    String address = notifyUrl + "?channelCode=" + channelCode + "&instOrderNo=" + instOrderNo + "&appKey=" + appId;
                    URL url = new URL(address);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(1000);
                    connection.setUseCaches(false);
                    if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        InputStream in = connection.getInputStream();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                        String tmpString;
                        StringBuilder retJSON = new StringBuilder();
                        while ((tmpString = reader.readLine()) != null) {
                            retJSON.append(tmpString + "\n");
                        }
                    }
                } catch (Exception e) {
                }
            }
        }).start();
    }

    // 获得手机外网 ip
    private String getIpAddress() {
        ConnectivityManager mConnectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo mActiveNetInfo = mConnectivityManager.getActiveNetworkInfo(); //获取网络连接的信息
        String ip = "";
        // WiFi 上网
        if (mActiveNetInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            ip = getWifiIp();
        }
        // 手机流量上网
        else if (mActiveNetInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
            ip = getIPAddress(true);

            // 获得手机的外网 ip, 需要另起个线程, 或去掉不允许同步 http 请求的限制, 效率略低
//            if (android.os.Build.VERSION.SDK_INT > 9) {
//                StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
//                StrictMode.setThreadPolicy(policy);
//            }
//            return GetNetIp();
        }
        return ip;
    }

    // 获得 ip 地址, 如果是手机流量上网
    private static String GetNetIp() {
        try {
            String address = "http://ip.taobao.com/service/getIpInfo2.php?ip=myip";
            URL url = new URL(address);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(1000);
            connection.setUseCaches(false);

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream in = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String tmpString;
                StringBuilder retJSON = new StringBuilder();
                while ((tmpString = reader.readLine()) != null) {
                    retJSON.append(tmpString + "\n");
                }
                JSONObject jsonObject = new JSONObject(retJSON.toString());
                String code = jsonObject.getString("code");
                if ("0".equals(code)) {
                    return jsonObject.getJSONObject("data").getString("ip"); // 成功获得 ip
                }
            }
        } catch (Exception e) {
        }
        return "";
    }

    /**
     * Get IP address from first non-localhost interface
     *
     * @param useIPv4 true=return ipv4, false=return ipv6
     * @return address or empty string
     */
    private static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;
                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                return delim < 0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
        } // for now eat exceptions
        return "";
    }

    // 获得 ip 地址, 如果是 Wifi
    private String getWifiIp() {

        //获取wifi服务
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        //判断wifi是否开启
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();
        return intToIp(ipAddress);
    }

    private String intToIp(int i) {

        return (i & 0xFF) + "." +
                ((i >> 8) & 0xFF) + "." +
                ((i >> 16) & 0xFF) + "." +
                (i >> 24 & 0xFF);
    }
}
