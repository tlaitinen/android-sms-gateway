package fi.feriko.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.SmsMessage;
import android.util.Log;
import android.util.Pair;

import java.util.HashMap;


public class SMSReceiver extends BroadcastReceiver {
    public static final String SMS_EXTRA_NAME ="pdus";
    public static final int SMS_RECEIVED = 1;

    private Handler ownerHandler;
    public SMSReceiver(Handler oHandler) {
        ownerHandler = oHandler;

    }

    @Override
    public void onReceive( Context context, Intent intent )
    {
        // Get the SMS map from Intent
        Bundle extras = intent.getExtras();


        HashMap<String, String> msgs = new HashMap<String, String>();
        if ( extras != null )
        {
            // Get received SMS array
            Object[] smsExtra = (Object[]) extras.get( SMS_EXTRA_NAME );

            for ( int i = 0; i < smsExtra.length; ++i )
            {
                SmsMessage sms = SmsMessage.createFromPdu((byte[])smsExtra[i]);

                String body = sms.getMessageBody().toString();
                String address = sms.getOriginatingAddress();
                String prev = msgs.get(address);
                if (prev == null)
                    prev = "";

                msgs.put(address, prev + body);
            }
        }
        for (HashMap.Entry<String,String> entry : msgs.entrySet()) {
            Message msg = ownerHandler.obtainMessage(SMS_RECEIVED);
            msg.obj = new Pair<String, String>(entry.getKey(), entry.getValue());
            ownerHandler.sendMessage(msg);
        }
        // WARNING!!!
        // If you uncomment the next line then received SMS will not be put to incoming.
        // Be careful!
        // this.abortBroadcast();
    }
}
