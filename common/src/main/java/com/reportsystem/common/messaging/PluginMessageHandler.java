package com.reportsystem.common.messaging;

import java.io.*;

public class PluginMessageHandler {

    public static final String CHANNEL = "reportsystem:main";

    public enum MessageType {
        REPORT_CREATED,
        REPORT_STATUS_UPDATE,
        REPLAY_REQUEST,
        BROADCAST_MESSAGE
    }

    /**
     * Mesaj oluşturur
     */
    public static byte[] createMessage(MessageType type, Object... data) throws IOException {
        ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
        DataOutputStream msgOut = new DataOutputStream(msgBytes);

        msgOut.writeUTF(type.name());

        switch (type) {
            case REPORT_CREATED:
                msgOut.writeInt((int) data[0]); // Report ID
                msgOut.writeUTF((String) data[1]); // Reporter
                msgOut.writeUTF((String) data[2]); // Reported
                msgOut.writeUTF((String) data[3]); // Reason
                msgOut.writeUTF((String) data[4]); // Server
                break;

            case REPORT_STATUS_UPDATE:
                msgOut.writeInt((int) data[0]); // Report ID
                msgOut.writeUTF((String) data[1]); // New Status
                break;

            case REPLAY_REQUEST:
                msgOut.writeInt((int) data[0]); // Report ID
                msgOut.writeUTF((String) data[1]); // Requester
                break;

            case BROADCAST_MESSAGE:
                msgOut.writeUTF((String) data[0]); // Message
                msgOut.writeUTF((String) data[1]); // Permission
                break;
        }

        return msgBytes.toByteArray();
    }

    /**
     * Mesajı okur
     */
    public static void handleMessage(byte[] message, MessageHandler handler) throws IOException {
        DataInputStream msgIn = new DataInputStream(new ByteArrayInputStream(message));

        String typeStr = msgIn.readUTF();
        MessageType type = MessageType.valueOf(typeStr);

        switch (type) {
            case REPORT_CREATED:
                handler.onReportCreated(
                        msgIn.readInt(),    // Report ID
                        msgIn.readUTF(),    // Reporter
                        msgIn.readUTF(),    // Reported
                        msgIn.readUTF(),    // Reason
                        msgIn.readUTF()     // Server
                );
                break;

            case REPORT_STATUS_UPDATE:
                handler.onReportStatusUpdate(
                        msgIn.readInt(),    // Report ID
                        msgIn.readUTF()     // New Status
                );
                break;

            case REPLAY_REQUEST:
                handler.onReplayRequest(
                        msgIn.readInt(),    // Report ID
                        msgIn.readUTF()     // Requester
                );
                break;

            case BROADCAST_MESSAGE:
                handler.onBroadcastMessage(
                        msgIn.readUTF(),    // Message
                        msgIn.readUTF()     // Permission
                );
                break;
        }
    }

    /**
     * Mesaj handler interface
     */
    public interface MessageHandler {
        void onReportCreated(int reportId, String reporter, String reported, String reason, String server);
        void onReportStatusUpdate(int reportId, String newStatus);
        void onReplayRequest(int reportId, String requester);
        void onBroadcastMessage(String message, String permission);
    }
}