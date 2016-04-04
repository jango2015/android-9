package net.cyclestreets.liveride;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import net.cyclestreets.routing.Segment;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

/**
 * Created by jsinglet on 20/02/2015.
 */
public class PebbleNotifier {

  private static final String BROADCAST_PEBBLE_MESSAGE=PebbleNotifier.class.getName() + ".message";
  // UUID generated by pebble app creation
  private static final UUID APP_UID = UUID.fromString("7b99db93-2503-4d6e-a503-67353132a90c");
  public static final String TAG = "CS_PEBBLE";
  private final Context context;
  private int transactionId = 1;



  private boolean isSending = false;


  private Queue<PebbleDictionary> messageQueue = new LinkedList<PebbleDictionary>();
  private BroadcastReceiver pebbleMessageReceiver;
  private PebbleKit.PebbleAckReceiver pebbleAckReceiver;
  private PebbleKit.PebbleNackReceiver pebbleNackReceiver;

  private enum PebbleMessages {
    turn(0),
    street(1),
    distance(2),
    running(3),
    instruction(4),
    stateType(5);

    private final int key;

    PebbleMessages(int key) {
      this.key = key;
    }

    public int getKey() {
      return key;
    }

  }

  public PebbleNotifier(Context context) {
    this.context = context;
    pebbleMessageReceiver = new BroadcastReceiver() {

      @Override
      public void onReceive(Context context, Intent intent) {
        if (isSending() == false && !messageQueue.isEmpty()) {
          setSending(true);
          PebbleDictionary dictionary = messageQueue.peek();
          if (dictionary != null) {
            int txnId = nextTransactionId();
            Log.d(TAG, "sending message " + txnId + " Queue size: " + messageQueue.size());
            PebbleKit.sendDataToPebbleWithTransactionId(context, APP_UID, dictionary, txnId);
          }
        }
      }
    };

    pebbleAckReceiver = new PebbleKit.PebbleAckReceiver(APP_UID) {

      @Override
      public void receiveAck(Context context, int transactionId) {
        Log.i(TAG, "Received ack for transaction " + transactionId);
        messageQueue.remove();
        setSending(false);
        context.sendBroadcast(new Intent(BROADCAST_PEBBLE_MESSAGE));
      }

    };

    pebbleNackReceiver = new PebbleKit.PebbleNackReceiver(APP_UID) {

      @Override
      public void receiveNack(Context context, int transactionId) {
        Log.i(TAG, "Received nack for transaction " + transactionId + ", resending");
        setSending(false);
        context.sendBroadcast(new Intent(BROADCAST_PEBBLE_MESSAGE));
      }

    };
  }

  private void registerReceivers() {
    Log.d(TAG, "register");
    context.registerReceiver(pebbleMessageReceiver, new IntentFilter(BROADCAST_PEBBLE_MESSAGE));
    PebbleKit.registerReceivedAckHandler(context, pebbleAckReceiver);
    PebbleKit.registerReceivedNackHandler(context, pebbleNackReceiver);
  }

  private void unregisterReceivers() {
    Log.d(TAG, "unregister");
    context.unregisterReceiver(pebbleMessageReceiver);
    context.unregisterReceiver(pebbleAckReceiver);
    context.unregisterReceiver(pebbleNackReceiver);

  }

  private synchronized void addMessage(PebbleDictionary dictionary) {
    messageQueue.add(dictionary);
  }


  public boolean isConnected() {
    return PebbleKit.isWatchConnected(context);
  }

  public void connectIfNeeded() {
    if (!isConnected()) {
      Log.i(TAG, "Received ack for transaction " + transactionId);
    }
  }

  public void notifyStopped() {
    Log.d(TAG, "Stopping App");
    if (isConnected()) {
      PebbleKit.closeAppOnPebble(this.context, APP_UID);
      unregisterReceivers();
    }
  }

  public void notifyStart(LiveRideState state, Segment seg) {
    Log.d(TAG, "Starting App");
    registerReceivers();
    messageQueue.clear();
    PebbleKit.startAppOnPebble(this.context, APP_UID);
    PebbleDictionary dictionary = new PebbleDictionary();
    dictionary.addString(PebbleMessages.street.getKey(), "Starting Ride");
    dictionary.addString(PebbleMessages.stateType.getKey(), state.getClass().getSimpleName());

    Log.i(TAG, "NotifyStart " + state.getClass().getSimpleName() + " :: "+ seg.turn() + " into " + seg.street());
    messageQueue.add(dictionary);
    context.sendBroadcast(new Intent(BROADCAST_PEBBLE_MESSAGE));
  }

  public void notify(LiveRideState state) {
    if (isConnected()) {
      PebbleDictionary dictionary = new PebbleDictionary();
      dictionary.addString(PebbleMessages.stateType.getKey(), state.getClass().getSimpleName());

      Log.i(TAG, "Notifying State " + state.getClass().getSimpleName());
      messageQueue.add(dictionary);
      context.sendBroadcast(new Intent(BROADCAST_PEBBLE_MESSAGE));
    }
  }

  public void notify(LiveRideState state, Segment seg) {
    if (isConnected()) {
      PebbleDictionary dictionary = new PebbleDictionary();
      if (seg != null) {
        dictionary.addString(PebbleMessages.turn.getKey(), seg.turn());
        dictionary.addString(PebbleMessages.street.getKey(), seg.street());
        dictionary.addString(PebbleMessages.running.getKey(), seg.runningDistance());
        dictionary.addString(PebbleMessages.distance.getKey(), seg.distance());
        Log.i(TAG, "Notifying " + state.getClass().getSimpleName() + " :: " + seg.turn() + " into " + seg.street());
      } else {
        Log.i(TAG, "Notifying " + state.getClass().getSimpleName() + " :: with null segment");
      }
      dictionary.addString(PebbleMessages.stateType.getKey(), state.getClass().getSimpleName());

      messageQueue.add(dictionary);
      context.sendBroadcast(new Intent(BROADCAST_PEBBLE_MESSAGE));
    }
  }

  private int nextTransactionId() {
    return this.transactionId++;
  }

  public synchronized boolean isSending() {
    return isSending;
  }

  public synchronized void setSending(boolean isSending) {
    this.isSending = isSending;
  }

}
