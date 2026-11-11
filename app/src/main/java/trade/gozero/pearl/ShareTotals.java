package trade.gozero.pearl;

/** Native counters start at zero for each process; each backend owns one instance. */
final class ShareTotals {
    private int baseAccepted,baseRejected,currentAccepted,currentRejected;
    void update(int accepted,int rejected){currentAccepted=Math.max(currentAccepted,accepted);currentRejected=Math.max(currentRejected,rejected);}
    int accepted(){return baseAccepted+currentAccepted;}
    int rejected(){return baseRejected+currentRejected;}
    void nextProcess(){baseAccepted+=currentAccepted;baseRejected+=currentRejected;currentAccepted=currentRejected=0;}
    void reset(){baseAccepted=baseRejected=currentAccepted=currentRejected=0;}
}
