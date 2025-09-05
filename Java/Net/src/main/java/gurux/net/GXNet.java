//
// --------------------------------------------------------------------------
//  Gurux Ltd
// 
//
//
// Filename:        $HeadURL$
//
// Version:         $Revision$,
//                  $Date$
//                  $Author$
//
// Copyright (c) Gurux Ltd
//
//---------------------------------------------------------------------------
//
//  DESCRIPTION
//
// This file is a part of Gurux Device Framework.
//
// Gurux Device Framework is Open Source software; you can redistribute it
// and/or modify it under the terms of the GNU General Public License 
// as published by the Free Software Foundation; version 2 of the License.
// Gurux Device Framework is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of 
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
// See the GNU General Public License for more details.
//
// More information of Gurux products: http://www.gurux.org
//
// This code is licensed under the GNU General Public License v2. 
// Full text may be retrieved at http://www.gnu.org/licenses/gpl-2.0.txt
//---------------------------------------------------------------------------

package gurux.net;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.util.Log;
import android.util.Xml;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import gurux.common.GXCommon;
import gurux.common.GXSync;
import gurux.common.GXSynchronousMediaBase;
import gurux.common.IGXMedia2;
import gurux.common.IGXMediaListener;
import gurux.common.MediaStateEventArgs;
import gurux.common.PropertyChangedEventArgs;
import gurux.common.ReceiveEventArgs;
import gurux.common.ReceiveParameters;
import gurux.common.TraceEventArgs;
import gurux.common.enums.MediaState;
import gurux.common.enums.TraceLevel;
import gurux.common.enums.TraceTypes;
import gurux.net.enums.AvailableMediaSettings;
import gurux.net.enums.NetworkType;
import gurux.net.properties.PropertiesFragment;
import gurux.net.properties.PropertiesViewModel;

/**
 * The GXNet component determines methods that make the communication possible using TCP/IP or UDP
 * connection.
 */
public class GXNet implements IGXMedia2, AutoCloseable {
    /**
     * Used protocol.
     */
    private NetworkType Protocol = NetworkType.TCP;
    /**
     * Host name.
     */
    private String HostName;
    /**
     * Used port.
     */
    private int Port;
    private int receiveDelay;

    private int asyncWaitTime;

    private Closeable mSocket;

    /**
     * Receiver thread.
     */
    private GXReceiveThread mReceiver;

    /*
     * Synchronously class.
     */
    private final GXSynchronousMediaBase mSyncBase;
    /*
     * Amount of bytes sent.
     */
    private long mBytesSend = 0;
    /*
     * Synchronous counter.
     */
    private int mSynchronous = 0;
    /*
     * Trace level.
     */
    private TraceLevel mTrace = TraceLevel.OFF;
    /*
     * End of packet.
     */
    private Object mEop;
    /*
     * Configurable settings.
     */
    private int mConfigurableSettings;

    private final Context mContext;

    private Activity mActivity;

