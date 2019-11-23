package fi.feriko.sms2;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.telephony.SmsManager;

import java.util.ArrayList;

public class SMSDeliveryReceiver extends BroadcastReceiver {

    private SMSUtils owner;
    private Handler ownerHandler;
    private int messageId;


    public SMSDeliveryReceiver(SMSUtils owner_, Handler oh, int id) {
        owner = owner_;
        ownerHandler = oh;
        messageId = id;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        int what = SMSUtils.UNKNOWN;

        switch (getResultCode()) {
            case Activity.RESULT_OK:
                what = SMSUtils.DELIVERY_OK;
                break;
            case Activity.RESULT_CANCELED:
                what = SMSUtils.DELIVERY_FAIL;
                break;
        }

        Message msg = ownerHandler.obtainMessage(what);
        msg.arg1 = messageId;
        ownerHandler.sendMessage(msg);
        context.unregisterReceiver(this);
        owner.clearDeliveryReceiver(messageId);
    }


    public void unregister(final Context context) {

        try {
            context.unregisterReceiver(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}