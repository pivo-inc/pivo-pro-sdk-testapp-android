package app.pivo.android.prosdkdemo.camera;

public enum Tracking {
    FACE(1),
    ACTION(2),
    PERSON(3),
    DOG(4),
    CAT(5),
    HORSE(6),
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
