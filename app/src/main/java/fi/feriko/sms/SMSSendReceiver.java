package fi.feriko.sms;
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

public class SMSSendReceiver extends BroadcastReceiver {

    private SMSUtils owner;
    private Handler ownerHandler;
    private int messageId;

    public SMSSendReceiver(SMSUtils owner_, Handler oh, int id) {
        owner = owner_;
        ownerHandler = oh;
        messageId = id;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        int what = SMSUtils.UNKNOWN;

            switch (getResultCode()) {
                case Activity.RESULT_OK:
                    what = SMSUtils.SENT;
                    break;
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    what = SMSUtils.NOT_SENT;
                    break;
                case SmsManager.RESULT_ERROR_NO_SERVICE:
                    what = SMSUtils.NO_SERVICE;
                    break;
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    what = SMSUtils.NULL_PDU;
                    break;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    what = SMSUtils.RADIO_OFF;
                    break;

            }

        Message msg = ownerHandler.obtainMessage(what);
        msg.arg1 = messageId;
        ownerHandler.sendMessage(msg);
        context.unregisterReceiver(this);
        owner.clearSendReceiver(messageId);
    }


    public void unregister(final Context context) {

        try {
            context.unregisterReceiver(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}