    /**
     * Media listeners.
     */
    private final List<IGXMediaListener> mMediaListeners = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param context Context.
     */
    public GXNet(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context");
        }
        mContext = context;
        mSyncBase = new GXSynchronousMediaBase(200);
        setConfigurableSettings(AvailableMediaSettings.ALL.getValue());
    }

    /**
     * Constructor.
     *
     * @param activity Activity.
     */
    public GXNet(Activity activity) {
        this((Context) activity);
        mActivity = activity;
    }

    /**
     * Constructor.
     *
     * @param networkType Used protocol.
     * @param name        Host name.
     * @param port        Host port.
     */
    public GXNet(Context context, final NetworkType networkType, final String name,
                 final int port) {
        this(context);
        Protocol = networkType;
        HostName = name;
        Port = port;
    }

    /**
     * Constructor.
     *
     * @param networkType Used protocol.
     * @param name        Host name.
     * @param portNo      Client port number.
     */
    public GXNet(final Activity activity, final NetworkType networkType, final String name,
                 final int portNo) {
        this((Context) activity);
        mActivity = activity;
    }

    /**
     * Returns synchronous class used to communicate synchronously.
     *
     * @return Synchronous class.
     */
    final GXSynchronousMediaBase getSyncBase() {
        return mSyncBase;
    }

    @Override
    protected final void finalize() throws Throwable {
        super.finalize();
        if (isOpen()) {
            close();
        }
    }

    @Override
    public final TraceLevel getTrace() {
        return mTrace;
    }

    @Override
    public final void setTrace(final TraceLevel value) {
        mTrace = value;
        mSyncBase.setTrace(value);
    }

    /**
     * Notify that property has changed.
     *
     * @param info Name of changed property.
     */
    private void notifyPropertyChanged(final String info) {
        if (mActivity != null) {
            //New data is coming from worker thread.
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    for (IGXMediaListener listener : mMediaListeners) {
                        listener.onPropertyChanged(this, new PropertyChangedEventArgs(info));
                    }
                }
            });
        } else {
            for (IGXMediaListener listener : mMediaListeners) {
                listener.onPropertyChanged(this, new PropertyChangedEventArgs(info));
            }
        }
    }

    /**
     * Notify clients from error occurred.
     *
     * @param ex Occurred error.
     */
    final void notifyError(final RuntimeException ex) {
        if (mActivity != null) {
            //New data is coming from worker thread.
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    for (IGXMediaListener listener : mMediaListeners) {
                        listener.onError(this, ex);
                        if (mTrace.ordinal() >= TraceLevel.ERROR.ordinal()) {
                            listener.onTrace(this, new TraceEventArgs(TraceTypes.ERROR, ex));
                        }
                    }
                }
            });
        } else {
            for (IGXMediaListener listener : mMediaListeners) {
                listener.onError(this, ex);
                if (mTrace.ordinal() >= TraceLevel.ERROR.ordinal()) {
                    listener.onTrace(this, new TraceEventArgs(TraceTypes.ERROR, ex));
                }
            }
        }
    }

    /**
     * Notify clients from new data received.
     *
     * @param arg Received event argument.
     */
    final void notifyReceived(final ReceiveEventArgs arg) {
        if (mActivity != null) {
            //New data is coming from worker thread.
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    for (IGXMediaListener listener : mMediaListeners) {
                        listener.onReceived(this, arg);
                    }
                }
            });
        } else {
            for (IGXMediaListener listener : mMediaListeners) {
                listener.onReceived(this, arg);
            }
        }
    }

    /**
     * Notify clients from trace events.
     *
     * @param arg Trace event argument.
     */
    final void notifyTrace(final TraceEventArgs arg) {
        if (mActivity != null) {
            //New data is coming from worker thread.
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    for (IGXMediaListener listener : mMediaListeners) {
                        listener.onTrace(this, arg);
                    }
                }
            });
        } else {
            for (IGXMediaListener listener : mMediaListeners) {
                listener.onTrace(this, arg);
            }
        }
    }

    @Override
    public final int getConfigurableSettings() {
        return mConfigurableSettings;
    }

    @Override
    public final void setConfigurableSettings(final int value) {
        mConfigurableSettings = value;
    }

    @Override
    public final void send(final Object data, final String target) throws Exception {
        send(data);
    }

    public final void send(final Object data) throws Exception {
        if (mSocket == null) {
            throw new RuntimeException("Network connection is not open.");
        }
        if (mTrace == TraceLevel.VERBOSE) {
            notifyTrace(new TraceEventArgs(TraceTypes.SENT, data));
        }
        // Reset last position if end of packet is used.
        mSyncBase.resetLastPosition();
        byte[] buff = GXSynchronousMediaBase.getAsByteArray(data);
        if (buff == null) {
            throw new IllegalArgumentException("Data send failed. Invalid data.");
        }
        final Exception[] exceltionHolder = new Exception[1];
        CountDownLatch latch = new CountDownLatch(1);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (Protocol == NetworkType.TCP) {
                        ((Socket) mSocket).getOutputStream().write(buff);

                    } else if (getProtocol() == NetworkType.UDP) {
                        InetAddress addr = InetAddress.getByName(getHostName());
                        DatagramPacket p =
                                new DatagramPacket(buff, buff.length, addr, getPort());
                        ((DatagramSocket) mSocket).send(p);
                    }
                } catch (Exception ex) {
                    exceltionHolder[0] = ex;
                } finally {
                    latch.countDown();
                }
            }
        }).start();
        latch.await();
        if (exceltionHolder[0] != null) {
            if (exceltionHolder[0] instanceof java.net.SocketException) {
                close();
                return;
            }
            throw exceltionHolder[0];
        }
        this.mBytesSend += buff.length;
    }

    /**
     * Notify client from media state change.
     *
     * @param state New media state.
     */
    private void notifyMediaStateChange(final MediaState state) {
        for (IGXMediaListener listener : mMediaListeners) {
            if (mTrace.ordinal() >= TraceLevel.ERROR.ordinal()) {
                listener.onTrace(this, new TraceEventArgs(TraceTypes.INFO, state));
            }
            listener.onMediaStateChange(this, new MediaStateEventArgs(state));
        }
    }

    @Override
    public final void open() throws Exception {
        close();
        synchronized (mSyncBase.getSync()) {
            mSyncBase.resetLastPosition();
        }
        notifyMediaStateChange(MediaState.OPENING);
        if (Protocol == NetworkType.TCP) {
            final Exception[] exceltionHolder = new Exception[1];
            final Socket[] socketHolder = new Socket[1];
            CountDownLatch latch = new CountDownLatch(1);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        socketHolder[0] = new Socket(HostName, Port);
                    } catch (Exception ex) {
                        exceltionHolder[0] = ex;
                    } finally {
                        latch.countDown();
                    }
                }
            }).start();
            latch.await();
            mSocket = socketHolder[0];
            if (mSocket != null && ((Socket) mSocket).isConnected()) {
                if (mTrace.ordinal() >= TraceLevel.INFO.ordinal()) {
                    String eopString = "None";
                    if (getEop() instanceof byte[]) {
                        eopString = GXCommon.bytesToHex((byte[]) getEop());
                    } else if (getEop() != null) {
                        eopString = getEop().toString();
                    }
                    notifyTrace(new TraceEventArgs(TraceTypes.INFO,
                            "Client settings: Protocol: "
                                    + Protocol + " Host: "
                                    + HostName + " Port: "
                                    + String.valueOf(Port)));
                }
                mReceiver = new GXReceiveThread(this, mSocket);
                mReceiver.start();
                notifyMediaStateChange(MediaState.OPEN);
            } else {
                notifyMediaStateChange(MediaState.CLOSING);
                notifyMediaStateChange(MediaState.CLOSED);
                throw exceltionHolder[0];
            }
        } else {
            mSocket = new DatagramSocket();
            mReceiver = new GXReceiveThread(this, mSocket);
            mReceiver.start();
            notifyMediaStateChange(MediaState.OPEN);
        }
    }

    @Override
    public final void close() {
        if (mSocket != null) {
            try {
                notifyMediaStateChange(MediaState.CLOSING);
            } catch (RuntimeException ex) {
                notifyError(ex);
                throw ex;
            } finally {
                try {
                    if (mSocket instanceof Socket) {
                        mReceiver.interrupt();
                        try {
                            ((Socket) mSocket).shutdownOutput();
                        } catch (Exception ignored) {
                            //This might fail if connection to the server is lost.
                        }
                        try {
                            if (mReceiver != null) {
                                // Wait until the server has send ACK.
                                mReceiver.join();
                                mReceiver = null;
                            }
                        } catch (InterruptedException e) {
                            mReceiver = null;
                        }
                    }
                    mSocket.close();
                } catch (IOException e) {
                    mSocket = null;
                    throw new RuntimeException(e.getMessage());
                }
                mSocket = null;
                notifyMediaStateChange(MediaState.CLOSED);
                mSyncBase.resetReceivedSize();
            }
        }
    }

    /**
     * Retrieves the used protocol.
     *
     * @return Protocol in use.
     */
    public final NetworkType getProtocol() {
        return Protocol;
    }

    /**
     * Sets the used protocol.
     *
     * @param value Used protocol.
     */
    public final void setProtocol(final NetworkType value) {
        if (Protocol != value) {
            Protocol = value;
            notifyPropertyChanged("Protocol");
        }
    }

    /**
     * Retrieves the name or IP address of the host.
     *
     * @return The name of the host.
     * @see #open
     * @see #Port
     * @see #Protocol
     */
    public final String getHostName() {
        return HostName;
    }

    /**
     * Sets the name or IP address of the host.
     *
     * @param value The name of the host.
     */
    public final void setHostName(final String value) {
        if (HostName == null || !HostName.equals(value)) {
            HostName = value;
            notifyPropertyChanged("HostName");
        }
    }

    /**
     * Retrieves or sets the host or server port number.
     *
     * @return Host or server port number.
     * @see #open
     * @see #HostName
     * @see #Protocol
     */
    public final int getPort() {
        return Port;
    }

    /**
     * Retrieves or sets the host or server port number.
     *
     * @param value Host or server port number
     * @see #open
     * @see #HostName
     * @see #Protocol
     */
    public final void setPort(final int value) {
        if (Port != value) {
            Port = value;
            notifyPropertyChanged("Port");
        }
    }

    @Override
    public final boolean isOpen() {
        return mSocket != null;
    }


    @Override
    public final <T> boolean receive(final ReceiveParameters<T> args) {
        return mSyncBase.receive(args);
    }

    @Override
    public final long getBytesSent() {
        return mBytesSend;
    }

    @Override
    public final long getBytesReceived() {
        return mReceiver.getBytesReceived();
    }

    @Override
    public final void resetByteCounters() {
        mBytesSend = 0;
        mReceiver.resetBytesReceived();
    }

    @Override
    public final String getSettings() {
        StringBuilder sb = new StringBuilder();
        if (HostName != null && !HostName.isEmpty()) {
            sb.append("<IP>");
            sb.append(HostName);
            sb.append("</IP>");
            sb.append(System.lineSeparator());
        }
        if (Port != 0) {
            sb.append("<Port>");
            sb.append(Port);
            sb.append("</Port>");
            sb.append(System.lineSeparator());
        }
        if (Protocol != NetworkType.TCP) {
            sb.append("<Protocol>");
            sb.append(Protocol.ordinal());
            sb.append("</Protocol>");
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    private static String readText(XmlPullParser parser) throws
            IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

    @Override
    public final void setSettings(final String value) {
        //Reset to default values.
        Protocol = NetworkType.TCP;
        HostName = null;
        Port = 0;
        if (value != null && !value.isEmpty()) {
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(new StringReader(value));
                int event;
                while ((event = parser.next()) != XmlPullParser.END_TAG && event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        String target = parser.getName();
                        boolean found = false;
                        if ("Port".equalsIgnoreCase(target)) {
                            setPort(Integer.parseInt(readText(parser)));
                        } else if ("IP".equalsIgnoreCase(target)) {
                            setHostName(readText(parser));
                        } else if ("Protocol".equalsIgnoreCase(target)) {
                            setProtocol(NetworkType.values()[Integer.parseInt(readText(parser))]);
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        }
    }

    public void properties(final Activity activity) {
        Intent intent = new Intent(activity, GXPropertiesActivity.class);
        intent.putExtra("mediaSettings", getSettings());
        activity.startActivity(intent);
    }


    public Fragment properties() {
        final PropertiesViewModel propertiesViewModel = new ViewModelProvider((ViewModelStoreOwner) mActivity).get(PropertiesViewModel.class);
        propertiesViewModel.setMedia(this);
        return new PropertiesFragment();
    }

    @Override
    public final void copy(final Object target) {
        GXNet tmp = (GXNet) target;
        setPort(tmp.getPort());
        setHostName(tmp.getHostName());
        setProtocol(tmp.getProtocol());
    }

    @Override
    public final String getName() {
        if (HostName == null) {
            return "";
        }
        return HostName + ":" + Port;
    }

    @Override
    public final String getMediaType() {
        return "Net";
    }

    @Override
    public final Object getSynchronous() {
        synchronized (this) {
            int[] tmp = new int[]{mSynchronous};
            GXSync obj = new GXSync(tmp);
            mSynchronous = tmp[0];
            return obj;
        }
    }

    @Override
    public final boolean getIsSynchronous() {
        synchronized (this) {
            return mSynchronous != 0;
        }
    }

    @Override
    public final void resetSynchronousBuffer() {
        synchronized (mSyncBase.getSync()) {
            mSyncBase.resetReceivedSize();
        }
    }

    @Override
    public final void validate() {
        if (HostName == null || HostName.isEmpty()) {
            throw new RuntimeException("Invalid hostname.");
        }
        if (Port == 0) {
            throw new RuntimeException("Invalid port.");
        }
    }

    @Override
    public final Object getEop() {
        return mEop;
    }

    @Override
    public final void setEop(final Object value) {
        mEop = value;
    }

    @Override
    public final void addListener(final IGXMediaListener listener) {
        if (mMediaListeners.contains(listener)) {
            Log.w("GXNet", "Listener already added.");
        }
        mMediaListeners.add(listener);
    }

    @Override
    public final void removeListener(final IGXMediaListener listener) {
        mMediaListeners.remove(listener);
    }

    @Override
    public int getReceiveDelay() {
        return receiveDelay;
    }

    @Override
    public void setReceiveDelay(final int value) {
        receiveDelay = value;
    }

    @Override
    public int getAsyncWaitTime() {
        return asyncWaitTime;
    }

    @Override
    public void setAsyncWaitTime(final int value) {
        asyncWaitTime = value;
    }

    @Override
    public Object getAsyncWaitHandle() {
        return null;
    }

    @Override
    public int getIconResId() {
        return R.drawable.ic_launcher_net;
    }

    @Override
    public String getVersion() {
        try {
            PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (Exception ex) {
            return null;
        }
    }
}