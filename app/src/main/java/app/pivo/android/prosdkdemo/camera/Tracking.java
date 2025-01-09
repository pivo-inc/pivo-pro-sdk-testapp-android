package app.pivo.android.prosdkdemo.camera;

public enum Tracking {
    ACTION(1),
    PERSON(2),
    HORSE(3),
    FACE(4),
    NONE(0),
    INITIAL(-1);
    int type;
    Tracking(int type){
        this.type = type;
    }
    public int getType(){
        return type;
    }
}

