package cn.vfinance.demo.util;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;

import cn.vfinance.demo.R;

public class DisplayUtils {

    public static void initBack(final Activity activity) {
        LinearLayout clickArea = (LinearLayout) activity.findViewById(R.id.backClickArea);

        clickArea.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    activity.finish();
                }
            });
    }
}
