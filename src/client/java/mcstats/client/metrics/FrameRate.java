package mcstats.client.metrics;

/** Allocation-free half-second completed-frame window, independent of ticks and UI refresh. */
public final class FrameRate {
    private static final long WINDOW=500_000_000L;
    private final long[] timestamps=new long[8192],durations=new long[8192];
    private long previous,total,sessionNanos,sessionFrames;
    private int head,size;
    public double record(long now) {
        if(previous==0){previous=now;return Double.NaN;}
        if(now<=previous)throw new IllegalArgumentException("Frame timestamps must increase");
        long elapsed=now-previous;previous=now;
        if(size==timestamps.length)removeFirst();
        int i=(head+size++)%timestamps.length;timestamps[i]=now;durations[i]=elapsed;total+=elapsed;
        expire(now);sessionNanos+=elapsed;sessionFrames++;
        return elapsed/1e6;
    }
    private void removeFirst(){total-=durations[head];head=(head+1)%timestamps.length;size--;}
    private void expire(long now){while(size>0&&now-timestamps[head]>=WINDOW)removeFirst();}
    public double publish(long now){expire(now);return size==0?Double.NaN:1e9*size/total;}
    public double sessionFps(){return sessionFrames==0?Double.NaN:1e9*sessionFrames/sessionNanos;}
    public void reset(){previous=total=sessionNanos=sessionFrames=0;head=size=0;}
}
