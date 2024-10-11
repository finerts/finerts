package junit.kr.ac.korea.agent.recorder;

import junit.kr.ac.korea.agent.instrumentor.Strategy.Context;


public interface TraceCallee {
    public void traceClassName(Context context);

    public void traceMethodAndClass(Context context);

    public void traceInstanceMethod(Context context);

    public void traceRuntimeMethod(Context context);
}
