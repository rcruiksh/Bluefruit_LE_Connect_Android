package com.adafruit.bluefruit.le.connect.mqtt;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.adafruit.bluefruit.le.connect.R;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.android.service.MqttTraceHandler;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MqttManager implements IMqttActionListener, MqttCallback, MqttTraceHandler {
    // Log
    private final static String TAG = MqttManager.class.getSimpleName();

    // Singleton
    private static MqttManager mInstance = null;

    // Types
    public enum MqqtConnectionStatus {
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED,
        ERROR,
        NONE
    }

    public static int MqqtQos_AtMostOnce = 0;
    public static int MqqtQos_AtLeastOnce = 1;
    public static int MqqtQos_ExactlyOnce = 2;

    // Data
    private MqttAndroidClient mMqttClient;
    private MqttManagerListener mListener;
    private MqqtConnectionStatus mMqqtClientStatus = MqqtConnectionStatus.NONE;
    private Context mContext;

    public static MqttManager getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new MqttManager(context);
        }
        return mInstance;
    }

    public MqttManager(Context context) {
        mContext = context.getApplicationContext();
    }

    @Override
    public void finalize() throws Throwable {

        try {
            if (mMqttClient != null) {
                mMqttClient.unregisterResources();
            }
        } finally {
            super.finalize();
        }
    }


    public MqqtConnectionStatus getClientStatus() {
        return mMqqtClientStatus;
    }

    public void setListener(MqttManagerListener listener) {
        mListener = listener;
    }

    // region MQTT
    public void subscribe(String topic, int qos) {
        if (mMqttClient != null && mMqqtClientStatus == MqqtConnectionStatus.CONNECTED) {
            try {
                Log.d(TAG, "Mqtt: subscribe to " + topic + " qos:" + qos);
                mMqttClient.subscribe(topic, qos);
            } catch (MqttException e) {
                Log.e(TAG, "Mqtt:x subscribe error: ", e);
            }
        }
    }

    public void unsubscribe(String topic) {
        if (mMqttClient != null && mMqqtClientStatus == MqqtConnectionStatus.CONNECTED) {
            try {
                Log.d(TAG, "Mqtt: unsubscribe from " + topic);
                mMqttClient.unsubscribe(topic);
            } catch (MqttException e) {
                Log.e(TAG, "Mqtt:x unsubscribe error: ", e);
            }
        }

    }


    public void publish(String topic, String payload, int qos) {
        if (mMqttClient != null && mMqqtClientStatus == MqqtConnectionStatus.CONNECTED) {
            boolean retained = false;

            try {
                Log.d(TAG, "Mqtt: publish " + payload + " for topic " + topic + " qos:" + qos);
                mMqttClient.publish(topic, payload.getBytes(), qos, retained, null, null);
            } catch (MqttException e) {
                Log.e(TAG, "Mqtt:x publish error: ", e);
            }
        }
    }

    public void disconnect() {
        if (mMqttClient != null && mMqqtClientStatus == MqqtConnectionStatus.CONNECTED) {
            try {
                Log.d(TAG, "Mqtt: disconnect");
//                mMqqtClientStatus = MqqtConnectionStatus.DISCONNECTING;
                mMqqtClientStatus = MqqtConnectionStatus.DISCONNECTED;      // Note: it seems that the disconnected callback is never invoked. So we fake here that the final state is disconnected
                mMqttClient.disconnect(null, this);

                mMqttClient.unregisterResources();
                mMqttClient = null;
            } catch (MqttException e) {
                Log.e(TAG, "Mqtt:x disconnection error: ", e);
            }
        }
    }

    public void connectFromSavedSettings(Context context) {
        MqttSettings settings = MqttSettings.getInstance(context);
        String host = settings.getServerAddress();
        int port = settings.getServerPort();

        connect(context, host, port);
    }

    public void connect(Context context, String host, int port) {
        boolean sslConnection = false;
        String clientId = "Bluefruit";
        boolean cleanSession = true;
        int timeout = 1000;
        int keepalive = 10;
        String username = null;
        String password = null;

        String message = null;
        String topic = null;
        int qos = 0;
        boolean retained = false;

        String uri;
        if (sslConnection) {
            uri = "ssl://" + host + ":" + port;

        } else {
            uri = "tcp://" + host + ":" + port;
        }
        String handle = uri + clientId;

        Log.d(TAG, "Mqtt: Create client");
        mMqttClient = new MqttAndroidClient(context, uri, clientId);
        mMqttClient.registerResources(mContext);

        MqttConnectOptions conOpt = new MqttConnectOptions();
        conOpt.setCleanSession(cleanSession);
        conOpt.setConnectionTimeout(timeout);
        conOpt.setKeepAliveInterval(keepalive);
        if (username != null && username.length() > 0) {
            conOpt.setUserName(username);
        }
        if (password != null && password.length() > 0) {
            conOpt.setPassword(password.toCharArray());
        }

        boolean doConnect = true;
        if ((message != null && message.length() > 0) || (topic != null && topic.length() > 0)) {
            // need to make a message since last will is set
            Log.d(TAG, "Mqtt: setwill");
            try {
                conOpt.setWill(topic, message.getBytes(), qos, retained);
            } catch (Exception e) {
                Log.e(TAG, "Mqtt: Can't set will", e);
                doConnect = false;
                //callback.onFailure(null, e);
            }
        }
        mMqttClient.setCallback(this);
        mMqttClient.setTraceCallback(this);

        if (doConnect) {
            try {
                Log.d(TAG, "Mqtt: connect");
                mMqqtClientStatus = MqqtConnectionStatus.CONNECTING;
                mMqttClient.connect(conOpt, null, this);
            } catch (MqttException e) {
                Log.e(TAG, "Mqtt:x connection error: ", e);
            }
        }
    }

    // endregion

    // region IMqttActionListener
    @Override
    public void onSuccess(IMqttToken iMqttToken) {
        if (mMqqtClientStatus == MqqtConnectionStatus.CONNECTING) {
            Log.d(TAG, "Mqtt connect onSuccess");
            mMqqtClientStatus = MqqtConnectionStatus.CONNECTED;
            if (mListener != null) mListener.onMqttConnected();
            MqttSettings settings = MqttSettings.getInstance(mContext);
            String topic = settings.getSubscribeTopic();
            if (settings.isSubscribeEnabled() && topic != null) {
                subscribe(topic, MqqtQos_ExactlyOnce);
            }
        } else if (mMqqtClientStatus == MqqtConnectionStatus.DISCONNECTING) {
            Log.d(TAG, "Mqtt disconnect onSuccess");
            mMqqtClientStatus = MqqtConnectionStatus.DISCONNECTED;
            if (mListener != null) mListener.onMqttDisconnected();
        } else {
            Log.d(TAG, "Mqtt unknown onSuccess");
        }
    }

    @Override
    public void onFailure(IMqttToken iMqttToken, Throwable throwable) {
        Log.d(TAG, "Mqtt onFailure. " + throwable);
        mMqqtClientStatus = MqqtConnectionStatus.ERROR;

        if (mListener != null) mListener.onMqttDisconnected();

        Toast.makeText(mContext, R.string.mqtt_connection_failed, Toast.LENGTH_LONG).show();
    }
    // endregion

    // region MqttCallback
    @Override
    public void connectionLost(Throwable throwable) {
        Log.d(TAG, "Mqtt connectionLost. " + throwable);

        if (throwable != null) {        // if disconnected because a reason show toast. Standard disconnect will have a null throwable
            Toast.makeText(mContext, R.string.mqtt_connection_lost, Toast.LENGTH_LONG).show();
        }

        mMqqtClientStatus = MqqtConnectionStatus.DISCONNECTED;

        if (mListener != null) {
            mListener.onMqttDisconnected();
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
        String message = new String(mqttMessage.getPayload());

        Log.d(TAG, "Mqtt messageArrived from topic: " + topic + " message: " + message + " isDuplicate: " + (mqttMessage.isDuplicate() ? "yes" : "no"));
        if (mListener != null) {
            mListener.onMqttMessageArrived(topic, mqttMessage);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        Log.d(TAG, "Mqtt deliveryComplete");

    }

    // endregion

    // region MqttTraceHandler
    @Override
    public void traceDebug(String source, String message) {
        Log.d(TAG, "Mqtt traceDebug");

    }

    @Override
    public void traceError(String source, String message) {
        Log.d(TAG, "Mqtt traceError");

    }

    @Override
    public void traceException(String source, String message, Exception e) {
        Log.d(TAG, "Mqtt traceException");

    }

    // endregion


    public interface MqttManagerListener {
        void onMqttConnected();

        void onMqttDisconnected();

        void onMqttMessageArrived(String topic, MqttMessage mqttMessage);
    }
}
