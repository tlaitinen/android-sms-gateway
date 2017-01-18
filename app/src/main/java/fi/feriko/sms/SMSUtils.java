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
import java.util.HashMap;
import java.util.Map;

public class SMSUtils  {

    public static final String SENT_SMS_ACTION_NAME = "SMS_SENT";
    public static final String DELIVERED_SMS_ACTION_NAME = "SMS_DELIVERED";
    private Handler ownerHandler;
    private Context ctx;
    public static final int SENT = 1;
    public static final int NOT_SENT = 2;
    public static final int NO_SERVICE = 3;
    public static final int NULL_PDU = 4;
    public static final int RADIO_OFF = 5;
    public static final int UNKNOWN = 6;
    public static final int DELIVERY_OK = 7;
    public static final int DELIVERY_FAIL = 8;
    public static final int NO_SMS_FEATURE = 9;

    private Map<Integer, SMSSendReceiver> sendReceivers = new HashMap<Integer, SMSSendReceiver>();
    private Map<Integer, SMSDeliveryReceiver> deliveryReceivers = new HashMap<Integer, SMSDeliveryReceiver> ();

    public SMSUtils(Handler oh,Context context) {
        ownerHandler = oh;
        ctx = context;

    }

    /**
     * Test if device can send SMS
     * @param context
     * @return
     */
    public static boolean canSendSMS(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    public void unregister(final Context context) {
        for (Map.Entry<Integer, SMSSendReceiver> entry : sendReceivers.entrySet()) {
            try {
                context.unregisterReceiver(entry.getValue());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        for (Map.Entry<Integer, SMSDeliveryReceiver> entry : deliveryReceivers.entrySet()) {
            try {
                context.unregisterReceiver(entry.getValue());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    public void clearSendReceiver(int id) {
        sendReceivers.remove(id);
    }
    public void clearDeliveryReceiver(int id) {
        deliveryReceivers.remove(id);
    }

    public  void sendSMS(final Context context, int id, String phoneNumber, String message) {

        if (!canSendSMS(context)) {
            Message msg = ownerHandler.obtainMessage(NO_SMS_FEATURE);
            msg.arg1 = id;
            ownerHandler.sendMessage(msg);
            return;
        }

        SMSSendReceiver sendReceiver = new SMSSendReceiver(this, ownerHandler, id);
        SMSDeliveryReceiver deliveryReceiver = new SMSDeliveryReceiver(this, ownerHandler, id);
        sendReceivers.put(id, sendReceiver);
        deliveryReceivers.put(id, deliveryReceiver);

        String smsSentAction = SMSUtils.SENT_SMS_ACTION_NAME + Integer.toString(id);
        String smsDeliveredAction = DELIVERED_SMS_ACTION_NAME + Integer.toString(id);

        context.registerReceiver(sendReceiver, new IntentFilter(smsSentAction));
        context.registerReceiver(deliveryReceiver, new IntentFilter(smsDeliveredAction));

        Intent sent = new Intent(smsSentAction);
        Intent delivered = new Intent(smsDeliveredAction);
        PendingIntent sentPI = PendingIntent.getBroadcast(context, 0, sent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent deliveredPI = PendingIntent.getBroadcast(context, 0, delivered, PendingIntent.FLAG_UPDATE_CURRENT);

        SmsManager sms = SmsManager.getDefault();

        ArrayList<String> parts = sms.divideMessage(message);

        ArrayList<PendingIntent> sendList = new ArrayList<>();
        sendList.add(sentPI);

        ArrayList<PendingIntent> deliverList = new ArrayList<>();
        deliverList.add(deliveredPI);

        sms.sendMultipartTextMessage(phoneNumber, null, parts, sendList, deliverList);
    }
}