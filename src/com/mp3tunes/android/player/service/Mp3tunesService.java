package com.mp3tunes.android.player.service;

import java.util.Vector;

import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.LockerDb;
import com.mp3tunes.android.player.MP3tunesApplication;
import com.mp3tunes.android.player.ParcelableTrack;
import com.mp3tunes.android.player.service.ITunesService;
import com.mp3tunes.android.player.service.MediaPlayerTrack.TrackFinishedHandler;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.telephony.TelephonyManager;

public class Mp3tunesService extends Service
{
    private PlayerHandler       mPlayerHandler;
    private MusicPlayStateLocker mPlayStateLocker;
    
    private Mp3TunesPhoneStateListener mPhoneStateListener;
    private TelephonyManager           mTelephonyManager;
    
    @Override
    public void onCreate()
    {
        super.onCreate();

        // we don't want the service to be killed while playing
        setForeground(true);
        
        mPlayStateLocker = new MusicPlayStateLocker(getBaseContext());
        mPlayerHandler   = new PlayerHandler(this, getBaseContext());
        mPlayStateLocker.lock();
        
        //mTelephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        //mTelephonyManager.listen(mPhoneStateListener, Mp3TunesPhoneStateListener.LISTEN_CALL_STATE);
    }

    @Override
    public void onDestroy()
    {
        mPlayerHandler.destroy();
        mPlayStateLocker.release();
    }

    @Override
    public IBinder onBind(Intent arg0)
    {
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent)
    {
        mDeferredStopHandler.cancelStopSelf();
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent)
    {
        if (mPlayerHandler.getTrack().isPlaying())
            return true;

        mDeferredStopHandler.deferredStopSelf();
        return true;
    }
    
    /**
     * Deferred stop implementation from the five music player for android:
     * http://code.google.com/p/five/ (C) 2008 jasta00
     */
    private final DeferredStopHandler mDeferredStopHandler = new DeferredStopHandler();

    private class DeferredStopHandler extends Handler
    {

        /* Wait 1 minute before vanishing. */
        public static final long DEFERRAL_DELAY = 1 * (60 * 1000);

        private static final int DEFERRED_STOP = 0;

        public void handleMessage(Message msg)
        {

            switch (msg.what) {
                case DEFERRED_STOP:
                    stopSelf();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }

        public void deferredStopSelf()
        {
            Logger.log(this, "deferredStopSelf", "Service stop scheduled "
                    + (DEFERRAL_DELAY / 1000 / 60) + " minutes from now.");
            sendMessageDelayed(obtainMessage(DEFERRED_STOP), DEFERRAL_DELAY);
        }

        public void cancelStopSelf()
        {

            if (hasMessages(DEFERRED_STOP) == true) {
                Logger.log(this, "cancelStopSelf", "Service stop cancelled.");
                removeMessages(DEFERRED_STOP);
            }
        }
    };

    private final ITunesService.Stub mBinder = new ITunesService.Stub() {

        public int getBufferPercent() throws RemoteException
        {
            try {
                return mPlayerHandler.getTrack().getBufferPercent();
            } catch (Exception e) {
                return 0;
            } 
        }

        public long getDuration() throws RemoteException
        {
            try {
                return mPlayerHandler.getTrack().getDuration();
            } catch (Exception e) {
                return 0;
            }  
        }

        public long getPosition() throws RemoteException
        {
            try {
                return mPlayerHandler.getTrack().getPosition();
            } catch (Exception e) {
                return 0;
            } 
        }

        public boolean isPlaying() throws RemoteException
        {
            try {
                return mPlayerHandler.getTrack().isPlaying();
            } catch (Exception e) {
                return false;
            }
        }

        public void next() throws RemoteException
        {
            if (!mPlayerHandler.playNext()) throw new RemoteException();
        }

        public void pause() throws RemoteException
        {
            if (!mPlayerHandler.pause()) throw new RemoteException();
        }

        public void prev() throws RemoteException
        {
            if (!mPlayerHandler.playPrevious()) throw new RemoteException();
        }
        
        public void start() throws RemoteException
        {
            if (!mPlayerHandler.playAt(0)) throw new RemoteException();
        }

        public void startAt(int pos) throws RemoteException
        {
            if (!mPlayerHandler.playAt(pos)) throw new RemoteException();
        }

        public void stop() throws RemoteException
        {
            if (!mPlayerHandler.stop()) throw new RemoteException();
        }

        public ParcelableTrack getTrack() throws RemoteException
        {
            try {
                return new ParcelableTrack(mPlayerHandler.getTrack().getTrack());
            } catch (Exception e) {
                throw new RemoteException();
            }
        }

        public boolean isPaused() throws RemoteException
        {
            try {
                return mPlayerHandler.getTrack().isPaused();
            } catch (Exception e) {
                throw new RemoteException();
            }     
        }

        public int getQueuePosition() throws RemoteException
        {
            try {
                return mPlayerHandler.getQueuePosition();
            } catch (Exception e) {
                return -1;
            }   
        }

        public void createPlaybackList(int[] trackIds) throws RemoteException
        {
            PlaybackList list = new PlaybackList(getTracksForList(trackIds));
            mPlayerHandler.setPlaybackList(list);
        }

        public void togglePlayback() throws RemoteException
        {
            mPlayerHandler.tooglePlayback();
        }
        
    };
    
    private Vector<MediaPlayerTrack> getTracksForList(int[] trackIds)
    {
        Vector<MediaPlayerTrack> tracks = new Vector<MediaPlayerTrack>();
        LockerDb db = new LockerDb(getBaseContext(), MP3tunesApplication.getInstance().getLocker());
        for (int id : trackIds) {
            Track t = db.getTrack(id);
            MediaPlayerTrack track = new MediaPlayerTrack(t, this, getBaseContext());
            tracks.add(track);
        }
        db.close();
        return tracks;
    }
}
