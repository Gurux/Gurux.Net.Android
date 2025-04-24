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
// Gurux Device Framework is distributed mInput the hope that it will be useful,
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

import java.io.Closeable;
import java.io.DataInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

import gurux.common.GXSynchronousMediaBase;
import gurux.common.ReceiveEventArgs;
import gurux.common.TraceEventArgs;
import gurux.common.enums.TraceLevel;
import gurux.common.enums.TraceTypes;

/**
 * Receive thread listens network connection and sends received data to the listeners.
 *
 * @author Gurux Ltd.
 */
class GXReceiveThread extends Thread {

    /**
     * Size of receive buffer. Ethernet maximum frame size is 1518 bytes.
     */
    public static final int RECEIVE_BUFFER_SIZE = 1518;

    /**
     * Parent component where notifies are send.
     */
    private final GXNet mParentMedia;

    private final Closeable mSocket;

    /**
     * Amount of bytes received.
     */
    private long mBytesReceived = 0;

    /**
     * Constructor.
     *
     * @param parent Parent component.
     */
    GXReceiveThread(final GXNet parent, final Closeable socket) {
        mParentMedia = parent;
        mSocket = socket;
    }

    /**
     * Get amount of received bytes.
     *
     * @return Amount of received bytes.
     */
    final long getBytesReceived() {
        return mBytesReceived;
    }

    /**
     * Reset amount of received bytes.
     */
    final void resetBytesReceived() {
        mBytesReceived = 0;
    }

    /**
     * Handle received data.
     *
     * @param length Length of received data.
     * @param info   Sender information.
     */
    private void handleReceivedData(final byte[] buffer, final int length, final String info) {
        if (length == 0) {
            return;
        }
        Object eop = mParentMedia.getEop();
        mBytesReceived += length;
        int totalCount = 0;
        if (mParentMedia.getIsSynchronous()) {
            TraceEventArgs arg = null;
            synchronized (mParentMedia.getSyncBase().getSync()) {
                mParentMedia.getSyncBase().appendData(buffer, 0, length);
                // Search end of packet if it is given.
                if (eop != null) {
                    if (eop instanceof Object[]) {
                        for (Object it : (Object[]) eop) {
                            totalCount = GXSynchronousMediaBase.indexOf(buffer,
                                    GXSynchronousMediaBase.getAsByteArray(it),
                                    0, length);
                            if (totalCount != -1) {
                                break;
                            }
                        }
                    } else {
                        totalCount = GXSynchronousMediaBase.indexOf(buffer,
                                GXSynchronousMediaBase.getAsByteArray(eop), 0,
                                length);
                    }
                }
                if (totalCount != -1) {
                    if (mParentMedia.getTrace() == TraceLevel.VERBOSE) {
                        arg = new gurux.common.TraceEventArgs(
                                TraceTypes.RECEIVED, buffer, 0, totalCount + 1);
                    }
                    mParentMedia.getSyncBase().setReceived();
                }
            }
            if (arg != null) {
                mParentMedia.notifyTrace(arg);
            }
        } else {
            mParentMedia.getSyncBase().resetReceivedSize();
            byte[] data = new byte[length];
            System.arraycopy(buffer, 0, data, 0, length);
            if (mParentMedia.getTrace() == TraceLevel.VERBOSE) {
                mParentMedia.notifyTrace(new gurux.common.TraceEventArgs(
                        TraceTypes.RECEIVED, data));
            }
            ReceiveEventArgs e = new ReceiveEventArgs(data, info);
            mParentMedia.notifyReceived(e);
        }
    }

    @Override
    public final void run() {
        byte[] buffer = new byte[RECEIVE_BUFFER_SIZE];
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (mSocket instanceof Socket) {
                    Socket s = (Socket) mSocket;
                    DataInputStream in = new DataInputStream(s.getInputStream());
                    int count = in.read(buffer);
                    if (count == -1) {
                        in.close();
                        throw new SocketException();
                    }
                    handleReceivedData(buffer, count, s.getRemoteSocketAddress().toString());
                }else {
                    DatagramSocket s= (DatagramSocket) mSocket;
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    s.receive(packet);
                    InetSocketAddress socketAddress =
                            (InetSocketAddress) packet
                                    .getSocketAddress();
                    String address = socketAddress.getHostName() + ":" + socketAddress.getPort();
                    handleReceivedData(buffer, packet.getLength(), address);
                }
            } catch (Exception ex) {
                if (!Thread.currentThread().isInterrupted()) {
                    if (mSocket instanceof Socket) {
                        Socket s = (Socket) mSocket;
                        if (s.isClosed()) {
                            mParentMedia.close();
                        } else {
                            mParentMedia.notifyError(new RuntimeException(ex.getMessage()));
                        }
                    }
                }
            }
        }
    }
}