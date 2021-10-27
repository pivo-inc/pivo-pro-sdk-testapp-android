package app.pivo.android.prosdkdemo;

public interface Bound extends org.ros.internal.message.Message {
    static final java.lang.String_Type="rosjava_pivo/Bound";
    static final java.lang.String_DEFINITION="double64 x1 \n double64 y1 \n double64 x2 \n double64 y2";

    long getA();

    void SetA(long value);

    String getB();

    void setB(String value);
}
